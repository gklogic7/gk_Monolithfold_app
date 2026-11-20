package com.example.data.vault

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.VaultItemDao
import com.example.data.local.VaultItemEntity
import com.example.data.storage.StorageRepository
import com.example.model.FileEntry
import com.example.model.FileSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class VaultRepository(
    private val context: Context,
    private val vaultItemDao: VaultItemDao,
    private val storageRepository: StorageRepository
) {
    private val vaultDir: File
        get() = File(context.filesDir, ".vault").apply {
            if (!exists()) mkdirs()
            val nomedia = File(this, ".nomedia")
            if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
        }

    private val previewCacheDir: File
        get() = File(context.cacheDir, "vault_ephemeral").apply {
            if (!exists()) mkdirs()
        }

    val vaultItemsFlow: Flow<List<VaultItemEntity>> = vaultItemDao.observeAll()

    fun hashPin(pin: String): Pair<String, String> {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derivePinHash(pin, salt)
        return Base64.encodeToString(hash, Base64.NO_WRAP) to
            Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, storedHashBase64: String, storedSaltBase64: String): Boolean {
        return runCatching {
            val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(storedHashBase64, Base64.NO_WRAP)
            val actual = derivePinHash(pin, salt)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 65_536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrElse {
            // Local fallback for JVM/Robolectric test environments without AndroidKeyStore
            val keyFile = File(vaultDir, ".fallback_k")
            val raw = if (keyFile.exists()) {
                keyFile.readBytes()
            } else {
                ByteArray(32).also {
                    SecureRandom().nextBytes(it)
                    keyFile.writeBytes(it)
                }
            }
            SecretKeySpec(raw, "AES")
        }
    }

    suspend fun encryptEntry(entry: FileEntry, deleteOriginal: Boolean): Result<VaultItemEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(!entry.isDirectory) { "Select individual files to encrypt into the Vault" }
                val id = UUID.randomUUID().toString()
                val encryptedFileName = "$id.mvlt"
                val destFile = File(vaultDir, encryptedFileName)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
                val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

                storageRepository.openInputStream(entry).use { input ->
                    CipherOutputStream(FileOutputStream(destFile), cipher).use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                }

                val entity = VaultItemEntity(
                    id = id,
                    originalName = entry.name,
                    mimeType = entry.mimeType,
                    originalSizeBytes = entry.sizeBytes,
                    originalPathOrUri = entry.pathOrUri,
                    encryptedFileName = encryptedFileName,
                    ivBase64 = ivBase64
                )
                vaultItemDao.insert(entity)

                if (deleteOriginal) {
                    storageRepository.deleteEntries(listOf(entry))
                }
                entity
            }
        }

    suspend fun encryptUri(uri: Uri): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = DocumentFile.fromSingleUri(context, uri)
            val name = doc?.name ?: "vault_item_${System.currentTimeMillis()}"
            val mime = doc?.type ?: StorageRepository.guessMimeType(name)
            val size = doc?.length() ?: 0L

            val id = UUID.randomUUID().toString()
            val encryptedFileName = "$id.mvlt"
            val destFile = File(vaultDir, encryptedFileName)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
            val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

            var copiedBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                CipherOutputStream(FileOutputStream(destFile), cipher).use { output ->
                    copiedBytes = input.copyTo(output, bufferSize = 32 * 1024)
                }
            } ?: error("Cannot read selected file")

            val entity = VaultItemEntity(
                id = id,
                originalName = name,
                mimeType = mime,
                originalSizeBytes = if (size > 0L) size else copiedBytes,
                originalPathOrUri = uri.toString(),
                encryptedFileName = encryptedFileName,
                ivBase64 = ivBase64
            )
            vaultItemDao.insert(entity)
            entity
        }
    }

    suspend fun decryptToEphemeralFile(item: VaultItemEntity): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedFile = File(vaultDir, item.encryptedFileName)
            require(encryptedFile.exists()) { "Encrypted payload not found" }

            val outFile = File(previewCacheDir, "${item.id}_${item.originalName.replace('/', '_')}")
            decryptFileInternal(encryptedFile, outFile, item.ivBase64)
            outFile
        }
    }

    suspend fun exportAndDecrypt(item: VaultItemEntity, removeFromVault: Boolean): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encryptedFile = File(vaultDir, item.encryptedFileName)
                require(encryptedFile.exists()) { "Encrypted payload not found" }
                val restoreDir = File(storageRepository.workspaceDir, "Restored").apply { mkdirs() }
                val destFile = storageRepository.uniqueFile(restoreDir, item.originalName)
                decryptFileInternal(encryptedFile, destFile, item.ivBase64)
                if (removeFromVault) {
                    encryptedFile.delete()
                    vaultItemDao.deleteById(item.id)
                }
                destFile
            }
        }

    suspend fun deleteVaultItem(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        val encryptedFile = File(vaultDir, item.encryptedFileName)
        if (encryptedFile.exists()) encryptedFile.delete()
        vaultItemDao.deleteById(item.id)
    }

    suspend fun wipeEphemeralCache() = withContext(Dispatchers.IO) {
        previewCacheDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun decryptFileInternal(encryptedFile: File, destFile: File, ivBase64: String) {
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(128, iv))
        CipherInputStream(FileInputStream(encryptedFile), cipher).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "monolith_vault_aes256_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

fun VaultItemEntity.toVirtualFileEntry(decryptedPath: String): FileEntry = FileEntry(
    id = id,
    name = originalName,
    pathOrUri = decryptedPath,
    parentPathOrUri = null,
    isDirectory = false,
    sizeBytes = originalSizeBytes,
    lastModifiedMs = addedAtMs,
    mimeType = mimeType,
    sourceType = FileSourceType.FILE
)

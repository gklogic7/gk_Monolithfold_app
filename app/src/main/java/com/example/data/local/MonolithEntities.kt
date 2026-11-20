package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val pathOrUri: String,
    val label: String,
    val sourceType: String,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "saf_mounts")
data class SafMountEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val pathOrUri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceType: String,
    val openedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val originalName: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val originalPathOrUri: String,
    val encryptedFileName: String,
    val ivBase64: String,
    val addedAtMs: Long = System.currentTimeMillis()
)

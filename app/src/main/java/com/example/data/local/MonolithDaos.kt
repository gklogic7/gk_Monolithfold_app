package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE pathOrUri = :pathOrUri")
    suspend fun delete(pathOrUri: String)
}

@Dao
interface SafMountDao {
    @Query("SELECT * FROM saf_mounts ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<SafMountEntity>>

    @Query("SELECT * FROM saf_mounts ORDER BY addedAtMs DESC")
    suspend fun getAll(): List<SafMountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mount: SafMountEntity)

    @Query("DELETE FROM saf_mounts WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)
}

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY openedAtMs DESC LIMIT 20")
    fun observeRecent(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE pathOrUri = :pathOrUri")
    suspend fun delete(pathOrUri: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

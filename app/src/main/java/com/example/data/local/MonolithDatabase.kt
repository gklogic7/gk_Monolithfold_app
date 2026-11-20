package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        SafMountEntity::class,
        RecentFileEntity::class,
        VaultItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MonolithDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun safMountDao(): SafMountDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        @Volatile
        private var instance: MonolithDatabase? = null

        fun getInstance(context: Context): MonolithDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonolithDatabase::class.java,
                    "monolith_files.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

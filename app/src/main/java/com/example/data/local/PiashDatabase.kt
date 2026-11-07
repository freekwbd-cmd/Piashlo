package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntryEntity::class,
        SkillEntity::class,
        McpServerEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PiashDatabase : RoomDatabase() {
    abstract fun dao(): PiashDao

    companion object {
        @Volatile
        private var INSTANCE: PiashDatabase? = null

        fun getInstance(context: Context): PiashDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PiashDatabase::class.java,
                    "piash_agent_workspace.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

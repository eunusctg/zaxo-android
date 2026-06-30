package com.zaxo.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zaxo.app.data.dao.*
import com.zaxo.app.model.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        Chat::class,
        Message::class,
        Status::class,
        StatusView::class,
        MutedStatus::class,
        BlockedCaller::class,
        ChatNotificationSettings::class,
        CallRecord::class
    ],
    version = 7,
    exportSchema = false
)
abstract class ZaxoDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun statusDao(): StatusDao
    abstract fun mutedStatusDao(): MutedStatusDao
    abstract fun blockedCallerDao(): BlockedCallerDao
    abstract fun chatNotificationSettingsDao(): ChatNotificationSettingsDao
    abstract fun callHistoryDao(): CallHistoryDao
}

/**
 * Migration v3 → v4: Add muted_statuses table for status mute feature.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS muted_statuses (
                id TEXT NOT NULL PRIMARY KEY,
                mutedUserId TEXT NOT NULL,
                mutedUserName TEXT NOT NULL,
                mutedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_muted_statuses_mutedUserId ON muted_statuses(mutedUserId)")
    }
}

/**
 * F14: Migration from v2 to v3 — adds FTS4 virtual table for message search.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts 
            USING fts4(content='messages', content)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO messages_fts (docid, content)
            SELECT rowid, content FROM messages WHERE isDeleted = 0 AND content != ''
        """.trimIndent())
        db.execSQL("ALTER TABLE messages ADD COLUMN syncState TEXT NOT NULL DEFAULT 'synced'")
    }
}

/**
 * F29: Migration from v4 to v5 — adds syncState column to statuses table
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE statuses ADD COLUMN syncState TEXT NOT NULL DEFAULT 'synced'")
    }
}

/**
 * Migration v6 → v7: Add new columns to call_history for calling system.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE call_history ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'audio'")
        db.execSQL("ALTER TABLE call_history ADD COLUMN isGroupCall INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE call_history ADD COLUMN groupId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE call_history ADD COLUMN groupName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE call_history ADD COLUMN roomId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE call_history ADD COLUMN cachedName TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_history_contactId ON call_history(contactId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_history_timestamp ON call_history(timestamp)")
    }
}

/**
 * Migration v5 → v6: Add blocked_callers, chat_notification_settings, and call_history tables.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Blocked callers table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS blocked_callers (
                id TEXT NOT NULL PRIMARY KEY,
                blockedUserId TEXT NOT NULL,
                blockedUserName TEXT NOT NULL,
                blockedUserZaxoNumber TEXT NOT NULL,
                blockedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_callers_blockedUserId ON blocked_callers(blockedUserId)")

        // Chat notification settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_notification_settings (
                chatId TEXT NOT NULL PRIMARY KEY,
                isMuted INTEGER NOT NULL DEFAULT 0,
                soundEnabled INTEGER NOT NULL DEFAULT 1,
                vibrationEnabled INTEGER NOT NULL DEFAULT 1,
                soundUri TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_notification_settings_chatId ON chat_notification_settings(chatId)")

        // Call history table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS call_history (
                id TEXT NOT NULL PRIMARY KEY,
                contactId TEXT NOT NULL,
                contactName TEXT NOT NULL,
                contactPhotoUrl TEXT NOT NULL,
                callType TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                duration INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZaxoDatabase {
        return Room.databaseBuilder(
            context,
            ZaxoDatabase::class.java,
            "zaxo_database"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatDao(database: ZaxoDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageDao(database: ZaxoDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideStatusDao(database: ZaxoDatabase): StatusDao = database.statusDao()

    @Provides
    fun provideMutedStatusDao(database: ZaxoDatabase): MutedStatusDao = database.mutedStatusDao()

    @Provides
    fun provideBlockedCallerDao(database: ZaxoDatabase): BlockedCallerDao = database.blockedCallerDao()

    @Provides
    fun provideChatNotificationSettingsDao(database: ZaxoDatabase): ChatNotificationSettingsDao =
        database.chatNotificationSettingsDao()

    @Provides
    fun provideCallHistoryDao(database: ZaxoDatabase): CallHistoryDao = database.callHistoryDao()
}

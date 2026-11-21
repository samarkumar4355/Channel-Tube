package com.example.channeltube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChannelEntity::class,
        VideoEntity::class,
        CustomPlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_playlists` (
                        `playlistId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_items` (
                        `playlistId` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `publishedAt` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `videoId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `custom_playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_videoId` ON `playlist_items` (`videoId`)")
                db.execSQL("INSERT OR IGNORE INTO `custom_playlists` (`playlistId`, `title`, `createdAt`, `updatedAt`) VALUES ('watch_later', 'Watch Later', ${System.currentTimeMillis()}, ${System.currentTimeMillis()})")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `duration` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `playlist_items` ADD COLUMN `duration` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

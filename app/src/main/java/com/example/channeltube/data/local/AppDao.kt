package com.example.channeltube.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM channels ORDER BY addedAt DESC")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY addedAt DESC")
    suspend fun getChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getChannel(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE channelId = :channelId LIMIT 1")
    fun observeChannel(channelId: String): Flow<ChannelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    @Query("SELECT * FROM videos ORDER BY publishedAt DESC")
    fun observeVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY publishedAt DESC")
    fun observeVideosForChannel(channelId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY publishedAt DESC")
    suspend fun getVideosForChannel(channelId: String): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE channelId = :channelId")
    suspend fun deleteVideosForChannel(channelId: String)

    @Query("SELECT * FROM videos WHERE duration = '' LIMIT 100")
    suspend fun getVideosMissingDuration(): List<VideoEntity>

    // ── Custom Playlists ────────────────────────────────────────────────────────

    @Query("SELECT * FROM custom_playlists ORDER BY CASE WHEN playlistId = 'watch_later' THEN 0 ELSE 1 END, updatedAt DESC")
    fun observeCustomPlaylists(): Flow<List<CustomPlaylistEntity>>

    @Query("SELECT * FROM custom_playlists ORDER BY CASE WHEN playlistId = 'watch_later' THEN 0 ELSE 1 END, updatedAt DESC")
    suspend fun getCustomPlaylists(): List<CustomPlaylistEntity>

    @Query("SELECT * FROM custom_playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getCustomPlaylist(playlistId: String): CustomPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPlaylist(playlist: CustomPlaylistEntity)

    @Query("UPDATE custom_playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updatePlaylistTimestamp(playlistId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM custom_playlists WHERE playlistId = :playlistId")
    suspend fun deleteCustomPlaylist(playlistId: String)

    // ── Playlist Items ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    suspend fun getPlaylistItems(playlistId: String): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deletePlaylistItem(playlistId: String, videoId: String)

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    fun observePlaylistItemCount(playlistId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getPlaylistItemCount(playlistId: String): Int

    @Query("SELECT thumbnailUrl FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC LIMIT 1")
    suspend fun getPlaylistCoverThumbnail(playlistId: String): String?

    @Query("SELECT playlistId FROM playlist_items WHERE videoId = :videoId")
    fun observePlaylistsForVideo(videoId: String): Flow<List<String>>

    @Query("SELECT playlistId FROM playlist_items WHERE videoId = :videoId")
    suspend fun getPlaylistsForVideo(videoId: String): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId)")
    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Boolean
}

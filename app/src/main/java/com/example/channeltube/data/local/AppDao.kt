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
}

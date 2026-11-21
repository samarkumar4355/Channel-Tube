package com.example.channeltube.data.remote

import android.net.Uri
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** A small YouTube Data API v3 client. All network work runs on Dispatchers.IO. */
class YouTubeApi(private val apiKey: String) {

    suspend fun searchChannels(query: String): List<ChannelSearchResult> {
        ensureApiKey()
        val value = query.trim()

        // Accept a channel ID or a YouTube @handle URL in addition to channel names.
        val channelId = Regex("UC[A-Za-z0-9_-]{20,}").find(value)?.value
        if (channelId != null) {
            return listOfNotNull(getChannelDetails("id" to channelId))
        }

        val handle = Regex("@([A-Za-z0-9._-]+)").find(value)?.groupValues?.getOrNull(1)
        if (handle != null) {
            return listOfNotNull(getChannelDetails("forHandle" to handle))
        }

        val json = getJson(
            "search",
            mapOf(
                "part" to "snippet",
                "type" to "channel",
                "maxResults" to "10",
                "q" to value
            )
        )

        val items = json.optJSONArray("items") ?: return emptyList()
        val results = mutableListOf<ChannelSearchResult>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optJSONObject("id")?.optString("channelId").orEmpty()
            val snippet = item.optJSONObject("snippet") ?: continue
            if (id.isBlank()) continue

            results.add(
                ChannelSearchResult(
                    channelId = id,
                    channelName = snippet.optString("title", "Unknown channel"),
                    description = snippet.optString("description", ""),
                    thumbnailUrl = thumbnailUrl(snippet)
                )
            )
        }
        return results
    }

    suspend fun getSavedChannel(searchResult: ChannelSearchResult): ChannelEntity {
        ensureApiKey()
        val json = getJson(
            "channels",
            mapOf("part" to "snippet,contentDetails", "id" to searchResult.channelId)
        )
        return parseChannel(json, searchResult.channelId)
            ?: throw IOException("YouTube did not return details for this channel.")
    }

    suspend fun getUploads(channel: ChannelEntity): List<VideoEntity> {
        return getUploadsPage(
            playlistId = channel.uploadsPlaylistId,
            pageToken = null,
            maxResults = 25,
            fallbackChannelName = channel.channelName,
            channelId = channel.channelId
        ).videos
    }

    suspend fun getChannelProfile(channelId: String): ChannelProfile {
        ensureApiKey()
        val json = getJson(
            "channels",
            mapOf(
                "part" to "snippet,contentDetails,statistics",
                "id" to channelId
            )
        )
        val item = json.optJSONArray("items")?.optJSONObject(0)
            ?: throw IOException("Channel not found on YouTube.")
        val snippet = item.optJSONObject("snippet")
            ?: throw IOException("Channel details missing from YouTube response.")
        val contentDetails = item.optJSONObject("contentDetails")
        val statistics = item.optJSONObject("statistics")

        val uploadsPlaylistId = contentDetails?.optJSONObject("relatedPlaylists")
            ?.optString("uploads").orEmpty()

        return ChannelProfile(
            channelId = item.optString("id", channelId),
            channelName = snippet.optString("title", "Unknown channel"),
            description = snippet.optString("description", ""),
            thumbnailUrl = thumbnailUrl(snippet),
            uploadsPlaylistId = uploadsPlaylistId,
            customUrl = snippet.optString("customUrl").takeIf { it.isNotBlank() },
            subscriberCount = statistics?.optString("subscriberCount")?.toLongOrNull(),
            videoCount = statistics?.optString("videoCount")?.toLongOrNull(),
            viewCount = statistics?.optString("viewCount")?.toLongOrNull()
        )
    }

    suspend fun getUploadsPage(
        playlistId: String,
        pageToken: String? = null,
        maxResults: Int = 20,
        fallbackChannelName: String = "",
        channelId: String = ""
    ): PaginatedVideos {
        ensureApiKey()
        val params = mutableMapOf(
            "part" to "snippet,contentDetails",
            "playlistId" to playlistId,
            "maxResults" to maxResults.toString()
        )
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        val json = getJson("playlistItems", params)
        val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        val items = json.optJSONArray("items") ?: return PaginatedVideos(emptyList(), null)
        val videos = mutableListOf<VideoEntity>()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val snippet = item.optJSONObject("snippet") ?: continue
            val contentDetails = item.optJSONObject("contentDetails")
            val resourceId = snippet.optJSONObject("resourceId")
            val videoId = contentDetails?.optString("videoId")
                ?.takeIf { !it.isNullOrBlank() }
                ?: resourceId?.optString("videoId")
                ?: ""

            if (videoId.isBlank()) continue

            val resolvedChannelId = snippet.optString("channelId").takeIf { it.isNotBlank() }
                ?: channelId
            val resolvedChannelName = snippet.optString("channelTitle").takeIf { it.isNotBlank() }
                ?: fallbackChannelName

            videos.add(
                VideoEntity(
                    videoId = videoId,
                    channelId = resolvedChannelId,
                    channelName = resolvedChannelName,
                    title = decodeHtml(snippet.optString("title", "Untitled video")),
                    description = snippet.optString("description", ""),
                    thumbnailUrl = thumbnailUrl(snippet),
                    publishedAt = snippet.optString("publishedAt", "")
                )
            )
        }
        return PaginatedVideos(populateDurations(videos), nextPageToken)
    }

    suspend fun searchVideos(
        query: String,
        pageToken: String? = null,
        maxResults: Int = 25
    ): PaginatedVideos {
        ensureApiKey()
        val params = mutableMapOf(
            "part" to "snippet",
            "type" to "video",
            "q" to query.trim(),
            "maxResults" to maxResults.toString()
        )
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        val json = getJson("search", params)
        val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        val items = json.optJSONArray("items") ?: return PaginatedVideos(emptyList(), null)
        val videos = mutableListOf<VideoEntity>()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val idObj = item.optJSONObject("id") ?: continue
            val videoId = idObj.optString("videoId").orEmpty()
            if (videoId.isBlank()) continue

            val snippet = item.optJSONObject("snippet") ?: continue
            val channelId = snippet.optString("channelId").orEmpty()
            val channelTitle = snippet.optString("channelTitle").takeIf { it.isNotBlank() }
                ?: "YouTube"

            videos.add(
                VideoEntity(
                    videoId = videoId,
                    channelId = channelId,
                    channelName = channelTitle,
                    title = decodeHtml(snippet.optString("title", "Untitled video")),
                    description = snippet.optString("description", ""),
                    thumbnailUrl = thumbnailUrl(snippet),
                    publishedAt = snippet.optString("publishedAt", "")
                )
            )
        }
        return PaginatedVideos(populateDurations(videos), nextPageToken)
    }

    suspend fun searchChannelVideos(
        channelId: String,
        query: String,
        pageToken: String? = null,
        maxResults: Int = 20,
        fallbackChannelName: String = ""
    ): PaginatedVideos {
        ensureApiKey()
        val params = mutableMapOf(
            "part" to "snippet",
            "type" to "video",
            "channelId" to channelId,
            "q" to query.trim(),
            "maxResults" to maxResults.toString()
        )
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        val json = getJson("search", params)
        val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        val items = json.optJSONArray("items") ?: return PaginatedVideos(emptyList(), null)
        val videos = mutableListOf<VideoEntity>()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val idObj = item.optJSONObject("id") ?: continue
            val videoId = idObj.optString("videoId").orEmpty()
            if (videoId.isBlank()) continue

            val snippet = item.optJSONObject("snippet") ?: continue

            // Strict channel verification: ensure the returned item actually belongs to the requested channel
            val itemChannelId = snippet.optString("channelId").orEmpty()
            if (itemChannelId.isNotBlank() && itemChannelId != channelId) {
                continue
            }

            val channelTitle = snippet.optString("channelTitle").takeIf { it.isNotBlank() }
                ?: fallbackChannelName

            videos.add(
                VideoEntity(
                    videoId = videoId,
                    channelId = channelId,
                    channelName = channelTitle,
                    title = decodeHtml(snippet.optString("title", "Untitled video")),
                    description = snippet.optString("description", ""),
                    thumbnailUrl = thumbnailUrl(snippet),
                    publishedAt = snippet.optString("publishedAt", "")
                )
            )
        }
        return PaginatedVideos(populateDurations(videos), nextPageToken)
    }

    suspend fun getChannelShorts(
        channelId: String,
        pageToken: String? = null,
        maxResults: Int = 20,
        fallbackChannelName: String = ""
    ): PaginatedVideos {
        ensureApiKey()
        val params = mutableMapOf(
            "part" to "snippet",
            "type" to "video",
            "channelId" to channelId,
            "videoDuration" to "short",
            "order" to "date",
            "maxResults" to maxResults.toString()
        )
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        val json = getJson("search", params)
        val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        val items = json.optJSONArray("items") ?: return PaginatedVideos(emptyList(), null)
        val videos = mutableListOf<VideoEntity>()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val idObj = item.optJSONObject("id") ?: continue
            val videoId = idObj.optString("videoId").orEmpty()
            if (videoId.isBlank()) continue

            val snippet = item.optJSONObject("snippet") ?: continue

            val itemChannelId = snippet.optString("channelId").orEmpty()
            if (itemChannelId.isNotBlank() && itemChannelId != channelId) {
                continue
            }

            val channelTitle = snippet.optString("channelTitle").takeIf { it.isNotBlank() }
                ?: fallbackChannelName

            videos.add(
                VideoEntity(
                    videoId = videoId,
                    channelId = channelId,
                    channelName = channelTitle,
                    title = decodeHtml(snippet.optString("title", "Untitled Short")),
                    description = snippet.optString("description", ""),
                    thumbnailUrl = thumbnailUrl(snippet),
                    publishedAt = snippet.optString("publishedAt", "")
                )
            )
        }
        return PaginatedVideos(populateDurations(videos), nextPageToken)
    }

    private suspend fun getChannelDetails(parameter: Pair<String, String>): ChannelSearchResult? {
        val json = getJson(
            "channels",
            mapOf("part" to "snippet,contentDetails", parameter.first to parameter.second)
        )
        val id = json.optJSONArray("items")?.optJSONObject(0)?.optString("id").orEmpty()
        if (id.isBlank()) return null
        val channel = parseChannel(json, id) ?: return null
        return ChannelSearchResult(
            channelId = channel.channelId,
            channelName = channel.channelName,
            description = channel.description,
            thumbnailUrl = channel.thumbnailUrl
        )
    }

    private fun parseChannel(json: JSONObject, expectedId: String): ChannelEntity? {
        val item = json.optJSONArray("items")?.optJSONObject(0) ?: return null
        val snippet = item.optJSONObject("snippet") ?: return null
        val uploadsPlaylistId = item.optJSONObject("contentDetails")
            ?.optJSONObject("relatedPlaylists")
            ?.optString("uploads")
            .orEmpty()
        if (uploadsPlaylistId.isBlank()) return null

        return ChannelEntity(
            channelId = item.optString("id", expectedId),
            channelName = snippet.optString("title", "Unknown channel"),
            description = snippet.optString("description", ""),
            thumbnailUrl = thumbnailUrl(snippet),
            uploadsPlaylistId = uploadsPlaylistId
        )
    }

    private fun thumbnailUrl(snippet: JSONObject): String {
        val thumbnails = snippet.optJSONObject("thumbnails") ?: return ""
        val best = thumbnails.optJSONObject("high")
            ?: thumbnails.optJSONObject("medium")
            ?: thumbnails.optJSONObject("default")
        return best?.optString("url").orEmpty()
    }

    private fun ensureApiKey() {
        if (apiKey.isBlank() || apiKey == "YOUR_YOUTUBE_API_KEY") {
            throw IllegalStateException(
                "YouTube API key missing. Add YOUTUBE_API_KEY=your_key to local.properties."
            )
        }
    }

    private suspend fun getJson(path: String, parameters: Map<String, String>): JSONObject {
        return withContext(Dispatchers.IO) {
            val builder = Uri.parse("https://www.googleapis.com/youtube/v3/$path").buildUpon()
            parameters.forEach { (name, value) -> builder.appendQueryParameter(name, value) }
            builder.appendQueryParameter("key", apiKey)

            val connection = URL(builder.build().toString()).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Connection", "Keep-Alive")

                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (statusCode !in 200..299) {
                    val message = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    throw IOException(
                        if (message.isNotBlank()) message else "YouTube API request failed ($statusCode)."
                    )
                }
                JSONObject(body)
            } finally {
                // Do not call connection.disconnect(); closing the stream puts the underlying
                // socket into Android's keep-alive connection pool for lightning-fast reuse.
            }
        }
    }

    suspend fun getChannelPlaylists(
        channelId: String,
        pageToken: String? = null,
        maxResults: Int = 25
    ): PaginatedPlaylists {
        ensureApiKey()
        val params = mutableMapOf(
            "part" to "snippet,contentDetails",
            "channelId" to channelId,
            "maxResults" to maxResults.toString()
        )
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        val json = getJson("playlists", params)
        val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        val items = json.optJSONArray("items") ?: return PaginatedPlaylists(emptyList(), null)
        val playlists = mutableListOf<PlaylistEntity>()

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id").orEmpty()
            if (id.isBlank()) continue

            val snippet = item.optJSONObject("snippet") ?: continue
            val contentDetails = item.optJSONObject("contentDetails")
            val itemCount = contentDetails?.optInt("itemCount", 0) ?: 0
            val title = decodeHtml(snippet.optString("title", "Untitled Playlist"))
            val description = snippet.optString("description", "")
            val resolvedChannelId = snippet.optString("channelId").ifBlank { channelId }
            val channelTitle = snippet.optString("channelTitle", "")

            playlists.add(
                PlaylistEntity(
                    playlistId = id,
                    channelId = resolvedChannelId,
                    channelTitle = channelTitle,
                    title = title,
                    description = description,
                    thumbnailUrl = thumbnailUrl(snippet),
                    itemCount = itemCount
                )
            )
        }
        return PaginatedPlaylists(playlists, nextPageToken)
    }

    suspend fun getPlaylistVideos(
        playlistId: String,
        pageToken: String? = null,
        maxResults: Int = 20,
        fallbackChannelName: String = "",
        channelId: String = ""
    ): PaginatedVideos {
        return getUploadsPage(playlistId, pageToken, maxResults, fallbackChannelName, channelId)
    }

    suspend fun getVideoDurations(videoIds: List<String>): Map<String, String> {
        val nonBlankIds = videoIds.filter { it.isNotBlank() }.distinct()
        if (nonBlankIds.isEmpty()) return emptyMap()
        ensureApiKey()
        val result = mutableMapOf<String, String>()
        for (chunk in nonBlankIds.chunked(50)) {
            try {
                val json = getJson(
                    "videos",
                    mapOf(
                        "part" to "contentDetails",
                        "id" to chunk.joinToString(",")
                    )
                )
                val items = json.optJSONArray("items") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val durationIso = item.optJSONObject("contentDetails")?.optString("duration").orEmpty()
                    val formatted = formatIsoDuration(durationIso)
                    if (id.isNotBlank() && formatted.isNotBlank()) {
                        result[id] = formatted
                    }
                }
            } catch (_: Exception) {
                // Ignore duration errors so video loading is never blocked
            }
        }
        return result
    }

    private suspend fun populateDurations(videos: List<VideoEntity>): List<VideoEntity> {
        if (videos.isEmpty()) return videos
        val durations = getVideoDurations(videos.map { it.videoId })
        if (durations.isEmpty()) return videos
        return videos.map { video ->
            val d = durations[video.videoId]
            if (!d.isNullOrBlank()) video.copy(duration = d) else video
        }
    }

    fun formatIsoDuration(iso: String): String {
        if (iso.isBlank() || !iso.startsWith("P")) return ""
        return try {
            val duration = java.time.Duration.parse(iso)
            val totalSeconds = duration.seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodeHtml(text: String): String {
        if (!text.contains('&')) return text
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(text).toString()
            }
        } catch (_: Exception) {
            text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
        }
    }
}

data class ChannelSearchResult(
    val channelId: String,
    val channelName: String,
    val description: String,
    val thumbnailUrl: String
)

data class ChannelProfile(
    val channelId: String,
    val channelName: String,
    val description: String,
    val thumbnailUrl: String,
    val uploadsPlaylistId: String,
    val customUrl: String? = null,
    val subscriberCount: Long? = null,
    val videoCount: Long? = null,
    val viewCount: Long? = null
)

data class PaginatedVideos(
    val videos: List<VideoEntity>,
    val nextPageToken: String?
)

data class PlaylistEntity(
    val playlistId: String,
    val channelId: String,
    val channelTitle: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val itemCount: Int
)

data class PaginatedPlaylists(
    val playlists: List<PlaylistEntity>,
    val nextPageToken: String?
)

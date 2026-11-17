package com.noadytv.channel

import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.noadytv.python.VideoSearchResult
import com.noadytv.channel.data.ChannelSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicReference

/**
 * 頻道搜尋 Python Bridge
 * 完全獨立於 YouTubePythonBridge，不依賴其任何方法
 */
class ChannelSearchBridge private constructor() {

    private val python = Python.getInstance()
    private val module: PyObject = python.getModule("youtube_extractor")

    companion object {
        @Volatile
        private var instance: ChannelSearchBridge? = null

        fun getInstance(): ChannelSearchBridge {
            return instance ?: synchronized(this) {
                instance ?: ChannelSearchBridge().also { instance = it }
            }
        }
    }

    suspend fun searchChannels(query: String, maxResults: Int): Result<List<ChannelSearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = module.callAttr("search_channels", query, maxResults).toString()
                val jsonArray = JSONArray(raw)
                val list = mutableListOf<ChannelSearchResult>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ChannelSearchResult(
                            id = obj.optString("channel_id", ""),
                            title = obj.optString("title", ""),
                            follower_count = if (obj.has("follower_count") && !obj.isNull("follower_count"))
                                obj.optLong("follower_count").takeIf { it >= 0 } else null,
                            thumbnail = obj.optString("thumbnail"),
                            description = obj.optString("description"),
                        )
                    )
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getChannelVideos(channelUrl: String, maxVideos: Int): Result<List<VideoSearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = module.callAttr("get_channel_videos", channelUrl, maxVideos).toString()
                val jsonArray = JSONArray(raw)
                val list = mutableListOf<VideoSearchResult>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        VideoSearchResult(
                            id = obj.optString("id"),
                            title = obj.optString("title", ""),
                            duration = if (obj.has("duration") && !obj.isNull("duration"))
                                obj.optLong("duration").takeIf { it > 0 } else null,
                            thumbnail = obj.optString("thumbnail"),
                            channel = obj.optString("channel"),
                            channel_id = obj.optString("channel_id"),
                            view_count = if (obj.has("view_count") && !obj.isNull("view_count"))
                                obj.optLong("view_count").takeIf { it > 0 } else null,
                            url = obj.optString("url"),
                            upload_date = if (obj.has("upload_date") && !obj.isNull("upload_date"))
                                obj.optString("upload_date") else null,
                        )
                    )
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

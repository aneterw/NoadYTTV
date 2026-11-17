package com.noadytv.channel.data

/** 頻道搜尋結果資料類，與 VideoSearchResult 結構平行，完全獨立 */
data class ChannelSearchResult(
    val id: String,           // channel_id
    val title: String,
    val follower_count: Long?,
    val thumbnail: String?,
    val description: String?
)

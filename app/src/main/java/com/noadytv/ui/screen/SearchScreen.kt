package com.noadytv.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noadytv.R
import com.noadytv.python.VideoSearchResult
import com.noadytv.ui.components.SearchBar
import com.noadytv.ui.components.VideoGrid
import android.content.Intent
import android.speech.RecognizerIntent

@Composable
fun SearchScreen(
    onSearch: (String) -> Unit,
    onVideoClick: (VideoSearchResult) -> Unit,
    videos: List<VideoSearchResult>,
    isSearching: Boolean,
    searchError: String?,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    searchFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var query by remember { mutableStateOf("") }

    // 語音搜尋啟動器
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { text ->
                query = text
                if (text.isNotBlank()) onSearch(text)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 標題列
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 64.dp, vertical = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 搜尋欄
        SearchBar(
            onSearch = { text ->
                query = text
                onSearch(text)
            },
            modifier = Modifier.fillMaxWidth(),
            focusRequester = searchFocusRequester
        )

        // 錯誤訊息
        searchError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 8.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp
                )
            }
        }

        // 結果區域
        if (videos.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.search_hint_detail),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "💡 可用鍵盤語音搜尋，效率更高",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        } else if (videos.isNotEmpty()) {
            VideoGrid(
                videos = videos,
                onVideoClick = onVideoClick,
                focusedIndex = focusedIndex,
                onFocusChange = onFocusChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

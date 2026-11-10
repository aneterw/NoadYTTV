package com.noadytv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VideoGrid(
    videos: List<com.noadytv.python.VideoSearchResult>,
    onVideoClick: (com.noadytv.python.VideoSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    focusedIndex: Int = 0,
    onFocusChange: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 32.dp)
    ) {
        Text(
            text = "Search Results",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                val index = videos.indexOf(video)
                val isFocused = index == focusedIndex

                VideoCard(
                    video = video,
                    isFocused = isFocused,
                    onClick = { onVideoClick(video) }
                )
            }
        }
    }
}
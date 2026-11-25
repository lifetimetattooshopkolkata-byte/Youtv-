package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.SearchHistoryEntry
import com.example.data.model.UiState
import com.example.data.model.Video
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.VideoFeedCard
import com.example.ui.components.YouTvBrandLockup
import com.example.viewmodel.FeedSortMode

@Composable
fun HomeScreen(
    feedState: UiState<List<Video>>,
    selectedCategory: String,
    searchQuery: String,
    sortMode: FeedSortMode,
    searchHistory: List<SearchHistoryEntry>,
    unreadNotificationsCount: Int,
    columnsCount: Int,
    onSelectCategory: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCommitSearch: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onToggleSortMode: (FeedSortMode) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenVideo: (Video) -> Unit,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var searchExpanded by remember { mutableStateOf(false) }

    val orderedCategories = listOf(
        "Home", "Trending", "Latest", "Shorts",
        "Music", "Education", "Entertainment", "Technology", "Gaming", "Sports"
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YouTvBrandLockup(showSlogan = false, iconSize = 34.dp)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { searchExpanded = !searchExpanded },
                modifier = Modifier.testTag("home_search_toggle_btn")
            ) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = strings.searchHint
                )
            }
            IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier.testTag("home_notifications_btn")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotificationsCount > 0) {
                            Badge { Text(unreadNotificationsCount.toString()) }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = strings.notifications)
                }
            }
        }

        if (searchExpanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(strings.searchHint) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        IconButton(onClick = { onCommitSearch(searchQuery) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
                if (searchHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.searchHistory,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClearSearchHistory) {
                            Text(strings.clearSearch)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchHistory) { entry ->
                            AssistChip(
                                onClick = {
                                    onSearchQueryChange(entry.query)
                                    onCommitSearch(entry.query)
                                },
                                label = { Text(entry.query) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.History,
                                        contentDescription = null,
                                        modifier = Modifier.padding(0.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Categories + Feed Mode Toggle
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = sortMode == FeedSortMode.CHRONOLOGICAL,
                    onClick = {
                        onToggleSortMode(
                            if (sortMode == FeedSortMode.PERSONALIZED) FeedSortMode.CHRONOLOGICAL else FeedSortMode.PERSONALIZED
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    label = {
                        Text(
                            if (sortMode == FeedSortMode.PERSONALIZED) strings.personalizedFeed else strings.chronologicalFeed
                        )
                    },
                    modifier = Modifier.testTag("feed_sort_toggle")
                )
            }
            items(orderedCategories) { key ->
                val isSelected = selectedCategory == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(key) },
                    label = { Text(strings.categories[key] ?: key) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("category_chip_$key")
                )
            }
        }

        when (feedState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(feedState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            is UiState.Success -> {
                if (feedState.data.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        EmptyStateBox(message = strings.emptyVideos)
                        Button(
                            onClick = onOpenUpload,
                            modifier = Modifier.testTag("empty_upload_cta")
                        ) {
                            Text(strings.uploadTitle)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (searchQuery.isNotBlank()) {
                            item(span = { GridItemSpan(columnsCount) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "\"$searchQuery\" (${feedState.data.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = strings.clearSearch,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onSearchQueryChange("") }
                                    )
                                }
                            }
                        }
                        items(feedState.data, key = { it.id }) { video ->
                            VideoFeedCard(
                                video = video,
                                onClick = { onOpenVideo(video) }
                            )
                        }
                        item(span = { GridItemSpan(columnsCount) }) {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

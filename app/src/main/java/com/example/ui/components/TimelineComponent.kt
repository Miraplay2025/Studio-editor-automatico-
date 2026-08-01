package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.MediaItemEntity
import com.example.data.model.CameraAnimation
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TimelineComponent(
    mediaItems: List<MediaItemEntity>,
    selectedIndex: Int,
    hasUnassignedAnimation: Boolean,
    firstUnassignedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant)
            .padding(vertical = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline de Mídia (${mediaItems.size} clipes)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                IconButton(
                    onClick = onAddMediaClick,
                    modifier = Modifier.testTag("add_media_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar Mídia",
                        tint = SecondaryCyan
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(mediaItems) { index, item ->
                        val isSelected = index == selectedIndex
                        val isUnassigned = item.mediaType == "IMAGE" && item.animationType == "NONE"
                        val animLabel = CameraAnimation.fromId(item.animationType).label

                        TimelineItemCard(
                            item = item,
                            index = index,
                            isSelected = isSelected,
                            isUnassigned = isUnassigned,
                            animLabel = animLabel,
                            onSelect = { onItemSelected(index) },
                            onRemove = { onRemoveItem(index) }
                        )
                    }
                }

                // Floating Red Arrow Validation Badge over Timeline
                if (hasUnassignedAnimation && firstUnassignedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 8.dp)
                    ) {
                        Surface(
                            color = WarningRed,
                            shape = CircleShape,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .testTag("floating_red_arrow")
                                .clickable {
                                    if (firstUnassignedIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(firstUnassignedIndex)
                                            onItemSelected(firstUnassignedIndex)
                                        }
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Item Sem Animação",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Selecione animação para este item",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItemCard(
    item: MediaItemEntity,
    index: Int,
    isSelected: Boolean,
    isUnassigned: Boolean,
    animLabel: String,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = when {
        isSelected -> SecondaryCyan
        isUnassigned -> WarningRed
        else -> BorderDark
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        modifier = Modifier
            .width(110.dp)
            .height(130.dp)
            .border(
                width = if (isSelected || isUnassigned) 2.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .testTag("timeline_item_$index")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.uri,
                contentDescription = "Thumbnail $index",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark Gradient Overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )

            // Index badge top-left
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Delete icon top-right
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remover Clipe",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Animation Tag & Duration Bottom Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = animLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = if (isUnassigned) WarningRed else SecondaryCyan,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format("%.1fs", item.durationSeconds),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                )
            }
        }
    }
}

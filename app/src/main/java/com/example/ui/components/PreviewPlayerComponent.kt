package com.example.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.db.MediaItemEntity
import com.example.data.model.CameraAnimation
import com.example.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun PreviewPlayerComponent(
    selectedItem: MediaItemEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    // Live Pan/Zoom simulation animation progress (0f to 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "pan_zoom_prev")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .testTag("preview_player")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (selectedItem != null) {
                val anim = CameraAnimation.fromId(selectedItem.animationType)
                val isVideo = selectedItem.mediaType == "VIDEO" || selectedItem.uri.contains("video") || selectedItem.uri.endsWith(".mp4")

                if (isVideo) {
                    // ExoPlayer Video Player View
                    val exoPlayer = remember(selectedItem.uri) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(Uri.parse(selectedItem.uri)))
                            prepare()
                            repeatMode = Player.REPEAT_MODE_ALL
                        }
                    }

                    DisposableEffect(selectedItem.uri) {
                        onDispose {
                            exoPlayer.release()
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Image with Real-time Camera Animation (Pan/Zoom) Transform matrix simulation
                    var scaleX = 1.15f
                    var scaleY = 1.15f
                    var translationX = 0f
                    var translationY = 0f

                    val offset = 40f * animProgress

                    when (anim) {
                        CameraAnimation.NONE -> {}
                        CameraAnimation.PAN_RIGHT -> translationX = -offset
                        CameraAnimation.PAN_LEFT -> translationX = offset
                        CameraAnimation.PAN_UP -> translationY = offset
                        CameraAnimation.PAN_DOWN -> translationY = -offset
                        CameraAnimation.ZOOM_IN -> {
                            scaleX = 1.0f + (0.25f * animProgress)
                            scaleY = 1.0f + (0.25f * animProgress)
                        }
                        CameraAnimation.ZOOM_OUT -> {
                            scaleX = 1.25f - (0.25f * animProgress)
                            scaleY = 1.25f - (0.25f * animProgress)
                        }
                        CameraAnimation.DIAGONAL_TOP_LEFT -> {
                            translationX = offset
                            translationY = offset
                        }
                        CameraAnimation.DIAGONAL_TOP_RIGHT -> {
                            translationX = -offset
                            translationY = offset
                        }
                        CameraAnimation.DIAGONAL_BOTTOM_LEFT -> {
                            translationX = offset
                            translationY = -offset
                        }
                        CameraAnimation.DIAGONAL_BOTTOM_RIGHT -> {
                            translationX = -offset
                            translationY = -offset
                        }
                    }

                    AsyncImage(
                        model = selectedItem.uri,
                        contentDescription = "Preview Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.scaleX = scaleX
                                this.scaleY = scaleY
                                this.translationX = translationX
                                this.translationY = translationY
                            }
                    )
                }

                // Top Badge with Camera Animation Tag
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SecondaryCyan)
                        )
                        Text(
                            text = CameraAnimation.fromId(selectedItem.animationType).label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            } else {
                Text(
                    text = "Nenhuma mídia selecionada na timeline",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
        }
    }
}

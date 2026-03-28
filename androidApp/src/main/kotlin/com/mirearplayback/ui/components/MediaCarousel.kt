package com.mirearplayback.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.mirearplayback.data.CarouselState
import com.mirearplayback.data.CropRegion
import com.mirearplayback.data.MediaItem
import com.mirearplayback.data.MediaType
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaCarousel(
    carouselState: CarouselState,
    isPlaying: Boolean,
    playbackProgress: Float,
    onAddMedia: () -> Unit,
    onCropCurrent: () -> Unit,
    onSelectItem: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (carouselState.items.isNotEmpty()) {
                val listState =
                    rememberLazyListState(
                        initialFirstVisibleItemIndex = carouselState.currentIndex
                    )
                val carouselScope = rememberCoroutineScope()
                val latestSelectedIndex = rememberUpdatedState(carouselState.currentIndex)
                val latestOnSelectItem = rememberUpdatedState(onSelectItem)
                val visuallyCenteredIndex by
                    remember(
                        listState,
                        carouselState.currentIndex,
                        carouselState.items.size
                    ) {
                        derivedStateOf {
                            centeredItemIndex(listState) ?: carouselState.currentIndex
                        }
                    }

                LaunchedEffect(carouselState.currentIndex, carouselState.items.size) {
                    if (carouselState.items.isNotEmpty()) {
                        listState.animateScrollToItem(carouselState.currentIndex)
                    }
                }

                LaunchedEffect(listState, enabled, carouselState.items.size) {
                    if (!enabled) return@LaunchedEffect

                    snapshotFlow { centeredItemIndex(listState) }
                        .distinctUntilChanged()
                        .collect { centeredItem ->
                            if (centeredItem != null &&
                                centeredItem in carouselState.items.indices &&
                                centeredItem != latestSelectedIndex.value
                            ) {
                                latestOnSelectItem.value(centeredItem)
                            }
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(432.dp),
                    contentPadding = PaddingValues(vertical = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    userScrollEnabled = enabled
                ) {
                    items(
                        count = carouselState.items.size,
                        key = { index ->
                            carouselState.items[index].uri
                        }
                    ) { index ->
                        val item = carouselState.items[index]
                        val isCurrent = index == carouselState.currentIndex
                        val isCentered = index == visuallyCenteredIndex
                        val progressStyle =
                            when {
                                !isCurrent || !isPlaying -> MediaProgressStyle.None

                                item.type == MediaType.GIF -> MediaProgressStyle.Indeterminate

                                item.type == MediaType.VIDEO -> {
                                    MediaProgressStyle.Determinate(
                                        playbackProgress.coerceIn(0f, 1f)
                                    )
                                }

                                else -> MediaProgressStyle.None
                            }

                        CarouselMediaItem(
                            item = item,
                            isCurrent = isCurrent,
                            isCentered = isCentered,
                            isPlaying = isPlaying,
                            progressStyle = progressStyle,
                            onSelect = {
                                if (enabled) {
                                    carouselScope.launch {
                                        listState.animateScrollToItem(index)
                                    }
                                    onSelectItem(index)
                                }
                            },
                            onRemove = { onRemoveItem(index) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                CarouselFooter(
                    carouselState = carouselState,
                    onAddMedia = onAddMedia,
                    onCropCurrent = onCropCurrent,
                    enabled = enabled
                )
            } else {
                EmptyCarouselState(
                    onAddMedia = onAddMedia,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun CarouselFooter(
    carouselState: CarouselState,
    onAddMedia: () -> Unit,
    onCropCurrent: () -> Unit,
    enabled: Boolean
) {
    val currentLabel =
        carouselState.currentItem?.let {
            when (it.type) {
                MediaType.IMAGE -> "Photo"
                MediaType.GIF -> "GIF"
                MediaType.VIDEO -> "Video"
            }
        } ?: "Ready"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "CURRENT FOCUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text =
                    carouselState.currentItem?.displayName?.ifBlank { currentLabel }
                        ?: "No media selected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    "${carouselState.size} item${if (carouselState.size != 1) "s" else ""} in queue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onCropCurrent,
                enabled = enabled && carouselState.currentItem != null
            ) {
                Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text =
                        if (carouselState.currentItem?.cropRegion?.isFullFrame == false) {
                            "Adjust Crop"
                        } else {
                            "Crop"
                        },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            FilledTonalButton(
                onClick = onAddMedia,
                enabled = enabled
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add Media", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun EmptyCarouselState(onAddMedia: () -> Unit, enabled: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "CAROUSEL",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text =
                "No media added yet. Import photos, GIFs, or videos to build the rear display queue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = onAddMedia,
            enabled = enabled
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add Media", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private sealed interface MediaProgressStyle {
    data object None : MediaProgressStyle

    data object Indeterminate : MediaProgressStyle

    data class Determinate(val progress: Float) : MediaProgressStyle
}

@Composable
private fun CarouselMediaItem(
    item: MediaItem,
    isCurrent: Boolean,
    isCentered: Boolean,
    isPlaying: Boolean,
    progressStyle: MediaProgressStyle,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val horizontalInset by animateDpAsState(
        targetValue = if (isCentered) 0.dp else 24.dp,
        label = "carouselHorizontalInset"
    )
    val borderColor by animateColorAsState(
        targetValue =
            if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            },
        label = "carouselBorderColor"
    )
    val tonalElevation by animateDpAsState(
        targetValue = if (isCurrent) 7.dp else 1.dp,
        label = "carouselTonalElevation"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isCurrent) 14.dp else 3.dp,
        label = "carouselShadowElevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isCentered) 1f else 0.88f,
        animationSpec = spring(),
        label = "carouselScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCentered) 1f else 0.72f,
        label = "carouselAlpha"
    )

    val imageRequest =
        ImageRequest
            .Builder(context)
            .data(Uri.parse(item.uri))
            .crossfade(true)
            .apply {
                if (item.type == MediaType.VIDEO) {
                    decoderFactory(VideoFrameDecoder.Factory())
                }
            }.build()

    Surface(
        modifier =
            modifier
                .padding(horizontal = horizontalInset)
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clickable(enabled = enabled, onClick = onSelect),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(width = if (isCurrent) 2.dp else 1.dp, color = borderColor),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CarouselThumbnail(
                item = item,
                cropRegion = item.cropRegion,
                imageRequest = imageRequest,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
            )

            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                color = Color.Black.copy(alpha = 0.46f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = typeIcon(item.type),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text =
                            when (item.type) {
                                MediaType.IMAGE -> "Photo"
                                MediaType.GIF -> "GIF"
                                MediaType.VIDEO -> "Video"
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp),
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp)
                )
            }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = item.displayName.ifBlank { item.type.name },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isCurrent) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isPlaying) "NOW PLAYING" else "SELECTED",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                when (progressStyle) {
                    MediaProgressStyle.None -> Unit

                    is MediaProgressStyle.Determinate -> {
                        Surface(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            MediaProgressIndicator(
                                progress = { progressStyle.progress },
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    MediaProgressStyle.Indeterminate -> {
                        Surface(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            MediaProgressIndicatorIndeterminate(
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselThumbnail(
    item: MediaItem,
    cropRegion: CropRegion,
    imageRequest: ImageRequest,
    modifier: Modifier = Modifier
) {
    if (item.type == MediaType.GIF) {
        val context = LocalContext.current
        val previewBitmap by produceState<ImageBitmap?>(
            initialValue = null,
            key1 = item.uri
        ) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { input ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
        }

        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap!!,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = modifier.applyCropRegion(cropRegion)
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = modifier.applyCropRegion(cropRegion)
            )
        }
    } else {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.applyCropRegion(cropRegion)
        )
    }
}

private fun centeredItemIndex(listState: LazyListState): Int? {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return visibleItems.minByOrNull { itemInfo ->
        abs((itemInfo.offset + itemInfo.size / 2) - viewportCenter)
    }?.index
}

private fun Modifier.applyCropRegion(cropRegion: CropRegion): Modifier = graphicsLayer {
    val cropWidth = (cropRegion.right - cropRegion.left).coerceAtLeast(0.05f)
    val cropHeight = (cropRegion.bottom - cropRegion.top).coerceAtLeast(0.05f)

    if (cropRegion.isFullFrame) {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        return@graphicsLayer
    }

    val cropCenterX = (cropRegion.left + cropRegion.right) / 2f
    val cropCenterY = (cropRegion.top + cropRegion.bottom) / 2f
    scaleX = 1f / cropWidth
    scaleY = 1f / cropHeight
    translationX = (0.5f - cropCenterX) * size.width * scaleX
    translationY = (0.5f - cropCenterY) * size.height * scaleY
}

private fun typeIcon(type: MediaType): ImageVector = when (type) {
    MediaType.IMAGE -> Icons.Filled.Image
    MediaType.GIF -> Icons.Filled.GifBox
    MediaType.VIDEO -> Icons.Filled.VideoFile
}

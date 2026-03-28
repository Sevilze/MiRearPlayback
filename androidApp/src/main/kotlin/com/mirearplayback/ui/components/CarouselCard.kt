package com.mirearplayback.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mirearplayback.data.CarouselState
import com.mirearplayback.data.MediaItem
import com.mirearplayback.data.MediaType

@Composable
fun CarouselCard(
    carouselState: CarouselState,
    onAddMedia: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAROUSEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${carouselState.size} item${if (carouselState.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                FilledTonalButton(
                    onClick = onAddMedia,
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Add Media", modifier = Modifier.padding(start = 6.dp))
                }
            }

            if (carouselState.items.isNotEmpty()) {
                carouselState.items.forEachIndexed { index, item ->
                    MediaItemRow(
                        item = item,
                        index = index,
                        isCurrent = index == carouselState.currentIndex,
                        onRemove = { onRemoveItem(index) },
                        enabled = enabled
                    )
                }
            } else {
                Text(
                    text = "No media added. Tap 'Add Media' to select images, GIFs, or videos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaItemRow(
    item: MediaItem,
    index: Int,
    isCurrent: Boolean,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    val icon =
        when (item.type) {
            MediaType.IMAGE -> Icons.Filled.Image
            MediaType.GIF -> Icons.Filled.GifBox
            MediaType.VIDEO -> Icons.Filled.VideoFile
        }
    val typeLabel =
        when (item.type) {
            MediaType.IMAGE -> "Image"
            MediaType.GIF -> "GIF"
            MediaType.VIDEO -> "Video"
        }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = typeLabel,
                tint =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName.ifBlank { typeLabel },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color =
                        if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                )
                if (isCurrent) {
                    Text(
                        text = "Currently playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

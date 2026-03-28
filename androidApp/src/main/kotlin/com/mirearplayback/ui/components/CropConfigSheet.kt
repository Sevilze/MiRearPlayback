package com.mirearplayback.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.mirearplayback.data.CropRegion
import com.mirearplayback.data.MediaItem
import com.mirearplayback.data.MediaType

@Composable
fun CropConfigSheet(
    mediaItem: MediaItem,
    onCropChange: (CropRegion) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cropRegion by remember { mutableStateOf(mediaItem.cropRegion) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Crop Region",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Drag the corners or edges to adjust the crop area.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .onSizeChanged { containerSize = it }
            ) {
                val context = LocalContext.current
                val imageRequest =
                    ImageRequest
                        .Builder(context)
                        .data(Uri.parse(mediaItem.uri))
                        .crossfade(true)
                        .apply {
                            if (mediaItem.type == MediaType.VIDEO) {
                                decoderFactory(VideoFrameDecoder.Factory())
                            }
                        }.build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                CropOverlay(
                    cropRegion = cropRegion,
                    containerSize = containerSize,
                    onCropRegionChange = { cropRegion = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        cropRegion = CropRegion()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        onCropChange(cropRegion)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }

            Text(
                text =
                    "L: ${"%.0f".format(cropRegion.left * 100)}%  " +
                        "T: ${"%.0f".format(cropRegion.top * 100)}%  " +
                        "R: ${"%.0f".format(cropRegion.right * 100)}%  " +
                        "B: ${"%.0f".format(cropRegion.bottom * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Box(modifier = Modifier.height(24.dp))
        }
    }
}

private enum class DragHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    BODY
}

@Composable
private fun CropOverlay(
    cropRegion: CropRegion,
    containerSize: IntSize,
    onCropRegionChange: (CropRegion) -> Unit
) {
    if (containerSize == IntSize.Zero) return

    val w = containerSize.width.toFloat()
    val h = containerSize.height.toFloat()

    var activeDrag by remember { mutableStateOf<DragHandle?>(null) }

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(containerSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cropLeft = cropRegion.left * w
                            val cropTop = cropRegion.top * h
                            val cropRight = cropRegion.right * w
                            val cropBottom = cropRegion.bottom * h
                            val handleRadius = 30f

                            activeDrag =
                                when {
                                    isNear(
                                        offset,
                                        cropLeft,
                                        cropTop,
                                        handleRadius
                                    ) -> DragHandle.TOP_LEFT

                                    isNear(
                                        offset,
                                        cropRight,
                                        cropTop,
                                        handleRadius
                                    ) -> DragHandle.TOP_RIGHT

                                    isNear(
                                        offset,
                                        cropLeft,
                                        cropBottom,
                                        handleRadius
                                    ) -> DragHandle.BOTTOM_LEFT

                                    isNear(
                                        offset,
                                        cropRight,
                                        cropBottom,
                                        handleRadius
                                    ) -> DragHandle.BOTTOM_RIGHT

                                    offset.x in cropLeft..cropRight &&
                                        offset.y in cropTop..cropBottom -> DragHandle.BODY

                                    else -> null
                                }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val handle = activeDrag ?: return@detectDragGestures
                            val dx = dragAmount.x / w
                            val dy = dragAmount.y / h

                            val new =
                                when (handle) {
                                    DragHandle.TOP_LEFT -> {
                                        cropRegion.copy(
                                            left = (cropRegion.left + dx).coerceIn(
                                                0f,
                                                cropRegion.right - 0.05f
                                            ),
                                            top = (cropRegion.top + dy).coerceIn(
                                                0f,
                                                cropRegion.bottom - 0.05f
                                            )
                                        )
                                    }

                                    DragHandle.TOP_RIGHT -> {
                                        cropRegion.copy(
                                            right = (cropRegion.right + dx).coerceIn(
                                                cropRegion.left + 0.05f,
                                                1f
                                            ),
                                            top = (cropRegion.top + dy).coerceIn(
                                                0f,
                                                cropRegion.bottom - 0.05f
                                            )
                                        )
                                    }

                                    DragHandle.BOTTOM_LEFT -> {
                                        cropRegion.copy(
                                            left = (cropRegion.left + dx).coerceIn(
                                                0f,
                                                cropRegion.right - 0.05f
                                            ),
                                            bottom = (cropRegion.bottom + dy).coerceIn(
                                                cropRegion.top + 0.05f,
                                                1f
                                            )
                                        )
                                    }

                                    DragHandle.BOTTOM_RIGHT -> {
                                        cropRegion.copy(
                                            right = (cropRegion.right + dx).coerceIn(
                                                cropRegion.left + 0.05f,
                                                1f
                                            ),
                                            bottom = (cropRegion.bottom + dy).coerceIn(
                                                cropRegion.top + 0.05f,
                                                1f
                                            )
                                        )
                                    }

                                    DragHandle.BODY -> {
                                        val cropW = cropRegion.right - cropRegion.left
                                        val cropH = cropRegion.bottom - cropRegion.top
                                        val newLeft = (cropRegion.left + dx).coerceIn(
                                            0f,
                                            1f - cropW
                                        )
                                        val newTop = (cropRegion.top + dy).coerceIn(0f, 1f - cropH)
                                        CropRegion(
                                            left = newLeft,
                                            top = newTop,
                                            right = newLeft + cropW,
                                            bottom = newTop + cropH
                                        )
                                    }
                                }
                            onCropRegionChange(new)
                        },
                        onDragEnd = { activeDrag = null },
                        onDragCancel = { activeDrag = null }
                    )
                }
    ) {
        val cropRect =
            Rect(
                left = cropRegion.left * w,
                top = cropRegion.top * h,
                right = cropRegion.right * w,
                bottom = cropRegion.bottom * h
            )

        // Dim area outside crop
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset.Zero,
            size = Size(w, cropRect.top)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, cropRect.bottom),
            size = Size(w, h - cropRect.bottom)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, cropRect.top),
            size = Size(cropRect.left, cropRect.height)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(cropRect.right, cropRect.top),
            size = Size(w - cropRect.right, cropRect.height)
        )

        // Crop border
        drawRect(
            color = Color.White,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = Size(cropRect.width, cropRect.height),
            style = Stroke(width = 2f)
        )

        // Corner handles
        val handleSize = 16f
        listOf(
            Offset(cropRect.left, cropRect.top),
            Offset(cropRect.right, cropRect.top),
            Offset(cropRect.left, cropRect.bottom),
            Offset(cropRect.right, cropRect.bottom)
        ).forEach { corner ->
            drawCircle(
                color = Color.White,
                radius = handleSize / 2f,
                center = corner
            )
        }

        // Rule of thirds grid lines
        val thirdW = cropRect.width / 3f
        val thirdH = cropRect.height / 3f
        for (i in 1..2) {
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(cropRect.left + thirdW * i, cropRect.top),
                end = Offset(cropRect.left + thirdW * i, cropRect.bottom),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(cropRect.left, cropRect.top + thirdH * i),
                end = Offset(cropRect.right, cropRect.top + thirdH * i),
                strokeWidth = 1f
            )
        }
    }
}

private fun isNear(offset: Offset, x: Float, y: Float, radius: Float): Boolean =
    (offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y) <= radius * radius

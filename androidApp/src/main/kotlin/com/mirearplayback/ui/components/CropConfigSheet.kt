package com.mirearplayback.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.mirearplayback.data.CropRegion
import com.mirearplayback.data.MediaItem
import com.mirearplayback.data.MediaType
import com.mirearplayback.media.MediaCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CropConfigSheet(
    mediaItem: MediaItem,
    onCropChange: (CropRegion) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val rearDisplaySize = remember { MediaCropper.getRearDisplaySize(context) }
    val imageDimensions by produceState<IntSize?>(null, mediaItem.uri) {
        value = withContext(Dispatchers.IO) {
            resolveMediaDimensions(context, Uri.parse(mediaItem.uri), mediaItem.type)
        }
    }

    val imageAR = imageDimensions?.let { it.width.toFloat() / it.height.toFloat() } ?: (16f / 9f)
    val rearAR = rearDisplaySize?.let { it.width.toFloat() / it.height.toFloat() } ?: imageAR
    val normalizedCropAR = rearAR / imageAR

    val initialCrop = remember(imageDimensions, rearDisplaySize) {
        val dims = imageDimensions ?: return@remember null
        val imgAR = dims.width.toFloat() / dims.height.toFloat()
        val dispAR = rearDisplaySize?.let { it.width.toFloat() / it.height.toFloat() } ?: imgAR
        val nAR = dispAR / imgAR
        val cropW: Float
        val cropH: Float
        if (nAR < 1f) {
            cropW = nAR
            cropH = 1f
        } else {
            cropW = 1f
            cropH = 1f / nAR
        }
        CropRegion(
            left = (1f - cropW) / 2f,
            top = (1f - cropH) / 2f,
            right = (1f + cropW) / 2f,
            bottom = (1f + cropH) / 2f
        )
    }

    var cropRegion by remember { mutableStateOf(mediaItem.cropRegion) }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(initialCrop) {
        if (hasInitialized || initialCrop == null) return@LaunchedEffect
        if (mediaItem.cropRegion.isFullFrame) {
            cropRegion = initialCrop
        }
        hasInitialized = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Crop Region",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Text(
                    text = "Drag the corners or edges to adjust the crop area.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(imageAR.coerceIn(0.3f, 3f))
                            .onSizeChanged { containerSize = it }
                    ) {
                        val imageRequest = ImageRequest.Builder(context)
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
                            normalizedCropAR = normalizedCropAR,
                            onCropRegionChange = { cropRegion = it }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { cropRegion = CropRegion() },
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
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )

                Box(modifier = Modifier.height(24.dp))
            }
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
    normalizedCropAR: Float,
    onCropRegionChange: (CropRegion) -> Unit
) {
    if (containerSize == IntSize.Zero) return

    val w = containerSize.width.toFloat()
    val h = containerSize.height.toFloat()

    val latestCrop by rememberUpdatedState(cropRegion)
    val latestOnChange by rememberUpdatedState(onCropRegionChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val cr = latestCrop
                    val cLeft = cr.left * w
                    val cTop = cr.top * h
                    val cRight = cr.right * w
                    val cBottom = cr.bottom * h
                    val hitRadius = 48f

                    val handle = when {
                        isNear(down.position, cLeft, cTop, hitRadius) -> DragHandle.TOP_LEFT

                        isNear(down.position, cRight, cTop, hitRadius) -> DragHandle.TOP_RIGHT

                        isNear(down.position, cLeft, cBottom, hitRadius) -> DragHandle.BOTTOM_LEFT

                        isNear(down.position, cRight, cBottom, hitRadius) -> DragHandle.BOTTOM_RIGHT

                        down.position.x in cLeft..cRight &&
                            down.position.y in cTop..cBottom -> DragHandle.BODY

                        else -> null
                    }

                    if (handle == null) return@awaitEachGesture

                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val current = latestCrop

                        if (changes.size >= 2) {
                            val c0 = changes[0]
                            val c1 = changes[1]
                            val curDist = (c0.position - c1.position).getDistance()
                            val prevDist = (c0.previousPosition - c1.previousPosition).getDistance()
                            if (prevDist > 1f) {
                                val zoom = curDist / prevDist
                                val cx = (current.left + current.right) / 2f
                                val cy = (current.top + current.bottom) / 2f
                                val halfW = (current.right - current.left) / 2f
                                // Pinch outward (zoom>1) = expand crop
                                val newHalfW = (halfW * zoom).coerceIn(0.05f, 0.5f)
                                val newHalfH = newHalfW / normalizedCropAR
                                if (newHalfH <= 0.5f) {
                                    val ncx = cx.coerceIn(newHalfW, 1f - newHalfW)
                                    val ncy = cy.coerceIn(newHalfH, 1f - newHalfH)
                                    latestOnChange(
                                        CropRegion(
                                            left = ncx - newHalfW,
                                            top = ncy - newHalfH,
                                            right = ncx + newHalfW,
                                            bottom = ncy + newHalfH
                                        )
                                    )
                                }
                            }
                        } else if (changes.size == 1) {
                            val delta = changes[0].positionChange()
                            val dx = delta.x / w
                            val dy = delta.y / h

                            val newRegion = when (handle) {
                                DragHandle.TOP_LEFT -> {
                                    val newLeft = (current.left + dx).coerceIn(
                                        0f,
                                        current.right - 0.1f
                                    )
                                    val newW = current.right - newLeft
                                    val newH = newW / normalizedCropAR
                                    val newTop = current.bottom - newH
                                    if (newTop >=
                                        0f
                                    ) {
                                        CropRegion(
                                            newLeft,
                                            newTop,
                                            current.right,
                                            current.bottom
                                        )
                                    } else {
                                        current
                                    }
                                }

                                DragHandle.TOP_RIGHT -> {
                                    val newRight = (current.right + dx).coerceIn(
                                        current.left + 0.1f,
                                        1f
                                    )
                                    val newW = newRight - current.left
                                    val newH = newW / normalizedCropAR
                                    val newTop = current.bottom - newH
                                    if (newTop >=
                                        0f
                                    ) {
                                        CropRegion(
                                            current.left,
                                            newTop,
                                            newRight,
                                            current.bottom
                                        )
                                    } else {
                                        current
                                    }
                                }

                                DragHandle.BOTTOM_LEFT -> {
                                    val newLeft = (current.left + dx).coerceIn(
                                        0f,
                                        current.right - 0.1f
                                    )
                                    val newW = current.right - newLeft
                                    val newH = newW / normalizedCropAR
                                    val newBottom = current.top + newH
                                    if (newBottom <=
                                        1f
                                    ) {
                                        CropRegion(
                                            newLeft,
                                            current.top,
                                            current.right,
                                            newBottom
                                        )
                                    } else {
                                        current
                                    }
                                }

                                DragHandle.BOTTOM_RIGHT -> {
                                    val newRight = (current.right + dx).coerceIn(
                                        current.left + 0.1f,
                                        1f
                                    )
                                    val newW = newRight - current.left
                                    val newH = newW / normalizedCropAR
                                    val newBottom = current.top + newH
                                    if (newBottom <=
                                        1f
                                    ) {
                                        CropRegion(
                                            current.left,
                                            current.top,
                                            newRight,
                                            newBottom
                                        )
                                    } else {
                                        current
                                    }
                                }

                                DragHandle.BODY -> {
                                    val cropW = current.right - current.left
                                    val cropH = current.bottom - current.top
                                    val newLeft = (current.left + dx).coerceIn(0f, 1f - cropW)
                                    val newTop = (current.top + dy).coerceIn(0f, 1f - cropH)
                                    CropRegion(newLeft, newTop, newLeft + cropW, newTop + cropH)
                                }
                            }
                            latestOnChange(newRegion)
                        }

                        changes.forEach { it.consume() }
                    } while (changes.any { it.pressed })
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cropRect = Rect(
                left = cropRegion.left * w,
                top = cropRegion.top * h,
                right = cropRegion.right * w,
                bottom = cropRegion.bottom * h
            )

            drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(w, cropRect.top))
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                Offset(0f, cropRect.bottom),
                Size(
                    w,
                    h - cropRect.bottom
                )
            )
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                Offset(0f, cropRect.top),
                Size(cropRect.left, cropRect.height)
            )
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                Offset(cropRect.right, cropRect.top),
                Size(w - cropRect.right, cropRect.height)
            )

            drawRect(
                Color.White,
                Offset(cropRect.left, cropRect.top),
                Size(cropRect.width, cropRect.height),
                style = Stroke(width = 2f)
            )

            val handleSize = 16f
            listOf(
                Offset(cropRect.left, cropRect.top),
                Offset(cropRect.right, cropRect.top),
                Offset(cropRect.left, cropRect.bottom),
                Offset(cropRect.right, cropRect.bottom)
            ).forEach { corner ->
                drawCircle(color = Color.White, radius = handleSize / 2f, center = corner)
            }

            val thirdW = cropRect.width / 3f
            val thirdH = cropRect.height / 3f
            for (i in 1..2) {
                drawLine(
                    Color.White.copy(alpha = 0.3f),
                    Offset(cropRect.left + thirdW * i, cropRect.top),
                    Offset(cropRect.left + thirdW * i, cropRect.bottom),
                    strokeWidth = 1f
                )
                drawLine(
                    Color.White.copy(alpha = 0.3f),
                    Offset(
                        cropRect.left,
                        cropRect.top + thirdH * i
                    ),
                    Offset(cropRect.right, cropRect.top + thirdH * i),
                    strokeWidth = 1f
                )
            }
        }
    }
}

private fun isNear(offset: Offset, x: Float, y: Float, radius: Float): Boolean =
    (offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y) <= radius * radius

private fun resolveMediaDimensions(
    context: android.content.Context,
    uri: Uri,
    type: MediaType
): IntSize? = try {
    if (type == MediaType.VIDEO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val w = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull()
        val h = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull()
        retriever.release()
        if (w != null && h != null) IntSize(w, h) else null
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        if (options.outWidth > 0 &&
            options.outHeight > 0
        ) {
            IntSize(options.outWidth, options.outHeight)
        } else {
            null
        }
    }
} catch (_: Exception) {
    null
}

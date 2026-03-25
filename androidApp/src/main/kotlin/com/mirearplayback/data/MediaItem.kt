package com.mirearplayback.data

import kotlinx.serialization.Serializable

enum class MediaType {
    IMAGE,
    GIF,
    VIDEO
    ;

    companion object {
        fun fromMimeType(mimeType: String?): MediaType = when {
            mimeType == null -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType == "image/gif" -> GIF
            mimeType.startsWith("image/") -> IMAGE
            else -> IMAGE
        }
    }
}

@Serializable
data class MediaItem(
    val uri: String,
    val type: MediaType,
    val displayName: String = "",
    val cropRegion: CropRegion = CropRegion()
)

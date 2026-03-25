package com.mirearplayback.data

import kotlinx.serialization.Serializable

@Serializable
data class CarouselState(val items: List<MediaItem> = emptyList(), val currentIndex: Int = 0) {
    val currentItem: MediaItem?
        get() = items.getOrNull(currentIndex)

    val isEmpty: Boolean
        get() = items.isEmpty()

    val size: Int
        get() = items.size

    fun nextIndex(): Int = if (items.isEmpty()) 0 else (currentIndex + 1) % items.size

    fun previousIndex(): Int = if (items.isEmpty()) {
        0
    } else {
        (currentIndex - 1 + items.size) %
            items.size
    }
}

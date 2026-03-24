package com.mirearplayback.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mirearplayback_settings",
)

class MediaRepository(
    context: Context,
) {
    private val dataStore = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val carouselState: Flow<CarouselState> =
        dataStore.data.map { prefs ->
            val itemsJson = prefs[Keys.CAROUSEL_ITEMS] ?: "[]"
            val index = prefs[Keys.CAROUSEL_INDEX] ?: 0
            val items =
                try {
                    json.decodeFromString<List<MediaItem>>(itemsJson)
                } catch (_: Exception) {
                    emptyList()
                }
            CarouselState(items = items, currentIndex = index.coerceIn(0, maxOf(items.size - 1, 0)))
        }

    suspend fun setCarouselItems(items: List<MediaItem>) {
        dataStore.edit { prefs ->
            prefs[Keys.CAROUSEL_ITEMS] = json.encodeToString(items)
            val currentIndex = prefs[Keys.CAROUSEL_INDEX] ?: 0
            prefs[Keys.CAROUSEL_INDEX] = currentIndex.coerceIn(0, maxOf(items.size - 1, 0))
        }
    }

    suspend fun addMediaItem(item: MediaItem) {
        dataStore.edit { prefs ->
            val existing =
                try {
                    json.decodeFromString<List<MediaItem>>(prefs[Keys.CAROUSEL_ITEMS] ?: "[]")
                } catch (_: Exception) {
                    emptyList()
                }
            prefs[Keys.CAROUSEL_ITEMS] = json.encodeToString(existing + item)
        }
    }

    suspend fun removeMediaItem(index: Int) {
        dataStore.edit { prefs ->
            val existing =
                try {
                    json.decodeFromString<List<MediaItem>>(prefs[Keys.CAROUSEL_ITEMS] ?: "[]")
                } catch (_: Exception) {
                    emptyList()
                }
            if (index in existing.indices) {
                val updated = existing.toMutableList().apply { removeAt(index) }
                prefs[Keys.CAROUSEL_ITEMS] = json.encodeToString(updated)
                val currentIndex = prefs[Keys.CAROUSEL_INDEX] ?: 0
                prefs[Keys.CAROUSEL_INDEX] = currentIndex.coerceIn(0, maxOf(updated.size - 1, 0))
            }
        }
    }

    suspend fun setCurrentIndex(index: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.CAROUSEL_INDEX] = index
        }
    }

    suspend fun advanceToNext() {
        dataStore.edit { prefs ->
            val items =
                try {
                    json.decodeFromString<List<MediaItem>>(prefs[Keys.CAROUSEL_ITEMS] ?: "[]")
                } catch (_: Exception) {
                    emptyList()
                }
            if (items.isNotEmpty()) {
                val current = prefs[Keys.CAROUSEL_INDEX] ?: 0
                prefs[Keys.CAROUSEL_INDEX] = (current + 1) % items.size
            }
        }
    }

    suspend fun advanceToPrevious() {
        dataStore.edit { prefs ->
            val items =
                try {
                    json.decodeFromString<List<MediaItem>>(prefs[Keys.CAROUSEL_ITEMS] ?: "[]")
                } catch (_: Exception) {
                    emptyList()
                }
            if (items.isNotEmpty()) {
                val current = prefs[Keys.CAROUSEL_INDEX] ?: 0
                prefs[Keys.CAROUSEL_INDEX] = (current - 1 + items.size) % items.size
            }
        }
    }

    suspend fun moveItem(
        fromIndex: Int,
        toIndex: Int,
    ) {
        dataStore.edit { prefs ->
            val existing =
                try {
                    json.decodeFromString<List<MediaItem>>(prefs[Keys.CAROUSEL_ITEMS] ?: "[]")
                } catch (_: Exception) {
                    emptyList()
                }
            if (fromIndex in existing.indices && toIndex in existing.indices) {
                val updated =
                    existing.toMutableList().apply {
                        val item = removeAt(fromIndex)
                        add(toIndex, item)
                    }
                prefs[Keys.CAROUSEL_ITEMS] = json.encodeToString(updated)
            }
        }
    }

    private object Keys {
        val CAROUSEL_ITEMS = stringPreferencesKey("carousel_items")
        val CAROUSEL_INDEX = intPreferencesKey("carousel_index")
        val GESTURE_SETTINGS = stringPreferencesKey("gesture_settings")
    }

    val gestureSettings: Flow<GestureSettings> =
        dataStore.data.map { prefs ->
            val settingsJson = prefs[Keys.GESTURE_SETTINGS]
            if (settingsJson != null) {
                try {
                    json.decodeFromString<GestureSettings>(settingsJson)
                } catch (_: Exception) {
                    GestureSettings()
                }
            } else {
                GestureSettings()
            }
        }

    suspend fun updateGestureSettings(settings: GestureSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.GESTURE_SETTINGS] = json.encodeToString(settings)
        }
    }

    suspend fun updateMediaItemCrop(
        index: Int,
        cropRegion: CropRegion,
    ) {
        dataStore.edit { prefs ->
            val existing =
                try {
                    json.decodeFromString<List<MediaItem>>(prefs[Keys.CAROUSEL_ITEMS] ?: "[]")
                } catch (_: Exception) {
                    emptyList()
                }
            if (index in existing.indices) {
                val updated =
                    existing.toMutableList().apply {
                        this[index] = this[index].copy(cropRegion = cropRegion)
                    }
                prefs[Keys.CAROUSEL_ITEMS] = json.encodeToString(updated)
            }
        }
    }
}

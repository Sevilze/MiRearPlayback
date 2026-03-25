package com.mirearplayback.data

import kotlinx.serialization.Serializable

enum class GestureAction {
    NONE,
    OPEN_CAMERA,
    NEXT_MEDIA,
    PREVIOUS_MEDIA,
    SHOW_NOTIFICATIONS,
    TOGGLE_REAR_DISPLAY
    ;

    val displayName: String
        get() =
            when (this) {
                NONE -> "None"
                OPEN_CAMERA -> "Open Camera"
                NEXT_MEDIA -> "Next Media"
                PREVIOUS_MEDIA -> "Previous Media"
                SHOW_NOTIFICATIONS -> "Show Notifications"
                TOGGLE_REAR_DISPLAY -> "Toggle Rear Display"
            }
}

@Serializable
data class GestureSettings(
    val swipeLeftToRight: GestureAction = GestureAction.OPEN_CAMERA,
    val swipeRightToLeft: GestureAction = GestureAction.NONE,
    val swipeTopToBottom: GestureAction = GestureAction.SHOW_NOTIFICATIONS,
    val swipeBottomToTop: GestureAction = GestureAction.NONE,
    val doubleTap: GestureAction = GestureAction.TOGGLE_REAR_DISPLAY,
    val twoFingerSwipeLeft: GestureAction = GestureAction.NEXT_MEDIA,
    val twoFingerSwipeRight: GestureAction = GestureAction.PREVIOUS_MEDIA
)

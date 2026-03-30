package com.mirearplayback

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Animatable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import coil3.ImageLoader
import coil3.asDrawable
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.mirearplayback.data.CropRegion
import com.mirearplayback.data.GestureAction
import com.mirearplayback.data.GestureSettings
import com.mirearplayback.data.MediaType
import com.mirearplayback.gesture.MultiTouchGestureHandler
import com.mirearplayback.service.MediaKeeperService

class MediaPlaybackActivity :
    Activity(),
    SurfaceHolder.Callback {
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var imageView: ImageView? = null
    private var container: FrameLayout? = null
    private var mediaUri: Uri? = null
    private var mediaType: MediaType = MediaType.VIDEO
    private var cropRegion: CropRegion = CropRegion()
    private var mediaPlayerPrepared = false
    private var isBlankScreen = false
    private var blankOverlay: View? = null
    private lateinit var gestureHandler: MultiTouchGestureHandler
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private val stopReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_STOP -> {
                        Log.d(TAG, "Stop broadcast received, finishing")
                        finish()
                    }

                    ACTION_SWITCH_MEDIA -> {
                        val newUri = intent.getStringExtra(EXTRA_MEDIA_URI)
                        val newType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
                        if (newUri != null && newType != null) {
                            mediaUri = Uri.parse(newUri)
                            mediaType = MediaType.valueOf(newType)
                            cropRegion = readCropRegion(intent)
                            Log.d(TAG, "Switching to $mediaType: $mediaUri")
                            switchMedia()
                        }
                    }

                    ACTION_BLANK -> {
                        blankRearDisplay()
                    }

                    ACTION_UNBLANK -> {
                        unblankRearDisplay()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriStr = intent.getStringExtra(EXTRA_MEDIA_URI)
        if (uriStr != null) {
            mediaUri = Uri.parse(uriStr)
        }
        val typeStr = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        if (typeStr != null) {
            mediaType =
                try {
                    MediaType.valueOf(typeStr)
                } catch (_: Exception) {
                    MediaType.VIDEO
                }
        }
        cropRegion = readCropRegion(intent)

        setupGestures()
        setupContent()
    }

    private fun setupGestures() {
        gestureHandler =
            MultiTouchGestureHandler { gestureType ->
                val settings =
                    MediaKeeperService.instance?.currentGestureSettings ?: GestureSettings()
                val action = mapGestureToAction(gestureType, settings)
                executeGestureAction(action)
            }
    }

    private fun mapGestureToAction(
        gestureType: MultiTouchGestureHandler.GestureType,
        settings: GestureSettings
    ): GestureAction = when (gestureType) {
        MultiTouchGestureHandler.GestureType.ONE_FINGER_SWIPE_RIGHT -> settings.swipeLeftToRight
        MultiTouchGestureHandler.GestureType.ONE_FINGER_SWIPE_LEFT -> settings.swipeRightToLeft
        MultiTouchGestureHandler.GestureType.ONE_FINGER_SWIPE_DOWN -> settings.swipeTopToBottom
        MultiTouchGestureHandler.GestureType.ONE_FINGER_SWIPE_UP -> settings.swipeBottomToTop
        MultiTouchGestureHandler.GestureType.TWO_FINGER_SWIPE_LEFT -> settings.twoFingerSwipeLeft
        MultiTouchGestureHandler.GestureType.TWO_FINGER_SWIPE_RIGHT -> settings.twoFingerSwipeRight
        MultiTouchGestureHandler.GestureType.DOUBLE_TAP -> settings.doubleTap
    }

    private fun executeGestureAction(action: GestureAction) {
        val service = MediaKeeperService.instance ?: return
        when (action) {
            GestureAction.NONE -> {}

            GestureAction.OPEN_CAMERA -> {
                service.openCamera()
            }

            GestureAction.NEXT_MEDIA -> {
                service.advanceCarousel(forward = true)
            }

            GestureAction.PREVIOUS_MEDIA -> {
                service.advanceCarousel(forward = false)
            }

            GestureAction.SHOW_NOTIFICATIONS -> {
                service.showNotifications()
            }

            GestureAction.TOGGLE_REAR_DISPLAY -> {
                service.toggleRearDisplay()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureHandler.onTouchEvent(ev)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back gesture from exiting
    }

    private fun blankRearDisplay() {
        if (isBlankScreen) return
        isBlankScreen = true
        pausePlayback()

        blankOverlay =
            View(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }
        container?.addView(blankOverlay)
        stopProgressReporting()
        Log.d(TAG, "Rear display blanked")
    }

    private fun unblankRearDisplay() {
        if (!isBlankScreen) return
        isBlankScreen = false
        blankOverlay?.let { container?.removeView(it) }
        blankOverlay = null
        resumePlayback()
        startProgressReporting()
        Log.d(TAG, "Rear display unblanked")
    }

    @Suppress("DEPRECATION")
    private fun setupContent() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        container =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }
        setContentView(container)

        val filter =
            IntentFilter().apply {
                addAction(ACTION_STOP)
                addAction(ACTION_SWITCH_MEDIA)
                addAction(ACTION_BLANK)
                addAction(ACTION_UNBLANK)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }

        showMedia()
    }

    private fun showMedia() {
        when (mediaType) {
            MediaType.VIDEO -> showVideo()
            MediaType.IMAGE, MediaType.GIF -> showImage()
        }
        if (mediaType == MediaType.VIDEO || mediaType == MediaType.GIF) {
            startProgressReporting()
        }
    }

    private fun switchMedia() {
        stopProgressReporting()
        releaseMediaPlayer()
        container?.removeAllViews()
        surfaceView = null
        imageView = null
        blankOverlay = null
        isBlankScreen = false
        showMedia()
    }

    private fun showVideo() {
        surfaceView =
            SurfaceView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }
        container?.addView(surfaceView)
        surfaceView!!.holder.addCallback(this)
        prepareMediaPlayer()
    }

    private fun showImage() {
        imageView =
            ImageView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                scaleType = ImageView.ScaleType.MATRIX
            }
        container?.addView(imageView)
        applyCropTransform(imageView)

        val uri = mediaUri ?: return

        val loaderBuilder = ImageLoader.Builder(this)
        if (mediaType == MediaType.GIF) {
            loaderBuilder.components { add(GifDecoder.Factory()) }
        }
        val loader = loaderBuilder.build()

        val request =
            ImageRequest
                .Builder(this)
                .data(uri)
                .allowHardware(false)
                .target(
                    onSuccess = { result ->
                        val drawable = result.asDrawable(resources)
                        imageView?.setImageDrawable(drawable)
                        (drawable as? Animatable)?.start()
                        applyCropTransform(imageView)
                    }
                ).build()
        loader.enqueue(request)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyCropTransform(surfaceView)
        applyCropTransform(imageView)
    }

    private fun applyCropTransform(targetView: View?) {
        val view = targetView ?: return
        val region = cropRegion

        if (view is ImageView) {
            applyImageMatrixCrop(view, region)
        } else {
            applyViewScaleCrop(view, region)
        }
    }

    private fun applyImageMatrixCrop(iv: ImageView, region: CropRegion) {
        iv.post {
            val viewW = iv.width.toFloat()
            val viewH = iv.height.toFloat()
            val drawable = iv.drawable
            if (viewW <= 0f || viewH <= 0f || drawable == null) return@post

            val imgW = drawable.intrinsicWidth.toFloat()
            val imgH = drawable.intrinsicHeight.toFloat()
            if (imgW <= 0f || imgH <= 0f) return@post

            val cropPixW = (region.right - region.left).coerceAtLeast(0.05f) * imgW
            val cropPixH = (region.bottom - region.top).coerceAtLeast(0.05f) * imgH
            val cropCenterPixX = (region.left + region.right) / 2f * imgW
            val cropCenterPixY = (region.top + region.bottom) / 2f * imgH

            val scale = maxOf(viewW / cropPixW, viewH / cropPixH)
            val matrix = Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(
                viewW / 2f - cropCenterPixX * scale,
                viewH / 2f - cropCenterPixY * scale
            )

            iv.scaleType = ImageView.ScaleType.MATRIX
            iv.imageMatrix = matrix
            iv.scaleX = 1f
            iv.scaleY = 1f
            iv.translationX = 0f
            iv.translationY = 0f
        }
    }

    private fun applyViewScaleCrop(view: View, region: CropRegion) {
        view.post {
            val width = view.width.toFloat()
            val height = view.height.toFloat()
            if (width <= 0f || height <= 0f) return@post

            if (region.isFullFrame) {
                view.scaleX = 1f
                view.scaleY = 1f
                view.translationX = 0f
                view.translationY = 0f
                return@post
            }

            val cropWidth = (region.right - region.left).coerceAtLeast(0.05f)
            val cropHeight = (region.bottom - region.top).coerceAtLeast(0.05f)
            val cropCenterX = (region.left + region.right) / 2f
            val cropCenterY = (region.top + region.bottom) / 2f

            view.pivotX = width / 2f
            view.pivotY = height / 2f
            view.scaleX = 1f / cropWidth
            view.scaleY = 1f / cropHeight
            view.translationX = (0.5f - cropCenterX) * width * view.scaleX
            view.translationY = (0.5f - cropCenterY) * height * view.scaleY
        }
    }

    private fun readCropRegion(intent: Intent): CropRegion = CropRegion(
        left = intent.getFloatExtra(EXTRA_CROP_LEFT, 0f),
        top = intent.getFloatExtra(EXTRA_CROP_TOP, 0f),
        right = intent.getFloatExtra(EXTRA_CROP_RIGHT, 1f),
        bottom = intent.getFloatExtra(EXTRA_CROP_BOTTOM, 1f)
    )

    private fun prepareMediaPlayer() {
        if (mediaUri == null) {
            Log.e(TAG, "No media URI provided")
            return
        }
        if (mediaPlayer != null) return

        try {
            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(this@MediaPlaybackActivity, mediaUri!!)
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "MediaPlayer prepared")
                        mediaPlayerPrepared = true
                        val sv = surfaceView
                        if (sv != null && sv.holder.surface.isValid) {
                            attachSurfaceAndPlay(sv.holder)
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                        true
                    }
                    prepareAsync()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
        }
    }

    private fun attachSurfaceAndPlay(holder: SurfaceHolder) {
        val mp = mediaPlayer ?: return
        if (!mediaPlayerPrepared) return
        try {
            mp.setDisplay(holder)
            applyCropTransform(surfaceView)
            if (!mp.isPlaying) {
                mp.start()
                Log.d(TAG, "Playback started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach surface and play", e)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (mediaPlayer != null && mediaPlayerPrepared) {
            attachSurfaceAndPlay(holder)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mediaPlayer?.let { mp ->
            try {
                mp.setDisplay(null)
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching display", e)
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.pause()
            } catch (_: Exception) {
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let { mp ->
            try {
                if (!mp.isPlaying && mediaPlayerPrepared) mp.start()
            } catch (_: Exception) {
            }
        }
    }

    private fun startProgressReporting() {
        stopProgressReporting()
        progressRunnable =
            object : Runnable {
                override fun run() {
                    if (mediaType == MediaType.VIDEO) {
                        mediaPlayer?.let { mp ->
                            if (mediaPlayerPrepared) {
                                try {
                                    val duration = mp.duration
                                    val position = mp.currentPosition
                                    if (duration > 0) {
                                        broadcastProgress(position.toFloat() / duration.toFloat())
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } else if (mediaType == MediaType.GIF) {
                        broadcastProgress(-1f)
                    }
                    progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
                }
            }
        progressHandler.post(progressRunnable!!)
    }

    private fun stopProgressReporting() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun broadcastProgress(progress: Float) {
        val intent =
            Intent(ACTION_PROGRESS_UPDATE).apply {
                putExtra(EXTRA_PROGRESS, progress)
                setPackage(packageName)
            }
        sendBroadcast(intent)
    }

    private fun releaseMediaPlayer() {
        mediaPlayerPrepared = false
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopProgressReporting()
        releaseMediaPlayer()
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {
        }

        Thread {
            try {
                Thread.sleep(100)
                val taskService = MediaKeeperService.getTaskService()
                if (taskService != null) {
                    taskService.executeShellCommand(
                        "am start --display 1 -n com.xiaomi.subscreencenter/.subscreenlauncher.SubScreenLauncherActivity"
                    )
                    Log.d(TAG, "System launcher restored")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore system launcher", e)
            }
        }.start()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaPlaybackActivity"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        const val EXTRA_MEDIA_URI = "media_uri"
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_CROP_LEFT = "crop_left"
        const val EXTRA_CROP_TOP = "crop_top"
        const val EXTRA_CROP_RIGHT = "crop_right"
        const val EXTRA_CROP_BOTTOM = "crop_bottom"
        const val EXTRA_PROGRESS = "playback_progress"
        const val ACTION_STOP = "com.mirearplayback.STOP_PLAYBACK"
        const val ACTION_SWITCH_MEDIA = "com.mirearplayback.SWITCH_MEDIA"
        const val ACTION_BLANK = "com.mirearplayback.BLANK_DISPLAY"
        const val ACTION_UNBLANK = "com.mirearplayback.UNBLANK_DISPLAY"
        const val ACTION_PROGRESS_UPDATE = "com.mirearplayback.PROGRESS_UPDATE"
    }
}

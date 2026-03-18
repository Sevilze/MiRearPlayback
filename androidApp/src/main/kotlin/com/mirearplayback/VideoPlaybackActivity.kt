package com.mirearplayback

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager

class VideoPlaybackActivity :
    Activity(),
    SurfaceHolder.Callback {
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var videoUri: Uri? = null
    private var mediaPlayerPrepared = false

    private val stopReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (ACTION_STOP == intent.action) {
                    Log.d(TAG, "Stop broadcast received, finishing")
                    finish()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriStr != null) {
            videoUri = Uri.parse(uriStr)
        }

        val displayId = getDisplayIdCompat()
        Log.d(TAG, "onCreate on displayId=$displayId")

        // Always set up the content view, even on display 0.
        // The activity will be moved to display 1 shortly after launch,
        // and the view tree (including SurfaceView) follows the move.
        setupContent()
    }

    private fun getDisplayIdCompat(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && display != null) {
            return display!!.displayId
        }
        return 0
    }

    @Suppress("DEPRECATION")
    private fun setupContent() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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

        setContentView(R.layout.activity_video_playback)

        surfaceView = findViewById(R.id.surface_view)
        surfaceView!!.holder.addCallback(this)

        val filter = IntentFilter(ACTION_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }

        prepareMediaPlayer()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val displayId = getDisplayIdCompat()
        Log.d(TAG, "onConfigurationChanged, displayId=$displayId")
    }

    private fun prepareMediaPlayer() {
        if (videoUri == null) {
            Log.e(TAG, "No video URI provided")
            return
        }
        if (mediaPlayer != null) return

        try {
            mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(this@VideoPlaybackActivity, videoUri!!)
                    isLooping = true
                    setOnPreparedListener {
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
            if (!mp.isPlaying) {
                mp.start()
                Log.d(TAG, "Playback started/resumed on displayId=${getDisplayIdCompat()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach surface and play", e)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface created, displayId=${getDisplayIdCompat()}")
        if (mediaPlayer != null && mediaPlayerPrepared) {
            attachSurfaceAndPlay(holder)
        }
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        Log.d(TAG, "Surface changed: ${width}x$height, displayId=${getDisplayIdCompat()}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed - detaching display only, displayId=${getDisplayIdCompat()}")
        mediaPlayer?.let { mp ->
            try {
                mp.setDisplay(null)
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching display", e)
            }
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayerPrepared = false
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        releaseMediaPlayer()
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {
        }

        Thread {
            try {
                Thread.sleep(100)
                val taskService = VideoKeeperService.getTaskService()
                if (taskService != null) {
                    taskService.executeShellCommand(
                        "am start --display 1 -n com.xiaomi.subscreencenter/.subscreenlauncher.SubScreenLauncherActivity",
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
        private const val TAG = "VideoPlaybackActivity"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val ACTION_STOP = "com.mirearplayback.STOP_PLAYBACK"
    }
}

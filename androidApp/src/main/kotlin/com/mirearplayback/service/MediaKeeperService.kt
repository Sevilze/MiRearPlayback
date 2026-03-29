package com.mirearplayback.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.mirearplayback.ITaskService
import com.mirearplayback.MediaPlaybackActivity
import com.mirearplayback.data.CarouselState
import com.mirearplayback.data.CropRegion
import com.mirearplayback.data.GestureSettings
import com.mirearplayback.data.MediaRepository
import com.mirearplayback.data.MediaType
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MediaKeeperService : Service() {
    private var taskService: ITaskService? = null
    private lateinit var displayMonitorHandler: Handler
    private var displayMonitorRunnable: Runnable? = null
    private var isPlaying = false
    private var rearDisplayOn = true
    private var rearDisplayBlanked = false
    private var currentCarouselState: CarouselState? = null
    var currentGestureSettings: GestureSettings = GestureSettings()
        private set
    private lateinit var mediaRepository: MediaRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val serviceArgs =
        Shizuku
            .UserServiceArgs(
                ComponentName("com.mirearplayback", TaskService::class.java.name)
            ).daemon(false)
            .processNameSuffix("task_service")
            .debuggable(false)
            .version(1)

    private val taskServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.d(TAG, "TaskService connected")
                taskService = ITaskService.Stub.asInterface(binder)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(TAG, "TaskService disconnected")
                taskService = null
                Handler(Looper.getMainLooper()).postDelayed({
                    if (taskService == null) bindTaskService()
                }, 1000)
            }
        }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaRepository = MediaRepository(this)
        displayMonitorHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))

        Shizuku.addBinderReceivedListenerSticky { bindTaskService() }
        Shizuku.addBinderDeadListener {
            taskService = null
            Handler(Looper.getMainLooper()).postDelayed({ bindTaskService() }, 1000)
        }

        bindTaskService()

        serviceScope.launch {
            mediaRepository.gestureSettings.collectLatest { settings ->
                currentGestureSettings = settings
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_START_PLAYBACK -> {
                serviceScope.launch {
                    currentCarouselState = mediaRepository.carouselState.first()
                    startPlaybackOnRearScreen()
                }
            }

            ACTION_STOP_PLAYBACK -> {
                stopPlayback()
            }
        }

        return START_STICKY
    }

    private fun startPlaybackOnRearScreen() {
        val state = currentCarouselState
        if (state == null || state.isEmpty) {
            Log.w(TAG, "No media items in carousel")
            return
        }

        if (taskService == null) {
            Log.w(TAG, "TaskService not ready, retrying in 500ms")
            Handler(Looper.getMainLooper()).postDelayed({ startPlaybackOnRearScreen() }, 500)
            return
        }

        Thread {
            try {
                taskService!!.executeShellCommand("am force-stop com.xiaomi.subscreencenter")
                Thread.sleep(200)

                taskService!!.executeShellCommand("input -d 1 keyevent KEYCODE_WAKEUP")
                Thread.sleep(100)

                val currentItem = state.currentItem ?: return@Thread
                val componentName = "com.mirearplayback/.MediaPlaybackActivity"
                val launchCmd =
                    buildString {
                        append("am start --display 1 -n $componentName")
                        append(
                            " --es ${MediaPlaybackActivity.EXTRA_MEDIA_URI} '${currentItem.uri}'"
                        )
                        append(
                            " --es ${MediaPlaybackActivity.EXTRA_MEDIA_TYPE} '${currentItem.type.name}'"
                        )
                        appendCropExtras(currentItem.cropRegion)
                    }

                Log.d(TAG, "Launching playback activity on display 1")
                val result = taskService!!.executeShellCommandWithResult(launchCmd)
                Log.d(TAG, "Launch result: $result")

                Thread.sleep(300)
                taskService!!.executeShellCommand("input -d 1 keyevent KEYCODE_WAKEUP")

                isPlaying = true
                rearDisplayOn = true
                updateNotification("Playing on rear screen")
                startDisplayMonitor()
                broadcastStateChange()

                Log.d(TAG, "Media playback started on rear screen")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start playback on rear screen", e)
            }
        }.start()
    }

    fun advanceCarousel(forward: Boolean) {
        serviceScope.launch {
            if (forward) {
                mediaRepository.advanceToNext()
            } else {
                mediaRepository.advanceToPrevious()
            }

            currentCarouselState = mediaRepository.carouselState.first()
            val item = currentCarouselState?.currentItem ?: return@launch

            val switchIntent =
                Intent(MediaPlaybackActivity.ACTION_SWITCH_MEDIA).apply {
                    putExtra(MediaPlaybackActivity.EXTRA_MEDIA_URI, item.uri)
                    putExtra(MediaPlaybackActivity.EXTRA_MEDIA_TYPE, item.type.name)
                    putExtra(MediaPlaybackActivity.EXTRA_CROP_LEFT, item.cropRegion.left)
                    putExtra(MediaPlaybackActivity.EXTRA_CROP_TOP, item.cropRegion.top)
                    putExtra(MediaPlaybackActivity.EXTRA_CROP_RIGHT, item.cropRegion.right)
                    putExtra(MediaPlaybackActivity.EXTRA_CROP_BOTTOM, item.cropRegion.bottom)
                    setPackage(packageName)
                }
            sendBroadcast(switchIntent)
        }
    }

    private fun StringBuilder.appendCropExtras(cropRegion: CropRegion) {
        append(
            " --ef ${MediaPlaybackActivity.EXTRA_CROP_LEFT} " +
                String.format(Locale.US, "%.6f", cropRegion.left)
        )
        append(
            " --ef ${MediaPlaybackActivity.EXTRA_CROP_TOP} " +
                String.format(Locale.US, "%.6f", cropRegion.top)
        )
        append(
            " --ef ${MediaPlaybackActivity.EXTRA_CROP_RIGHT} " +
                String.format(Locale.US, "%.6f", cropRegion.right)
        )
        append(
            " --ef ${MediaPlaybackActivity.EXTRA_CROP_BOTTOM} " +
                String.format(Locale.US, "%.6f", cropRegion.bottom)
        )
    }

    fun toggleRearDisplay() {
        if (rearDisplayBlanked) {
            rearDisplayBlanked = false
            val intent =
                Intent(MediaPlaybackActivity.ACTION_UNBLANK).apply {
                    setPackage(packageName)
                }
            sendBroadcast(intent)
            Log.d(TAG, "Rear display unblanked via toggle")
        } else {
            rearDisplayBlanked = true
            val intent =
                Intent(MediaPlaybackActivity.ACTION_BLANK).apply {
                    setPackage(packageName)
                }
            sendBroadcast(intent)
            Log.d(TAG, "Rear display blanked via toggle")
        }
    }

    fun openCamera() {
        Thread {
            try {
                taskService?.executeShellCommand(
                    "am start --display 1 -a android.media.action.STILL_IMAGE_CAMERA"
                )
                Log.d(TAG, "Camera launched on rear display")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch camera", e)
            }
        }.start()
    }

    fun showNotifications() {
        Thread {
            try {
                taskService?.executeShellCommand("cmd statusbar expand-notifications")
                Log.d(TAG, "Notifications expanded")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to expand notifications", e)
            }
        }.start()
    }

    private fun startDisplayMonitor() {
        stopDisplayMonitor()
        displayMonitorRunnable =
            object : Runnable {
                override fun run() {
                    if (!isPlaying || taskService == null) return
                    try {
                        val result =
                            taskService!!.executeShellCommandWithResult(
                                "dumpsys display | grep 'mState=' | head -2"
                            )
                        val displayStates =
                            result?.lines()?.mapNotNull { line ->
                                if (line.contains("mState=")) {
                                    line
                                        .substringAfter("mState=")
                                        .trim()
                                        .takeWhile { it.isDigit() }
                                        .toIntOrNull()
                                } else {
                                    null
                                }
                            } ?: emptyList()

                        // Display state 2 = ON, 1 = DOZE, other = OFF
                        val rearState = displayStates.getOrNull(1)
                        val wasOn = rearDisplayOn
                        rearDisplayOn = rearState == 2

                        if (wasOn && !rearDisplayOn) {
                            Log.d(TAG, "Rear display turned off, pausing playback")
                            broadcastPauseResume(pause = true)
                        } else if (!wasOn && rearDisplayOn) {
                            Log.d(TAG, "Rear display turned on, resuming playback")
                            broadcastPauseResume(pause = false)
                        }

                        // Kill system rear launcher if it's running
                        val psResult =
                            taskService!!.executeShellCommandWithResult(
                                "ps -A | grep com.xiaomi.subscreencenter"
                            )
                        if (!psResult.isNullOrBlank()) {
                            taskService!!.executeShellCommand(
                                "am force-stop com.xiaomi.subscreencenter"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Display monitor cycle error", e)
                    }
                    displayMonitorHandler.postDelayed(this, DISPLAY_MONITOR_INTERVAL_MS)
                }
            }
        displayMonitorHandler.post(displayMonitorRunnable!!)
    }

    private fun broadcastPauseResume(pause: Boolean) {
        val intent =
            Intent(if (pause) ACTION_PAUSE else ACTION_RESUME).apply {
                setPackage(packageName)
            }
        sendBroadcast(intent)
    }

    private fun stopDisplayMonitor() {
        displayMonitorRunnable?.let {
            displayMonitorHandler.removeCallbacks(it)
        }
        displayMonitorRunnable = null
    }

    private fun stopPlayback() {
        isPlaying = false
        stopDisplayMonitor()

        val stopIntent =
            Intent(MediaPlaybackActivity.ACTION_STOP).apply {
                setPackage(packageName)
            }
        sendBroadcast(stopIntent)

        updateNotification("Stopped")
        broadcastStateChange()

        Handler(Looper.getMainLooper()).postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, 500)
    }

    private fun broadcastStateChange() {
        val intent =
            Intent("com.mirearplayback.STATE_CHANGED").apply {
                putExtra("is_playing", isPlaying)
                setPackage(packageName)
            }
        sendBroadcast(intent)
    }

    private fun bindTaskService() {
        try {
            if (!Shizuku.pingBinder()) {
                Log.w(TAG, "Shizuku not available")
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "No Shizuku permission")
                return
            }
            Shizuku.bindUserService(serviceArgs, taskServiceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind TaskService", e)
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Rear Screen Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps media playing on the rear screen"
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent =
            Intent(this, MediaKeeperService::class.java).apply {
                action = ACTION_STOP_PLAYBACK
            }
        val stopPending =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle("MiRearPlayback")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "Stop", stopPending).build())
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopDisplayMonitor()
        serviceScope.cancel()
        instance = null
        try {
            Shizuku.unbindUserService(serviceArgs, taskServiceConnection, true)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MediaKeeperService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mirearplayback_service"
        private const val DISPLAY_MONITOR_INTERVAL_MS = 5000L

        const val ACTION_START_PLAYBACK = "com.mirearplayback.START_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.mirearplayback.STOP_PLAYBACK"
        const val ACTION_PAUSE = "com.mirearplayback.PAUSE_PLAYBACK"
        const val ACTION_RESUME = "com.mirearplayback.RESUME_PLAYBACK"

        var instance: MediaKeeperService? = null
            private set

        fun getTaskService(): ITaskService? = instance?.taskService

        fun isCurrentlyPlaying(): Boolean = instance?.isPlaying == true
    }
}

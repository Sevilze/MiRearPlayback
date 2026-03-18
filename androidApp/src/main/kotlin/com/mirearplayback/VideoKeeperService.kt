package com.mirearplayback

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
import android.os.PowerManager
import android.util.Log
import rikka.shizuku.Shizuku

class VideoKeeperService : Service() {
    private var taskService: ITaskService? = null
    private lateinit var wakeupHandler: Handler
    private var wakeupRunnable: Runnable? = null
    private var isPlaying = false
    private var currentVideoUri: Uri? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceArgs =
        Shizuku
            .UserServiceArgs(
                ComponentName("com.mirearplayback", TaskService::class.java.name),
            ).daemon(false)
            .processNameSuffix("task_service")
            .debuggable(false)
            .version(1)

    private val taskServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
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
        wakeupHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))

        Shizuku.addBinderReceivedListenerSticky { bindTaskService() }
        Shizuku.addBinderDeadListener {
            taskService = null
            Handler(Looper.getMainLooper()).postDelayed({ bindTaskService() }, 1000)
        }

        bindTaskService()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_START_PLAYBACK -> {
                val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
                if (uriStr != null) {
                    currentVideoUri = Uri.parse(uriStr)
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
        if (taskService == null) {
            Log.w(TAG, "TaskService not ready, retrying in 500ms")
            Handler(Looper.getMainLooper()).postDelayed({ startPlaybackOnRearScreen() }, 500)
            return
        }

        Thread {
            try {
                acquireWakeLock()

                // Kill the system rear screen launcher to prevent interference
                taskService!!.executeShellCommand("am force-stop com.xiaomi.subscreencenter")
                Thread.sleep(200)

                // Wake up the rear screen first
                taskService!!.executeShellCommand("input -d 1 keyevent KEYCODE_WAKEUP")
                Thread.sleep(100)

                // Launch directly on display 1 (rear screen)
                val componentName = "com.mirearplayback/.VideoPlaybackActivity"
                val launchCmd = "am start --display 1 -n $componentName --es video_uri '$currentVideoUri'"

                Log.d(TAG, "Launching playback activity directly on display 1")
                val result = taskService!!.executeShellCommandWithResult(launchCmd)
                Log.d(TAG, "Launch result: $result")

                Thread.sleep(300)

                // Wake up rear screen again after launch
                taskService!!.executeShellCommand("input -d 1 keyevent KEYCODE_WAKEUP")

                isPlaying = true
                updateNotification("Playing on rear screen")
                startWakeupLoop()
                broadcastStateChange()

                Log.d(TAG, "Video playback started on rear screen")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start playback on rear screen", e)
                releaseWakeLock()
            }
        }.start()
    }

    private fun stopPlayback() {
        isPlaying = false
        stopWakeupLoop()

        // Send stop broadcast to VideoPlaybackActivity
        val stopIntent =
            Intent(VideoPlaybackActivity.ACTION_STOP).apply {
                setPackage(packageName)
            }
        sendBroadcast(stopIntent)

        releaseWakeLock()
        updateNotification("Stopped")
        broadcastStateChange()

        // Stop service after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, 500)
    }

    private fun startWakeupLoop() {
        stopWakeupLoop()
        wakeupRunnable =
            object : Runnable {
                override fun run() {
                    if (!isPlaying || taskService == null) return
                    try {
                        taskService!!.executeShellCommand("input -d 1 keyevent KEYCODE_WAKEUP")

                        // Also ensure system launcher stays dead
                        val result =
                            taskService!!.executeShellCommandWithResult(
                                "ps -A | grep com.xiaomi.subscreencenter",
                            )
                        if (!result.isNullOrBlank()) {
                            taskService!!.executeShellCommand("am force-stop com.xiaomi.subscreencenter")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Wakeup/monitor cycle error", e)
                    }
                    wakeupHandler.postDelayed(this, WAKEUP_INTERVAL_MS)
                }
            }
        wakeupHandler.post(wakeupRunnable!!)
    }

    private fun stopWakeupLoop() {
        wakeupRunnable?.let {
            wakeupHandler.removeCallbacks(it)
        }
        wakeupRunnable = null
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

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiRearPlayback:Keeper").apply {
                    setReferenceCounted(false)
                }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(3600000) // 1 hour max
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Rear Screen Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps video playing on the rear screen"
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent =
            Intent(this, VideoKeeperService::class.java).apply {
                action = ACTION_STOP_PLAYBACK
            }
        val stopPending =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle("MiRearPlayback")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(
                Notification.Action.Builder(null, "Stop", stopPending).build(),
            ).setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopWakeupLoop()
        releaseWakeLock()
        instance = null
        try {
            Shizuku.unbindUserService(serviceArgs, taskServiceConnection, true)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VideoKeeperService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mirearplayback_service"
        private const val WAKEUP_INTERVAL_MS = 2000L

        const val EXTRA_VIDEO_URI = "video_uri"
        const val ACTION_START_PLAYBACK = "com.mirearplayback.START_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.mirearplayback.STOP_PLAYBACK"

        private var instance: VideoKeeperService? = null

        fun getTaskService(): ITaskService? = instance?.taskService

        fun isCurrentlyPlaying(): Boolean = instance?.isPlaying == true
    }
}

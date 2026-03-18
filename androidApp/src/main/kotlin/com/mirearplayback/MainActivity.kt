package com.mirearplayback

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var videoPathText: TextView
    private lateinit var selectVideoBtn: Button
    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button
    private var selectedVideoUri: Uri? = null
    private var shizukuPermissionGranted = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("com.mirearplayback.STATE_CHANGED" == intent.action) {
                val playing = intent.getBooleanExtra("is_playing", false)
                runOnUiThread { updatePlaybackUI(playing) }
            }
        }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
                shizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                runOnUiThread { updateShizukuStatus() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        videoPathText = findViewById(R.id.video_path_text)
        selectVideoBtn = findViewById(R.id.select_video_btn)
        playBtn = findViewById(R.id.play_btn)
        stopBtn = findViewById(R.id.stop_btn)

        selectVideoBtn.setOnClickListener { pickVideo() }
        playBtn.setOnClickListener { startPlayback() }
        stopBtn.setOnClickListener { stopPlayback() }

        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // Load saved video URI
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUri = prefs.getString(KEY_VIDEO_URI, null)
        if (savedUri != null) {
            selectedVideoUri = Uri.parse(savedUri)
            videoPathText.text = savedUri
        }

        val filter = IntentFilter("com.mirearplayback.STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }

        checkShizukuPermission()
    }

    override fun onResume() {
        super.onResume()
        checkShizukuPermission()
        updatePlaybackUI(VideoKeeperService.isCurrentlyPlaying())
    }

    private fun checkShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                statusText.text = "Shizuku is not running. Please start Shizuku first."
                shizukuPermissionGranted = false
                updateButtonStates()
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuPermissionGranted = true
                updateShizukuStatus()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                statusText.text = "Shizuku permission denied. Please grant it in Shizuku app."
                shizukuPermissionGranted = false
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION)
            }
        } catch (e: Exception) {
            statusText.text = "Shizuku not available: ${e.message}"
            shizukuPermissionGranted = false
        }
        updateButtonStates()
    }

    private fun updateShizukuStatus() {
        if (shizukuPermissionGranted) {
            statusText.text = "Ready - Shizuku connected"
        } else {
            statusText.text = "Shizuku permission not granted"
        }
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val canPlay = shizukuPermissionGranted && selectedVideoUri != null
        playBtn.isEnabled = canPlay && !VideoKeeperService.isCurrentlyPlaying()
        stopBtn.isEnabled = VideoKeeperService.isCurrentlyPlaying()
        selectVideoBtn.isEnabled = !VideoKeeperService.isCurrentlyPlaying()
    }

    private fun updatePlaybackUI(playing: Boolean) {
        if (playing) {
            statusText.text = "Playing on rear screen"
        } else if (shizukuPermissionGranted) {
            statusText.text = "Ready - Shizuku connected"
        }
        updateButtonStates()
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_VIDEO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            selectedVideoUri = data.data
            selectedVideoUri?.let { uri ->
                // Persist permission
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not persist URI permission", e)
                }

                videoPathText.text = uri.toString()

                // Save the URI
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_VIDEO_URI, uri.toString())
                    .apply()

                updateButtonStates()
            }
        }
    }

    private fun startPlayback() {
        if (selectedVideoUri == null) {
            Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!shizukuPermissionGranted) {
            Toast.makeText(this, "Shizuku permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, VideoKeeperService::class.java).apply {
            action = VideoKeeperService.ACTION_START_PLAYBACK
            putExtra(VideoKeeperService.EXTRA_VIDEO_URI, selectedVideoUri.toString())
        }
        startForegroundService(serviceIntent)

        statusText.text = "Starting playback..."
        updateButtonStates()
    }

    private fun stopPlayback() {
        val serviceIntent = Intent(this, VideoKeeperService::class.java).apply {
            action = VideoKeeperService.ACTION_STOP_PLAYBACK
        }
        startService(serviceIntent)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PICK_VIDEO = 100
        private const val REQUEST_SHIZUKU_PERMISSION = 101
        private const val PREFS_NAME = "mirearplayback_prefs"
        private const val KEY_VIDEO_URI = "video_uri"
    }
}

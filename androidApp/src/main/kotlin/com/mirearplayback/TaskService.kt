package com.mirearplayback

import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader

@Keep
class TaskService : ITaskService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    @Throws(RemoteException::class)
    override fun executeShellCommand(cmd: String): Boolean {
        return try {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shell command failed: $cmd", e)
            false
        }
    }

    @Throws(RemoteException::class)
    override fun executeShellCommandWithResult(cmd: String): String? {
        return try {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.redirectErrorStream(true)
            val process = pb.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream), 8192)
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Shell command with result failed: $cmd", e)
            null
        }
    }

    companion object {
        private const val TAG = "TaskService"
    }
}

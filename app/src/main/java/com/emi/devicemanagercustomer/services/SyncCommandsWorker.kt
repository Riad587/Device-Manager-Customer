package com.emi.devicemanagercustomer.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class SyncCommandsWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val mdmCommandExecutor by lazy { MdmCommandExecutor(applicationContext) }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun doWork(): Result {
        Log.d("SYNC_WORKER", "Periodic out-of-sync backup verification polling routine woke up.")
        showToast("🔄 WorkManager Woke Up! Contacting Django API...")

        val pendingCommandPayload = fetchPendingCommandsFromServer()
        if (pendingCommandPayload == null) {
            return Result.retry()
        }

        try {
            val executed = pendingCommandPayload.optBoolean("executed", true)

            if (executed) {
                Log.d("SYNC_WORKER", "Matrix status up-to-date. No pending executions discovered.")
                showToast("✅ Sync Check: Device is already up to date.")
                return Result.success()
            }

            val commandId = pendingCommandPayload.optString("id", "")
            val cameraLock = pendingCommandPayload.optBoolean("camera_lock", false)
            val settingsLock = pendingCommandPayload.optBoolean("settings_lock", false)
            val callLock = pendingCommandPayload.optBoolean("call_lock", false)
            val simLock = pendingCommandPayload.optBoolean("sim_lock", false)
            val deviceLock = pendingCommandPayload.optBoolean("device_lock", false)

            if (commandId.isEmpty()) {
                showToast("❌ Sync Error: Command payload has an empty ID property.")
                return Result.failure()
            }

            val isVerified = verifyExecutionWithServer(commandId)

            if (isVerified) {
                Log.d("SYNC_WORKER", "Polling confirmation success. Synchronizing MDM state layers.")
                showToast("🛡️ Locks Found! Applying local MDM changes...")

                mdmCommandExecutor.executeCameraLock(cameraLock)
                mdmCommandExecutor.executeSettingsLock(settingsLock)
                mdmCommandExecutor.executeCallLock(callLock)
                mdmCommandExecutor.executeSimLock(simLock)
                mdmCommandExecutor.executeDeviceLock(deviceLock)

                return Result.success()
            } else {
                Log.w("SYNC_WORKER", "Server acknowledgment handshake failed.")
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.e("SYNC_WORKER", "Error processing periodic synchronization fallback: ${e.message}")
            showToast("⚠️ Worker Core Exception: ${e.message}")
            return Result.failure()
        }
    }

    private fun fetchPendingCommandsFromServer(): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            // ১. ইউনিভার্সাল মেথড কল করে সরাসরি রিয়াল আইএমইআই নেওয়া
            val targetImei = ImeiHardwareResolver.getUniversalDeviceImei(applicationContext)

            if (targetImei.isEmpty() || targetImei.contains("UNAVAILABLE")) {
                Log.e("SYNC_WORKER", "❌ Pipeline Aborted: Universal Hardware Identifier is unresolvable.")
                return null
            }

            // ২. সিকিউরড JSON পেলোড তৈরি করা (প্যারামিটার নয়, বডিতে যাবে)
            val jsonPayload = JSONObject().apply {
                put("imei", targetImei)
            }

            val url = URL(ApiEndpoints.PENDING_COMMANDS_URL)
            connection = url.openConnection() as HttpURLConnection

            // ── মেথড পরিবর্তন করে POST করা হলো ──
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true // আউটপুট স্ট্রিম এনাবল করা

            // ৩. HTTP বডিতে JSON রাইট করা
            val os = connection.outputStream
            val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()
            os.close()

            // ৪. রেসপন্স রিড করা
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                Log.d("SYNC_WORKER", "📥 POST Fetch Success! Encapsulated data payload synchronized.")
                JSONObject(response.toString())
            } else {
                Log.w("SYNC_WORKER", "❌ POST Fetch Failed! Server HTTP Code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e("SYNC_WORKER", "Secure POST network pipeline halted: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun verifyExecutionWithServer(commandId: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(ApiEndpoints.COMMAND_EXECUTION_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.doOutput = true

            val jsonPayload = "{\"command_id\": \"$commandId\"}"
            val outputBytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)

            connection.outputStream.use { os ->
                os.write(outputBytes, 0, outputBytes.size)
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                showToast("📤 Acknowledge Success! Command handshaked.")
                true
            } else {
                showToast("❌ Acknowledge Failed! Server HTTP Code: $responseCode")
                false
            }
        } catch (e: Exception) {
            showToast("🌐 Acknowledge Network Error: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }
}
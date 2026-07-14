package com.emi.customer.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.WorkManager
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

class SyncCommandsWorker(context: Context, workerParams: WorkerParameters) : androidx.work.Worker(context, workerParams) {

    private val mdmCommandExecutor by lazy { MdmCommandExecutor(applicationContext) }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun doWork(): Result {
        Log.d("SYNC_WORKER", "Periodic out-of-sync backup verification polling routine woke up.")

        // ── 🚨 CRITICAL GATEWAY INTERCEPTION: IMEI EXTRACTION AND VERIFICATION ──
        val targetImei = ImeiHardwareResolver.getUniversalDeviceImei(applicationContext)
        if (targetImei.isEmpty()) {
            showToast("❌ IMEI is not found")
            Log.e("SYNC_WORKER", "Aborting background sync lifecycle sequence: Valid hardware identifier missing.")
            return Result.failure()
        }

        showToast("🔄 WorkManager Woke Up! Contacting Django API...")

        val pendingCommandPayload = fetchPendingCommandsFromServer(targetImei)
        if (pendingCommandPayload == null) {
            return Result.retry()
        }

        try {
            // ── 🚨 COMPLETE UNENROLLMENT SIGNAL INTERCEPTION ──
            val isCompleted = pendingCommandPayload.optBoolean("completed", false)
            if (isCompleted) {
                Log.w("SYNC_WORKER", "Remote server confirmed 'completed' status true. Launching unenrollment teardown...")

                Handler(Looper.getMainLooper()).post {
                    _root_ide_package_.com.emi.customer.helper.MdmPolicyManager(applicationContext).removeDevicePoliciesAndUninstall {
                        _root_ide_package_.com.emi.customer.controller.ShopOwnerController().clearLocalStates()
                        WorkManager.getInstance(applicationContext).cancelUniqueWork("MDM_COMMANDS_FALLBACK_SYNC")
                        Log.d("SYNC_WORKER", "Volatile runtime fields wiped; background synchronization cancelled.")
                    }
                }
                return Result.success()
            }

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

            // ── 🆕 EXTRACT NEW SOCIAL APP LOGS FROM DJANGO ──────────────────
            val whatsappLock = pendingCommandPayload.optBoolean("whatsapp_lock", false)
            val imoLock = pendingCommandPayload.optBoolean("imo_lock", false)
            val messengerLock = pendingCommandPayload.optBoolean("messenger_lock", false)
            val facebookLock = pendingCommandPayload.optBoolean("facebook_lock", false)
            val youtubeLock = pendingCommandPayload.optBoolean("youtube_lock", false)

            // 🎯 Extract the unlock key directly injected by your updated Django API view
            val unlockKey = pendingCommandPayload.optString("unlock_key", "").trim()

            if (commandId.isEmpty()) {
                showToast("❌ Sync Error: Command payload has an empty ID property.")
                return Result.failure()
            }

            val isVerified = verifyExecutionWithServer(commandId)

            if (isVerified) {
                Log.d("SYNC_WORKER", "Polling confirmation success. Synchronizing MDM state layers.")
                showToast("🛡️ Locks Found! Applying local MDM changes...")

                // ── 🛡️ FIXED CONTEXT DIVISION PIPELINE ──────────────────
                // MDM execution profiles run on the Main Looper context to prevent system verification crashes.
                Handler(Looper.getMainLooper()).post {
                    try {
                        mdmCommandExecutor.executeCameraLock(cameraLock)
                        mdmCommandExecutor.executeSettingsLock(settingsLock)
                        mdmCommandExecutor.executeCallLock(callLock)
                        mdmCommandExecutor.executeSimLock(simLock)

                        // ── ⚙️ EXECUTE NEW APPLICATION POLICIES ──────────────────
                        mdmCommandExecutor.executeWhatsAppLock(whatsappLock)
                        mdmCommandExecutor.executeImoLock(imoLock)
                        mdmCommandExecutor.executeMessengerLock(messengerLock)
                        mdmCommandExecutor.executeFacebookLock(facebookLock)
                        mdmCommandExecutor.executeYouTubeLock(youtubeLock)

                        // 🎯 FIX: Pass the unlock key string parameters straight to the executor.
                        // The executor internally handles saving it under "dynamic_unlock_key" inside protected context prefs.
                        val keyToPass = if (unlockKey.isNotEmpty()) unlockKey else null
                        mdmCommandExecutor.executeDeviceLock(deviceLock, keyToPass)

                    } catch (ex: Exception) {
                        Log.e("SYNC_WORKER", "Failed applying hardware configuration on fallback looper: ${ex.message}")
                    }
                }

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

    private fun fetchPendingCommandsFromServer(targetImei: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val jsonPayload = JSONObject().apply {
                put("imei", targetImei)
            }

            val url = URL(ApiEndpoints.PENDING_COMMANDS_URL)
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true

            val os = connection.outputStream
            val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()
            os.close()

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
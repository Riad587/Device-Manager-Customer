package com.emi.customer.services

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MyFirebaseMessagingService : com.google.firebase.messaging.FirebaseMessagingService() {

    // Lazy instantiation of our core business logic layer
    private val mdmCommandExecutor by lazy { MdmCommandExecutor(applicationContext) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM_SYSTEM", "Service Woke Up. Data received: ${remoteMessage.data}")

        if (remoteMessage.data.isNotEmpty()) {
            // 🎯 FIX: Pull from "command" key instead of empty space string " "
            val command = remoteMessage.data["command"] ?: remoteMessage.data[" "] ?: ""
            val statusStr = remoteMessage.data["status"] ?: "false"
            val commandId = remoteMessage.data["command_id"] ?: ""
            val isActivated = statusStr.equals("true", ignoreCase = true)

            val incomingTitle = remoteMessage.data["title"] ?: "System Policy Update"
            val incomingBody = remoteMessage.data["body"] ?: "Remote action rule execution trigger applied."

            // ── 🧠 OFFLINE-SAFE EXTRACTION PIPELINE ──────────────────
            var processedCommand = command
            var extractedUnlockKey: String? = null

            // Detect if command matches pattern signature: device_lock(secretKeyString)
            if (command.contains("device_lock(") && command.endsWith(")")) {
                processedCommand = "device_lock"
                extractedUnlockKey = command.substringAfter("device_lock(").substringBeforeLast(")")
                Log.d("FCM_SYSTEM", "Parsed offline dynamic key successfully: $extractedUnlockKey")
            }

            // Remote teardown completion check
            if (processedCommand == "completed" && isActivated) {
                Log.w("FCM_SYSTEM", "Remote completion confirmed. Releasing all hardware policies...")
                Handler(Looper.getMainLooper()).post {
                    _root_ide_package_.com.emi.customer.helper.MdmPolicyManager(applicationContext).removeDevicePoliciesAndUninstall {
                        _root_ide_package_.com.emi.customer.controller.ShopOwnerController().clearLocalStates()
                        Log.d("FCM_TEARDOWN", "Volatile runtime states cleared successfully on remote completion.")
                    }
                }
                return
            }

            // ── 🛡️ THREADED EXECUTION PIPELINE ──────────────────
            Thread {
                try {
                    Log.d("FCM_SYSTEM", "Executing MDM hardware profile for command: $processedCommand")

                    // Step 1: Fire the localized MDM configuration changes instantly
                    when (processedCommand) {
                        "device_lock" -> mdmCommandExecutor.executeDeviceLock(isActivated, extractedUnlockKey)
                        "camera_lock" -> mdmCommandExecutor.executeCameraLock(isActivated)
                        "call_lock" -> mdmCommandExecutor.executeCallLock(isActivated)
                        "settings_lock" -> mdmCommandExecutor.executeSettingsLock(isActivated)
                        "sim_lock" -> mdmCommandExecutor.executeSimLock(isActivated)
                        // Added social and entertainment app locking maps
                        "whatsapp_lock" -> mdmCommandExecutor.executeWhatsAppLock(isActivated)
                        "imo_lock" -> mdmCommandExecutor.executeImoLock(isActivated)
                        "messenger_lock" -> mdmCommandExecutor.executeMessengerLock(isActivated)
                        "facebook_lock" -> mdmCommandExecutor.executeFacebookLock(isActivated)
                        "youtube_lock" -> mdmCommandExecutor.executeYouTubeLock(isActivated)
                        else -> Log.w("FCM_SYSTEM", "Unmapped command string signature skipped: '$processedCommand'")
                    }

                    // Step 2: Notify the server that the operation was received so history clears "pending"
                    if (commandId.isNotEmpty()) {
                        updateCommandExecutionStatusOnServer(commandId)
                    }

                    // Step 3: Map notification body design
                    val formattedNotificationBody = when (processedCommand) {
                        "device_lock" -> if (isActivated) "🔒 Action Required: Device Lock has been activated." else "🔓 Device Lock has been lifted."
                        "camera_lock" -> if (isActivated) "📷 Privacy Alert: Device Camera hardware has been disabled." else "📷 Device Camera hardware is now active."
                        "call_lock" -> if (isActivated) "📞 Communication Restricted: Outgoing calling blocked." else "📞 Voice calling features restored."
                        "settings_lock" -> if (isActivated) "🛡️ Settings has been restricted." else "🛡️ Settings restriction released."
                        "sim_lock" -> if (isActivated) "📍 Device sim service has been locked." else "Sim unblock"
                        // Added social and entertainment notification translations
                        "whatsapp_lock" -> if (isActivated) "💬 WhatsApp app access has been locked." else "💬 WhatsApp app access has been restored."
                        "imo_lock" -> if (isActivated) "📱 IMO app access has been locked." else "📱 IMO app access has been restored."
                        "messenger_lock" -> if (isActivated) "✉️ Messenger app access has been locked." else "✉️ Messenger app access has been restored."
                        "youtube_lock" -> if (isActivated) "📺 YouTube app access has been locked." else "📺 YouTube app access has been restored."
                        else -> "$incomingBody (Command: $command)"
                    }

                    showNotification(incomingTitle, formattedNotificationBody)

                } catch (e: Exception) {
                    Log.e("FCM_SYSTEM", "Crash in threaded pipeline layout handling: ${e.message}")
                }
            }.start()
        }
    }

    /**
     * Hits your verification/update endpoint to clear the "pending" flag in Django history log.
     */
    private fun updateCommandExecutionStatusOnServer(commandId: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(ApiEndpoints.COMMAND_EXECUTION_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true

            val jsonPayload = "{\"command_id\": \"$commandId\"}"
            val outputBytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)

            connection.outputStream.use { os ->
                os.write(outputBytes, 0, outputBytes.size)
                os.flush()
            }

            val responseCode = connection.responseCode
            Log.d("FCM_AUDIT", "Server History Update Ping Response Code: $responseCode")
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            Log.e("FCM_AUDIT", "Failed syncing executed status back to DB layer: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "admin_alerts_v3"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Admin Policy Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays incoming policy and merchant pairing alerts."
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, _root_ide_package_.com.emi.customer.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "🔄 A fresh FCM registration token was generated by Google.")
        val currentImei = ImeiHardwareResolver.getUniversalDeviceImei(applicationContext)
        if (currentImei.isNotEmpty() && !currentImei.contains("UNAVAILABLE")) {
            sendTokenToBackend(currentImei, token)
        }
    }

    companion object {
        fun sendTokenToBackend(imei: String, token: String, activityContext: com.emi.customer.MainActivity? = null) {
            Thread {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(ApiEndpoints.UPDATE_FCM_URL)
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.doOutput = true

                    val jsonPayload = JSONObject().apply {
                        put("imei", imei)
                        put("fcm_token", token)
                    }

                    OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                        writer.write(jsonPayload.toString())
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.d("FCM_SYNC", "🛡️ Django successfully re-mapped token.")
                        activityContext?.runOnUiThread {
                            Toast.makeText(activityContext, "✅ FCM টোকেন সফলভাবে ব্যাকএন্ডে সংরক্ষিত হয়েছে!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.w("FCM_SYNC", "Server rejected FCM sync. Status: $responseCode")
                        activityContext?.runOnUiThread {
                            Toast.makeText(activityContext, "❌ টোকেন সেভ হয়নি! সার্ভার কোড: $responseCode", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FCM_SYNC", "Volatile connection drop syncing token: ${e.message}")
                    activityContext?.runOnUiThread {
                        Toast.makeText(activityContext, "❌ নেটওয়ার্ক ত্রুটি! টোকেন আপডেট ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }
    }
}
package com.emi.devicemanagercustomer.services
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.emi.devicemanagercustomer.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Lazy instantiation of our core business logic layer
    private val mdmCommandExecutor by lazy { MdmCommandExecutor(applicationContext) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM_SYSTEM", "Service Woke Up. Data received: ${remoteMessage.data}")

        if (remoteMessage.data.isNotEmpty()) {
            val command = remoteMessage.data["command"]
            val statusStr = remoteMessage.data["status"] ?: "false"
            val commandId = remoteMessage.data["command_id"] ?: ""
            val isActivated = statusStr.equals("true", ignoreCase = true)

            val incomingTitle = remoteMessage.data["title"] ?: "System Policy Update"
            val incomingBody =
                remoteMessage.data["body"] ?: "Remote action rule execution trigger applied."

            // ── 🔒 STRICT VERIFY-FIRST ORDER OF EXECUTION ──────────────────
            // Network calls cannot be run on the main thread, so we manage the sequence inside a Worker Thread
            Thread {
                var canExecuteAction = false

                if (commandId.isNotEmpty()) {
                    // Step 1: Hit the API first and wait for response status
                    canExecuteAction = verifyExecutionWithServer(commandId)
                } else {
                    Log.e(
                        "FCM_SYSTEM",
                        "Aborting command routing. Reason: Missing command_id tracking property."
                    )
                }

                // Step 2: ONLY execute MDM configurations if the database successfully marked it true
                if (canExecuteAction) {
                    Log.d(
                        "FCM_SYSTEM",
                        "API confirmation successful. Executing MDM hardware switches."
                    )

                    // Unified lock execution path
                    when (command) {
                        "device_lock" -> mdmCommandExecutor.executeDeviceLock(isActivated)
                        "camera_lock" -> mdmCommandExecutor.executeCameraLock(isActivated)
                        "call_lock" -> mdmCommandExecutor.executeCallLock(isActivated)
                        "settings_lock" -> mdmCommandExecutor.executeSettingsLock(isActivated)
                        "sim_lock" -> mdmCommandExecutor.executeSimLock(isActivated)
                        else -> Log.w(
                            "FCM_SYSTEM",
                            "Unmapped command string signature skipped: $command"
                        )
                    }

                    // Dynamic Notification Layout mapping
                    val formattedNotificationBody = when (command) {
                        "device_lock" -> if (isActivated) "🔒 Action Required: Device Lock has been activated." else "🔓 Device Lock has been lifted."
                        "camera_lock" -> if (isActivated) "📷 Privacy Alert: Device Camera hardware has been disabled." else "📷 Device Camera hardware is now active."
                        "call_lock" -> if (isActivated) "📞 Communication Restricted: Outgoing calling blocked." else "📞 Voice calling features restored."
                        "settings_lock" -> if (isActivated) "🛡️ Settings has been restricted." else "🛡️ Settings restriction released."
                        "sim_lock" -> if (isActivated) "📍 Device sim service has been locked." else "Sim unblock"
                        else -> "$incomingBody (Command: ${command ?: "Unknown"})"
                    }

                    showNotification(incomingTitle, formattedNotificationBody)
                } else {
                    Log.e(
                        "FCM_SYSTEM",
                        "MDM execution dropped. Server failed to acknowledge tracking signature."
                    )
                }
            }.start()
        }
    }

    /**
     * Hits the Django validation route synchronously.
     * Returns true ONLY if Django successfully saves and yields an HTTP 200 OK code back.
     */
    private fun verifyExecutionWithServer(commandId: String): Boolean {
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
            Log.d("FCM_AUDIT", "Server Verification Ping Response Code: $responseCode")

            // Return true only on a perfect HTTP 200 setup confirmation response
            responseCode == HttpURLConnection.HTTP_OK

        } catch (e: Exception) {
            Log.e(
                "FCM_AUDIT",
                "Network handshake connection drop during database verification: ${e.message}",
                e
            )
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "admin_alerts_v3"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
            // ক্লাসের ভেতর থেকেও এখন কম্প্যানিয়ন অবজেক্টের মেthডটি কল হবে
            sendTokenToBackend(currentImei, token)
        }
    }

    // 🚨 এই ব্লকটি নিশ্চিত করুন (এটির কারণেই MainActivity থেকে সরাসরি অ্যাক্সেস পাওয়া যাবে)
    companion object {
        // ৩টি প্যারামিটার করে দেওয়া হয়েছে (শেষেরটি context: MainActivity? = null)
        fun sendTokenToBackend(imei: String, token: String, activityContext: MainActivity? = null) {
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

                    OutputStreamWriter(
                        connection.outputStream,
                        StandardCharsets.UTF_8
                    ).use { writer ->
                        writer.write(jsonPayload.toString())
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.d("FCM_SYNC", "🛡️ Django successfully re-mapped token.")

                        // ✅ মেইন থ্রেডে পুশ করে সফলতার বাংলা টোস্ট মেসেজ
                        activityContext?.runOnUiThread {
                            Toast.makeText(
                                activityContext,
                                "✅ FCM টোকেন সফলভাবে ব্যাকএন্ডে সংরক্ষিত হয়েছে!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Log.w("FCM_SYNC", "Server rejected FCM sync. Status: $responseCode")

                        // ❌ মেইন থ্রেডে পুশ করে ব্যর্থতার বাংলা টোস্ট মেসেজ
                        activityContext?.runOnUiThread {
                            Toast.makeText(
                                activityContext,
                                "❌ টোকেন সেভ হয়নি! সার্ভার কোড: $responseCode",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FCM_SYNC", "Volatile connection drop syncing token: ${e.message}")

                    // ❌ নেটওয়ার্ক এররের জন্য টোস্ট মেসেজ
                    activityContext?.runOnUiThread {
                        Toast.makeText(
                            activityContext,
                            "❌ নেটওয়ার্ক ত্রুটি! টোকেন আপডেট ব্যর্থ হয়েছে।",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }
    }
}
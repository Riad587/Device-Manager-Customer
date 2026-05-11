package com.emi.devicemanagercustomer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.emi.devicemanagercustomer.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Log to verify background wakeup
        Log.d("FCM_SYSTEM", "Service Woke Up. Data: ${remoteMessage.data}")

        // 1. Handle Remote Commands (Always check Data map)
        val command = remoteMessage.data["command"]
        if (command != null && command != "none") {
            handleRemoteCommand(command, remoteMessage.data)
        }

        // 2. Show Manual Notification
        // Since we send 'data-only' messages for background reliability,
        // we must pull the title/body from the data map.
        val title = remoteMessage.data["title"]
        val body = remoteMessage.data["body"]

        if (!title.isNullOrEmpty()) {
            showNotification(title, body)
        }
    }

    private fun handleRemoteCommand(command: String, data: Map<String, String>) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val secretToken = "11112222333344445555666677778888".toByteArray(Charsets.UTF_8)

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.e("FCM_SYSTEM", "ERROR: App is not Device Owner!")
            return
        }

        try {
            when (command) {
                "lock" -> dpm.lockNow()
                "passChange" -> {
                    val newPin = data["newPin"] ?: "123456"
                    dpm.setResetPasswordToken(adminComponent, secretToken)
                    dpm.resetPasswordWithToken(adminComponent, newPin, secretToken, 0)
                    dpm.lockNow()
                }
                "camera" -> {
                    val disable = data["status"] == "disable"
                    dpm.setCameraDisabled(adminComponent, disable)
                }
                "wipe" -> dpm.wipeData(0)
                "reset" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dpm.reboot(adminComponent)
                }
            }

            // Toast for visual confirmation if screen is on
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Command: $command executed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("FCM_SYSTEM", "Execution failed: ${e.message}")
        }
    }

    private fun showNotification(title: String?, body: String?) {
        val channelId = "admin_alerts_v3"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Admin Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
package com.emi.devicemanagercustomer.services

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CommandWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dpm = applicationContext.getSystemService(DevicePolicyManager::class.java)!!
        val adminComponent = ComponentName(applicationContext, MyDeviceAdminReceiver::class.java)
        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)

        return try {
            val commands = RetrofitClient.instance.getPendingCommands(deviceId)

            commands.forEach { cmd ->
                executeCommand(cmd.action, dpm, adminComponent)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun executeCommand(action: String, dpm: DevicePolicyManager, admin: ComponentName) {
        when (action) {
            "lock" -> dpm.lockNow()
            "wipe" -> dpm.wipeData(0)
            "camera_off" -> dpm.setCameraDisabled(admin, true)
            "camera_on" -> dpm.setCameraDisabled(admin, false)
        }
    }
}
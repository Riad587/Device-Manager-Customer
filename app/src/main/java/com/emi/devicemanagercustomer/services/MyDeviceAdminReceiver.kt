package com.emi.devicemanagercustomer.services

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.admin.DeviceAdminReceiver
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent) // Fixed: Both parameters passed

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
        val secretToken = "11112222333344445555666677778888".toByteArray(Charsets.UTF_8)

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.setResetPasswordToken(adminComponent, secretToken)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Admin Disabled", Toast.LENGTH_SHORT).show()
    }
}
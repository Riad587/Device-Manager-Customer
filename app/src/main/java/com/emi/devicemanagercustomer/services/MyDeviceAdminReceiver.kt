package com.emi.devicemanagercustomer.services

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("MDM_ADMIN", "🛡️ Device Admin status activated successfully via Phase 1 deployment.")

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // This whitelists your app package so it can pin itself to the screen
                // when Phase 2 (The Shop Owner connection API) succeeds.
                dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                Log.d("MDM_ADMIN", "🔒 Package successfully whitelisted for Lock Task Mode.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val secretToken = "11112222333344445555666677778888".toByteArray(Charsets.UTF_8)
                    dpm.setResetPasswordToken(adminComponent, secretToken)
                }
            }
        } catch (e: Exception) {
            Log.e("MDM_ADMIN", "Error setting up baseline administrative profiles: ${e.message}")
        }
    }
}
package com.emi.customer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class MyDeviceAdminReceiver : android.app.admin.DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MDM_ADMIN"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "User attempted to disable Device Admin.")
        return "This application manages your device."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled.")
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        // Delegate all intent handling up to the Android Enterprise framework
        super.onReceive(context, intent)
    }

    // Triggers perfectly after the handshake activities complete initialization parameters
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d(TAG, "Profile provisioning completed.")
        handleProvisioningComplete(context, intent)
    }

    private fun handleProvisioningComplete(context: Context, originalIntent: Intent) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        try {
            val isOwner = dpm.isDeviceOwnerApp(context.packageName)
            Log.d(TAG, "Package = ${context.packageName}")
            Log.d(TAG, "Is Device Owner = $isOwner")

            if (isOwner) {
                // Configure LockTask rules for full Kiosk capability
                dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                Log.d(TAG, "LockTask packages configured.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val token = "11112222333344445555666677778888".toByteArray(Charsets.UTF_8)
                    val success = dpm.setResetPasswordToken(adminComponent, token)
                    Log.d(TAG, "ResetPasswordToken Result = $success")
                }
            } else {
                Log.e(TAG, "App is NOT Device Owner.")
            }

            // Launch your core landing interface
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                action = "android.app.action.PROFILE_PROVISIONING_COMPLETE"
                val bundle = originalIntent.extras
                if (bundle != null) {
                    putExtras(bundle)
                }
            }
            context.startActivity(launchIntent)
            Log.d(TAG, "MainActivity launched successfully.")

        } catch (e: Exception) {
            Log.e(TAG, "Provisioning Exception occurred during registration", e)
        }
    }
}
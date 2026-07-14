package com.emi.customer.services

import android.content.Context
import android.content.Intent
import android.util.Log


class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle standard boot, quickboot, or encrypted pre-auth boot states cleanly
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // 🔥 CRITICAL: Direct memory wrapper to access storage before device pattern is typed
            val protectedContext = context.createDeviceProtectedStorageContext()
            val prefs = protectedContext.getSharedPreferences("mdm_policy", Context.MODE_PRIVATE)
            val isLocked = prefs.getBoolean("is_locked", false)

            if (isLocked) {
                Log.d("MDM_BOOT", "🔒 Device encrypted state bypassed. Launching lock overlay.")
                val lockIntent = Intent(context, _root_ide_package_.com.emi.customer.ui.LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                context.startActivity(lockIntent)
            }
        }
    }
}
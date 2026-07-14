package com.emi.customer.helper

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

class MdmPolicyManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, _root_ide_package_.com.emi.customer.MyDeviceAdminReceiver::class.java)
    private val packageName = context.packageName

    fun applyDevicePolicies() {
        try {
            if (!dpm.isDeviceOwnerApp(packageName)) {
                Log.w("MDM_POLICY", "Warning: App is not recognized as the active Device Owner.")
                return
            }

            // ── 🔒 CORE HARDWARE BASELINE RESTRICTIONS (ALL VERSIONS) ──
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
           // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // ⚡ NEW CODE ADDED: Permanently locks down ADB access and Developer Options menus device-wide
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            dpm.setUninstallBlocked(adminComponent, packageName, true)

            // ── 🔒 VERSION-SPECIFIC ENTERPRISE UPGRADES (ANDROID 9 TO 16+) ──
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    applyAndroid11PlusPolicies()
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    applyAndroid10Policies()
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> {
                    applyAndroid9Policies()
                }
            }

            bypassBatteryOptimizations()
            Log.d("MDM_POLICY", "All corporate enterprise restrictions locked down successfully for API ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "Apply Failure: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyAndroid11PlusPolicies() {
        try {
            // Cloud-level cryptographic hardware vault lock (Survives cold flashes)
            val frpPolicy = FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(listOf("113303555253426700673"))
                .setFactoryResetProtectionEnabled(true)
                .build()
            dpm.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)

            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)

            // ⚡ NEW CODE ADDED: Prevents cellular configuration tinkering or manual network selection
           // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

            // ⚡ NEW CODE ADDED: Blocks physical OEM bootloader unlocking switches on Android 8.0+ / Oreo+ systems
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                try {
                    dpm.addUserRestriction(adminComponent, "no_oem_unlock")
                } catch (e: Exception) {
                    Log.w("MDM_VERSION", "OEM layers rejected explicit bootloader programmatic toggle.")
                }
            }

            // ⚡ NEW CODE ADDED: Sever active physical USB type-C data signalling lines (Android 12 / S+)
            // Leaves charging completely operational, but drops PC link connection to 0% transparency
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (dpm.canUsbDataSignalingBeDisabled()) {
                        dpm.setUsbDataSignalingEnabled(false)
                        Log.d("MDM_VERSION", "Physical Type-C Data Controller hardware isolated.")
                    }
                } catch (usbEx: Exception) {
                    Log.e("MDM_POLICY", "USB Hard-Lock Engine Execution Exception: ${usbEx.message}")
                }
            }

            Log.d("MDM_VERSION", "Android 11 to 16+ Advanced Security Blockades Engaged.")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "FRP/VPN/USB Policy Injection Failed: ${e.message}")
        }
    }

    private fun applyAndroid10Policies() {
        try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)

            // ⚡ NEW CODE ADDED: Prevents cellular carrier configuration alteration loops
           // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

            // ⚡ NEW CODE ADDED: Blocks structural bootloader unlock menu switch targets
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { dpm.addUserRestriction(adminComponent, "no_oem_unlock") } catch (e: Exception) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
            }
            Log.d("MDM_VERSION", "Android 10 Core Restrictions Applied.")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "Android 10 injection error: ${e.message}")
        }
    }

    private fun applyAndroid9Policies() {
        try {
            // ⚡ NEW CODE ADDED: Disallow network configuration manipulation paths on legacy Android 9 models
           // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

            // ⚡ NEW CODE ADDED: Disallow OEM bootloader modification triggers inside Developer Options
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { dpm.addUserRestriction(adminComponent, "no_oem_unlock") } catch (e: Exception) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
            }
            Log.d("MDM_VERSION", "Android 9 Custom Target Enforced.")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "Android 9 injection error: ${e.message}")
        }
    }

    private fun bypassBatteryOptimizations() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "Battery Whitelisting Failed: ${e.message}")
        }
    }

    /**
     * শুধুমাত্র আমরা যে পলিসিগুলো লক করেছিলাম, ক্র্যাশ এড়াতে ঠিক সেগুলোই ক্লিনলি রিমুভ করা।
     */
    fun removeDevicePoliciesAndUninstall(onDismantleComplete: () -> Unit) {
        try {
            if (dpm.isDeviceOwnerApp(packageName)) {
                // ⚡ NEW CODE CLEANUP REGISTERED: Clear base components cleanly in array queue
                val activeRestrictions = mutableListOf(
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_APPS_CONTROL,
                    UserManager.DISALLOW_DEBUGGING_FEATURES,      // ⚡ Clear ADB Lock
                    UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS  // ⚡ Clear Network Lock
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try { dpm.clearUserRestriction(adminComponent, "no_oem_unlock") } catch (e: Exception) {}
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_VPN)
                    try { dpm.setFactoryResetProtectionPolicy(adminComponent, null) } catch (e: Exception) {}
                }

                // ⚡ NEW CODE CLEANUP REGISTERED: Restores USB port wiring signaling functionality back to 100% normal
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { dpm.setUsbDataSignalingEnabled(true) } catch (e: Exception) {}
                }

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_VPN)
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_DATE_TIME)
                }

                activeRestrictions.forEach { restriction ->
                    try { dpm.clearUserRestriction(adminComponent, restriction) } catch (e: Exception) {}
                }

                // আনইনস্টল ব্লক রিলিজ করা
                dpm.setUninstallBlocked(adminComponent, packageName, false)

                // লোকাল অবজেক্ট এবং ভিউ স্টেটস ক্লিয়ারেন্স
                onDismantleComplete()

                // ডিভাইস এডমিন ও অনারশিপ ড্রপ করা
                dpm.clearDeviceOwnerApp(packageName)
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.removeActiveAdmin(adminComponent)
                }
                Log.d("MDM_TEARDOWN", "Device Owner and restrictions permanently released.")
            }
        } catch (e: Exception) {
            Log.e("MDM_TEARDOWN", "Teardown Error: ${e.message}")
        }

        // নেভীভ আনইনস্টল ডায়ালগ ফায়ার করা
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(uninstallIntent)
        } catch (e: Exception) {
            Log.e("MDM_TEARDOWN", "Uninstall Intent Failed: ${e.message}")
        }
    }
}
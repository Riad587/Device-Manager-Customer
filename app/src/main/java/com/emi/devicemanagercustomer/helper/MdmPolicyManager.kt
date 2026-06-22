package com.emi.devicemanagercustomer.helper

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
import com.emi.devicemanagercustomer.services.MyDeviceAdminReceiver

class MdmPolicyManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
    private val packageName = context.packageName

    /**
     * অ্যান্ড্রয়েড ৯ থেকে সর্বশেষ সংস্করণ এবং সব চাইনিজ ওএসের জন্য
     * বুলেটপ্রুফ পলিসি এনফোর্সমেন্ট পাইপলাইন (জিমেইল অ্যাকাউন্ট ফ্রিডম এনাবল্ড)।
     */
    fun applyDevicePolicies() {
        try {
            if (!dpm.isDeviceOwnerApp(packageName)) {
                Log.w("MDM_POLICY", "Warning: App is not recognized as the active Device Owner.")
                return
            }

            // ── ১. গ্লোবাল রুলস (সব অ্যান্ড্রয়েড ভার্সনের জন্য বাধ্যতামূলক এবং নিরাপদ) ──
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

            // চাইনিজ ওএসের ইউজাররা যেন সেটিংসে গিয়ে "Force Stop" বা "Clear Data" করতে না পারে
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // অ্যাপ আনইনস্টল চিরতরে ব্লক করা
            dpm.setUninstallBlocked(adminComponent, packageName, true)

            // ── ২. ওএস ভার্সন নির্দিষ্ট স্পেশাল সিকিউরিটি লেয়ার (কন্ডিশনাল এক্সিকিউশন) ──
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // 🛡️ Android 11 to Android 16+
                    applyAndroid11PlusPolicies()
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    // 🛡️ Android 10 Specific
                    applyAndroid10Policies()
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> {
                    // 🛡️ Android 9 Specific
                    applyAndroid9Policies()
                }
            }

            // ── ৩. ব্যাটারি অপ্টিমাইজেশন ও ব্যাকগ্রাউন্ড কিলিং প্রটেকশন ──
            bypassBatteryOptimizations()

            Log.d("MDM_POLICY", "All corporate enterprise restrictions locked down successfully for API ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "Apply Failure: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyAndroid11PlusPolicies() {
        try {
            // হার্ডওয়্যার ব্যাকড ফ্যাক্টরি রিসেট প্রটেকশন (FRP) এনাবল করা
            val frpPolicy = FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(listOf("113303555253426700673"))
                .setFactoryResetProtectionEnabled(true)
                .build()
            dpm.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)

            // আধুনিক ওএসে ভিপিএন বাইপাস রোধ করা
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
            Log.d("MDM_VERSION", "Android 11+ Advanced Infrastructure Applied.")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "FRP/VPN Policy Injection Failed: ${e.message}")
        }
    }

    private fun applyAndroid10Policies() {
        try {
            // ভিপিএন ও কাস্টম সেল মেসেজিং অ্যালার্ট লক করা
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)

            // টাইম চেঞ্জ বা জ্যামিং অ্যাটাক বন্ধ করা
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
            // টাইম চেঞ্জ অ্যাটাক প্রতিরোধ করা (অ্যান্ড্রয়েড ৯ ফ্রন্টলাইন ডিফেন্স)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
            }
            // অ্যাকাউন্ট সংক্রান্ত সমস্ত লক বর্জন করা হয়েছে যাতে কাস্টমার নিজস্ব জিমেইল ফ্রিলি ব্যবহার করতে পারে
            Log.d("MDM_VERSION", "Android 9 Custom Target Enforced (Accounts Allowed).")
        } catch (e: Exception) {
            Log.e("MDM_POLICY", "Android 9 injection error: ${e.message}")
        }
    }

    /**
     * ওএস জেনো ব্যাকগ্রাউন্ড থেকে অ্যাপ কিল করতে না পারে, তার ব্যবস্থা।
     * ডিভাইস ওনার অ্যাপ সরাসরি ইউজার প্রম্পট ছাড়াই নিজেকে হোয়াইটলিস্ট করতে পারে।
     */
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

                // গ্লোবাল একটিভ রেস্ট্রিকশন লিস্ট
                val activeRestrictions = mutableListOf(
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                    UserManager.DISALLOW_SAFE_BOOT,
                    UserManager.DISALLOW_APPS_CONTROL
                )

                // ওএস ভার্সন চেক করে কন্ডিশনাল ক্লিয়ারিং (যাতে চাইনিজ ফোনে SecurityException না আসে)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_VPN)
                    try { dpm.setFactoryResetProtectionPolicy(adminComponent, null) } catch (e: Exception) {}
                }

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_VPN)
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activeRestrictions.add(UserManager.DISALLOW_CONFIG_DATE_TIME)
                }

                // ক্লিনআপ লুপ এক্সিকিউশন
                activeRestrictions.forEach { restriction ->
                    try { dpm.clearUserRestriction(adminComponent, restriction) } catch (e: Exception) {}
                }

                dpm.setUninstallBlocked(adminComponent, packageName, false)

                onDismantleComplete()

                // ডিভাইস ওনারশিপ পার্মানেন্ট রিলিজ
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(packageName)
                dpm.removeActiveAdmin(adminComponent)
            }
        } catch (e: Exception) {
            Log.e("MDM_TEARDOWN", "Teardown Error: ${e.message}")
        }

        // নেটিভ আনইনস্টল উইন্ডো ফায়ার করা
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
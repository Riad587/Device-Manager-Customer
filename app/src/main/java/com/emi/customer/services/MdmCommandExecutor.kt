package com.emi.customer.services

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log

class MdmCommandExecutor(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, _root_ide_package_.com.emi.customer.MyDeviceAdminReceiver::class.java)

    companion object {
        // 🛠️ BACKWARD COMPATIBILITY FIX: Defines the Android 15 key literal manually
        // to bypass compiler errors when building with compileSdk 34 or lower.
        private const val DISALLOW_SIM_GLOBALLY_COMPAT = "no_sim_globally"
    }

    private fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    /**
     * ক্যামেরা লক: ওয়ানপ্লাস (OxygenOS) এবং কালারওএস স্পেশাল হার্ডওয়্যার কার্নেল কিল-সুইচ
     */
    fun executeCameraLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            // ওয়ানপ্লাসের ব্যাকগ্রাউন্ড থ্রেড ব্লকিং বাইপাস করার জন্য মেইন থ্রেডে ট্রান্সফার করা হলো
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                try {
                    // লেয়ার ১: অফিশিয়াল হার্ডওয়্যার লেভেল ডিজেবল
                    dpm.setCameraDisabled(adminComponent, isActivated)

                    // লেয়ার ২: গ্লোবাল সিস্টেম রেস্ট্রিকশন স্ট্রিং
                    if (isActivated) {
                        dpm.addUserRestriction(adminComponent, "no_camera")
                    } else {
                        dpm.clearUserRestriction(adminComponent, "no_camera")
                    }

                    // ── লেয়ার ৩: ওয়ানপ্লাসের জন্য ক্যামেরা অ্যাপস সম্পূর্ণ 'সাসপেন্ড' ও ফ্রিজ করা ──
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val cameraPackages = arrayOf(
                            "com.oneplus.camera",           // OnePlus Default Camera
                            "com.oplus.camera",             // New OnePlus/Oppo Merged Camera
                            "com.android.camera",           // Generic Fallback
                            "com.android.camera2",
                            "com.google.android.GoogleCamera"
                        )

                        if (isActivated) {
                            // এটি ওয়ানপ্লাস ওএসের হোমস্ক্রিন থেকে ক্যামেরার আইকন লুকিয়ে বা অকেজো করে দেবে
                            dpm.setPackagesSuspended(adminComponent, cameraPackages, true)

                            // ইনস্ট্যান্ট রানিং ক্যামেরা প্রসেস কিল করা
                            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            for (pkg in cameraPackages) {
                                activityManager.killBackgroundProcesses(pkg)
                            }
                        } else {
                            // আনলক করার সময় আবার সচল করা
                            dpm.setPackagesSuspended(adminComponent, cameraPackages, false)
                        }
                    }

                    Log.d("MDM_EXECUTOR", "🔥 OnePlus Nord Camera Lock Applied: $isActivated")
                } catch (ex: Exception) {
                    Log.e("MDM_EXECUTOR", "Main thread camera operation failed: ${ex.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MDM_EXECUTOR", "Camera pipeline crash: ${e.message}")
        }
    }

    /**
     * ডিভাইস লক (Kiosk Mode): চাইনিজ ওএম-এর সমস্ত বাইপাস ট্রিক বন্ধ করার জন্য লেয়ার্ড মেথড Crowded.
     */
    fun executeDeviceLock(isActivated: Boolean, dynamicUnlockKey: String? = null) {
        if (!isDeviceOwner()) return
        try {
            val protectedContext = context.createDeviceProtectedStorageContext()
            val prefs = protectedContext.getSharedPreferences("mdm_policy", Context.MODE_PRIVATE)

            val editor = prefs.edit().putBoolean("is_locked", isActivated)
            if (isActivated && !dynamicUnlockKey.isNullOrBlank()) {
                editor.putString("dynamic_unlock_key", dynamicUnlockKey.trim())
                Log.d("MDM_EXECUTOR", "Stored dynamic unlock key locally: $dynamicUnlockKey")
            }
            editor.apply()

            if (isActivated) {
                Log.d("MDM_EXECUTOR", "🔒 Initializing Iron-Clad Lock down...")

                try {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dpm.addUserRestriction(adminComponent, "no_oem_unlock")
                    }
                } catch (resEx: Exception) {
                    Log.w("MDM_EXECUTOR", "Non-fatal restriction error: ${resEx.message}")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                }

                try { dpm.setKeyguardDisabled(adminComponent, true) } catch (e: Exception) { }

                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    try {
                        val lockIntent = Intent(context, _root_ide_package_.com.emi.customer.ui.LockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        context.startActivity(lockIntent)
                    } catch (launchEx: Exception) {
                        Log.e("MDM_EXECUTOR", "Failed launching LockActivity: ${launchEx.message}")
                    }
                }

            } else {
                Log.d("MDM_EXECUTOR", "🔓 Releasing Kiosk lock...")

                try {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dpm.clearUserRestriction(adminComponent, "no_oem_unlock")
                    }
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                } catch (e: Exception) { }

                try { dpm.setKeyguardDisabled(adminComponent, false) } catch (e: Exception) { }
                try { dpm.setStatusBarDisabled(adminComponent, false) } catch (e: Exception) { }

                val closeIntent = Intent("com.emi.devicemanagercustomer.CLOSE_LOCK_SCREEN").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(closeIntent)
            }
        } catch (e: Exception) {
            Log.e("MDM_EXECUTOR", "Critical failure in lock down pipeline: ${e.message}")
        }
    }

    fun executeCallLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            if (isActivated) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
            }
            Log.d("MDM_EXECUTOR", "Call Restriction successfully pushed: $isActivated")
        } catch (e: Exception) {
            Log.e("MDM_EXECUTOR", "Call restriction modification failed: ${e.message}")
        }
    }

    /**
     * ৪. সেটিংস লক (জিমেইল অ্যাকাউন্ট সম্পূর্ণ ফ্রি রেখে অ্যাপ কন্ট্রোল লক)
     * Xiaomi, Oppo, Vivo-তে DISALLOW_CONFIG_SETTINGS অনেক সময় কাজ করে না।
     */
    fun executeSettingsLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            if (isActivated) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
                }
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
                }
            }
            Log.d("MDM_EXECUTOR", "Universal Settings Protection deployed: $isActivated")
        } catch (e: Exception) {
            Log.e("MDM_EXECUTOR", "Settings block configuration failed: ${e.message}")
        }
    }

    /**
     * ৫. সিম এবং মোবাইল网络 লক (ডুয়াল-লেয়ার চাইনিজ ওএম ফলব্যাক)
     */
    fun executeSimLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            if (isActivated) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

                // 🛠️ FIX: Uses the companion string tracking literal instead of standard target resolution
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        dpm.addUserRestriction(adminComponent, DISALLOW_SIM_GLOBALLY_COMPAT)
                    } catch (simEx: Exception) {
                        Log.w("MDM_SIM_COMPAT", "DISALLOW_SIM_GLOBALLY bypassed by OEM layer: ${simEx.message}")
                    }
                }
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

                // 🛠️ FIX: Clears using the companion tracking literal securely
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        dpm.clearUserRestriction(adminComponent, DISALLOW_SIM_GLOBALLY_COMPAT)
                    } catch (simEx: Exception) {
                        Log.e("MDM_SIM_COMPAT", "Failed releasing SIM restriction")
                    }
                }
            }
            Log.d("MDM_EXECUTOR", "SIM Hardware & Carrier Lock State: $isActivated")
        } catch (e: Exception) {
            Log.e("MDM_EXECUTOR", "SIM block pipeline failed: ${e.message}")
        }
    }

    private fun toggleAppSuspension(packages: Array<String>, shouldSuspend: Boolean, logTag: String) {
        if (!isDeviceOwner()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                try {
                    dpm.setPackagesSuspended(adminComponent, packages, shouldSuspend)

                    if (shouldSuspend) {
                        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        for (pkg in packages) {
                            activityManager.killBackgroundProcesses(pkg)
                        }
                    }
                    Log.d("MDM_EXECUTOR", "🚀 $logTag target state changed to suspended=$shouldSuspend")
                } catch (e: Exception) {
                    Log.e("MDM_EXECUTOR", "Failed targeting package suspension for $logTag: ${e.message}")
                }
            }
        } else {
            Log.w("MDM_EXECUTOR", "OS Version below Nougat. App suspension skipped for $logTag")
        }
    }

    /**
     * হোয়াটসঅ্যাপ লক (WhatsApp Business সহ সম্পূর্ণ ফ্রিজ)
     */
    fun executeWhatsAppLock(isActivated: Boolean) {
        val whatsappPackages = arrayOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
        toggleAppSuspension(whatsappPackages, isActivated, "WhatsApp Lock")
    }

    /**
     * ইমো লক (IMO, IMO HD এবং Lite সংস্করণ সহ)
     */
    fun executeImoLock(isActivated: Boolean) {
        val imoPackages = arrayOf(
            "com.imo.android.imoim",
            "com.imo.android.imoimhd",
            "com.imo.android.imolite"
        )
        toggleAppSuspension(imoPackages, isActivated, "IMO Lock")
    }

    /**
     * মেসেঞ্জার লক (Facebook Messenger এবং Messenger Lite)
     */
    fun executeMessengerLock(isActivated: Boolean) {
        val messengerPackages = arrayOf(
            "com.facebook.orca",
            "com.facebook.mlite"
        )
        toggleAppSuspension(messengerPackages, isActivated, "Messenger Lock")
    }

    fun executeFacebookLock(isActivated: Boolean) {
        val facebookPackages = arrayOf(
            "com.facebook.katana",
            "com.facebook.lite"
        )
        toggleAppSuspension(facebookPackages, isActivated, "Facebook Lock")
    }

    /**
     * ইউটিউব লক (YouTube Core, Vanced, ReVanced এবং Kids সংস্করণ সহ)
     */
    fun executeYouTubeLock(isActivated: Boolean) {
        val youtubePackages = arrayOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "app.revanced.android.youtube"
        )
        toggleAppSuspension(youtubePackages, isActivated, "YouTube Lock")
    }
}
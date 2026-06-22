package com.emi.devicemanagercustomer.services

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.emi.devicemanagercustomer.ui.LockActivity

class MdmCommandExecutor(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

    private fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    /**
     * ক্যামেরা লক: ওয়ানপ্লাস (OxygenOS) এবং কালারওএস স্পেশাল হার্ডওয়্যার কার্নেল কিল-সুইচ
     */
    fun executeCameraLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            // ওয়ানপ্লাসের ব্যাকগ্রাউন্ড থ্রেড ব্লকিং বাইপাস করার জন্য মেইন থ্রেডে ট্রান্সফার করা হলো
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                try {
                    // লেয়ার ১: অফিশিয়াল হার্ডওয়্যার লেভেল ডিজেবল
                    dpm.setCameraDisabled(adminComponent, isActivated)

                    // লেয়ার ২: গ্লোবাল সিস্টেম রেস্ট্রিকশন স্ট্রিং
                    if (isActivated) {
                        dpm.addUserRestriction(adminComponent, "no_camera")
                    } else {
                        dpm.clearUserRestriction(adminComponent, "no_camera")
                    }

                    // ── লেয়ার ৩: ওয়ানপ্লাসের জন্য ক্যামেরা অ্যাপস সম্পূর্ণ 'সাসপেন্ড' ও ফ্রিজ করা ──
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val cameraPackages = arrayOf(
                            "com.oneplus.camera",           // OnePlus Default Camera
                            "com.oplus.camera",             // New OnePlus/Oppo Merged Camera
                            "com.android.camera",           // Generic Fallback
                            "com.android.camera2",
                            "com.google.android.GoogleCamera"
                        )

                        if (isActivated) {
                            // এটি ওয়ানপ্লাস ওএসের হোমস্ক্রিন থেকে ক্যামেরার আইকন লুকিয়ে বা অকেজো করে দেবে
                            dpm.setPackagesSuspended(adminComponent, cameraPackages, true)

                            // ইনস্ট্যান্ট রানিং ক্যামেরা প্রসেস কিল করা
                            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            for (pkg in cameraPackages) {
                                activityManager.killBackgroundProcesses(pkg)
                            }
                        } else {
                            // আনলক করার সময় আবার সচল করা
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
     * ডিভাইস লক (Kiosk Mode): চাইনিজ ওএম-এর সমস্ত বাইপাস ট্রিক বন্ধ করার জন্য লেয়ার্ড মেথড।
     */
    fun executeDeviceLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            // লোকাল প্রিসিস্টেন্ট স্টেট সেভ করা (রিবুট প্রটেকশনের জন্য)
            val prefs = context.getSharedPreferences("mdm_policy", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_locked", isActivated).apply()

            if (isActivated) {
                Log.d("MDM_EXECUTOR", "🔒 Initializing Iron-Clad Lock down...")

                // লেয়ার ১: লক টাস্ক প্যাকেজ সেট করা (Android M+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                }

                // লেয়ার ২: গ্লোবাল লেভেলে কীগার্ড এবং স্ট্যাটাস বার সম্পূর্ণ শাটডাউন
                try { dpm.setKeyguardDisabled(adminComponent, true) } catch (e: Exception) { }
                try { dpm.setStatusBarDisabled(adminComponent, true) } catch (e: Exception) { }

                // লেয়ার ৩: ইনটেন্ট ফ্ল্যাগ অপ্টিমাইজেশন (যাতে ব্যাক বোতাম বা রিসেন্ট অ্যাপস কাজ না করে)
                val lockIntent = Intent(context, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                context.startActivity(lockIntent)

            } else {
                Log.d("MDM_EXECUTOR", "🔓 Releasing Kiosk lock...")
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
     * Xiaomi, Oppo, Vivo-তে DISALLOW_CONFIG_SETTINGS অনেক সময় কাজ করে না।
     * তাই ইউনিভার্সাল ট্রিক হলো DISALLOW_APPS_CONTROL ব্যবহার করা, যাতে কাস্টমার সেটিংস থেকে
     * অ্যাপ আনইনস্টল বা ডেটা ক্লিয়ার করতে না পারে, কিন্তু ফ্রিলি জিমেইল ব্যবহার করতে পারে।
     */
    fun executeSettingsLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            if (isActivated) {
                // কাস্টমার যেন কোনো অ্যাপ্লিকেশন মডিফাই, ফোর্স স্টপ বা আনইনস্টল করতে না পারে
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
     * ৫. সিম এবং মোবাইল নেটওয়ার্ক লক (ডুয়াল-লেয়ার চাইনিজ ওএম ফলব্যাক)
     * অ্যান্ড্রয়েড ৯ ও ১০ এ গ্লোবাল সিম লক নেই, তাই সেখানে নেটওয়ার্ক কনফিগারেশন লক করা হয়।
     * অ্যান্ড্রয়েড ১১ থেকে ১৬+ এ হার্ডওয়্যার লেভেলে সিম কার্ড পুরোপুরি ডিজেবল করা হয়।
     */
    fun executeSimLock(isActivated: Boolean) {
        if (!isDeviceOwner()) return
        try {
            if (isActivated) {
                // লেয়ার ১: মোবাইল নেটওয়ার্ক এপিএন (APN) বা সেলুলার সেটিংস পরিবর্তন ব্লক (Android 9+)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

                // লেয়ার ২: গ্লোবাল হার্ডওয়্যার সিম স্লট ডিজেবল করা (Android 11 থেকে 16+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SIM_GLOBALLY)
                    } catch (simEx: Exception) {
                        Log.w("MDM_SIM_COMPAT", "DISALLOW_SIM_GLOBALLY bypassed by OEM layer: ${simEx.message}")
                    }
                }
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SIM_GLOBALLY)
                    } catch (simEx: Exception) { Log.e("MDM_SIM_COMPAT", "Failed releasing SIM restriction") }
                }
            }
            Log.d("MDM_EXECUTOR", "SIM Hardware & Carrier Lock State: $isActivated")
        } catch (e: Exception) {
            Log.e("MDM_EXECUTOR", "SIM block pipeline failed: ${e.message}")
        }
    }

    // ── 🛠️ ব্র্যান্ড ও কাস্টম রম কমপ্যাটিবিলিটির জন্য সেফটি ইউটিলিটি ফাংশনসমূহ ──

    private fun safelyToggleKeyguard(disable: Boolean) {
        try {
            dpm.setKeyguardDisabled(adminComponent, disable)
        } catch (e: Exception) {
            Log.w("MDM_OEM_COMPAT", "Keyguard bypass rejected by OEM Skin: ${e.message}")
        }
    }

    private fun safelyToggleStatusBar(disable: Boolean) {
        try {
            dpm.setStatusBarDisabled(adminComponent, disable)
        } catch (e: Exception) {
            Log.w("MDM_OEM_COMPAT", "Status bar restriction rejected by OEM Skin: ${e.message}")
        }
    }
}
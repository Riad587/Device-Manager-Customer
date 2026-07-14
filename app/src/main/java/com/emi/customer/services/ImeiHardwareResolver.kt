package com.emi.customer.services

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log

object ImeiHardwareResolver {

    /**
     * অ্যান্ড্রয়েড ৯ থেকে শুরু করে লেটেস্ট অ্যান্ড্রয়েড ১৬+ এবং সব চাইনিজ ফোনের জন্য
     * লিন্ট-সেফ এবং ক্র্যাশ-ফ্রি ইউনিভার্সাল হার্ডওয়্যার আইডেন্টিফায়ার এক্সট্র্যাক্টর।
     * যদি কোনো ভ্যালিড হার্ডওয়্যার টোকেন না পাওয়া যায়, তবে এটি সরাসরি খালি স্ট্রিং ("") রিটার্ন করবে।
     */
    @SuppressLint("HardwareIds", "MissingPermission", "NewApi")
    fun getUniversalDeviceImei(context: Context): String {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val packageName = context.packageName

        // ── লেয়ার ১: অ্যান্ড্রয়েড ১২ থেকে ১৬+ (গুগল এন্টারপ্রাইজ এনভায়রনমেন্ট) ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dpm.isDeviceOwnerApp(packageName)) {
            try {
                val enrollmentId = dpm.getEnrollmentSpecificId()
                if (!enrollmentId.isNullOrBlank() && !isInvalidImei(enrollmentId)) {
                    return enrollmentId.trim()
                }
            } catch (e: Exception) {
                Log.w("IMEI_RESOLVER", "Enrollment ID lookup bypassed: ${e.message}")
            }
        }

        val telMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return ""

        // ── লেয়ার ২: অ্যান্ড্রয়েড ৯ এবং ১০ (স্ট্যান্ডার্ড আইএমইআই স্লট ১) ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val imeiSlot0 = telMgr.getImei(0)
                if (!isInvalidImei(imeiSlot0)) return imeiSlot0!!.trim()
            } catch (e: Exception) {
                Log.w("IMEI_RESOLVER", "Slot 0 extraction bypassed: ${e.message}")
            }

            // ── লেয়ার ৩: ডুয়াল সিমের চাইনিজ ফোন ফলব্যাক (স্লট ২ চেকিং) ──
            try {
                val imeiSlot1 = telMgr.getImei(1)
                if (!isInvalidImei(imeiSlot1)) return imeiSlot1!!.trim()
            } catch (e: Exception) {
                Log.w("IMEI_RESOLVER", "Slot 1 extraction bypassed: ${e.message}")
            }
        }

        // ── লেয়ার ৪: ওল্ড জেনেরিক ওএস এবং কাস্টম রম মেথড ফলব্যাক ──
        try {
            @Suppress("DEPRECATION")
            val deviceId = telMgr.deviceId
            if (!isInvalidImei(deviceId)) return deviceId!!.trim()
        } catch (e: Exception) {
            Log.w("IMEI_RESOLVER", "Legacy Device ID extraction bypassed: ${e.message}")
        }

        // ── লেয়ার ৫: আল্টিমেট ক্রাশ প্রটেকশন ফলব্যাক ──
        // মেমোরি এবং ডাটাবেজ ডার্টি এন্ট্রি প্রটেক্ট করার জন্য সরাসরি খালি স্ট্রিং রিটার্ন করা হচ্ছে
        return ""
    }

    private fun isInvalidImei(imei: String?): Boolean {
        if (imei.isNullOrBlank()) return true
        val normalized = imei.trim().lowercase()
        return normalized == "unknown" ||
                normalized == "null" ||
                normalized.contains("0000000000") ||
                normalized == "build.unknown" ||
                normalized.contains("hardware")
    }
}
package com.emi.devicemanagercustomer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.work.*
import com.emi.devicemanagercustomer.controller.ShopOwnerController
import com.emi.devicemanagercustomer.helper.MdmPolicyManager
import com.emi.devicemanagercustomer.services.ImeiHardwareResolver
import com.emi.devicemanagercustomer.services.SyncCommandsWorker
import com.emi.devicemanagercustomer.services.MyFirebaseMessagingService // ইম্পোর্ট নিশ্চিত করুন
import com.emi.devicemanagercustomer.ui.AppNavigationRouting
import com.emi.devicemanagercustomer.ui.ConnectionErrorScreen
import com.emi.devicemanagercustomer.ui.IncompatibleHardwareScreen
import com.emi.devicemanagercustomer.ui.SplashScreenOverlay
import com.emi.devicemanagercustomer.ui.SystemIssueScreen
import com.emi.devicemanagercustomer.ui.theme.DeviceManagerCustomerTheme
import com.google.firebase.messaging.FirebaseMessaging
import java.time.Duration

class MainActivity : ComponentActivity() {

    private val shopOwnerController by lazy { ShopOwnerController() }
    private val mdmPolicyManager by lazy { MdmPolicyManager(this) }

    // ── 🧠 OPTIMIZATION: SharedPreferences এর বদলে মেমোরিতে টোকেন রাখার রানটাইম ভেরিয়েবল ──
    private var runtimeFcmToken: String = ""

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Log.d("FCM_SYSTEM", "Notification permissions approved.")
        else Log.w("FCM_SYSTEM", "Push permissions denied.")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── 1. HARD HARDWARE GATE CHECK ──
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setContent { DeviceManagerCustomerTheme { IncompatibleHardwareScreen() } }
            return
        }

        // ── 2. INITIAL SERVICE STREAMS ──
        handleNotificationPermission()
        fetchAndStoreFcmTokenInMemory() // রানটাইম মেমোরিতে টোকেন লোড করা হচ্ছে

        setContent {
            DeviceManagerCustomerTheme {
                var isInitialBootSyncing by remember { mutableStateOf(true) }
                var isCurrentlyConnected by remember { mutableStateOf(false) }
                val targetDeviceImei = remember { ImeiHardwareResolver.getUniversalDeviceImei(this@MainActivity) }
                var triggerRetryCounter by remember { mutableStateOf(0) }

                // ── 3. HANDSHAKE RUNTIME PIPELINE ──
                LaunchedEffect(targetDeviceImei, triggerRetryCounter) {
                    isInitialBootSyncing = true
                    try {
                        // ব্যাকএন্ড থেকে চেক করা ডিভাইসটি অলরেডি রেজিস্টার্ড কিনা
                        shopOwnerController.fetchDeviceOwnerInformation(targetDeviceImei, this@MainActivity)
                        isCurrentlyConnected = shopOwnerController.isConnected

                        // ডিভাইস পেয়ার্ড থাকলে টোকেন জোরপূর্বক ব্যাকএন্ডে সাবমিট করা হবে
                        if (isCurrentlyConnected) {
                            Log.d("FCM_AUTO_SYNC", "Device is already registered. Enforcing live token synchronization...")
                            scheduleFallbackSyncRoutine()
                            if (runtimeFcmToken.isNotEmpty()) {
                                // মেমোরিতে টোকেন অলরেডি থাকলে সরাসরি পুশ এবং কন্টেক্সট পাসিং (টোস্টের জন্য)
                                triggerTokenSyncWithToast(targetDeviceImei, runtimeFcmToken)
                            } else {
                                // কোনো কারণে মেমোরি খালি থাকলে ফায়ারবেস থেকে লাইভ টোকেন তুলে সাথে সাথে পুশ
                                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                    if (task.isSuccessful && task.result != null) {
                                        runtimeFcmToken = task.result
                                        triggerTokenSyncWithToast(targetDeviceImei, runtimeFcmToken)
                                    } else {
                                        Log.e("FCM_AUTO_SYNC", "Failed to fetch token during enforcement.")
                                        Toast.makeText(this@MainActivity, "❌ ফায়ারবেস টোকেন জেনারেট করতে ব্যর্থ!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MDM_BOOT_EXCEPTION", "Handshake processing failed: ${e.message}")
                    } finally {
                        isInitialBootSyncing = false
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFFF7F9FC))
                    ) {
                        when {
                            isInitialBootSyncing -> {
                                SplashScreenOverlay()
                            }
                            shopOwnerController.uiInternetError -> {
                                ConnectionErrorScreen(onRetry = { triggerRetryCounter++ })
                            }
                            shopOwnerController.uiServerError -> {
                                SystemIssueScreen(
                                    errorDetails = shopOwnerController.uiServerErrorDetails,
                                    onRetry = { triggerRetryCounter++ },
                                    onAdminBypass = {
                                        showMasterKeyDialog("removeFromServer") {
                                            shopOwnerController.clearLocalStates()
                                            cancelFallbackSyncRoutine()
                                            isCurrentlyConnected = false
                                        }
                                    }
                                )
                            }
                            else -> {
                                AppNavigationRouting(
                                    isCurrentlyConnected = isCurrentlyConnected,
                                    shopOwnerController = shopOwnerController,
                                    onEnrollmentAgreed = {
                                        val nativeImei = ImeiHardwareResolver.getUniversalDeviceImei(this@MainActivity)

                                        // ── 🧠 OPTIMIZATION: SharedPreferences এর বদলে সরাসরি মেমোরি ভেরিয়েবল পাস ──
                                        shopOwnerController.processEnrollmentPipeline(
                                            imei = nativeImei,
                                            deviceName = Build.MANUFACTURER,
                                            deviceModel = Build.MODEL,
                                            serialId = fetchHardwareSerial(),
                                            fcmToken = runtimeFcmToken, // পাসিং মেমোরি অবজেক্ট
                                            onSuccess = {
                                                mdmPolicyManager.applyDevicePolicies()
                                                scheduleFallbackSyncRoutine()
                                                isCurrentlyConnected = true
                                                Toast.makeText(this@MainActivity, "🛡️ Enrollment Activated!", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    onTroubleshootClick = {
                                        val runtimeKeyword = if (isCurrentlyConnected) "uninstall" else "remove"
                                        showMasterKeyDialog(runtimeKeyword) {
                                            shopOwnerController.clearLocalStates()
                                            cancelFallbackSyncRoutine()
                                            isCurrentlyConnected = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── DATA AND INFRASTRUCTURE MANAGEMENT METHODS ──

    /**
     * UI Thread ও সেফটি মেইনটেইন করে টোকেন ব্যাকগ্রাউন্ডে পাঠানো এবং রেসপন্স অনুযায়ী স্ক্রিনে টোস্ট দেখানো
     */
    private fun triggerTokenSyncWithToast(imei: String, token: String) {
        // টোস্ট দেখানোর সুবিধার্থে আমরা সরাসরি MyFirebaseMessagingService.sendTokenToBackend কল করার সময়
        // UI হ্যান্ডলিং মেথডকে রান করাই অথবা সাইলেন্টলি ট্র্যাক করি।
        // সার্ভিস ফাইলে টোস্ট দেখাতে চাইলে মেইন থ্রেডে পুশ করতে হবে।
        Toast.makeText(this, "🔄 সার্ভারের সাথে FCM টোকেন সিঙ্ক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        MyFirebaseMessagingService.sendTokenToBackend(imei, token, activityContext = this)
    }

    private fun scheduleFallbackSyncRoutine() {
        val executionConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PeriodicWorkRequestBuilder<SyncCommandsWorker>(Duration.ofMinutes(15))
                .setConstraints(executionConstraints)
                .build()
        } else {
            @Suppress("DEPRECATION")
            PeriodicWorkRequestBuilder<SyncCommandsWorker>(30, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(executionConstraints)
                .build()
        }

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "MDM_COMMANDS_FALLBACK_SYNC", ExistingPeriodicWorkPolicy.KEEP, periodicSyncRequest
        )
    }

    private fun cancelFallbackSyncRoutine() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork("MDM_COMMANDS_FALLBACK_SYNC")
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun fetchHardwareSerial(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial() else @Suppress("DEPRECATION") Build.SERIAL
    } catch (e: Exception) { null }

    private fun showMasterKeyDialog(expectedKeyword: String, onDismantleComplete: () -> Unit) {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("System Bypass Validation")
            .setMessage("Provide management security keyword input '$expectedKeyword':")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (input.text.toString().trim() == expectedKeyword) {
                    mdmPolicyManager.removeDevicePoliciesAndUninstall(onDismantleComplete)
                    finishAndRemoveTask()
                } else {
                    Toast.makeText(this, "Access Denied: Invalid Key", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun handleNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun fetchAndStoreFcmTokenInMemory() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                runtimeFcmToken = task.result
                Log.d("FCM_SYSTEM", "Token cached into volatile runtime successfully.")
            } else {
                Log.e("FCM_SYSTEM", "Token cache failed: ${task.exception?.message}")
            }
        }
    }
}
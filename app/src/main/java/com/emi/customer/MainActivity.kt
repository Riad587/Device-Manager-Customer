package com.emi.customer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.*
import com.emi.customer.ui.AppNavigationRouting
import com.emi.customer.ui.ConnectionErrorScreen
import com.emi.customer.ui.IncompatibleHardwareScreen
import com.emi.customer.ui.SplashScreenOverlay
import com.google.firebase.messaging.FirebaseMessaging
import java.time.Duration
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val shopOwnerController by lazy { _root_ide_package_.com.emi.customer.controller.ShopOwnerController() }
    private val mdmPolicyManager by lazy {
        _root_ide_package_.com.emi.customer.helper.MdmPolicyManager(this)
    }

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

        val isProvisioningIntent = intent?.action == "android.app.action.PROFILE_PROVISIONING_COMPLETE"

        // 🛡️ Pre-emptive Auto-Grant: Programmatically grant READ_PHONE_STATE to yourself
        // before running any conditional evaluations or state lookups.
        if (!isProvisioningIntent && isAppDeviceOwner()) {
            autoGrantDeviceOwnerPermissions()
        }

        // 🎯 CRITICAL IMEI EXTRACTION RESOLUTION:
        // Safely pull the hardware string early to ensure compliance logic functions properly.
        val targetDeviceImei = if (!isProvisioningIntent) {
            val rawImei = _root_ide_package_.com.emi.customer.services.ImeiHardwareResolver
                .getUniversalDeviceImei(this).orEmpty().trim()

            if (rawImei.isEmpty() ||
                rawImei.contains("HARDWARE", ignoreCase = true) ||
                rawImei.contains("unknown", ignoreCase = true)
            ) "" else rawImei
        } else {
            ""
        }

        // 🚫 COMBINED HARDWARE INCOMPATIBILITY FILTER:
        // If the platform rules fail, if it isn't Device Owner, OR if the IMEI is empty on normal launch,
        // intercept execution immediately and force render the Incompatible Hardware screen to allow clean uninstallation.
        if (!isProvisioningIntent && (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isAppDeviceOwner() || targetDeviceImei.isEmpty())) {
            Log.e("MDM_GUARD", "Hardware validation crash avoided. Target IMEI is missing or platform rules failed. Redirecting to clean teardown window.")
            setContent {
                _root_ide_package_.com.emi.customer.ui.theme.DeviceManagerCustomerTheme {
                    IncompatibleHardwareScreen(
                        onUninstallClick = {
                            mdmPolicyManager.removeDevicePoliciesAndUninstall {
                                shopOwnerController.clearLocalStates()
                                cancelFallbackSyncRoutine()
                            }
                            finishAndRemoveTask()
                        }
                    )
                }
            }
            return
        }

        // Only request platform secondary hooks if the app is booting normally
        if (!isProvisioningIntent) {
            handleNotificationPermission()
            fetchAndStoreFcmTokenInMemory()
        }

        setContent {
            _root_ide_package_.com.emi.customer.ui.theme.DeviceManagerCustomerTheme {
                var isInitialBootSyncing by remember { mutableStateOf(true) }
                var isCurrentlyConnected by remember { mutableStateOf(false) }
                var triggerRetryCounter by remember { mutableStateOf(0) }

                LaunchedEffect(triggerRetryCounter) {
                    isInitialBootSyncing = true
                    try {
                        shopOwnerController.fetchDeviceOwnerInformation(
                            targetDeviceImei,
                            this@MainActivity
                        )
                        isCurrentlyConnected = shopOwnerController.isConnected

                        if (isCurrentlyConnected) {
                            Log.d("FCM_AUTO_SYNC", "Device is registered. Synchronizing infrastructure tokens...")
                            scheduleFallbackSyncRoutine()
                            if (runtimeFcmToken.isNotEmpty()) {
                                triggerTokenSyncWithToast(targetDeviceImei, runtimeFcmToken)
                            } else {
                                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                    if (task.isSuccessful && task.result != null) {
                                        runtimeFcmToken = task.result
                                        triggerTokenSyncWithToast(targetDeviceImei, runtimeFcmToken)
                                    } else {
                                        Log.e("FCM_AUTO_SYNC", "Failed to fetch token during enforcement.")
                                        Toast.makeText(
                                            this@MainActivity,
                                            "❌ Failed to generate Firebase Token!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MDM_BOOT_EXCEPTION", "Handshake processing failed: ${e.message}")
                        isCurrentlyConnected = shopOwnerController.isConnected
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
                                _root_ide_package_.com.emi.customer.ui.SystemIssueScreen(
                                    errorDetails = shopOwnerController.uiServerErrorDetails,
                                    targetDeviceImei = targetDeviceImei,
                                    shopOwnerController = shopOwnerController,
                                    onRetry = { triggerRetryCounter++ },
                                    onAdminBypassAuthorized = {
                                        mdmPolicyManager.removeDevicePoliciesAndUninstall {
                                            shopOwnerController.clearLocalStates()
                                            cancelFallbackSyncRoutine()
                                            isCurrentlyConnected = false
                                        }
                                        finishAndRemoveTask()
                                    }
                                )
                            }

                            else -> {
                                AppNavigationRouting(
                                    isCurrentlyConnected = isCurrentlyConnected,
                                    shopOwnerController = shopOwnerController,
                                    targetDeviceImei = targetDeviceImei,
                                    onEnrollmentAgreed = {
                                        shopOwnerController.processEnrollmentPipeline(
                                            imei = targetDeviceImei,
                                            deviceName = Build.MANUFACTURER,
                                            deviceModel = Build.MODEL,
                                            serialId = fetchHardwareSerial(),
                                            fcmToken = runtimeFcmToken,
                                            onSuccess = {
                                                mdmPolicyManager.applyDevicePolicies()
                                                scheduleFallbackSyncRoutine()
                                                isCurrentlyConnected = true
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "🛡️ Enrollment Activated!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                    },
                                    onUnpairedTroubleshootClick = {
                                        showUnpairedTroubleshootDialog {
                                            shopOwnerController.clearLocalStates()
                                            cancelFallbackSyncRoutine()
                                            isCurrentlyConnected = false
                                            finishAndRemoveTask()
                                        }
                                    },
                                    onPairedUninstallAuthorized = {
                                        mdmPolicyManager.removeDevicePoliciesAndUninstall {
                                            shopOwnerController.clearLocalStates()
                                            cancelFallbackSyncRoutine()
                                            isCurrentlyConnected = false
                                        }
                                        finishAndRemoveTask()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isAppDeviceOwner(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isDeviceOwnerApp(packageName) == true
    }

    /**
     * Programmatically auto-grants required runtime phone state permissions
     * using the app's established Device Owner administrative privileges.
     */
    private fun autoGrantDeviceOwnerPermissions() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

            if (dpm != null && dpm.isDeviceOwnerApp(packageName)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setPermissionGrantState(
                        adminComponent,
                        packageName,
                        Manifest.permission.READ_PHONE_STATE,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    Log.i("MDM_PERMISSIONS", "Successfully auto-granted READ_PHONE_STATE permission.")
                }
            }
        } catch (e: Exception) {
            Log.e("MDM_PERMISSIONS", "Failed to auto-grant Device Owner permissions: ${e.message}")
        }
    }

    private fun triggerTokenSyncWithToast(imei: String, token: String) {
        Toast.makeText(this, "🔄 Syncing FCM Token with infrastructure...", Toast.LENGTH_SHORT).show()
        _root_ide_package_.com.emi.customer.services.MyFirebaseMessagingService.Companion.sendTokenToBackend(imei, token, activityContext = this)
    }

    private fun scheduleFallbackSyncRoutine() {
        val executionConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PeriodicWorkRequestBuilder<com.emi.customer.services.SyncCommandsWorker>(Duration.ofMinutes(15))
                .setConstraints(executionConstraints)
                .build()
        } else {
            @Suppress("DEPRECATION")
            PeriodicWorkRequestBuilder<com.emi.customer.services.SyncCommandsWorker>(30, TimeUnit.MINUTES)
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

    private fun showUnpairedTroubleshootDialog(onDismantleComplete: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Are you want to remove this device? Uninstall")
            .setPositiveButton("Yes") { _, _ ->
                mdmPolicyManager.removeDevicePoliciesAndUninstall(onDismantleComplete)
            }
            .setNegativeButton("No", null)
            .show()
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
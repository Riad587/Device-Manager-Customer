package com.emi.devicemanagercustomer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.work.*
import com.emi.devicemanagercustomer.jacompose.*
import com.emi.devicemanagercustomer.services.CommandWorker
import com.emi.devicemanagercustomer.services.RetrofitClient
import com.emi.devicemanagercustomer.ui.theme.DeviceManagerCustomerTheme
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Notification Permission Request (Android 13+)
        val requestPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("FCM_TEST", "Notification permission granted")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setupCommandWorker()
        fetchAndStoreFcmToken()

        setContent {
            DeviceManagerCustomerTheme {
                val context = this
                val deviceId = remember {
                    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                }

                var screenState by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    val isConnectedLocally = context.getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)
                        .getBoolean("is_connected", false)

                    if (isConnectedLocally) {
                        screenState = 2
                    } else {
                        try {
                            val response = RetrofitClient.instance.checkDeviceStatus(deviceId)
                            if (response.success) {
                                context.getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("is_connected", true).apply()
                                screenState = 2
                            } else {
                                screenState = 1
                            }
                        } catch (e: Exception) {
                            screenState = 1
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screenState) {
                        0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        1 -> PairingScreen(
                            onPairingComplete = { screenState = 2 },
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> AdminDashboard(
                            context = context,
                            onLogout = { screenState = 1 },
                        )
                    }
                }
            }
        }
    }

    private fun setupCommandWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val commandRequest = PeriodicWorkRequestBuilder<CommandWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RemoteCommandWork",
            ExistingPeriodicWorkPolicy.KEEP,
            commandRequest
        )
    }

    private fun fetchAndStoreFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result

                // SAVE TO PREFS
                getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("fcm_token", token)
                    .apply()

                // PRINT TO LOGCAT (Filter by "FCM_TOKEN")
                Log.d("FCM_TOKEN", "----------------------------------------------")
                Log.d("FCM_TOKEN", "YOUR TOKEN: $token")
                Log.d("FCM_TOKEN", "----------------------------------------------")

                // OPTIONAL: Automatically copy it to clipboard for easy testing
                copyToClipboard(token)
            } else {
                Log.e("FCM_TOKEN", "Fetching FCM registration token failed", task.exception)
            }
        }
    }

    // Helper function to copy token to clipboard
    private fun copyToClipboard(token: String?) {
        if (token == null) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("FCM Token", token)
        clipboard.setPrimaryClip(clip)

        // Show a toast so you know it's copied
        Toast.makeText(this, "FCM Token copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
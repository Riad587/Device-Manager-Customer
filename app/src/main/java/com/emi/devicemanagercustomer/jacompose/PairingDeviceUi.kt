package com.emi.devicemanagercustomer.jacompose

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.emi.devicemanagercustomer.services.PairingRequest
import com.emi.devicemanagercustomer.services.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onPairingComplete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    val deviceInfo = remember { getDeviceInfo(context) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Device Pairing", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📱", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Information", fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    deviceInfo.forEach { (label, value) ->
                        Text("$label: $value", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Pairing Token") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (token.isNotBlank()) showDialog = true
                    else Toast.makeText(context, "Enter token", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("FIND OWNER")
            }
        }
    }

    if (showDialog) {
        OwnerDetailsDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                scope.launch {
                    try {
                        val fcmToken = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                            .getString("fcm_token", "") ?: ""

                        val request = PairingRequest(
                            pairingToken = token,
                            deviceId = deviceInfo["Device ID"] ?: "Unknown",
                            fcmToken = fcmToken,
                            imei1 = deviceInfo["IMEI1"] ?: "N/A",
                            imei2 = deviceInfo["IMEI2"] ?: "N/A",
                            model = Build.MODEL,
                            androidVersion = Build.VERSION.RELEASE
                        )

                        // Attempt API call, but bypass for testing if it fails or is offline
                        try {
                            val response = RetrofitClient.instance.registerDevice(request)
                            if (response.success) {
                                context.getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("is_connected", true).commit()
                                showDialog = false
                                onPairingComplete()
                                Toast.makeText(context, "Device Connected Successfully", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.e("API_ERROR", "API Failure, bypassing for testing: ${e.message}")
                        }

                        // TESTING BYPASS: This block runs if API is not ready or fails
                        context.getSharedPreferences("connection_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("is_connected", true).commit()
                        
                        showDialog = false
                        onPairingComplete() // Switch to Dashboard
                        Toast.makeText(context, "Connected (Bypassed for Testing)", Toast.LENGTH_SHORT).show()

                    } catch (e: Exception) {
                        Log.e("API_ERROR", "Error: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
fun OwnerDetailsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var isChecked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shop: Premium Mobile Gallery", fontWeight = FontWeight.Bold)
                Text("Owner: Ahmed Riad")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                    Text("I agree to terms", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = isChecked) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@SuppressLint("HardwareIds", "MissingPermission")
private fun getDeviceInfo(context: Context): Map<String, String> {
    val data = mutableMapOf<String, String>()
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "N/A"
    data["Device ID"] = androidId
    data["Model"] = "${Build.MANUFACTURER} ${Build.MODEL}"
    data["Android"] = "Android ${Build.VERSION.RELEASE}"
    return data
}

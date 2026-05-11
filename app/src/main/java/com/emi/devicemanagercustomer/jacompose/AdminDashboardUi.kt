package com.emi.devicemanagercustomer.jacompose

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emi.devicemanagercustomer.services.MyDeviceAdminReceiver

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(context: Context, onLogout: () -> Unit) {
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
    val scrollState = rememberScrollState()
    val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

    // UI States
    var cameraDisabled by remember { mutableStateOf(dpm.getCameraDisabled(adminComponent)) }
    var callsBlocked by remember { mutableStateOf(false) }
    var locationForced by remember { mutableStateOf(false) }
    var galleryLocked by remember { mutableStateOf(false) }

    // Secret Token restored as requested
    val secretToken = "11112222333344445555666677778888".toByteArray(Charsets.UTF_8)

    // FCM Token State
    val fcmToken = remember {
        context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .getString("fcm_token", "Token not found") ?: "Token not found"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EMI ENTERPRISE CONSOLE", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusBanner(isDeviceOwner)

            // --- SECTION 1: FINANCE LOCK ---
            EnterpriseSection(title = "Financial Enforcement", icon = Icons.Default.Lock) {
                var pinInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 6) pinInput = it },
                    label = { Text("Set Emergency PIN") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        try {
                            dpm.setResetPasswordToken(adminComponent, secretToken)
                            dpm.resetPasswordWithToken(adminComponent, pinInput, secretToken, 0)
                            dpm.lockNow()
                        } catch (e: Exception) { Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("FORCE LOCK DEVICE") }
            }

            // --- SECTION 2: STORAGE & APP CONTROL ---
            EnterpriseSection(title = "Device Restrictions", icon = Icons.Default.SettingsSystemDaydream) {
                ControlRow("Camera Hardware", cameraDisabled) {
                    cameraDisabled = !cameraDisabled
                    dpm.setCameraDisabled(adminComponent, cameraDisabled)
                }

                ControlRow("Enterprise Media Lock", galleryLocked) {
                    try {
                        galleryLocked = !galleryLocked
                        val pm = context.packageManager
                        val galleryPkgs = mutableSetOf(
                            "com.google.android.apps.photos",
                            "com.android.documentsui",
                            "com.google.android.documentsui",
                            "com.oplus.filemanager",
                            "com.coloros.filemanager"
                        )
                        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }, 0)
                            .forEach { galleryPkgs.add(it.activityInfo.packageName) }

                        val permissionsToToggle = mutableListOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToToggle.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                            permissionsToToggle.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                        }

                        val newState = if (galleryLocked) DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED else DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT

                        if (isDeviceOwner) {
                            galleryPkgs.remove(context.packageName)
                            galleryPkgs.forEach { pkg ->
                                permissionsToToggle.forEach { permission ->
                                    dpm.setPermissionGrantState(adminComponent, pkg, permission, newState)
                                }
                            }
                            Toast.makeText(context, if (galleryLocked) "Media Access Blocked" else "Media Access Restored", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }

            // --- SECTION 3: TRACKING & COMMS ---
            EnterpriseSection(title = "Tracking & Comms", icon = Icons.Default.LocationOn) {
                ControlRow("Force GPS Tracking", locationForced) {
                    locationForced = !locationForced
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) dpm.setLocationEnabled(adminComponent, locationForced)
                    if (locationForced) dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_LOCATION)
                    else dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_LOCATION)
                }
                ControlRow("Block Outgoing Calls", callsBlocked) {
                    callsBlocked = !callsBlocked
                    if (callsBlocked) dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
                    else dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
                }
            }

            // --- SECTION 5: FCM TOKEN ---
            EnterpriseSection(title = "FCM Diagnostics", icon = Icons.Default.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device FCM Token:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = fcmToken, fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(8.dp).fillMaxWidth())
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FCM Token", fcmToken))
                        Toast.makeText(context, "Token Copied!", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("COPY FCM TOKEN") }
                }
            }

            // --- SECTION 6: MASTER RELEASE ---
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                var releaseKey by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Master Release", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(value = releaseKey, onValueChange = { releaseKey = it }, placeholder = { Text("Enter 'remove'") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { if (releaseKey == "remove") releaseAndUninstall(context, dpm, adminComponent) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("PERMANENT WIPE & UNINSTALL") }
                }
            }
        }
    }
}

fun releaseAndUninstall(context: Context, dpm: DevicePolicyManager, adminComponent: ComponentName) {
    try {
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.clearDeviceOwnerApp(context.packageName)
        }
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Toast.makeText(context, "Device Owner Removed", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
}

@Composable
fun EnterpriseSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Divider(Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun ControlRow(label: String, isActive: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label); Switch(checked = isActive, onCheckedChange = { onToggle() })
    }
}

@Composable
fun StatusBanner(isDeviceOwner: Boolean) {
    val color = if (isDeviceOwner) Color(0xFF2E7D32) else Color(0xFFC62828)
    Box(modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(16.dp)) {
        Text(if (isDeviceOwner) "PROTECTION ACTIVE" else "UNSECURED", color = color, fontWeight = FontWeight.Bold)
    }
}

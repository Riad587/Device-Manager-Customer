package com.emi.devicemanagercustomer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emi.devicemanagercustomer.CameraScannerView
import com.emi.devicemanagercustomer.PairedDashboard
import com.emi.devicemanagercustomer.UnpairedStateDashboard
import com.emi.devicemanagercustomer.controller.ShopOwnerController

@Composable
fun SplashScreenOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFE65100))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Verifying Device Status...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IncompatibleHardwareScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F9FC)), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚫 Incompatible Hardware", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(14.dp))
                Text("This application is built for high-security MDM operations. Required: Android 9.0 (Pie) or higher.", fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun ConnectionErrorScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️ Connection Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                Spacer(modifier = Modifier.height(12.dp))
                Text("An active internet connection is required. Please verify data configuration rules.", fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.LightGray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)), onClick = onRetry) {
                    Text("Retry Connection", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SystemIssueScreen(errorDetails: String, onRetry: () -> Unit, onAdminBypass: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚨 System Issue Detected", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorDetails, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.Gray)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Any Trouble? Contact admin.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onAdminBypass() })
                Spacer(modifier = Modifier.height(16.dp))
                Button(colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), onClick = onRetry) {
                    Text("Retry Operation", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun BoxScope.AppNavigationRouting(
    isCurrentlyConnected: Boolean,
    shopOwnerController: ShopOwnerController,
    onEnrollmentAgreed: () -> Unit,
    onTroubleshootClick: () -> Unit
) {
    if (!isCurrentlyConnected) {
        if (shopOwnerController.showCameraScanner) {
            CameraScannerView(
                onQrScanned = { rawJson -> shopOwnerController.handleQrScanned(rawJson) },
                onClose = { shopOwnerController.showCameraScanner = false }
            )
        } else {
            UnpairedStateDashboard(onScanClick = { shopOwnerController.showCameraScanner = true })
        }
    } else {
        PairedDashboard(customerInfoMap = shopOwnerController.customerInfoMap)
    }

    if (shopOwnerController.isApiLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFE65100))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Contacting Device Infrastructure...", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    shopOwnerController.pendingPairingData?.let { data ->
        AlertDialog(
            onDismissRequest = {
                shopOwnerController.pendingPairingData = null
                shopOwnerController.merchantInfoMap = null
            },
            // ── 1. TITLE: Uses the Shop name fetched via API ──
            title = {
                val shopTitle = shopOwnerController.merchantInfoMap?.get("Shop Outlet")
                    ?: shopOwnerController.merchantInfoMap?.get("Merchant Name")
                    ?: "Confirm Connection"
                Text(text = shopTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                    // ── 2. RENDER VERIFIED SERVER MERCHANDISE INFORMATION ──
                    shopOwnerController.merchantInfoMap?.let { merchantProfile ->
                        Text(
                            text = "Verified Merchant Information:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        merchantProfile.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.Gray)
                                Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ── 3. RENDER SCANNED DATA STREAM ──
                    Text(
                        text = "Customer Registration Summary:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    data.forEach { (key, value) ->
                        // Hide raw ID since we successfully fetched its actual name
                        if (key.lowercase() != "shop_id") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(key.replace("_", " ").uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.DarkGray)
                                Text(value, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)), onClick = onEnrollmentAgreed) {
                    Text("Agree & Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    shopOwnerController.pendingPairingData = null
                    shopOwnerController.merchantInfoMap = null
                }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    Text(
        text = "Any Troubleshoot? Contact Admin",
        color = if (shopOwnerController.showCameraScanner) Color.Transparent else Color.LightGray,
        fontSize = 13.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).clickable { onTroubleshootClick() }
    )
}
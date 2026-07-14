package com.emi.customer.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun PairedDashboard(
    customerInfoMap: Map<String, String>,
    targetDeviceImei: String, // Pass IMEI straight down
    shopOwnerController: com.emi.customer.controller.ShopOwnerController, // Pass Controller down
    onUninstallAuthorized: () -> Unit // Triggers structural teardown logic in Activity
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── 🧠 IN-SCREEN COMPOSABLE STATE LOGIC ENGINE ──
    var isInputFormVisible by remember { mutableStateOf(false) }
    var bypassCodeInput by remember { mutableStateOf("") }
    var isApiLoading by remember { mutableStateOf(false) }
    var isCodeError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Device Enrollment Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE65100)
        )
        Text(
            text = "All verified provisioning parameters saved to this system:",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                customerInfoMap.forEach { (label, value) ->
                    if (label.lowercase() != "shop_id") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label.replace("_", " ").uppercase(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.DarkGray,
                                fontSize = 14.sp
                            )
                            Text(
                                text = value,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 14.sp
                            )
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── 🎯 UNINSTALL LOGIC SECTION INTERFACE ──
        if (!isInputFormVisible) {
            Text(
                text = "Device Enrolled Trouble? Uninstall Management",
                color = Color.Gray,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .clickable { isInputFormVisible = true }
            )
        }

        // ── 🛡️ LIVE DIRECT COMPOSE INPUT FLOW BLOCK ──
        AnimatedVisibility(visible = isInputFormVisible) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE9E7)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Management Uninstallation Verification",
                        color = Color(0xFFD84315),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bypassCodeInput,
                        onValueChange = {
                            bypassCodeInput = it
                            isCodeError = false
                        },
                        label = { Text("Enter Management Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        // Allows normal text input fields dynamically
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        isError = isCodeError,
                        enabled = !isApiLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD84315),
                            unfocusedBorderColor = Color.Gray,
                            errorBorderColor = Color.Red
                        )
                    )

                    if (isCodeError) {
                        Text(
                            text = "Invalid Code! Authentication Refused.",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { isInputFormVisible = false; bypassCodeInput = "" },
                            enabled = !isApiLoading
                        ) {
                            Text("Cancel", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                val codeToCheck = bypassCodeInput.trim()
                                if (codeToCheck.isEmpty()) return@Button

                                // 1. Immediate Local Validation Check Rule
                                if (codeToCheck == "@7wP!h8&M") {
                                    Toast.makeText(context, "Bypass Accepted Locally!", Toast.LENGTH_SHORT).show()
                                    onUninstallAuthorized()
                                    return@Button
                                }

                                // 2. Async Live Django API Pipeline Execution
                                isApiLoading = true
                                coroutineScope.launch {
                                    val targetKey = shopOwnerController.fetchEmergencyUnlockKey(targetDeviceImei)
                                    isApiLoading = false

                                    if (targetKey != null && codeToCheck == targetKey) {
                                        Toast.makeText(context, "Bypass Verified via Server!", Toast.LENGTH_SHORT).show()
                                        onUninstallAuthorized()
                                    } else {
                                        isCodeError = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315)),
                            enabled = !isApiLoading,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isApiLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Confirm Code", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
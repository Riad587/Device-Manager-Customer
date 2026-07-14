package com.emi.customer.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SystemIssueScreen(
    errorDetails: String,
    targetDeviceImei: String, // 🔥 Pass IMEI down to make the verification network call
    shopOwnerController: com.emi.customer.controller.ShopOwnerController, // 🔥 Pass Controller down
    onRetry: () -> Unit,
    onAdminBypassAuthorized: () -> Unit // 🔥 Renamed to match clean authorized execution
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val screenScrollState = rememberScrollState()

    // ── 🧠 IN-SCREEN COMPOSABLE STATE LOGIC ENGINE ──
    var isInputFormVisible by remember { mutableStateOf(false) }
    var bypassCodeInput by remember { mutableStateOf("") }
    var isApiLoading by remember { mutableStateOf(false) }
    var isCodeError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(screenScrollState)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚨 System Issue Detected",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .background(Color(0xFF2A1C1C), shape = RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF4C2626), shape = RoundedCornerShape(6.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (errorDetails.contains("<!DOCTYPE html>", ignoreCase = true) || errorDetails.contains("<html", ignoreCase = true)) {
                            "[HTML Payload Intercepted]\n" + errorDetails.take(1200) + "\n... [Truncated for safety]"
                        } else {
                            errorDetails
                        },
                        fontSize = 11.sp,
                        textAlign = TextAlign.Start,
                        color = Color(0xFFFF8A8A),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── 🎯 ACTION ENTRY LINK LAYER ──
                if (!isInputFormVisible) {
                    Text(
                        text = "Server Error? Clear Local Registration",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { isInputFormVisible = true }
                    )
                }

                // ── 🛡️ LIVE IN-SCREEN BYPASS CODE BLOCK ──
                AnimatedVisibility(visible = isInputFormVisible) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Bypass Authorization Validation",
                                color = Color(0xFFFF8A8A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = bypassCodeInput,
                                onValueChange = {
                                    bypassCodeInput = it
                                    isCodeError = false
                                },
                                label = { Text("Enter Validation Code", color = Color.Gray) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), // Alphanumeric input allowed
                                singleLine = true,
                                isError = isCodeError,
                                enabled = !isApiLoading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD32F2F),
                                    unfocusedBorderColor = Color.Gray,
                                    errorBorderColor = Color.Red,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            if (isCodeError) {
                                Text(
                                    text = "Access Denied: Invalid Management Key",
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
                                        if (codeToCheck == "G6#mXc_1") {
                                            Toast.makeText(context, "Bypass Accepted Locally!", Toast.LENGTH_SHORT).show()
                                            onAdminBypassAuthorized()
                                            return@Button
                                        }

                                        // 2. Async Live Django API Pipeline Execution
                                        isApiLoading = true
                                        coroutineScope.launch {
                                            val targetKey = shopOwnerController.fetchEmergencyUnlockKey(targetDeviceImei)
                                            isApiLoading = false

                                            if (targetKey != null && codeToCheck == targetKey) {
                                                Toast.makeText(context, "Bypass Verified via Server!", Toast.LENGTH_SHORT).show()
                                                onAdminBypassAuthorized()
                                            } else {
                                                isCodeError = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    enabled = !isApiLoading,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    if (isApiLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Confirm", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isApiLoading
                ) {
                    Text("Retry Operation", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
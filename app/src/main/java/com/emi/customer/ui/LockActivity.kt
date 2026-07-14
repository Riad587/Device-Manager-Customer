package com.emi.customer.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class LockActivity : ComponentActivity() {

    private val releaseReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                handleSystemUnlock()
            } catch (e: Exception) {
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            try {
                val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                km.requestDismissKeyguard(this, null)
            } catch (e: Exception) { }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val intentFilter = IntentFilter("com.emi.devicemanagercustomer.CLOSE_LOCK_SCREEN")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(releaseReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(releaseReceiver, intentFilter)
        }

        startLockTaskSafe()

        setContent {
            var bypassCode by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF8B0000), Color(0xFF3A0000))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ডিভাইসটি লক করা হয়েছে",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "অনুগ্রহ করে আপনার বকেয়া ইএমআই (EMI) কিস্তি পরিশোধ করুন। সম্পূর্ণ পরিশোধ করার পর আপনার হ্যান্ডসেটটি স্বয়ংক্রিয়ভাবে আনলক হয়ে যাবে।",
                        color = Color(0xFFE0E0E0),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "অনলাইন / অফলাইন বাইপাস ভেরিফিকেশন",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = bypassCode,
                                onValueChange = {
                                    bypassCode = it
                                    isError = false
                                },
                                label = { Text("বাইপাস কোড লিখুন", color = Color.LightGray) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                singleLine = true,
                                isError = isError,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedLabelColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (isError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ভুল কোড! অনুগ্রহ করে কাস্টমার সাপোর্টে যোগাযোগ করুন।",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    val enteredCode = bypassCode.trim()
                                    if (enteredCode.isEmpty()) return@Button

                                    // Fetch the dynamic custom token saved locally
                                    val protectedContext = createDeviceProtectedStorageContext()
                                    val savedPrefs = protectedContext.getSharedPreferences("mdm_policy",
                                        MODE_PRIVATE
                                    )
                                    val offlineStoredKey = savedPrefs.getString("dynamic_unlock_key", null)

                                    // 🎯 FIX: Verify input code against master or the dynamic key
                                    if (enteredCode == "@7wP!h8&M" || (!offlineStoredKey.isNullOrBlank() && enteredCode == offlineStoredKey)) {
                                        Toast.makeText(this@LockActivity, "সফলভাবে আনলক হয়েছে", Toast.LENGTH_SHORT).show()

                                        // 🎯 CRITICAL: Call executor directly to strip out OS-level hardware rules safely
                                        _root_ide_package_.com.emi.customer.services.MdmCommandExecutor(
                                            applicationContext
                                        ).executeDeviceLock(false)
                                    } else {
                                        isError = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(text = "কোড যাচাই করুন", color = Color(0xFF8B0000), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        startLockTaskSafe()
        try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
        } catch (e: Exception) { }
    }

    private fun startLockTaskSafe() {
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isLockTaskPermitted(packageName)) {
                startLockTask()
            }
        } catch (e: Exception) { }
    }

    private fun handleSystemUnlock() {
        try {
            val protectedContext = createDeviceProtectedStorageContext()
            protectedContext.getSharedPreferences("mdm_policy", MODE_PRIVATE).edit().putBoolean("is_locked", false).apply()
            stopLockTask()
        } catch (e: Exception) { }
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_HOME) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        try {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isLockTaskPermitted(packageName)) {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            }
        } catch (e: Exception) { }
    }

    @SuppressLint("MissingPermission")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            try {
                @Suppress("DEPRECATION")
                val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeDialogs)
            } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(releaseReceiver)
        } catch (e: Exception) { }
    }
}
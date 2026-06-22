package com.emi.devicemanagercustomer.ui

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
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

    private val releaseReceiver = object : BroadcastReceiver() {
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

        // 🧠 ওএম প্রটেকশন ফ্ল্যাগ: নোটিফিকেশন বার, স্ট্যাটাস বার লক এবং স্ক্রিন অন রাখা
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // অ্যান্ড্রয়েড ওএস-এর লেআউট ওভারল্যাপ সিকিউরিটি এনফোর্সমেন্ট
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val intentFilter = IntentFilter("com.emi.devicemanagercustomer.CLOSE_LOCK_SCREEN")

        // Android 13 (Tiramisu) বা তার উপরের সংস্করণের জন্য ব্রডকাস্ট রিসিভার রেজিস্ট্রি সেফটি
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(releaseReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(releaseReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        }

        // 🧠 কার্নেল পিনিং মেমরি লেয়ার: হার্ডওয়্যার নেভিগেশন বাটন ফ্রিজ করা
        startPersistentLockTask()

        setContent {
            var bypassCode by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 🎨 ডিজাইন আপডেট: ফ্ল্যাট রেডের বদলে প্রিমিয়াম ডার্ক রেড গ্রিডিয়েন্ট থিম
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
                    // 🎨 ডিজাইন আপডেট: একটি সিকিউর লক আইকন যোগ করা হয়েছে
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ডিভাইসটি লক করা হয়েছে",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "অনুগ্রহ করে আপনার বকেয়া ইএমআই (EMI) কিস্তি পরিশোধ করুন। সম্পূর্ণ পরিশোধ করার পর আপনার হ্যান্ডসেটটি স্বয়ংক্রিয়ভাবে আনলক হয়ে যাবে।",
                        color = Color(0xFFE0E0E0),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // অফলাইন ইমার্জেন্সি বাইপাস কন্টেইনার
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
                                text = "অফলাইন ইমার্জেন্সি আনলক",
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                    if (bypassCode.trim() == "123") {
                                        Toast.makeText(this@LockActivity, "সফলভাবে আনলক হয়েছে", Toast.LENGTH_SHORT).show()
                                        handleSystemUnlock()
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

    private fun startPersistentLockTask() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isLockTaskPermitted(packageName)) {
                startLockTask()
            }
        } catch (e: Exception) {
            // ফেলব্যাক ডিভাইস ওনার ক্র্যাশ ম্যানেজমেন্ট
        }
    }

    private fun handleSystemUnlock() {
        try {
            stopLockTask()
        } catch (e: Exception) { /* সোয়ালোড */ }
        finish()
    }

    // 🧠 হার্ডওয়্যার বাটন ইন্টারসেপ্ট: ব্যাক, ভলিউম এবং মেনু বাটন ব্লক
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

    // 🚨 চাইনিজ ওএম গেসচার/হোম বাইপাস প্রটেকশন (মাস্ট-হ্যাভ)
    // Xiaomi/Oppo/Vivo ফোনে ইউজার নিচ থেকে সোয়াইপ করে হোম স্ক্রিনে যাওয়ার চেষ্টা করলে এই মেথডটি ইনস্ট্যান্টলি অ্যাপকে আবার সামনে টেনে লক করে দেবে।
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isLockTaskPermitted(packageName)) {
                // যদি কোনো কারণে লক টাস্ক ড্রপ হয়, জোরপূর্বক টাস্ক সামনে আনা হবে
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            }
        } catch (e: Exception) { /* ওএম প্রোটেক্টেড */ }
    }

    // উইন্ডো ফোকাস লস্ট গার্ড: নোটিফিকেশন বার ড্রপ করার চেষ্টা রুখতে সাহায্য করে
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialogs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(releaseReceiver)
        } catch (e: Exception) { /* সোয়ালোড */ }
    }
}
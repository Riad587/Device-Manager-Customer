package com.emi.devicemanagercustomer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PairedDashboard(customerInfoMap: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
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
                    // ── 🛡️ FILTER OUT RAW SHOP ID INFRASTRUCTURE STRINGS ──
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
        Spacer(modifier = Modifier.height(80.dp))
    }
}
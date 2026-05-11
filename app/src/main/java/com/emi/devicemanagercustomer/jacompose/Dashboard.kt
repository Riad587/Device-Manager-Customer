package com.emi.devicemanagercustomer.jacompose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emi.devicemanagercustomer.services.CustomerInvoice

@Composable
fun DashboardScreen(invoice: CustomerInvoice) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Device Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Payment Progress Card
        PaymentProgressCard(invoice)

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Detailed Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Installment Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                DashboardRow("Customer Name", invoice.customerName)
                DashboardRow("Next Due Date", invoice.nextInstallmentDate)
                DashboardRow("Paid Installments", "${invoice.paidInstallments}/${invoice.totalInstallments}")
                DashboardRow("Due Amount", invoice.dueAmount)
                DashboardRow("Total Amount", invoice.totalAmount)
                DashboardRow("Shop", invoice.shopName)
                DashboardRow("Status", invoice.status)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Device Managed by ${invoice.shopName}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun PaymentProgressCard(invoice: CustomerInvoice) {
    val progress = if (invoice.totalInstallments > 0) {
        invoice.paidInstallments.toFloat() / invoice.totalInstallments.toFloat()
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Collection Progress", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(progress * 100).toInt()}% Paid", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("${invoice.totalInstallments - invoice.paidInstallments} Left", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DashboardRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
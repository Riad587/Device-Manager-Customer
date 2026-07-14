package com.emi.customer
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ProvisioningModeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resultIntent = Intent().apply {
            // 1 = Fully Managed Device (Device Owner)
            putExtra("android.app.extra.PROVISIONING_MODE", 1)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
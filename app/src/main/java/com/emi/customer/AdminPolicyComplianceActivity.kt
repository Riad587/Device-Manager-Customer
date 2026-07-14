package com.emi.customer
import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AdminPolicyComplianceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_OK)
        finish()
    }
}
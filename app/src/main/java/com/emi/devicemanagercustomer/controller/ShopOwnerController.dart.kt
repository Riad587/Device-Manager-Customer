package com.emi.devicemanagercustomer.controller

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emi.devicemanagercustomer.repository.ShopOwnerRepo
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

class ShopOwnerController(
    private val repository: ShopOwnerRepo = ShopOwnerRepo()
) : ViewModel() {

    // ── 🧠 STATELESS DYNAMIC STATE ENGINE FOR COMPOSE LAYERS ──────────
    var customerInfoMap by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isConnected by mutableStateOf(false)
        private set

    var showCameraScanner by mutableStateOf(false)
    var pendingPairingData by mutableStateOf<Map<String, String>?>(null)
    var merchantInfoMap by mutableStateOf<Map<String, String>?>(null)

    var isApiLoading by mutableStateOf(false)
    var showApiErrorDialog by mutableStateOf(false)
    var showRegistrationErrorDialog by mutableStateOf(false)

    // ── 📊 THE 4 STATE UI OBSERVABLES LOGIC ENGINE ────────────────────

    // State 1 Indicator: Customer side connection or general data pack dropped errors
    var uiInternetError by mutableStateOf(false)
        private set

    // State 2 Indicators: Query not performed / Server crash tracking for the "Contact Admin" screen
    var uiServerError by mutableStateOf(false)
        private set
    var uiServerErrorDetails by mutableStateOf("")
        private set


    // ── 🔄 STATELESS HARDWARE BOOT RUNTIME CHECK ──────────────────────
    /**
     * Pulls the validation profile via hardware IMEI signatures.
     * Maps operations cleanly across all 4 specific device connection states.
     */
    suspend fun fetchDeviceOwnerInformation(imei: String, context: android.content.Context? = null) {
        withContext(Dispatchers.IO) {
            isApiLoading = true

            // Flush all state observation properties before beginning execution stream
            uiInternetError = false
            uiServerError = false
            uiServerErrorDetails = ""

            try {
                val jsonPayload = JSONObject().apply { put("imei", imei) }
                val responseString = repository.fetchDeviceProfileByImei(jsonPayload)

                if (responseString != null) {
                    val responseObj = JSONObject(responseString)

                    // Route immediately to State 2 if the custom repository fault interception was triggered
                    if (responseObj.optBoolean("internal_server_fault_triggered", false)) {
                        uiServerError = true
                        uiServerErrorDetails = "Status Code: ${responseObj.optInt("code", 500)}\nDiagnostic Trace: ${responseObj.optString("details", "Server Fault")}"
                        throw java.io.IOException("Query execution failed due to an explicit remote gateway crash.")
                    }

                    val isSuccess = responseObj.optBoolean("success", false)
                    val registrationStatus = responseObj.optString("status", "unregistered")

                    if (isSuccess && registrationStatus == "registered") {
                        // 🟢 STATE 4: Server operational, query performed, device registered.
                        val metadataJson = responseObj.optJSONObject("metadata")
                        val extractedMap = mutableMapOf<String, String>()
                        metadataJson?.keys()?.forEach { key ->
                            extractedMap[key] = metadataJson.optString(key, "")
                        }
                        customerInfoMap = extractedMap
                        isConnected = true

                    } else if (isSuccess && registrationStatus == "unregistered") {
                        // 🟡 STATE 3: Server operational, query performed, device completely clean/unregistered.
                        clearLocalStates()
                    } else {
                        // Managed code exception on backend server side (e.g. invalid structural parameters)
                        uiServerError = true
                        uiServerErrorDetails = "Execution Error: ${responseObj.optString("message", "Validation Refused")}"
                        throw java.io.IOException("Query unperformed or blocked by backend validation rules.")
                    }
                } else {
                    // 🔴 STATE 1: Repository caught a low level disconnect exception or network timeout instance
                    uiInternetError = true
                    throw java.io.IOException("Datalink interface dropped or timeout reached.")
                }
            } catch (e: Exception) {
                Log.e("MDM_CONTROLLER", "Exception captured during runtime evaluation: ${e.message}")

                // Safe check: if it wasn't marked as a server fault but caught an exception, default to local net drop
                if (!uiServerError) {
                    uiInternetError = true
                }
                throw e
            } finally {
                isApiLoading = false
            }
        }
    }

    // ── STAGE 1: INGEST AND VALIDATE QR SCAN STRINGS ─────────────────
    fun handleQrScanned(rawJson: String) {
        val parsed = parseScannedJson(rawJson)
        if (parsed == null) {
            showApiErrorDialog = true
            return
        }

        val targetShopId = parsed["shop_id"]?.toIntOrNull() ?: 0
        if (targetShopId == 0) {
            showApiErrorDialog = true
            return
        }

        isApiLoading = true
        merchantInfoMap = null

        viewModelScope.launch {
            val fetchedProfile = repository.fetchShopOwnerProfile(targetShopId)
            isApiLoading = false

            if (fetchedProfile != null) {
                showCameraScanner = false
                merchantInfoMap = fetchedProfile
                pendingPairingData = parsed
            } else {
                showApiErrorDialog = true
            }
        }
    }

    // ── STAGE 2: PROCESS ENROLLMENT ON DJANGO BACKEND ───────────────
    fun processEnrollmentPipeline(
        imei: String,
        deviceName: String?,
        deviceModel: String?,
        serialId: String?,
        fcmToken: String?,
        onSuccess: () -> Unit
    ) {
        val data = pendingPairingData ?: return

        val jsonPayload = JSONObject().apply {
            put("shop_id", data["shop_id"])
            put("customer_name", data["customer_name"])
            put("customer_phone", data["customer_phone"])
            put("customer_address", data["customer_address"])
            put("total_price", data["total_price"])
            put("emi_duration", data["emi_duration"])
            put("imei", imei)
            put("device_name", deviceName ?: "")
            put("device_model", deviceModel ?: "")
            put("device_serial_id", serialId ?: "")
            put("fcm_token", fcmToken ?: "")
        }

        isApiLoading = true
        showRegistrationErrorDialog = false

        viewModelScope.launch {
            val serverResponseString = repository.registerDeviceOnBackend(jsonPayload)
            isApiLoading = false

            if (serverResponseString != null) {
                try {
                    val responseObj = JSONObject(serverResponseString)
                    val serverDeviceId = responseObj.optString("id", "")

                    if (serverDeviceId.isNotEmpty()) {
                        try {
                            fetchDeviceOwnerInformation(imei)
                        } catch (e: Exception) {
                            Log.e("ENROLLMENT_ERR", "Post-sync fetching step failed: ${e.message}")
                        }

                        pendingPairingData = null
                        merchantInfoMap = null
                        showCameraScanner = false

                        onSuccess()
                    } else {
                        showRegistrationErrorDialog = true
                    }
                } catch (e: Exception) {
                    Log.e("ENROLLMENT_ERR", "Failed to map backend components: ${e.message}")
                    showRegistrationErrorDialog = true
                }
            } else {
                showRegistrationErrorDialog = true
            }
        }
    }

    // ── RESET STATE ──────────────────────────────────────────────────
    fun clearLocalStates() {
        isConnected = false
        showCameraScanner = false
        pendingPairingData = null
        merchantInfoMap = null
        customerInfoMap = emptyMap()
    }

    private fun parseScannedJson(jsonStr: String): Map<String, String>? {
        return try {
            val obj = JSONObject(jsonStr)
            mapOf(
                "shop_id" to obj.optString("shop_id", ""),
                "customer_name" to obj.optString("customer_name", ""),
                "customer_phone" to obj.optString("customer_phone", ""),
                "customer_address" to obj.optString("customer_address", ""),
                "total_price" to obj.optString("total_price", ""),
                "emi_duration" to obj.optString("emi_duration", "")
            ).filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("JSON_PARSE", "Error parsing QR data payload block: ${e.message}")
            null
        }
    }
}

// ── 🛡️ HARDWARE SECURITY BACKUP ENGINE FOR PERSISTENT PARKING ─────────────────────
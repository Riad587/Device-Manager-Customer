package com.emi.customer.services

object ApiEndpoints {
    // ── 🌐 BASE NETWORKING INFRASTRUCTURE ─────────────────────────────
    private const val BASE_URL = "https://certainly-diffusion-saint.ngrok-free.dev/api"

    // ── 📁 ROUTING PATH MODULES ───────────────────────────────────────
    private const val AUTH_PATH = "$BASE_URL/auth"
    private const val DEVICES_PATH = "$BASE_URL/devices"
    private const val COMMANDS_PATH = "$BASE_URL/commands" // 👈 ADDED NEW COMMANDS MODULE PATH

    // ── 🔐 AUTHENTICATION ENDPOINTS ───────────────────────────────────
    fun getShopOwnerProfileUrl(shopId: Int): String {
        return "$AUTH_PATH/profile/$shopId/"
    }

    // ── 🛡️ DEVICE MANAGEMENT & WORKER ENDPOINTS ───────────────────────
    const val DEVICE_ADD_URL = "$DEVICES_PATH/add/"
    const val UPDATE_FCM_URL = "$DEVICES_PATH/update-fcm/"
    const val DEVICE_INFO_BY_IMEI_URL = "$DEVICES_PATH/info-by-imei/"
    const val DEVICE_UNLOCK_KEY = "$DEVICES_PATH/fetch-unlock-key/"

    // ── ⚙️ REFACTORED WORKER POLLING ENDPOINTS (ROUTED TO COMMANDS) ───
    // UPDATED: Points to: /api/commands/acknowledge/
    const val COMMAND_EXECUTION_URL = "$COMMANDS_PATH/acknowledge/"

    // UPDATED: Points to: /api/commands/status/
    const val PENDING_COMMANDS_URL = "$COMMANDS_PATH/status/"
}

//package com.emi.devicemanagercustomer.services
//
//object ApiEndpoints {
//    // ── 🌐 BASE NETWORKING INFRASTRUCTURE ─────────────────────────────
//    private const val BASE_URL = "https://certainly-diffusion-saint.ngrok-free.dev/api"
//
//    // ── 📁 ROUTING PATH MODULES ───────────────────────────────────────
//    private const val AUTH_PATH = "$BASE_URL/auth"
//    private const val DEVICES_PATH = "$BASE_URL/devices"
//
//    // ── 🔐 AUTHENTICATION ENDPOINTS ───────────────────────────────────
//    fun getShopOwnerProfileUrl(shopId: Int): String {
//        return "$AUTH_PATH/profile/$shopId/"
//    }
//
//    // ── 🛡️ DEVICE MANAGEMENT & WORKER ENDPOINTS ───────────────────────
//    const val DEVICE_ADD_URL = "$DEVICES_PATH/add/"
//    const val COMMAND_EXECUTION_URL = "$DEVICES_PATH/commands/acknowledge/"
//    const val PENDING_COMMANDS_URL = "$DEVICES_PATH/commands/status/"
//    const val UPDATE_FCM_URL = "$DEVICES_PATH/update-fcm/"
//    const val DEVICE_INFO_BY_IMEI_URL = "$DEVICES_PATH/info-by-imei/"
//    const val DEVICE_UNLOCK_KEY = "$DEVICES_PATH/fetch-unlock-key/"
//}
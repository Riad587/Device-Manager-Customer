package com.emi.devicemanagercustomer.services

import com.google.android.gms.common.api.PendingResult


object ApiEndpoints {
    private const val BASE_URL = "https://certainly-diffusion-saint.ngrok-free.dev/api"
    fun getShopOwnerProfileUrl(shopId: Int): String {
        return "$BASE_URL/auth/profile/$shopId/"
    }

    const val DEVICE_ADD_URL = "$BASE_URL/devices/add/"
    const val COMMAND_EXECUTION_URL = "$BASE_URL/devices/commands/acknowledge/"

    const val PENDING_COMMANDS_URL = "$BASE_URL/devices/commands/status/"
    const val UPDATE_FCM_URL = "$BASE_URL/devices/update-fcm/"
    const val DEVICE_INFO_BY_IMEI_URL = "$BASE_URL/devices/info-by-imei/"


}
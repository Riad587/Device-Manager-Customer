package com.emi.devicemanagercustomer.services

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// --- Data Models ---
data class RemoteCommand(val action: String)

data class PairingRequest(
    val pairingToken: String,
    val deviceId: String,
    val fcmToken: String,
    val imei1: String?,
    val imei2: String?,
    val model: String,
    val androidVersion: String
)

data class PairingResponse(
    val success: Boolean,
    val message: String
)

data class CustomerInvoice(
    val customerName: String,
    val shopName: String,
    val totalAmount: String,
    val paidAmount: String,
    val dueAmount: String,
    val nextInstallmentDate: String,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val status: String
)

data class DashboardResponse(
    val success: Boolean,
    val data: CustomerInvoice?,
    val message: String?
)

// --- API Interface ---
interface DeviceApiService {
    // One call to check status AND get dashboard data
    @GET("check-status/{deviceId}")
    suspend fun checkDeviceStatus(@Path("deviceId") deviceId: String): DashboardResponse

    @POST("pair-device")
    suspend fun registerDevice(@Body request: PairingRequest): PairingResponse

    @GET("commands/{deviceId}")
    suspend fun getPendingCommands(@Path("deviceId") deviceId: String): List<RemoteCommand>
}

// --- Retrofit Client ---
object RetrofitClient {
    private const val BASE_URL = "https://your-api-domain.com/" // REPLACE WITH YOUR URL

    val instance: DeviceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeviceApiService::class.java)
    }
}
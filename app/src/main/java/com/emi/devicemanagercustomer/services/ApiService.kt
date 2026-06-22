//package com.emi.devicemanagercustomer.services
//
//import retrofit2.http.Field
//import retrofit2.http.FormUrlEncoded
//import retrofit2.http.POST
//
//// This data class handles the JSON response from your server
//data class ApiResponse(
//    val success: Boolean,
//    val message: String? = null
//)
//
//interface ApiService {
//    @FormUrlEncoded
//    @POST("api/register-device") // The URL path on your server
//    suspend fun registerDevice(
//        @Field("shop_id") shopId: String,
//        @Field("device_id") deviceId: String,
//        @Field("fcm_token") fcmToken: String,
//        @Field("model") model: String
//    ): ApiResponse
//
//    // You likely have your checkDeviceStatus here as well
//    @POST("api/check-status")
//    suspend fun checkDeviceStatus(
//        @Field("device_id") deviceId: String
//    ): ApiResponse
//}
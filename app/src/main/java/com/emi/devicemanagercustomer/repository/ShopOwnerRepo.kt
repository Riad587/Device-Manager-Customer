package com.emi.devicemanagercustomer.repository

import android.util.Log
import com.emi.devicemanagercustomer.services.ApiEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ShopOwnerRepo {

    suspend fun fetchShopOwnerProfile(shopId: Int): Map<String, String>? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val endpointStr = ApiEndpoints.getShopOwnerProfileUrl(shopId)
                val urlTarget = URL(endpointStr)
                connection = urlTarget.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer YOUR_TOKEN_HERE")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line)
                    }
                    reader.close()

                    val responseJson = JSONObject(stringBuilder.toString())
                    if (responseJson.optBoolean("success", false) && !responseJson.isNull("data")) {
                        val profileData = responseJson.getJSONObject("data")

                        return@withContext mapOf(
                            "Merchant Name" to profileData.optString("fullname", ""),
                            "Merchant Email" to profileData.optString("email", ""),
                            "Merchant Phone" to profileData.optString("phone", ""),
                            "Merchant Address" to profileData.optString("address", ""),
                            "Shop Outlet" to profileData.optString("shop_name", "")
                        ).filterValues { !it.isNullOrEmpty() }
                    }
                }
                null
            } catch (e: Exception) {
                Log.e("MERCHANT_API_ERR", "Error calling profile retrieval endpoint", e)
                null
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun registerDeviceOnBackend(payload: JSONObject): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlTarget = URL(ApiEndpoints.DEVICE_ADD_URL)
            connection = urlTarget.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.use { it.readText() }

                return@withContext responseText
            } else {
                val stream = connection.errorStream ?: connection.inputStream
                val errorText = try {
                    BufferedReader(InputStreamReader(stream)).use { it.readText() }
                } catch (readEx: Exception) {
                    "Could not parse response stream text body: ${readEx.message}"
                }

                Log.e("DEVICE_API_ERR", "Server rejected payload with code $responseCode. Server says: $errorText")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("DEVICE_API_ERR", "Exception during device sync execution", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    // ── 🔄 STATELESS HARDWARE INTERFACE OVER-THE-AIR ROUTE ──────────────────
    /**
     * Drops the hardware IMEI payload into the validation view endpoint.
     * Expects an HTTP 200 OK for both registered and unregistered hardware lookups.
     * Returns a simple nullable String back to the controller state machine.
     */
    suspend fun fetchDeviceProfileByImei(payload: JSONObject): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlTarget = URL(ApiEndpoints.DEVICE_INFO_BY_IMEI_URL)
            connection = urlTarget.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                return@withContext reader.use { it.readText() }
            } else {
                val stream = connection.errorStream ?: connection.inputStream
                val errorBody = try {
                    BufferedReader(InputStreamReader(stream)).use { it.readText() }
                } catch (ex: Exception) {
                    "Unreadable error stream payload"
                }
                Log.w("IMEI_LOOKUP_ERR", "Server rejected lookup runtime execution. Code: $responseCode")

                // Signal an unhandled structural server error state by sending standard HTTP failure codes inside a readable schema
                return@withContext JSONObject().apply {
                    put("internal_server_fault_triggered", true)
                    put("code", responseCode)
                    put("details", errorBody)
                }.toString()
            }
        } catch (e: Exception) {
            Log.e("IMEI_LOOKUP_ERR", "Network trace disconnect occurred: ${e.message}")
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }
}
package com.example.sbe.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sbe.models.PointData
import com.example.sbe.network.RetrofitClient
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ذخیره مکان فعلی کاربر
    val currentLocation = MutableLiveData<LatLng?>()

    // LiveData برای دریافت پیام‌های جدید
    val newMessageLiveData = MutableLiveData<Pair<String, String>>()

    // متغیر برای ذخیره وضعیت ارسال توکن
    private var isTokenSent = false

    // تابع دریافت شناسه یکتا و توکن FCM
    fun fetchFCMToken(context: Context, deviceId: String, callback: (String?) -> Unit) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                callback(null)
            } else {
                val token = task.result
                token?.let {
                    sendTokenToServer(deviceId, it) // ارسال توکن به سرور
                }
                callback(token)
            }
        }
    }

    // ارسال یا به‌روزرسانی توکن در سرور
    private fun sendTokenToServer(deviceId: String, token: String) {
        if (isTokenSent) {
            Log.d(TAG, "توکن قبلاً ارسال شده است و نیازی به ارسال مجدد نیست.")
            return
        }

        val tokenData = mapOf("deviceId" to deviceId, "token" to token)
        RetrofitClient.apiService.sendUserToken(tokenData).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Token successfully updated on server")
                    isTokenSent = true
                } else {
                    Log.e(TAG, "Failed to update token on server: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e(TAG, "Error updating token on server: ${t.message}")
            }
        })
    }

    // بازیابی توکن ذخیره شده
    fun getStoredToken(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString("fcmToken", null)
    }

    // تابع برای دریافت توکن‌های کاربران از سرور
    fun getUserTokens(callback: (List<String>) -> Unit) {
        Log.d(TAG, "getUserTokens called")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getUserToken().execute()
                if (response.isSuccessful) {
                    val tokens = response.body() ?: emptyList()
                    withContext(Dispatchers.Main) {
                        callback(tokens)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(emptyList())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    // تنظیم WebSocket برای دریافت نقاط جدید
    private var webSocket: WebSocket? = null

    fun connectWebSocket(onNewPointReceived: (LatLng) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://44.203.226.200:8080").build()
        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: $text")
                try {
                    val jsonObject = JSONObject(text)
                    if (jsonObject.getString("type") == "newPoint") {
                        val location = jsonObject.getJSONObject("location")
                        val latitude = location.getDouble("latitude")
                        val longitude = location.getDouble("longitude")
                        val newPoint = LatLng(latitude, longitude)
                        viewModelScope.launch(Dispatchers.Main) {
                            onNewPointReceived(newPoint)
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Error parsing JSON from WebSocket", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "WebSocket connection failed", t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code/$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code/$reason")
            }
        }
        webSocket = client.newWebSocket(request, webSocketListener)
        client.dispatcher.executorService.shutdown()
    }

    // تابع بستن WebSocket
    fun closeWebSocket() {
        webSocket?.close(1000, "App is closing")
    }

    // تابع ارسال نقطه جدید به سرور
    fun addPointToServer(pointData: PointData, callback: (Response<ResponseBody>?) -> Unit) {
        RetrofitClient.apiService.addPoint(pointData).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                viewModelScope.launch(Dispatchers.Main) {
                    callback(response)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                viewModelScope.launch(Dispatchers.Main) {
                    callback(null)
                }
            }
        })
    }
}

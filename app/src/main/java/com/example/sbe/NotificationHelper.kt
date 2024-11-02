package com.example.sbe

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sbe.network.RetrofitClient
import com.google.android.gms.maps.model.LatLng
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.sqrt

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "geofence_notification_channel"
        private const val TAG = "NotificationHelper"
    }

    @SuppressLint("MissingPermission")
    fun sendNotification(title: String, message: String, intent: Intent) {
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geofence Notification Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        with(NotificationManagerCompat.from(context)) {
            notify((System.currentTimeMillis() % Integer.MAX_VALUE).toInt(), builder.build())
        }
    }

    fun sendGeofenceNotification(location: LatLng) {
        RetrofitClient.apiService.getUserToken().enqueue(object : Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                if (response.isSuccessful) {
                    val userTokens = response.body() ?: emptyList()
                    val tokensWithinRange = userTokens.filter { token ->
                        val userLocation = getUserLocationFromToken(token)
                        userLocation != null && calculateDistance(userLocation, location) <= 500
                    }
                    if (tokensWithinRange.isNotEmpty()) {
                        val intent = Intent(context, MainActivity::class.java)
                        sendNotification(
                            "سلام",
                            "نوتیفیکیشن geofencing بعد از ثبت نقطه!",
                            intent
                        )
                    }
                } else {
                    Log.e(TAG, "Failed to get user tokens: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                Log.e(TAG, "Error fetching user tokens: ${t.message}")
            }
        })
    }

    private fun getUserLocationFromToken(token: String): LatLng? {
        // این تابع باید موقعیت جغرافیایی کاربر را از طریق توکن برگرداند.
        // در حال حاضر فرض شده که این تابع از یک سرویس موقعیت‌یابی استفاده می‌کند.
        return null
    }

    private fun calculateDistance(location1: LatLng, location2: LatLng): Double {
        val latDiff = location1.latitude - location2.latitude
        val lngDiff = location1.longitude - location2.longitude
        return sqrt(latDiff * latDiff + lngDiff * lngDiff) * 111000 // تقریبی برای تبدیل درجه به متر
    }
}

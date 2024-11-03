package com.example.sbe


import android.annotation.SuppressLint

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.os.Handler
import android.os.Looper


class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "default_channel"
        private const val TAG = "MyFirebaseService"
    }


    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "onMessageReceived called")
        Log.d(TAG, "Message received: ${remoteMessage.data}")

        val latitude = remoteMessage.data["latitude"]?.toDouble()
        val longitude = remoteMessage.data["longitude"]?.toDouble()

        Log.d(TAG, "Latitude: $latitude, Longitude: $longitude")

        val app = application as MyApplication
        if (app.isAppInForeground()) {
            Log.d(TAG, "App is in foreground, sending data to MainActivity.")

            // ارسال Intent به MainActivity
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                putExtra("latitude", latitude)
                putExtra("longitude", longitude)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) // یا هر پرچم دیگری که لازم است
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // این پرچم ممکن است نیاز باشد
            startActivity(intent)
        } else {
            Log.d(TAG, "App is in background, sending notification.")
            sendNotificationToAll(remoteMessage.notification?.title ?: "عنوان پیش‌فرض", remoteMessage.notification?.body ?: "متن پیش‌فرض", latitude ?: 0.0, longitude ?: 0.0)
        }
    }






    @SuppressLint("MissingPermission")
    fun sendNotificationToAll(title: String, body: String, latitude: Double, longitude: Double) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("latitude", latitude)
            putExtra("longitude", longitude)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // آیکون نوتیفیکیشن
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }


}

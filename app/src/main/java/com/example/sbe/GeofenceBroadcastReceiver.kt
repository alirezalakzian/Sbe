package com.example.sbe

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.firebase.firestore.FirebaseFirestore

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GeofenceBroadcastReceiver", "Geofencing error: $errorMessage")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val geofenceTransition = geofencingEvent?.geofenceTransition
            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                val locationId = "GEOFENCE_ID" // باید به جای این مقدار شناسه درست نقطه را قرار دهید

                val db = FirebaseFirestore.getInstance()
                val userResponse = hashMapOf(
                    "locationId" to locationId,
                    "userId" to "USER_ID", // شناسه کاربر که باید شناسایی شود
                    "response" to "yes"
                )

                db.collection("responses")
                    .add(userResponse)
                    .addOnSuccessListener {
                        sendNotification(context, "تشکر", "پاسخ شما ثبت شد.")
                    }
                    .addOnFailureListener { e ->
                        sendNotification(context, "خطا", "خطایی در ذخیره پاسخ رخ داد.")
                    }

                sendNotification(context, "سلام", "یک محل جدید در نزدیکی شما تایید شده است!")
            }
        } else {
            Log.e("GeofenceBroadcastReceiver", "Permission not granted for accessing location")
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = "geofence_notification_channel"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Notification Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(context)) {
                val notificationId = (System.currentTimeMillis() % Integer.MAX_VALUE).toInt()
                notify(notificationId, builder.build())
            }
        } else {
            Log.e("GeofenceBroadcastReceiver", "Permission not granted for posting notifications")
        }
    }
}

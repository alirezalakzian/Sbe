package com.example.sbe.helpers

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionHelper(private val context: Context) {

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 2
        private const val PICK_IMAGE = 3
        const val LOCATION_PERMISSION_REQUEST_CODE = 100
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    fun requestLocationPermission(callback: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            callback(true)
        }
    }

    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                callback(true)
            }
        } else {
            callback(true)
        }
    }

    fun hasLocationPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }


    fun hasCameraPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission(callback: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_IMAGE_CAPTURE
            )
        } else {
            callback(true)
        }
    }

    fun showImageSourceDialog(activity: Activity, cameraLauncher: ActivityResultLauncher<Intent>, galleryLauncher: ActivityResultLauncher<Intent>) {
        val options = arrayOf("گرفتن عکس با دوربین", "انتخاب عکس از گالری")
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("انتخاب منبع عکس")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> requestCameraPermission { granted ->
                    if (granted) {
                        dispatchTakePictureIntent(cameraLauncher)
                    } else {
                        // Handle permission denial
                    }
                }
                1 -> openGallery(galleryLauncher)
            }
        }
        builder.show()
    }

    private fun dispatchTakePictureIntent(cameraLauncher: ActivityResultLauncher<Intent>) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            cameraLauncher.launch(takePictureIntent)
        }
    }

    private fun openGallery(galleryLauncher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
}

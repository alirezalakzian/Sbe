package com.example.sbe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sbe.databinding.ActivityMainBinding
import com.example.sbe.databinding.DialogAddLocationBinding
import com.example.sbe.models.PointData
import com.example.sbe.network.RetrofitClient
import com.example.sbe.viewmodel.MainViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private const val TAG = "MainActivity"
    }

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private var selectedImageUri: Uri? = null
    private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // استفاده از View Binding برای تنظیم محتوا
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // دریافت deviceId
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        if (deviceId == null) {
            Log.e(TAG, "Device ID not found")
            Toast.makeText(this, "شناسه دستگاه یافت نشد. لطفاً برنامه را دوباره اجرا کنید.", Toast.LENGTH_LONG).show()
            return
        }

        // تنظیم SupportMapFragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // تنظیم FusedLocationProviderClient و GeofencingClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        // ثبت launchers برای دوربین و گالری
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // مدیریت نتیجه عکس گرفته شده
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedImageUri = result.data?.data
                // مدیریت عکس انتخاب شده از گالری
            }
        }

        // درخواست مجوزها بلافاصله پس از اجرای برنامه
        requestAllPermissions()

        // دکمه ورود محل جدید
        binding.addLocationButton.setOnClickListener {
            addNewLocation()
        }

        // اتصال به WebSocket
        mainViewModel.connectWebSocket { newPoint ->
            runOnUiThread {
                mMap.addMarker(
                    MarkerOptions()
                        .position(newPoint)
                        .title("محل جدید")
                )
                Log.d(TAG, "Added new pin to the map: $newPoint")
            }

            // گوش دادن به تغییرات در LiveData برای نمایش پیام
            mainViewModel.newMessageLiveData.observe(this) { messagePair ->
                val (title, body) = messagePair
                showAlertDialog(title, body)
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        } else {
            // اگر تمامی مجوزها قبلاً داده شده‌اند، دریافت توکن را آغاز کنید
            deviceId?.let { id ->
                mainViewModel.fetchFCMToken(this, id) { token ->
                    token?.let {
                        Log.d(TAG, "FCM Token received: $it")
                        // ذخیره توکن در SharedPreferences
                        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        sharedPreferences.edit().putString("fcmToken", it).apply()

                        // ارسال توکن به سرور
                        sendTokenToServer(it)
                    } ?: Log.e(TAG, "خطا در دریافت توکن")
                }
            } ?: run {
                Log.e(TAG, "Device ID is null")
                Toast.makeText(this, "شناسه دستگاه یافت نشد. لطفاً برنامه را دوباره اجرا کنید.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAlertDialog(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("باشه") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun sendTokenToServer(token: String) {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedToken = sharedPreferences.getString("fcmToken", null)

        if (savedToken == token) {
            Log.d(TAG, "توکن قبلاً ارسال شده و نیازی به ارسال مجدد نیست.")
            return
        }

        deviceId?.let {
            Log.d(TAG, "Sending token to server: $token, deviceId: $it")
            val userTokenData = mapOf("deviceId" to it, "token" to token)
            RetrofitClient.apiService.sendUserToken(userTokenData).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Token successfully updated on server")
                        // ذخیره توکن جدید در صورت موفقیت
                        sharedPreferences.edit().putString("fcmToken", token).apply()
                    } else {
                        Log.e(TAG, "Failed to update token on server: ${response.errorBody()?.string()}")
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e(TAG, "Error updating token on server: ${t.message}")
                }
            })
        } ?: Log.e(TAG, "Device ID not found. Cannot send token to server.")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted")
                deviceId?.let { id ->
                    mainViewModel.fetchFCMToken(this, id) { token ->
                        token?.let {
                            Log.d(TAG, "FCM Token received after permissions granted: $it")
                            // ذخیره توکن در SharedPreferences
                            val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
                            sharedPreferences.edit().putString("fcmToken", it).apply()

                            // ارسال توکن به سرور
                            sendTokenToServer(it)
                        } ?: Log.e(TAG, "خطا در دریافت توکن")
                    }
                } ?: run {
                    Log.e(TAG, "Device ID is null")
                    Toast.makeText(this, "شناسه دستگاه یافت نشد. لطفاً برنامه را دوباره اجرا کنید.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "برخی از مجوزها تایید نشدند. برنامه ممکن است به درستی کار نکند.")
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Log.d(TAG, "Map is ready")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    mainViewModel.currentLocation.value = latLng
                    Log.d(TAG, "Current location: $latLng")
                } else {
                    Log.e(TAG, "Location is null")
                }
            }
        }
    }

    private fun addNewLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permissions not granted")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val newLocation = LatLng(location.latitude, location.longitude)
                Log.d(TAG, "New location: $newLocation")

                // نمایش دیالوگ اضافه کردن موقعیت
                val dialogBinding = DialogAddLocationBinding.inflate(layoutInflater)
                val dialog = AlertDialog.Builder(this)
                    .setView(dialogBinding.root)
                    .setTitle("افزودن محل جدید")
                    .setPositiveButton("ارسال") { _, _ ->
                        val description = dialogBinding.locationDescription.text.toString()
                        Log.d(TAG, "Location description: $description")

                        val locationData = PointData.Location(newLocation.latitude, newLocation.longitude)

                        // دریافت توکن از ViewModel
                        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        val userToken = mainViewModel.getStoredToken(sharedPreferences)
                        if (userToken == null) {
                            Log.e(TAG, "User token not found. Please restart the app.")
                            Toast.makeText(this, "توکن کاربر یافت نشد. لطفاً برنامه را دوباره اجرا کنید.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }

                        val pointData = PointData(locationData, userToken)
                        Log.d(TAG, "PointData to be sent: $pointData")

                        mainViewModel.addPointToServer(pointData) { response ->
                            if (response != null && response.isSuccessful) {
                                Log.d(TAG, "Point successfully added to server: $pointData")
                                Toast.makeText(this, "نقطه با موفقیت ارسال شد.", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e(TAG, "Failed to add point to server. Response: ${response?.code()}")
                                Toast.makeText(this, "ارسال نقطه ناموفق بود.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("لغو") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                    }
                    .create()

                dialogBinding.uploadImageButton.setOnClickListener {
                    // نمایش دیالوگ انتخاب منبع عکس (گالری یا دوربین)
                    val options = arrayOf("گرفتن عکس با دوربین", "انتخاب عکس از گالری")
                    AlertDialog.Builder(this)
                        .setTitle("انتخاب منبع عکس")
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> captureImage()
                                1 -> pickImageFromGallery()
                            }
                        }
                        .show()
                }

                dialog.show()
            } else {
                Log.e(TAG, "Location not identified. Please try again.")
                Toast.makeText(this, "موقعیت شما شناسایی نشد. لطفاً دوباره تلاش کنید.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun captureImage() {
        // کد برای گرفتن عکس با دوربین
        // از cameraLauncher استفاده کنید
        Log.d(TAG, "Capturing image with camera")
    }

    private fun pickImageFromGallery() {
        // کد برای انتخاب عکس از گالری
        // از galleryLauncher استفاده کنید
        Log.d(TAG, "Picking image from gallery")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Closing WebSocket connection")
        mainViewModel.closeWebSocket()
    }
}

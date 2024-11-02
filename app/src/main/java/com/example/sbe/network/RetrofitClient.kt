package com.example.sbe.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {
    private var retrofit: Retrofit? = null

    val apiService: ApiService
        get() {
            if (retrofit == null) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build()
                retrofit = Retrofit.Builder()
                    .baseUrl("http://44.203.226.200:4000/") // آدرس سرور خود را در اینجا وارد کنید
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!.create(ApiService::class.java).also {
                Log.d("RetrofitClient", "Attempting to connect to API")
            }


        }
}


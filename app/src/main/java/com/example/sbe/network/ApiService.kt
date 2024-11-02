package com.example.sbe.network

import com.example.sbe.models.PointData
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("add-point")
    fun addPoint(@Body pointData: PointData): Call<ResponseBody>

    @GET("get-user-tokens")
    fun getUserToken(): Call<List<String>>

    @POST("user/token")
    fun sendUserToken(@Body userTokenData: Map<String, String>): Call<Void>
}

package com.ash.axis.admin.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AdminApi {
    @POST("v1/admin/session")
    suspend fun createSession(
        @Body body: AdminSessionRequest,
    ): AxisSession

    @GET("v1/admin/users")
    suspend fun listUsers(
        @Header("Authorization") authorization: String,
    ): UsersResponse

    @POST("v1/admin/users/{admno}/allow")
    suspend fun allow(
        @Path("admno") admno: String,
        @Header("Authorization") authorization: String,
    ): AdminUser

    @POST("v1/admin/users/{admno}/kick")
    suspend fun kick(
        @Path("admno") admno: String,
        @Header("Authorization") authorization: String,
    ): AdminUser

    @POST("v1/admin/users/{admno}/ban")
    suspend fun ban(
        @Path("admno") admno: String,
        @Header("Authorization") authorization: String,
    ): AdminUser

    @POST("v1/admin/approve-all")
    suspend fun approveAll(
        @Header("Authorization") authorization: String,
    ): ApproveAllResponse

    @GET("v1/admin/health")
    suspend fun health(
        @Header("Authorization") authorization: String,
    ): HealthResponse

    @GET("v1/admin/config")
    suspend fun getConfig(
        @Header("Authorization") authorization: String,
    ): RemoteConfig

    @PUT("v1/admin/config")
    suspend fun putConfig(
        @Header("Authorization") authorization: String,
        @Body patch: ConfigPatch,
    ): RemoteConfig
}

interface IcloudAuthApi {
    @POST("users/login")
    suspend fun requestOtp(
        @Body body: Map<String, String>,
    ): LoginResponse

    @POST("users/login/validate")
    suspend fun validateOtp(
        @Body body: Map<String, String>,
    ): LoginResponse
}

package app.dockwallet.wallet.data.api

import retrofit2.Response
import retrofit2.http.*

// ─── DTOs Login ───────────────────────────────────────────────────────────────

data class LoginResponse(
    val token: String,
    val token_type: String
)

// ─── DTOs Passes (bestehend, server-seitig) ───────────────────────────────────

data class Pass(
    val id: String,           // UUID (war Int, jetzt String — Server gibt UUID zurück)
    val pass_type: String = "boardingPass",
    val passenger_name: String?,
    val flight_number: String?,
    val origin: String?,
    val destination: String?,
    val departure_time: String?,
    val arrival_time: String?,
    val event_date: String?,
    val seat: String?,
    val gate: String?,
    val booking_reference: String?,
    val barcode: String?,
    val subtitle: String?,
    val logo_text: String?,
    val color_background: String?,
    val color_foreground: String?,
    val color_label: String?,
    val is_voided: Boolean = false,
    val signature_valid: Boolean = false,
    val updated_at: String? = null,
    val created_at: String? = null,
)

// ─── DTOs Sync ───────────────────────────────────────────────────────────────

data class RegisterDeviceRequest(val device_name: String)
data class RegisterDeviceResponse(val sync_token: String)

data class SyncPullResponse(
    val passes: List<Pass>,
    val server_time: String,
    val count: Int,
)

data class PushPassRequest(
    val file: String? = null,       // base64 .pkpass
)

data class SyncDevice(
    val id: String,
    val device_name: String,
    val last_sync: String?,
    val created_at: String,
)

data class SyncStatusResponse(
    val pass_count: Int,
    val devices: List<SyncDevice>,
    val server_time: String,
)

// ─── API Interface ────────────────────────────────────────────────────────────

interface DockWalletApi {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): Response<LoginResponse>

    // ── Passes (bestehend) ────────────────────────────────────────────────────

    @GET("api/passes")
    suspend fun getPasses(
        @Header("Authorization") token: String
    ): Response<List<Pass>>

    @Multipart
    @POST("api/passes/upload")
    suspend fun uploadPass(
        @Header("Authorization") token: String,
        @Part("file") file: String   // base64
    ): Response<Pass>

    @DELETE("api/passes/{id}")
    suspend fun deletePass(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    // ── Sync ──────────────────────────────────────────────────────────────────

    @POST("api/sync/register-device")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body body: RegisterDeviceRequest
    ): Response<RegisterDeviceResponse>

    @GET("api/sync/passes")
    suspend fun syncPull(
        @Header("Authorization") token: String,
        @Query("since") since: String? = null
    ): Response<SyncPullResponse>

    @POST("api/sync/passes")
    suspend fun syncPush(
        @Header("Authorization") token: String,
        @Body body: PushPassRequest
    ): Response<Pass>

    @DELETE("api/sync/passes/{id}")
    suspend fun syncDelete(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @GET("api/sync/status")
    suspend fun syncStatus(
        @Header("Authorization") token: String
    ): Response<SyncStatusResponse>

    @GET("api/sync/devices")
    suspend fun syncDevices(
        @Header("Authorization") token: String
    ): Response<List<SyncDevice>>

    @DELETE("api/sync/devices/{id}")
    suspend fun removeSyncDevice(
        @Header("Authorization") token: String,
        @Path("id") deviceId: String
    ): Response<Unit>
}
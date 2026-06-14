package app.dockwallet.wallet.data.api

import retrofit2.Response
import retrofit2.http.*

data class LoginResponse(
    val access_token: String,
    val token_type: String
)

data class Pass(
    val id: Int,
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
    val signature_valid: Boolean = false
)

interface DockWalletApi {

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @GET("passes/")
    suspend fun getPasses(
        @Header("Authorization") token: String
    ): Response<List<Pass>>
}
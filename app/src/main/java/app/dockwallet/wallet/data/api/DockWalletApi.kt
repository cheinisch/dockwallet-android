package app.dockwallet.wallet.data.api

import retrofit2.Response
import retrofit2.http.*

data class LoginResponse(
    val access_token: String,
    val token_type: String
)

data class BoardingPass(
    val id: Int,
    val passenger_name: String,
    val flight_number: String,
    val origin: String,
    val destination: String,
    val departure_time: String,
    val seat: String?,
    val gate: String?,
    val booking_reference: String?
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
    ): Response<List<BoardingPass>>
}
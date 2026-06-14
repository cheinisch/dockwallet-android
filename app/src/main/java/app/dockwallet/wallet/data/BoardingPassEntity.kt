package app.dockwallet.wallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boarding_passes")
data class BoardingPassEntity(
    @PrimaryKey
    val id: Int,
    val passengerName: String,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureTime: String,
    val seat: String?,
    val gate: String?,
    val bookingReference: String?,
    val isLocal: Boolean = false  // true = nur lokal, nicht vom Server
)
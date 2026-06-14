package app.dockwallet.wallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passes")
data class PassEntity(
    @PrimaryKey
    val id: Int,
    val passType: String = "boardingPass",
    val passengerName: String?,
    val flightNumber: String?,
    val origin: String?,
    val destination: String?,
    val departureTime: String?,
    val arrivalTime: String?,
    val eventDate: String?,
    val seat: String?,
    val gate: String?,
    val bookingReference: String?,
    val barcode: String?,
    val subtitle: String?,
    val logoText: String?,
    val colorBackground: String?,
    val colorForeground: String?,
    val colorLabel: String?,
    val isVoided: Boolean = false,
    val signatureValid: Boolean = false,
    val isLocal: Boolean = false
)
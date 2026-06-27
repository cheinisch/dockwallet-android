package app.dockwallet.wallet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "passes",
    indices = [Index(value = ["serverId"], unique = true)]
)
data class PassEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Sync-Felder ───────────────────────────────────────────────────────────
    val serverId: String? = null,
    val isLocal: Boolean = true,
    val localFilePath: String? = null,
    val updatedAt: String? = null,

    // ── Pass-Daten ────────────────────────────────────────────────────────────
    val passType: String? = "boardingPass",
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
    val createdAt: String? = null,

    // ── Favorit ───────────────────────────────────────────────────────────────
    val isFavorite: Boolean = false,
    // true = User hat isFavorite lokal geändert, noch nicht zum Server gesynct
    val favoriteChangedLocally: Boolean = false,
)
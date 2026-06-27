package app.dockwallet.wallet.data

import androidx.room.ColumnInfo
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

    // ── Sync-Felder (NEU) ─────────────────────────────────────────────────────
    val serverId: String? = null,        // UUID vom Server; null = noch nicht gesynct
    val isLocal: Boolean = true,         // true = nur lokal, noch kein Push
    val localFilePath: String? = null,   // Pfad zur .pkpass-Datei für Push
    val updatedAt: String? = null,       // Server-Timestamp für Delta-Sync

    // ── Pass-Daten (unverändert) ──────────────────────────────────────────────
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
)
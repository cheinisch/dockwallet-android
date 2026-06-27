package app.dockwallet.wallet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PassDao {

    // ── Bestehende Queries (unverändert) ──────────────────────────────────────

    @Query("SELECT * FROM passes ORDER BY COALESCE(departureTime, eventDate) DESC")
    fun getAllPasses(): Flow<List<PassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passes: List<PassEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: PassEntity): Long

    @Delete
    suspend fun delete(pass: PassEntity)

    @Query("DELETE FROM passes WHERE isLocal = 0")
    suspend fun deleteAllServerPasses()

    @Query("SELECT * FROM passes WHERE isLocal = 1")
    suspend fun getLocalPasses(): List<PassEntity>

    @Query("SELECT * FROM passes WHERE passType = :type ORDER BY COALESCE(departureTime, eventDate) DESC")
    fun getPassesByType(type: String): Flow<List<PassEntity>>

    // ── Neue Sync-Queries ─────────────────────────────────────────────────────

    /** Pässe die noch nicht auf dem Server sind (kein serverId) */
    @Query("SELECT * FROM passes WHERE serverId IS NULL AND isLocal = 1")
    suspend fun getLocalOnlyPasses(): List<PassEntity>

    /** Alle bekannten Server-IDs */
    @Query("SELECT serverId FROM passes WHERE serverId IS NOT NULL")
    suspend fun getAllServerIds(): List<String>

    /** Nach Push: lokalen Eintrag mit Server-UUID verknüpfen */
    @Query("UPDATE passes SET serverId = :serverId, isLocal = 0 WHERE id = :localId")
    suspend fun linkToServer(localId: Int, serverId: String)

    /** Pass per Server-ID finden */
    @Query("SELECT * FROM passes WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): PassEntity?

    /** Pass per Server-ID löschen */
    @Query("DELETE FROM passes WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    /** Server-Pass aktualisieren (alle Felder außer id, isLocal, localFilePath) */
    @Query("""
        UPDATE passes SET
            passType = :passType,
            passengerName = :passengerName,
            flightNumber = :flightNumber,
            origin = :origin,
            destination = :destination,
            departureTime = :departureTime,
            arrivalTime = :arrivalTime,
            eventDate = :eventDate,
            seat = :seat,
            bookingReference = :bookingReference,
            barcode = :barcode,
            subtitle = :subtitle,
            logoText = :logoText,
            colorBackground = :colorBackground,
            colorForeground = :colorForeground,
            colorLabel = :colorLabel,
            isVoided = :isVoided,
            signatureValid = :signatureValid,
            updatedAt = :updatedAt,
            isLocal = 0
        WHERE serverId = :serverId
    """)
    suspend fun updateByServerId(
        serverId: String,
        passType: String,
        passengerName: String?,
        flightNumber: String?,
        origin: String?,
        destination: String?,
        departureTime: String?,
        arrivalTime: String?,
        eventDate: String?,
        seat: String?,
        bookingReference: String?,
        barcode: String?,
        subtitle: String?,
        logoText: String?,
        colorBackground: String?,
        colorForeground: String?,
        colorLabel: String?,
        isVoided: Boolean,
        signatureValid: Boolean,
        updatedAt: String?,
    )
}
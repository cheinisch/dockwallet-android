package app.dockwallet.wallet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PassDao {

    @Query("SELECT * FROM passes ORDER BY isFavorite DESC, COALESCE(departureTime, eventDate) DESC")
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

    @Query("SELECT * FROM passes WHERE passType = :type ORDER BY isFavorite DESC, COALESCE(departureTime, eventDate) DESC")
    fun getPassesByType(type: String): Flow<List<PassEntity>>

    // Favorit toggeln — markiert auch als lokal geändert
    @Query("UPDATE passes SET isFavorite = :favorite, favoriteChangedLocally = 1 WHERE id = :id")
    suspend fun setFavorite(id: Int, favorite: Boolean)

    // Nach erfolgreichem Server-Sync: Flag zurücksetzen
    @Query("UPDATE passes SET favoriteChangedLocally = 0 WHERE serverId = :serverId")
    suspend fun clearFavoriteChanged(serverId: String)

    // Alle Pässe mit pending Favorit-Änderung (zum Pushen)
    @Query("SELECT * FROM passes WHERE favoriteChangedLocally = 1 AND serverId IS NOT NULL")
    suspend fun getPendingFavoriteChanges(): List<PassEntity>

    // Pull: Favorit vom Server übernehmen (nur wenn kein lokaler Pending-Change)
    @Query("""
        UPDATE passes SET isFavorite = :favorite
        WHERE serverId = :serverId AND favoriteChangedLocally = 0
    """)
    suspend fun updateFavoriteFromServer(serverId: String, favorite: Boolean)

    // ── Sync-Queries ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM passes WHERE serverId IS NULL AND isLocal = 1")
    suspend fun getLocalOnlyPasses(): List<PassEntity>

    @Query("SELECT serverId FROM passes WHERE serverId IS NOT NULL")
    suspend fun getAllServerIds(): List<String>

    @Query("UPDATE passes SET serverId = :serverId, isLocal = 0 WHERE id = :localId")
    suspend fun linkToServer(localId: Int, serverId: String)

    @Query("SELECT * FROM passes WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): PassEntity?

    @Query("DELETE FROM passes WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("""
        UPDATE passes SET
            passType = :passType, passengerName = :passengerName,
            flightNumber = :flightNumber, origin = :origin,
            destination = :destination, departureTime = :departureTime,
            arrivalTime = :arrivalTime, eventDate = :eventDate,
            seat = :seat, bookingReference = :bookingReference,
            barcode = :barcode, subtitle = :subtitle,
            logoText = :logoText, colorBackground = :colorBackground,
            colorForeground = :colorForeground, colorLabel = :colorLabel,
            isVoided = :isVoided, signatureValid = :signatureValid,
            updatedAt = :updatedAt, isLocal = 0
        WHERE serverId = :serverId
    """)
    suspend fun updateByServerId(
        serverId: String, passType: String, passengerName: String?,
        flightNumber: String?, origin: String?, destination: String?,
        departureTime: String?, arrivalTime: String?, eventDate: String?,
        seat: String?, bookingReference: String?, barcode: String?,
        subtitle: String?, logoText: String?, colorBackground: String?,
        colorForeground: String?, colorLabel: String?,
        isVoided: Boolean, signatureValid: Boolean, updatedAt: String?,
    )
}
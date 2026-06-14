package app.dockwallet.wallet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PassDao {

    @Query("SELECT * FROM passes ORDER BY COALESCE(departureTime, eventDate) DESC")
    fun getAllPasses(): Flow<List<PassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passes: List<PassEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: PassEntity)

    @Delete
    suspend fun delete(pass: PassEntity)

    @Query("DELETE FROM passes WHERE isLocal = 0")
    suspend fun deleteAllServerPasses()

    @Query("SELECT * FROM passes WHERE isLocal = 1")
    suspend fun getLocalPasses(): List<PassEntity>

    @Query("SELECT * FROM passes WHERE passType = :type ORDER BY COALESCE(departureTime, eventDate) DESC")
    fun getPassesByType(type: String): Flow<List<PassEntity>>
}
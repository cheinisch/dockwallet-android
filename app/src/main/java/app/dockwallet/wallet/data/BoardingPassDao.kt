package app.dockwallet.wallet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardingPassDao {

    @Query("SELECT * FROM boarding_passes ORDER BY departureTime ASC")
    fun getAllPasses(): Flow<List<BoardingPassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passes: List<BoardingPassEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: BoardingPassEntity)

    @Delete
    suspend fun delete(pass: BoardingPassEntity)

    @Query("DELETE FROM boarding_passes WHERE isLocal = 0")
    suspend fun deleteAllServerPasses()

    @Query("SELECT * FROM boarding_passes WHERE isLocal = 1")
    suspend fun getLocalPasses(): List<BoardingPassEntity>
}
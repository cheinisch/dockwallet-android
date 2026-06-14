package app.dockwallet.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PassEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun passDao(): PassDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS boarding_passes")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS passes (
                        id INTEGER PRIMARY KEY NOT NULL,
                        passType TEXT NOT NULL DEFAULT 'boardingPass',
                        passengerName TEXT,
                        flightNumber TEXT,
                        origin TEXT,
                        destination TEXT,
                        departureTime TEXT,
                        arrivalTime TEXT,
                        eventDate TEXT,
                        seat TEXT,
                        gate TEXT,
                        bookingReference TEXT,
                        barcode TEXT,
                        subtitle TEXT,
                        logoText TEXT,
                        colorBackground TEXT,
                        colorForeground TEXT,
                        colorLabel TEXT,
                        isVoided INTEGER NOT NULL DEFAULT 0,
                        signatureValid INTEGER NOT NULL DEFAULT 0,
                        isLocal INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dockwallet.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
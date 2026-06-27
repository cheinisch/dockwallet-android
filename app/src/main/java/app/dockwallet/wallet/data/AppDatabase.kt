package app.dockwallet.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PassEntity::class],
    version = 3,             // War 2, jetzt 3 wegen neuer Sync-Spalten
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun passDao(): PassDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Bestehende Migration (unverändert)
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

        // Neue Migration: Sync-Felder hinzufügen
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Neue Spalten
                db.execSQL("ALTER TABLE passes ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN localFilePath TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN updatedAt TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN createdAt TEXT")

                // isLocal war schon vorhanden (Boolean), bleibt erhalten
                // Unique Index auf serverId für schnelles Lookup
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_passes_serverId ON passes(serverId)")

                // Autoincrement: id war INT PRIMARY KEY ohne AUTOINCREMENT.
                // Room generiert das korrekt über die Entity-Annotation.
                // Bestehende Zeilen behalten ihre id, neue bekommen autoincrement.
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dockwallet.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
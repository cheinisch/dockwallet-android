package app.dockwallet.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PassEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun passDao(): PassDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS boarding_passes")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS passes (
                        id INTEGER PRIMARY KEY NOT NULL,
                        passType TEXT NOT NULL DEFAULT 'boardingPass',
                        passengerName TEXT, flightNumber TEXT, origin TEXT,
                        destination TEXT, departureTime TEXT, arrivalTime TEXT,
                        eventDate TEXT, seat TEXT, gate TEXT, bookingReference TEXT,
                        barcode TEXT, subtitle TEXT, logoText TEXT,
                        colorBackground TEXT, colorForeground TEXT, colorLabel TEXT,
                        isVoided INTEGER NOT NULL DEFAULT 0,
                        signatureValid INTEGER NOT NULL DEFAULT 0,
                        isLocal INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passes ADD COLUMN serverId TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN localFilePath TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN updatedAt TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN createdAt TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_passes_serverId ON passes(serverId)")
                db.execSQL("UPDATE passes SET passType = 'generic' WHERE passType IS NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS passes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId TEXT, isLocal INTEGER NOT NULL DEFAULT 1,
                        localFilePath TEXT, updatedAt TEXT, createdAt TEXT,
                        passType TEXT DEFAULT 'boardingPass',
                        passengerName TEXT, flightNumber TEXT, origin TEXT,
                        destination TEXT, departureTime TEXT, arrivalTime TEXT,
                        eventDate TEXT, seat TEXT, gate TEXT, bookingReference TEXT,
                        barcode TEXT, subtitle TEXT, logoText TEXT,
                        colorBackground TEXT, colorForeground TEXT, colorLabel TEXT,
                        isVoided INTEGER NOT NULL DEFAULT 0,
                        signatureValid INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO passes_new (
                        id, serverId, isLocal, localFilePath, updatedAt, createdAt,
                        passType, passengerName, flightNumber, origin, destination,
                        departureTime, arrivalTime, eventDate, seat, gate,
                        bookingReference, barcode, subtitle, logoText,
                        colorBackground, colorForeground, colorLabel, isVoided, signatureValid
                    )
                    SELECT id, serverId, isLocal, localFilePath, updatedAt, createdAt,
                        COALESCE(passType, 'generic'), passengerName, flightNumber, origin, destination,
                        departureTime, arrivalTime, eventDate, seat, gate,
                        bookingReference, barcode, subtitle, logoText,
                        colorBackground, colorForeground, colorLabel, isVoided, signatureValid
                    FROM passes
                """.trimIndent())
                db.execSQL("DROP TABLE passes")
                db.execSQL("ALTER TABLE passes_new RENAME TO passes")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_passes_serverId ON passes(serverId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 5 → 6: favoriteChangedLocally Flag
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passes ADD COLUMN favoriteChangedLocally INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dockwallet.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
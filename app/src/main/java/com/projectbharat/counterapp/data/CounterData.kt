package com.projectbharat.counterapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CounterCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleMr: String,
    val titleEn: String,
    val kind: String = "GENERAL",
    val goal: Int = 0,
    val enabled: Boolean = true
)

@Entity(tableName = "counter_logs", indices = [Index("categoryId"), Index("timestamp")])
data class CounterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val categoryId: Long,
    val count: Int = 1,
    val sessionDurationMs: Long? = null,
    val strength: String? = null,
    val note: String? = null
)

@Dao
interface CounterDao {
    @Query("SELECT * FROM categories WHERE enabled = 1 AND kind NOT IN ('STEPS', 'BABY') ORDER BY id")
    fun categories(): Flow<List<CounterCategory>>
    @Insert suspend fun addCategory(category: CounterCategory): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(items: List<CounterCategory>)
    @Query("SELECT * FROM categories ORDER BY id") suspend fun allCategories(): List<CounterCategory>
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()
    @Update suspend fun updateCategory(category: CounterCategory)
    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1") suspend fun category(id: Long): CounterCategory?
    @Query("UPDATE categories SET enabled = 0 WHERE id = :id") suspend fun disableCategory(id: Long)
    @Query("SELECT * FROM categories WHERE enabled = 0 AND kind NOT IN ('BABY', 'STEPS') ORDER BY id") fun archivedCategories(): Flow<List<CounterCategory>>
    @Query("UPDATE categories SET enabled = 1 WHERE id = :id") suspend fun enableCategory(id: Long)

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    fun todayCount(categoryId: Long, date: String): Flow<Int>
    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun countNow(categoryId: Long, date: String): Int
    @Insert suspend fun addLog(log: CounterLog): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLogs(items: List<CounterLog>)
    @Query("SELECT * FROM counter_logs ORDER BY id") suspend fun allLogs(): List<CounterLog>
    @Query("DELETE FROM counter_logs") suspend fun deleteAllLogs()
    @Update suspend fun updateLog(log: CounterLog)
    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC, id DESC")
    fun logsForDay(categoryId: Long, date: String): Flow<List<CounterLog>>
    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date AND note LIKE 'MANTRA:%' ORDER BY note")
    fun mantraLogsForDay(categoryId: Long, date: String): Flow<List<CounterLog>>
    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND note LIKE 'MANTRA:%' ORDER BY date, note")
    fun allMantraLogs(categoryId: Long): Flow<List<CounterLog>>
    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date AND note=:note")
    fun mantraTodayCount(categoryId: Long, date: String, note: String): Flow<Int>
    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND note=:note")
    fun mantraAllTimeCount(categoryId: Long, note: String): Flow<Int>
    @Query("SELECT date, SUM(count) AS total FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate AND note=:note GROUP BY date ORDER BY date")
    fun mantraWeeklyTotals(categoryId: Long, fromDate: String, toDate: String, note: String): Flow<List<DayTotal>>
    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date AND note=:note LIMIT 1")
    suspend fun findMantraLog(categoryId: Long, date: String, note: String): CounterLog?
    @Transaction suspend fun incrementMantraDaily(categoryId: Long, date: String, mantra: String, amount: Int = 1) {
        if (amount <= 0) return
        val note = "MANTRA:" + mantra
        val existing = findMantraLog(categoryId, date, note)
        if (existing == null) addLog(CounterLog(date = date, timestamp = System.currentTimeMillis(), categoryId = categoryId, count = amount, note = note))
        else updateLog(existing.copy(count = existing.count + amount))
    }
    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate ORDER BY timestamp DESC, id DESC")
    fun logsForRange(categoryId: Long, fromDate: String, toDate: String): Flow<List<CounterLog>>
    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestLog(categoryId: Long, date: String): CounterLog?
    @Query("DELETE FROM counter_logs WHERE id=:id") suspend fun deleteLog(id: Long)
    @Query("DELETE FROM counter_logs WHERE categoryId=:categoryId AND date=:date") suspend fun resetDay(categoryId: Long, date: String)
    @Query("SELECT date, SUM(count) AS total FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate GROUP BY date ORDER BY date")
    fun weeklyTotals(categoryId: Long, fromDate: String, toDate: String): Flow<List<DayTotal>>
    @Query("SELECT AVG(sessionDurationMs) FROM counter_logs WHERE categoryId=:categoryId AND sessionDurationMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageSession(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>
    @Transaction suspend fun restoreSnapshot(categories: List<CounterCategory>, logs: List<CounterLog>) {
        deleteAllLogs()
        deleteAllCategories()
        insertCategories(categories)
        if (logs.isNotEmpty()) insertLogs(logs)
    }
}

data class DayTotal(val date: String, val total: Int)

@Database(entities = [CounterCategory::class, CounterLog::class], version = 10, exportSchema = false)
abstract class CounterDatabase : RoomDatabase() {
    abstract fun dao(): CounterDao
    companion object {
        @Volatile private var INSTANCE: CounterDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS baby_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER NOT NULL, date TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, dayPeriod TEXT NOT NULL, movementCount INTEGER NOT NULL DEFAULT 0, strength TEXT, note TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_sessions_categoryId ON baby_sessions(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_sessions_date ON baby_sessions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_sessions_startedAt ON baby_sessions(startedAt)")
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN timeToTenMs INTEGER")
                db.execSQL("ALTER TABLE counter_logs ADD COLUMN sessionId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_logs_sessionId ON counter_logs(sessionId)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS baby_detections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, timestamp INTEGER NOT NULL, magnitude REAL NOT NULL, status TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_detections_sessionId ON baby_detections(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_detections_timestamp ON baby_detections(timestamp)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE baby_sessions ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'TEN_MOVEMENTS'") }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_logs_categoryId_date_note ON counter_logs(categoryId, date, note)") }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN firstHourCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE baby_sessions SET firstHourCount=(SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE counter_logs.sessionId=baby_sessions.id AND counter_logs.timestamp < baby_sessions.startedAt+3600000)")
                db.execSQL("CREATE TABLE IF NOT EXISTS walking_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER NOT NULL, date TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, steps INTEGER NOT NULL DEFAULT 0, distanceMeters REAL NOT NULL DEFAULT 0.0, gpsReliable INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_walking_sessions_categoryId ON walking_sessions(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_walking_sessions_date ON walking_sessions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_walking_sessions_startedAt ON walking_sessions(startedAt)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("UPDATE categories SET enabled = 0 WHERE kind = 'STEPS'") }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("UPDATE categories SET enabled = 0 WHERE kind IN ('BABY', 'STEPS')") }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS baby_detections")
                db.execSQL("DROP TABLE IF EXISTS baby_sessions")
                db.execSQL("DROP TABLE IF EXISTS walking_sessions")
                db.execSQL("DROP INDEX IF EXISTS index_counter_logs_sessionId")
                db.execSQL("DROP INDEX IF EXISTS index_counter_logs_categoryId_date_note")
                db.execSQL("CREATE TABLE counter_logs_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, timestamp INTEGER NOT NULL, categoryId INTEGER NOT NULL, count INTEGER NOT NULL, sessionDurationMs INTEGER, strength TEXT, note TEXT)")
                db.execSQL("INSERT INTO counter_logs_new (id, date, timestamp, categoryId, count, sessionDurationMs, strength, note) SELECT id, date, timestamp, categoryId, count, sessionDurationMs, strength, note FROM counter_logs")
                db.execSQL("DROP TABLE counter_logs")
                db.execSQL("ALTER TABLE counter_logs_new RENAME TO counter_logs")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_logs_categoryId ON counter_logs(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_logs_timestamp ON counter_logs(timestamp)")
            }
        }
        fun get(context: Context): CounterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CounterDatabase::class.java, "counterapp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build().also { INSTANCE = it }
        }
    }
}

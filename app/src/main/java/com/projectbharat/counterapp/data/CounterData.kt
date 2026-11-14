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

@Entity(tableName = "baby_sessions", indices = [Index("categoryId"), Index("date"), Index("startedAt")])
data class BabySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val date: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val dayPeriod: String,
    val movementCount: Int = 0,
    val timeToTenMs: Long? = null,
    val strength: String? = null,
    val note: String? = null,
    @ColumnInfo(defaultValue = "'TEN_MOVEMENTS'") val trackingMode: String = "TEN_MOVEMENTS"
)

@Entity(tableName = "baby_detections", indices = [Index("sessionId"), Index("timestamp")])
data class BabyDetection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val magnitude: Float,
    val status: String = "PENDING"
)

@Entity(tableName = "counter_logs", indices = [Index("categoryId"), Index("timestamp"), Index("sessionId")])
data class CounterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val categoryId: Long,
    val count: Int = 1,
    val sessionDurationMs: Long? = null,
    val strength: String? = null,
    val note: String? = null,
    val sessionId: Long? = null
)

@Dao
interface CounterDao {
    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY id")
    fun categories(): Flow<List<CounterCategory>>

    @Insert
    suspend fun addCategory(category: CounterCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CounterCategory>)

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun allCategories(): List<CounterCategory>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Update
    suspend fun updateCategory(category: CounterCategory)

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun category(id: Long): CounterCategory?

    @Query("UPDATE categories SET enabled = 0 WHERE id = :id")
    suspend fun disableCategory(id: Long)

    @Query("SELECT * FROM categories WHERE enabled = 0 ORDER BY id")
    fun archivedCategories(): Flow<List<CounterCategory>>

    @Query("UPDATE categories SET enabled = 1 WHERE id = :id")
    suspend fun enableCategory(id: Long)

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    fun todayCount(categoryId: Long, date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun countNow(categoryId: Long, date: String): Int

    @Insert
    suspend fun addLog(log: CounterLog): Long

    @Insert
    suspend fun addBabyDetection(detection: BabyDetection): Long

    @Query("SELECT * FROM baby_detections WHERE sessionId=:sessionId ORDER BY timestamp DESC")
    fun detectionsForSession(sessionId: Long): Flow<List<BabyDetection>>

    @Query("SELECT d.* FROM baby_detections d INNER JOIN baby_sessions s ON s.id=d.sessionId WHERE s.categoryId=:categoryId AND s.date=:date ORDER BY d.timestamp DESC")
    fun detectionsForDay(categoryId: Long, date: String): Flow<List<BabyDetection>>

    @Query("SELECT * FROM baby_detections ORDER BY id")
    suspend fun allBabyDetections(): List<BabyDetection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBabyDetections(items: List<BabyDetection>)

    @Query("DELETE FROM baby_detections")
    suspend fun deleteAllBabyDetections()

    @Query("UPDATE baby_detections SET status='DISMISSED' WHERE id=:id AND status='PENDING'")
    suspend fun dismissDetection(id: Long)

    @Transaction
    suspend fun confirmDetection(id: Long, log: CounterLog, durationMs: Long): Boolean {
        val candidate = pendingDetection(id) ?: return false
        addLog(log.copy(timestamp = candidate.timestamp, sessionId = candidate.sessionId))
        incrementBabySession(candidate.sessionId, durationMs)
        markDetectionConfirmed(id)
        return true
    }

    @Query("SELECT * FROM baby_detections WHERE id=:id AND status='PENDING' LIMIT 1")
    suspend fun pendingDetection(id: Long): BabyDetection?

    @Query("UPDATE baby_detections SET status='CONFIRMED' WHERE id=:id AND status='PENDING'")
    suspend fun markDetectionConfirmed(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(items: List<CounterLog>)

    @Query("SELECT * FROM counter_logs ORDER BY id")
    suspend fun allLogs(): List<CounterLog>

    @Query("DELETE FROM counter_logs")
    suspend fun deleteAllLogs()

    @Update
    suspend fun updateLog(log: CounterLog)

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC, id DESC")
    fun logsForDay(categoryId: Long, date: String): Flow<List<CounterLog>>

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate ORDER BY timestamp DESC, id DESC")
    fun logsForRange(categoryId: Long, fromDate: String, toDate: String): Flow<List<CounterLog>>

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestLog(categoryId: Long, date: String): CounterLog?

    @Query("DELETE FROM counter_logs WHERE id=:id")
    suspend fun deleteLog(id: Long)

    @Transaction
    suspend fun addBabyMovementAtomically(log: CounterLog, sessionId: Long, durationMs: Long): Long {
        val id = addLog(log)
        incrementBabySession(sessionId, durationMs)
        return id
    }

    @Transaction
    suspend fun undoLatestBabyMovementAtomically(sessionId: Long): Boolean {
        val latest = latestSessionLog(sessionId) ?: return false
        deleteSessionLog(latest.id)
        decrementBabySession(sessionId)
        return true
    }

    @Query("DELETE FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun resetDay(categoryId: Long, date: String)

    @Query("SELECT date, SUM(count) AS total FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate GROUP BY date ORDER BY date")
    fun weeklyTotals(categoryId: Long, fromDate: String, toDate: String): Flow<List<DayTotal>>

    @Query("SELECT AVG(sessionDurationMs) FROM counter_logs WHERE categoryId=:categoryId AND sessionDurationMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageSession(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>

    @Insert
    suspend fun insertBabySession(session: BabySession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBabySessions(items: List<BabySession>)

    @Query("SELECT * FROM baby_sessions ORDER BY id")
    suspend fun allBabySessions(): List<BabySession>

    @Query("DELETE FROM baby_sessions")
    suspend fun deleteAllBabySessions()

    @Transaction
    suspend fun restoreSnapshot(categories: List<CounterCategory>, logs: List<CounterLog>, sessions: List<BabySession>, detections: List<BabyDetection>) {
        deleteAllLogs()
        deleteAllBabySessions()
        deleteAllBabyDetections()
        deleteAllCategories()
        insertCategories(categories)
        if (sessions.isNotEmpty()) insertBabySessions(sessions)
        if (detections.isNotEmpty()) insertBabyDetections(detections)
        if (logs.isNotEmpty()) insertLogs(logs)
    }

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND endedAt IS NULL AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeBabySession(categoryId: Long, lockStart: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestBabySession(categoryId: Long, lockStart: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND date=:date ORDER BY startedAt DESC")
    fun babySessionsForDay(categoryId: Long, date: String): Flow<List<BabySession>>

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate ORDER BY startedAt DESC")
    fun babySessionsForMonth(categoryId: Long, fromDate: String, toDate: String): Flow<List<BabySession>>

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    fun latestSessionFlow(categoryId: Long, lockStart: Long): Flow<BabySession?>

    @Query("UPDATE baby_sessions SET movementCount=movementCount+1, timeToTenMs=CASE WHEN movementCount=9 AND timeToTenMs IS NULL THEN :durationMs ELSE timeToTenMs END WHERE id=:sessionId")
    suspend fun incrementBabySession(sessionId: Long, durationMs: Long)

    @Query("UPDATE baby_sessions SET endedAt=:endedAt, strength=:strength WHERE id=:sessionId")
    suspend fun finishBabySession(sessionId: Long, endedAt: Long, strength: String?)

    @Query("UPDATE baby_sessions SET endedAt=NULL, strength=NULL WHERE id=:sessionId AND endedAt IS NOT NULL")
    suspend fun resumeBabySession(sessionId: Long)

    @Query("SELECT * FROM baby_sessions WHERE id=:id LIMIT 1")
    suspend fun babySession(id: Long): BabySession?

    @Query("SELECT AVG(timeToTenMs) FROM baby_sessions WHERE categoryId=:categoryId AND timeToTenMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageBabyTimeToTen(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>

    @Query("SELECT * FROM counter_logs WHERE sessionId=:sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestSessionLog(sessionId: Long): CounterLog?

    @Query("DELETE FROM counter_logs WHERE id=:id")
    suspend fun deleteSessionLog(id: Long)

    @Query("UPDATE baby_sessions SET movementCount=CASE WHEN movementCount>0 THEN movementCount-1 ELSE 0 END WHERE id=:sessionId")
    suspend fun decrementBabySession(sessionId: Long)
}

data class DayTotal(val date: String, val total: Int)

@Database(entities = [CounterCategory::class, CounterLog::class, BabySession::class, BabyDetection::class], version = 5, exportSchema = false)
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
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 already contains timeToTenMs.
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS baby_detections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, timestamp INTEGER NOT NULL, magnitude REAL NOT NULL, status TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_detections_sessionId ON baby_detections(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_detections_timestamp ON baby_detections(timestamp)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'TEN_MOVEMENTS'")
            }
        }
        fun get(context: Context): CounterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CounterDatabase::class.java, "counterapp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
        }
    }
}

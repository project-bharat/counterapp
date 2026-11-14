package com.projectbharat.counterapp.data

import android.content.Context
import androidx.room.*
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
    val note: String? = null
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

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun category(id: Long): CounterCategory?

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    fun todayCount(categoryId: Long, date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun countNow(categoryId: Long, date: String): Int

    @Insert
    suspend fun addLog(log: CounterLog): Long

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestLog(categoryId: Long, date: String): CounterLog?

    @Query("DELETE FROM counter_logs WHERE id=:id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun resetDay(categoryId: Long, date: String)

    @Query("SELECT date, SUM(count) AS total FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate GROUP BY date ORDER BY date")
    fun weeklyTotals(categoryId: Long, fromDate: String, toDate: String): Flow<List<DayTotal>>

    @Query("SELECT AVG(sessionDurationMs) FROM counter_logs WHERE categoryId=:categoryId AND sessionDurationMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageSession(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>

    @Insert
    suspend fun insertBabySession(session: BabySession): Long

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND endedAt IS NULL AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeBabySession(categoryId: Long, lockStart: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestBabySession(categoryId: Long, lockStart: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND date=:date ORDER BY startedAt DESC")
    fun babySessionsForDay(categoryId: Long, date: String): Flow<List<BabySession>>

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    fun latestSessionFlow(categoryId: Long, lockStart: Long): Flow<BabySession?>

    @Query("UPDATE baby_sessions SET movementCount=movementCount+1, timeToTenMs=CASE WHEN movementCount=9 THEN :durationMs ELSE timeToTenMs END WHERE id=:sessionId")
    suspend fun incrementBabySession(sessionId: Long, durationMs: Long)

    @Query("UPDATE baby_sessions SET endedAt=:endedAt, strength=:strength WHERE id=:sessionId")
    suspend fun finishBabySession(sessionId: Long, endedAt: Long, strength: String?)

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

@Database(entities = [CounterCategory::class, CounterLog::class, BabySession::class], version = 3, exportSchema = false)
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
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN timeToTenMs INTEGER")
            }
        }
        fun get(context: Context): CounterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CounterDatabase::class.java, "counterapp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
}

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
    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY id")
    fun categories(): Flow<List<CounterCategory>>

    @Insert
    suspend fun addCategory(category: CounterCategory): Long

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun category(id: Long): CounterCategory?

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    fun todayCount(categoryId: Long, date: String): Flow<Int>

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

    @Query("SELECT AVG(sessionDurationMs) FROM counter_logs WHERE categoryId=:categoryId AND count >= 10 AND sessionDurationMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageSession(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>
}

data class DayTotal(val date: String, val total: Int)

@Database(entities = [CounterCategory::class, CounterLog::class], version = 1, exportSchema = false)
abstract class CounterDatabase : RoomDatabase() {
    abstract fun dao(): CounterDao
    companion object {
        @Volatile private var INSTANCE: CounterDatabase? = null
        fun get(context: Context): CounterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CounterDatabase::class.java, "counterapp.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

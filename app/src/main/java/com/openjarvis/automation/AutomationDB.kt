package com.openjarvis.automation

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    val command: String,

    val scheduleType: String,

    val scheduleHour: Int = 0,

    val scheduleMinute: Int = 0,

    val scheduleDayOfWeek: Int = 0,

    /*
     * For Interval this stores the interval in milliseconds.
     *
     * For Once this stores the target timestamp (atMs).
     */
    val scheduleIntervalMs: Long = 0,

    val enabled: Boolean = true,

    val lastRun: Long? = null,

    val lastResult: String? = null,

    val runCount: Int = 0
)

@Dao
interface AutomationDao {

    @Query("SELECT * FROM automations ORDER BY name")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: AutomationEntity)

    @Update
    suspend fun update(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [AutomationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AutomationDB : RoomDatabase() {

    abstract fun automationDao(): AutomationDao

    companion object {

        @Volatile
        private var INSTANCE: AutomationDB? = null

        fun getInstance(context: Context): AutomationDB {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutomationDB::class.java,
                    "automations.db"
                )
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}

class AutomationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        val id = inputData.getString("automation_id")
            ?: return Result.failure()

        return try {

            val db = AutomationDB.getInstance(applicationContext)
            val dao = db.automationDao()

            val entity = dao.getById(id)
                ?: return Result.failure()

            /*
             * Convert the database entity into the domain model.
             */
            val automation = entity.toAutomation()

            /*
             * Give the automation a chance to execute.
             *
             * Actual command execution can be connected here later.
             */
            kotlinx.coroutines.delay(2000)

            val updated = automation.copy(
                lastRun = System.currentTimeMillis(),
                lastResult = "success",
                runCount = automation.runCount + 1
            )

            dao.update(updated.toEntity())

            Result.success()

        } catch (e: Exception) {

            e.printStackTrace()

            Result.failure()
        }
    }
}

/*
 * Room Entity -> AutomationManager.Automation
 */
fun AutomationEntity.toAutomation(): AutomationManager.Automation {

    val schedule = when (scheduleType.lowercase()) {

        "daily" -> {
            AutomationManager.AutomationSchedule.Daily(
                hour = scheduleHour,
                minute = scheduleMinute
            )
        }

        "weekly" -> {
            AutomationManager.AutomationSchedule.Weekly(
                dayOfWeek = scheduleDayOfWeek,
                hour = scheduleHour,
                minute = scheduleMinute
            )
        }

        "interval" -> {
            AutomationManager.AutomationSchedule.Interval(
                intervalMs = scheduleIntervalMs
            )
        }

        "once" -> {
            AutomationManager.AutomationSchedule.Once(
                atMs = scheduleIntervalMs
            )
        }

        else -> {
            AutomationManager.AutomationSchedule.Daily(
                hour = scheduleHour,
                minute = scheduleMinute
            )
        }
    }

    return AutomationManager.Automation(
        id = id,
        name = name,
        command = command,
        schedule = schedule,
        enabled = enabled,
        lastRun = lastRun,
        lastResult = lastResult,
        runCount = runCount
    )
}

/*
 * AutomationManager.Automation -> Room Entity
 */
fun AutomationManager.Automation.toEntity(): AutomationEntity {

    return when (val currentSchedule = schedule) {

        is AutomationManager.AutomationSchedule.Daily -> {
            AutomationEntity(
                id = id,
                name = name,
                command = command,
                scheduleType = "daily",
                scheduleHour = currentSchedule.hour,
                scheduleMinute = currentSchedule.minute,
                scheduleDayOfWeek = 0,
                scheduleIntervalMs = 0,
                enabled = enabled,
                lastRun = lastRun,
                lastResult = lastResult,
                runCount = runCount
            )
        }

        is AutomationManager.AutomationSchedule.Weekly -> {
            AutomationEntity(
                id = id,
                name = name,
                command = command,
                scheduleType = "weekly",
                scheduleHour = currentSchedule.hour,
                scheduleMinute = currentSchedule.minute,
                scheduleDayOfWeek = currentSchedule.dayOfWeek,
                scheduleIntervalMs = 0,
                enabled = enabled,
                lastRun = lastRun,
                lastResult = lastResult,
                runCount = runCount
            )
        }

        is AutomationManager.AutomationSchedule.Interval -> {
            AutomationEntity(
                id = id,
                name = name,
                command = command,
                scheduleType = "interval",
                scheduleHour = 0,
                scheduleMinute = 0,
                scheduleDayOfWeek = 0,
                scheduleIntervalMs = currentSchedule.intervalMs,
                enabled = enabled,
                lastRun = lastRun,
                lastResult = lastResult,
                runCount = runCount
            )
        }

        is AutomationManager.AutomationSchedule.Once -> {
            AutomationEntity(
                id = id,
                name = name,
                command = command,
                scheduleType = "once",
                scheduleHour = 0,
                scheduleMinute = 0,
                scheduleDayOfWeek = 0,
                scheduleIntervalMs = currentSchedule.atMs,
                enabled = enabled,
                lastRun = lastRun,
                lastResult = lastResult,
                runCount = runCount
            )
        }
    }
}

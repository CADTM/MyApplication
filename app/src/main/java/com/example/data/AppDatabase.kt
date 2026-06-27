package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    // Daily Reports
    @Query("SELECT * FROM daily_reports ORDER BY date DESC")
    fun getAllDailyReportsFlow(): Flow<List<DailyReport>>

    @Query("SELECT * FROM daily_reports WHERE date = :date LIMIT 1")
    suspend fun getDailyReport(date: String): DailyReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReport(report: DailyReport)

    @Update
    suspend fun updateDailyReport(report: DailyReport)

    // Container Items
    @Query("SELECT * FROM container_items ORDER BY timestamp DESC")
    fun getAllContainersFlow(): Flow<List<ContainerItem>>

    @Query("SELECT * FROM container_items WHERE reportId = :reportId ORDER BY timestamp ASC")
    fun getContainersForReportFlow(reportId: String): Flow<List<ContainerItem>>

    @Query("SELECT * FROM container_items WHERE reportId = :reportId AND locationType = :locationType ORDER BY timestamp ASC")
    suspend fun getContainersForReportAndLocation(reportId: String, locationType: String): List<ContainerItem>

    @Query("SELECT * FROM container_items WHERE reportId = :reportId ORDER BY timestamp ASC")
    suspend fun getContainersForReport(reportId: String): List<ContainerItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContainerItem(item: ContainerItem)

    @Delete
    suspend fun deleteContainerItem(item: ContainerItem)

    @Query("DELETE FROM container_items WHERE id = :id")
    suspend fun deleteContainerItemById(id: Long)

    // Damaged Boxes
    @Query("SELECT * FROM damaged_boxes WHERE reportId = :reportId ORDER BY timestamp ASC")
    fun getDamagedBoxesFlow(reportId: String): Flow<List<DamagedBox>>

    @Query("SELECT * FROM damaged_boxes WHERE reportId = :reportId ORDER BY timestamp ASC")
    suspend fun getDamagedBoxes(reportId: String): List<DamagedBox>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDamagedBox(box: DamagedBox)

    @Delete
    suspend fun deleteDamagedBox(box: DamagedBox)

    @Query("DELETE FROM damaged_boxes WHERE id = :id")
    suspend fun deleteDamagedBoxById(id: Long)

    // Dock Conditions
    @Query("SELECT * FROM dock_conditions WHERE reportId = :reportId ORDER BY dockNumber ASC")
    fun getDockConditionsFlow(reportId: String): Flow<List<DockCondition>>

    @Query("SELECT * FROM dock_conditions WHERE reportId = :reportId ORDER BY dockNumber ASC")
    suspend fun getDockConditions(reportId: String): List<DockCondition>

    @Query("SELECT * FROM dock_conditions WHERE id = :id LIMIT 1")
    suspend fun getDockConditionById(id: String): DockCondition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDockCondition(condition: DockCondition)
}

@Database(
    entities = [DailyReport::class, ContainerItem::class, DamagedBox::class, DockCondition::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "control_andenes_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

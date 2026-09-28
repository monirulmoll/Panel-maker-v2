package com.example.data

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
import kotlinx.coroutines.flow.Flow

enum class ComponentWidgetType(val displayName: String) {
    BUTTON("Button"),
    TOGGLE("Toggle Switch"),
    SLIDER("Slider"),
    TEXT("Text Label"),
    INPUT("Text Input"),
    IMAGE("Image Box"),
    LINK("Link Opener")
}

@Entity(tableName = "studio_projects")
data class StudioProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val packageName: String = "",
    val projectName: String = "",
    val overlayTitle: String = "",
    val appLogoPath: String = "",
    val floatingLogoPath: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val canvasWidthDp: Int = 280,
    val canvasHeightDp: Int = 360,
    val canvasBgColorHex: String = "#FFFFFF",
    val defaultTargetFilePath: String = "",
    val autoFixSize: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "canvas_components")
data class CanvasComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val type: String, // ComponentWidgetType.name
    val label: String,
    val posXDp: Int = 12,
    val posYDp: Int = 12,
    val widthDp: Int = 165,
    val heightDp: Int = 36,
    val bgColorHex: String = "#FFFFFF",
    val textColorHex: String = "#0F172A",
    val customImagePath: String = "",
    val soundTrigger: String = "SYSTEM_CLICK",
    val customSoundPath: String = "",
    val offSoundTrigger: String = "SWITCH_POP",
    val offCustomSoundPath: String = "",
    val targetFilePath: String = "",
    val byteOffsetHex: String = "0x04",
    val onPayloadHex: String = "0x01",
    val offPayloadHex: String = "0x00",
    val sliderMax: Int = 100,
    val currentValue: String = "0",
    val linkUrl: String = ""
)

@Entity(tableName = "config_write_audit")
data class ConfigWriteAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parameterKey: String,
    val byteOffsetHex: String,
    val previousValue: String,
    val newValue: String,
    val durationMicros: Long,
    val crc32Hex: String,
    val targetFilePath: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Dao
interface StudioDao {
    @Query("SELECT * FROM studio_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<StudioProjectEntity>>

    @Query("SELECT * FROM studio_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): StudioProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: StudioProjectEntity): Long

    @Update
    suspend fun updateProject(project: StudioProjectEntity)

    @Query("DELETE FROM studio_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("SELECT * FROM canvas_components WHERE projectId = :projectId ORDER BY id ASC")
    fun observeComponentsForProject(projectId: Long): Flow<List<CanvasComponentEntity>>

    @Query("SELECT * FROM canvas_components WHERE projectId = :projectId ORDER BY id ASC")
    suspend fun getComponentsForProjectSync(projectId: Long): List<CanvasComponentEntity>

    @Query("SELECT COUNT(*) FROM canvas_components WHERE projectId = :projectId")
    fun observeComponentCount(projectId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponent(component: CanvasComponentEntity): Long

    @Update
    suspend fun updateComponent(component: CanvasComponentEntity)

    @Query("DELETE FROM canvas_components WHERE id = :componentId")
    suspend fun deleteComponentById(componentId: Long)

    @Query("DELETE FROM canvas_components WHERE projectId = :projectId")
    suspend fun deleteAllComponentsForProject(projectId: Long)
}

@Dao
interface ConfigAuditDao {
    @Query("SELECT * FROM config_write_audit ORDER BY timestampMillis DESC LIMIT 100")
    fun observeRecentAudits(): Flow<List<ConfigWriteAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(entry: ConfigWriteAuditEntity)

    @Query("DELETE FROM config_write_audit")
    suspend fun clearAllAudits()
}

@Database(
    entities = [
        StudioProjectEntity::class,
        CanvasComponentEntity::class,
        ConfigWriteAuditEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studioDao(): StudioDao
    abstract fun configAuditDao(): ConfigAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studio_error_workspace.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}

class ConfigAuditRepository(private val dao: ConfigAuditDao) {
    val recentAudits: Flow<List<ConfigWriteAuditEntity>> = dao.observeRecentAudits()

    suspend fun recordWrite(entry: ConfigWriteAuditEntity) {
        dao.insertAudit(entry)
    }

    suspend fun clearHistory() {
        dao.clearAllAudits()
    }
}

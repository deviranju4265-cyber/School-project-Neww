package com.example.data.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "student_local_policy")
data class CachedStudentPolicyEntity(
  @PrimaryKey val singletonId: Int = 1,
  val setupCompleted: Boolean = false,
  val studentId: String = "",
  val studentName: String = "",
  val deviceRegistrationId: String = "",
  val pairedParentUid: String = "",
  val pairedParentId: String = "",
  val pairingStatus: String = "UNPAIRED",
  val approvedAppsCsv: String = "com.android.dialer,com.android.contacts,com.android.camera2,com.google.android.youtube",
  val proposedAppsCsv: String = "com.whatsapp",
  val authorizedScheduleCsv: String = "MON-FRI|16:00|18:00|After School Normal Use;;SAT-SUN|14:00|18:00|Weekend Normal Use",
  val proposedScheduleCsv: String = "MON-FRI|16:00|18:00|After School Normal Use",
  val safeLockStatus: String = "RESTRICTED",
  val manualOverrideState: String = "NONE",
  val emergencyUnlockUntilMillis: Long = 0L,
  val lastCommandId: String = "",
  // Student-only wallpaper customization (never sent to Lock Monitor)
  val selectedWallpaperPreset: String = "AURORA_MIDNIGHT",
  val customWallpaperUri: String = "",
  val updatedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface StudentPolicyDao {
  @Query("SELECT * FROM student_local_policy WHERE singletonId = 1 LIMIT 1")
  fun observeLocalPolicy(): Flow<CachedStudentPolicyEntity?>

  @Query("SELECT * FROM student_local_policy WHERE singletonId = 1 LIMIT 1")
  suspend fun getLocalPolicyOnce(): CachedStudentPolicyEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveLocalPolicy(entity: CachedStudentPolicyEntity)
}

@Database(entities = [CachedStudentPolicyEntity::class], version = 1, exportSchema = false)
abstract class SafeLockLocalDatabase : RoomDatabase() {
  abstract fun studentPolicyDao(): StudentPolicyDao

  companion object {
    @Volatile
    private var INSTANCE: SafeLockLocalDatabase? = null

    fun getInstance(context: Context): SafeLockLocalDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          SafeLockLocalDatabase::class.java,
          "safelock_offline_policy.db"
        ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
      }
    }
  }
}

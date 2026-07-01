package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "security_incidents")
data class SecurityIncident(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val threatLevel: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val category: String, // "Malware", "Phishing", "IP Blocked", "App Audit", "Port Scan"
    val timestamp: Long = System.currentTimeMillis(),
    val autoResponded: Boolean = true,
    val responseAction: String // Action taken automatically
)

@Entity(tableName = "security_settings")
data class SecuritySetting(
    @PrimaryKey val key: String,
    val value: Boolean
)

// --- DAO ---

@Dao
interface SecurityDao {
    @Query("SELECT * FROM security_incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<SecurityIncident>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: SecurityIncident)

    @Query("DELETE FROM security_incidents WHERE id = :id")
    suspend fun deleteIncident(id: Int)

    @Query("DELETE FROM security_incidents")
    suspend fun clearAllIncidents()

    @Query("SELECT * FROM security_settings")
    fun getAllSettings(): Flow<List<SecuritySetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SecuritySetting)
}

// --- Database ---

@Database(entities = [SecurityIncident::class, SecuritySetting::class], version = 1, exportSchema = false)
abstract class SecurityDatabase : RoomDatabase() {
    abstract fun securityDao(): SecurityDao

    companion object {
        @Volatile
        private var INSTANCE: SecurityDatabase? = null

        fun getDatabase(context: Context): SecurityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecurityDatabase::class.java,
                    "oistarsian_security_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository ---

class SecurityRepository(private val securityDao: SecurityDao) {
    val allIncidents: Flow<List<SecurityIncident>> = securityDao.getAllIncidents()
    val allSettings: Flow<List<SecuritySetting>> = securityDao.getAllSettings()

    suspend fun insertIncident(incident: SecurityIncident) {
        securityDao.insertIncident(incident)
    }

    suspend fun deleteIncident(id: Int) {
        securityDao.deleteIncident(id)
    }

    suspend fun clearAllIncidents() {
        securityDao.clearAllIncidents()
    }

    suspend fun insertSetting(key: String, value: Boolean) {
        securityDao.insertSetting(SecuritySetting(key, value))
    }
}

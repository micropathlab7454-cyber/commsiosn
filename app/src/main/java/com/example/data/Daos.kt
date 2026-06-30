package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY dateMillis DESC, id DESC")
    fun getAllEntriesFlow(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries ORDER BY dateMillis DESC, id DESC")
    suspend fun getAllEntries(): List<EntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Delete
    suspend fun deleteEntry(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)

    @Query("DELETE FROM entries")
    suspend fun clearAllEntries()
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctors ORDER BY name ASC")
    fun getAllDoctorsFlow(): Flow<List<DoctorEntity>>

    @Query("SELECT * FROM doctors ORDER BY name ASC")
    suspend fun getAllDoctors(): List<DoctorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctor(doctor: DoctorEntity): Long

    @Update
    suspend fun updateDoctor(doctor: DoctorEntity)

    @Delete
    suspend fun deleteDoctor(doctor: DoctorEntity)

    @Query("DELETE FROM doctors WHERE id = :id")
    suspend fun deleteDoctorById(id: Int)

    @Query("DELETE FROM doctors")
    suspend fun clearAllDoctors()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSettingByKey(key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun getSettingFlowByKey(key: String): Flow<SettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)
}

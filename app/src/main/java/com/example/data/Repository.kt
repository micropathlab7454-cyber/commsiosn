package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LabRepository(private val db: AppDatabase) {
    private val entryDao = db.entryDao()
    private val doctorDao = db.doctorDao()
    private val settingDao = db.settingDao()

    // --- Entries ---
    val allEntriesFlow: Flow<List<EntryEntity>> = entryDao.getAllEntriesFlow()

    suspend fun getAllEntries(): List<EntryEntity> = entryDao.getAllEntries()

    suspend fun insertEntry(entry: EntryEntity): Long = entryDao.insertEntry(entry)

    suspend fun updateEntry(entry: EntryEntity) = entryDao.updateEntry(entry)

    suspend fun deleteEntry(entry: EntryEntity) = entryDao.deleteEntry(entry)

    suspend fun deleteEntryById(id: Int) = entryDao.deleteEntryById(id)

    // --- Doctors ---
    val allDoctorsFlow: Flow<List<DoctorEntity>> = doctorDao.getAllDoctorsFlow()

    suspend fun getAllDoctors(): List<DoctorEntity> = doctorDao.getAllDoctors()

    suspend fun insertDoctor(doctor: DoctorEntity): Long = doctorDao.insertDoctor(doctor)

    suspend fun updateDoctor(doctor: DoctorEntity) = doctorDao.updateDoctor(doctor)

    suspend fun deleteDoctor(doctor: DoctorEntity) = doctorDao.deleteDoctor(doctor)

    suspend fun deleteDoctorById(id: Int) = doctorDao.deleteDoctorById(id)

    // --- Settings / Auth ---
    suspend fun getUsername(): String {
        return settingDao.getSettingByKey("username")?.value ?: "admin"
    }

    suspend fun getPassword(): String {
        return settingDao.getSettingByKey("password")?.value ?: "admin"
    }

    suspend fun updateCredentials(user: String, pass: String) {
        settingDao.insertSetting(SettingEntity("username", user))
        settingDao.insertSetting(SettingEntity("password", pass))
    }

    fun getThemeFlow(): Flow<String> {
        return settingDao.getSettingFlowByKey("theme").map { it?.value ?: "light" }
    }

    suspend fun updateTheme(theme: String) {
        settingDao.insertSetting(SettingEntity("theme", theme))
    }

    // --- Backup & Restore Helper ---
    suspend fun clearAllAndRestore(entries: List<EntryEntity>, doctors: List<DoctorEntity>) {
        db.runInTransaction {
            // Since this runs in a transaction, if anything fails it rolls back.
            // Under suspend, we run blocking transactions inside the lambda safely.
            kotlinx.coroutines.runBlocking {
                entryDao.clearAllEntries()
                doctorDao.clearAllDoctors()
                for (doc in doctors) {
                    doctorDao.insertDoctor(doc)
                }
                for (entry in entries) {
                    entryDao.insertEntry(entry)
                }
            }
        }
    }
}

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateMillis: Long,
    val dateStr: String, // format: "YYYY-MM-DD"
    val patientName: String,
    val age: Int,
    val test: String,
    val amount: Double,
    val doctorAmount: Double, // Doctor Commission
    val otherAmount: Double, // Other Expenses/Fees
    val doctorId: Int?, // nullable, references DoctorEntity.id
    val doctorName: String // Selected doctor's name, or "Self" / "None" if direct
)

@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val specialization: String = "",
    val contact: String = ""
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

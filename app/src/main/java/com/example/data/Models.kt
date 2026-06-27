package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reports")
data class DailyReport(
    @PrimaryKey val date: String, // format "YYYY-MM-DD"
    val syncedToDrive: Boolean = false,
    val driveFileId: String? = null
)

@Entity(tableName = "container_items")
data class ContainerItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String, // references DailyReport.date
    val type: String, // "Caja Seca", "Intermodal", "Contenedor Marítimo"
    val locationType: String, // "Andén" or "Patio"
    val dockNumber: Int? = null, // 1 to 14 if Andén
    val dockCategory: String? = null, // "Exportación" (1-9) or "Recibos" (10-14)
    val economicNumber: String,
    val plates: String,
    val platesLocation: String,
    val status: String, // "Vacía" or "Cargada"
    val loadStatus: String? = null, // "Completa", "Falta por completar", "Falta por procesar", "En espera de salida"
    val sealCode: String? = null, // numeric code for "En espera de salida"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "damaged_boxes")
data class DamagedBox(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String, // references DailyReport.date
    val economicNumber: String,
    val type: String, // "Caja Seca", "Intermodal", "Contenedor Marítimo"
    val damageDescription: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "dock_conditions")
data class DockCondition(
    @PrimaryKey val id: String, // "reportId_dockNumber"
    val reportId: String,
    val dockNumber: Int, // 1 to 14
    val isRampGood: Boolean = true,
    val isRampLockGood: Boolean = true,
    val isDockHookGood: Boolean = true,
    val isCurtainGood: Boolean = true,
    val isShelterGood: Boolean = true,
    val isRubbersGood: Boolean = true,
    val isSafetyStoppersGood: Boolean = true,
    val isTrafficLightsGood: Boolean = true,
    val isLightsGood: Boolean = true,
    val notes: String = ""
)

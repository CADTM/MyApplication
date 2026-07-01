package com.example.data

import android.content.Context
import android.util.Log

data class PreloadedUnit(
    val economicNumber: String,
    val plates: String,
    val platesLocation: String,
    val type: String,
    val company: String
)

object PreloadedDatabase {
    private const val TAG = "PreloadedDatabase"

    val UNITS = listOf(
        PreloadedUnit("213216", "4SZ4595", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213110", "4HX6749", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213114", "4HX6967", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213135", "4J569201", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213106", "4JB4886", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213307", "4JB5109", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213126", "4JB5133", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213174", "4JU3460", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("35372", "4JY1318", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213163", "4JZ5783", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213274", "4RM6216", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213204", "4SK2328", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213206", "4SK2330", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213208", "4SK2332", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213206", "4SK330", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("2133242", "4SZ438", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213242", "4SZ4385", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213212", "4SZ4520", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213248", "4SZ4544", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213218", "4SZ4672", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213252", "4TB4133", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213298", "4TB4437", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213307", "4TE2066", "CALIFORNIA", "Caja Seca", "MEX-CAL")
    )

    suspend fun seedToHistory(repository: ReportRepository): Int {
        var insertedCount = 0
        try {
            // Fetch existing items in history to avoid repeating them
            val existingList = repository.dao.getAllContainers()
            val existingEcos = existingList.map { it.economicNumber.trim().uppercase() }.toSet()

            for (unit in UNITS) {
                if (!existingEcos.contains(unit.economicNumber.trim().uppercase())) {
                    val item = ContainerItem(
                        reportId = "HISTORIAL",
                        type = unit.type,
                        locationType = "Patio",
                        economicNumber = unit.economicNumber,
                        plates = unit.plates,
                        platesLocation = unit.platesLocation,
                        status = "Vacía",
                        company = unit.company
                    )
                    repository.addContainerItem(item)
                    insertedCount++
                }
            }
            Log.d(TAG, "Seeded $insertedCount new units to history")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding units to history", e)
        }
        return insertedCount
    }

    suspend fun loadToCurrentReport(repository: ReportRepository, reportId: String): Int {
        var insertedCount = 0
        try {
            // Fetch existing items for today's report
            val todayList = repository.dao.getContainersForReport(reportId)
            val todayEcos = todayList.map { it.economicNumber.trim().uppercase() }.toSet()

            for (unit in UNITS) {
                if (!todayEcos.contains(unit.economicNumber.trim().uppercase())) {
                    val item = ContainerItem(
                        reportId = reportId,
                        type = unit.type,
                        locationType = "Patio",
                        economicNumber = unit.economicNumber,
                        plates = unit.plates,
                        platesLocation = unit.platesLocation,
                        status = "Vacía",
                        company = unit.company
                    )
                    repository.addContainerItem(item)
                    insertedCount++
                }
            }
            Log.d(TAG, "Imported $insertedCount units to report $reportId")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading units to current report", e)
        }
        return insertedCount
    }
}

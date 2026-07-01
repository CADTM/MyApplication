package com.example.data

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
        PreloadedUnit("213110", "4HX6749", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213114", "4HX6967", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213135", "4J569201", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213106", "4JB4886", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213013", "4JB5115", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213126", "4JB5133", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213174", "4JU3460", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213163", "4JZ5783", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213274", "4RM6216", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213204", "4SK2328", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213206", "4SK2330", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213208", "4SK2332", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213242", "4SZ4385", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213212", "4SZ4520", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213227", "4SZ4532", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213248", "4SZ4544", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213216", "4SZ4595", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213218", "4SZ4672", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213252", "4TB4133", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213298", "4TB4437", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213307", "4TE2066", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213314", "4TE2067", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213111", "4TE2069", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213310", "4TE2071", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213312", "4TE2072", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213300", "4TE2073", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213301", "4TE2074", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213303", "4TE2075", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213305", "4TE2076", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213306", "4TE2077", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213304", "4TE2078", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213309", "4TE2080", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213315", "4UW2526", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213320", "4UW2593", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213321", "4UW2594", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213317", "4UW2598", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213318", "4UW2599", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213319", "4UW2600", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213324", "4UW2601", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213323", "4UW2602", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213268", "4UW2781", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213337", "4VA5604", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213339", "4VA5605", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213331", "4VA5607", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213332", "4VA5608", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213333", "4VA5609", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213334", "4VA5610", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213335", "4VA5611", "CALIFORNIA", "Caja Seca", "MEX-CAL"),
        PreloadedUnit("213034", "4WW3643", "CALIFORNIA", "Caja Seca", "MEX-CAL")
    )

    suspend fun seedToHistory(repository: ReportRepository): Int {
        var insertedCount = 0
        try {
            val existingList = repository.dao.getAllContainers()
            for (item in existingList) {
                if (item.reportId == "HISTORIAL") {
                    repository.removeContainerItemById(item.id)
                }
            }

            for (unit in UNITS) {
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
            Log.d(TAG, "Seeded $insertedCount corrected units to history (All non-MEX-CAL cleared)")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding units to history", e)
        }
        return insertedCount
    }

    suspend fun loadSnapshotJuly7(repository: ReportRepository, reportId: String) {
        try {
            // Clear current report containers first to avoid duplicates
            val currentItems = repository.dao.getContainersForReport(reportId)
            for (item in currentItems) {
                repository.removeContainerItemById(item.id)
            }

            val snapshot = listOf(
                // --- ANDENES ---
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 2, economicNumber = "JBHU318002", type = "Intermodal", status = "Vacía", receptionDate = "7-jul-26", company = "JBHUNT", plates = "P863384", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 3, economicNumber = "JBHU315856", type = "Intermodal", status = "Vacía", receptionDate = "6/28/26", company = "JBHUNT", plates = "6578LX", platesLocation = "OKLAHOMA"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 4, economicNumber = "JBHU325079", type = "Intermodal", status = "Cargada", receptionDate = "7/8/26", company = "JBHUNT", plates = "PD29879", platesLocation = "INDIANA", destination = "KY", deliveryDocument = "801746528", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 5, economicNumber = "JBHU312505", type = "Intermodal", status = "Vacía", receptionDate = "6/29/26", company = "JBHUNT", plates = "PD34898", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 6, economicNumber = "JBHU312513", type = "Intermodal", status = "Vacía", receptionDate = "6/28/26", company = "JBHUNT", plates = "PD42017", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 7, economicNumber = "213316", type = "Caja Seca", status = "Cargada", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4TE2067", platesLocation = "CALIFORNIA", destination = "MXDC", mexCalType = "Interplanta", loadStatus = "Pendiente por completar"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 8, economicNumber = "213301", type = "Caja Seca", status = "Cargada", receptionDate = "7/7/26", company = "MEX-CAL", plates = "4TE2074", platesLocation = "CALIFORNIA", destination = "DSV", deliveryDocument = "11609529", loadStatus = "Pendiente por completar", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 9, economicNumber = "213225", type = "Caja Seca", status = "Vacía", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4SZ4532", platesLocation = "CALIFORNIA", destination = "EXPORTACION", mexCalType = "Exportación"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 11, economicNumber = "213318", type = "Caja Seca", status = "Vacía", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4UW2599", platesLocation = "CALIFORNIA", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 12, economicNumber = "213310", type = "Caja Seca", status = "Vacía", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4TE2071", platesLocation = "CALIFORNIA", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 13, economicNumber = "213317", type = "Caja Seca", status = "Vacía", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4UW2598", platesLocation = "CALIFORNIA", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 14, economicNumber = "213319", type = "Caja Seca", status = "Vacía", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4UW2600", platesLocation = "CALIFORNIA", mexCalType = "Interplanta"),

                // --- PATIO ---
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "TGBU5236762", type = "Contenedor Marítimo", status = "Cargada", receptionDate = "7/8/26", company = "CONTENEDOR", plates = "3918377", platesLocation = "MAINE", destination = "EXPORTACION", sealCode = "295-7630", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU350706", type = "Intermodal", status = "Cargada", receptionDate = "7/8/26", company = "JBHUNT", plates = "1500LX", platesLocation = "OKLAHOMA", destination = "KY", deliveryDocument = "85122080", sealCode = "295-7631", loadStatus = "En espera de salida", processedDate = "7-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU231169", type = "Intermodal", status = "Cargada", receptionDate = "7/2/26", company = "JBHUNT", plates = "6445JJ", platesLocation = "OKLAHOMA", destination = "KY", deliveryDocument = "85122079", sealCode = "295-7617", loadStatus = "En espera de salida", processedDate = "3-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU271528", type = "Intermodal", status = "Cargada", receptionDate = "7/6/26", company = "JBHUNT", plates = "1280LX", platesLocation = "OKLAHOMA", sealCode = "295-7621", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "HMMU4536053", type = "Contenedor Marítimo", status = "Cargada", receptionDate = "7/2/26", company = "CONTENEDOR", plates = "5065423", platesLocation = "MAINE", sealCode = "2557-624", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "HMMU4536624", type = "Contenedor Marítimo", status = "Cargada", receptionDate = "7/8/26", company = "CONTENEDOR", plates = "V272367", platesLocation = "TENNESSEE", destination = "FRANCIA", deliveryDocument = "801718810", sealCode = "255-7625", loadStatus = "En espera de salida", processedDate = "6-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "HMMU4523164", type = "Contenedor Marítimo", status = "Cargada", receptionDate = "7/8/26", company = "CONTENEDOR", plates = "4TE2443", platesLocation = "CALIFORNIA", destination = "FRANCIA", deliveryDocument = "801739225", sealCode = "255-7627", loadStatus = "En espera de salida", processedDate = "6-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "HDMU2797148", type = "Contenedor Marítimo", status = "Cargada", receptionDate = "7/7/26", company = "CONTENEDOR", plates = "3828057", platesLocation = "MAINE", destination = "GERMANY", deliveryDocument = "85122081", sealCode = "255-7628", loadStatus = "En espera de salida", processedDate = "3-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "213335", type = "Caja Seca", status = "Cargada", receptionDate = "7/8/26", company = "MEX-CAL", plates = "4VA5611", platesLocation = "CALIFORNIA", destination = "PERRIS", deliveryDocument = "85122267", sealCode = "255-7619", loadStatus = "En espera de salida", processedDate = "3-jul-26", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "213034", type = "Caja Seca", status = "Cargada", receptionDate = "7/7/26", company = "MEX-CAL", plates = "4WW3643", platesLocation = "CALIFORNIA", destination = "LOS NZ", deliveryDocument = "111565404", sealCode = "2557-623", loadStatus = "En espera de salida", processedDate = "7-jul-26", mexCalType = "Exportación")
            )

            for (item in snapshot) {
                repository.addContainerItem(item)
            }
            Log.d(TAG, "Loaded July 7 snapshot to report $reportId")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading snapshot", e)
        }
    }

    suspend fun loadSnapshotJuly3(repository: ReportRepository, reportId: String) {
        try {
            // Clear current report containers first to avoid duplicates
            val currentItems = repository.dao.getContainersForReport(reportId)
            for (item in currentItems) {
                repository.removeContainerItemById(item.id)
            }

            val snapshot = listOf(
                // --- ANDENES ---
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 1, economicNumber = "JBHU312505", type = "Intermodal", status = "Vacía", receptionDate = "29-jun-26", company = "JBHUNT", plates = "", platesLocation = ""),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 2, economicNumber = "JBHU262537", type = "Intermodal", status = "Cargada", receptionDate = "30-jun-26", company = "JBHUNT", plates = "2516BP", platesLocation = "OKLAHOMA", destination = "KY", deliveryDocument = "8017259793", sealCode = "255-7612", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 3, economicNumber = "JBHU255294", type = "Intermodal", status = "Cargada", receptionDate = "03-jul-26", company = "JBHUNT", plates = "P780171", platesLocation = "INDIANA", destination = "KY", deliveryDocument = "80122909", sealCode = "255-7614", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 4, economicNumber = "JBHU247380", type = "Intermodal", status = "Cargada", receptionDate = "27-jun-26", company = "JBHUNT", plates = "5493LX", platesLocation = "OKLAHOMA", destination = "KY", sealCode = "255-7613", loadStatus = "En espera de salida"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 5, economicNumber = "213334", type = "Caja Seca", status = "Cargada", receptionDate = "01-jul-26", company = "MEX-CAL", plates = "4UW2601", platesLocation = "CALIFORNIA", destination = "PERRIS", deliveryDocument = "801735548", sealCode = "255-7610", loadStatus = "Falta por completar", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 6, economicNumber = "HMMU4538166", type = "Contenedor Marítimo", status = "Vacía", receptionDate = "02-jul-26", company = "CONTENEDOR", plates = "4TR4662", platesLocation = "CALIFORNIA"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 7, economicNumber = "JBHU271528", type = "Intermodal", status = "Cargada", receptionDate = "30-jun-26", company = "JBHUNT", plates = "1280LX", platesLocation = "OKLAHOMA", destination = "KY", deliveryDocument = "801736587", loadStatus = "Falta por completar"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 8, economicNumber = "JBHU350706", type = "Intermodal", status = "Vacía", receptionDate = "02-jul-26", company = "JBHUNT", plates = "1382LX", platesLocation = "OKLAHOMA"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 9, economicNumber = "213335", type = "Caja Seca", status = "Cargada", receptionDate = "03-jul-26", company = "MEX-CAL", plates = "4VA5611", platesLocation = "CALIFORNIA", destination = "PERRIS", loadStatus = "Falta por completar", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 11, economicNumber = "213317", type = "Caja Seca", status = "Cargada", receptionDate = "03-jul-26", company = "MEX-CAL", plates = "4UW2598", platesLocation = "CALIFORNIA", loadStatus = "Falta por completar", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 12, economicNumber = "213309", type = "Caja Seca", status = "Cargada", receptionDate = "03-jul-26", company = "MEX-CAL", plates = "4TE2080", platesLocation = "CALIFORNIA", loadStatus = "Falta por completar", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 13, economicNumber = "213310", type = "Caja Seca", status = "Cargada", receptionDate = "03-jul-26", company = "MEX-CAL", plates = "4TE2071", platesLocation = "CALIFORNIA", loadStatus = "Falta por completar", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Andén", dockNumber = 14, economicNumber = "213318", type = "Caja Seca", status = "Cargada", receptionDate = "03-jul-26", company = "MEX-CAL", plates = "4SZ4595", platesLocation = "CALIFORNIA", loadStatus = "Falta por completar", mexCalType = "Interplanta"),

                // --- PATIO ---
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU244789", type = "Intermodal", status = "Cargada", receptionDate = "01-jul-26", company = "JBHUNT", plates = "2748FZ", platesLocation = "OKLAHOMA", destination = "KY", deliveryDocument = "801726751", sealCode = "255-7632", loadStatus = "En espera de salida", processedDate = "02-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU354842", type = "Intermodal", status = "Cargada", receptionDate = "25-jun-26", company = "JBHUNT", plates = "5509FL", platesLocation = "OKLAHOMA", destination = "KY", deliveryDocument = "801725596", sealCode = "255-7639", loadStatus = "En espera de salida", processedDate = "02-jul-26"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "35381", type = "Caja Seca", status = "Cargada", receptionDate = "28-jun-26", company = "MEX-CAL", plates = "4JY1431", platesLocation = "CALIFORNIA", destination = "DSV", deliveryDocument = "801711832", sealCode = "255-7654", loadStatus = "En espera de salida", processedDate = "02-jul-26", mexCalType = "Exportación"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU315856", type = "Intermodal", status = "Cargada", receptionDate = "30-jun-26", company = "JBHUNT", plates = "5678LX", platesLocation = "OKLAHOMA", destination = "ALEMANIA", deliveryDocument = "801718802", sealCode = "255-6673", loadStatus = "En espera de salida", processedDate = "02-jul-26", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "213337_C", type = "Caja Seca", status = "Cargada", receptionDate = "26-jun-26", company = "MEX-CAL", plates = "4TE2078", platesLocation = "CALIFORNIA", destination = "PERRIS", deliveryDocument = "80122150", sealCode = "567606", loadStatus = "En espera de salida", processedDate = "02-jul-26", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "213167", type = "Caja Seca", status = "Cargada", receptionDate = "03-jul-26", company = "MEX-CAL", plates = "4JB5109", platesLocation = "CALIFORNIA", destination = "PERRIS", deliveryDocument = "801729733", sealCode = "255-7655", loadStatus = "En espera de salida", processedDate = "02-jul-26", mexCalType = "Exportación"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU266261", type = "Intermodal", status = "Cargada", receptionDate = "30-jun-26", company = "JBHUNT", plates = "4661JN", platesLocation = "OKLAHOMA", destination = "CANADA", sealCode = "255-6672", loadStatus = "Falta por completar"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "213301", type = "Caja Seca", status = "Vacía", receptionDate = "02-jul-26", company = "MEX-CAL", plates = "4TE2074", platesLocation = "CALIFORNIA", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "213337_V", type = "Caja Seca", status = "Vacía", receptionDate = "01-jul-26", company = "MEX-CAL", plates = "4VA5604", platesLocation = "CALIFORNIA", mexCalType = "Interplanta"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU253832", type = "Intermodal", status = "Vacía", receptionDate = "23-jun-26", company = "JBHUNT", plates = "P237861", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU332257", type = "Intermodal", status = "Vacía", receptionDate = "27-jun-26", company = "JBHUNT", plates = "P750711", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU312513", type = "Intermodal", status = "Vacía", receptionDate = "29-jun-26", company = "JBHUNT", plates = "P242017", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU295885", type = "Intermodal", status = "Vacía", receptionDate = "29-jun-26", company = "JBHUNT", plates = "PC17053", platesLocation = "INDIANA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU325079", type = "Intermodal", status = "Vacía", receptionDate = "29-jun-26", company = "JBHUNT", plates = "PD233879", platesLocation = "OKLAHOMA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "JBHU331169", type = "Intermodal", status = "Vacía", receptionDate = "02-jul-26", company = "JBHUNT", plates = "5445JJ", platesLocation = "OKLAHOMA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "KOCU", type = "Contenedor Marítimo", status = "Vacía", receptionDate = "03-jul-26", company = "CONTENEDOR", plates = "4TE1992", platesLocation = "CALIFORNIA"),
                ContainerItem(reportId = reportId, locationType = "Patio", economicNumber = "HMMU4536162", type = "Contenedor Marítimo", status = "Vacía", receptionDate = "02-jul-26", company = "CONTENEDOR", plates = "5086423", platesLocation = "MAINE")
            )

            for (item in snapshot) {
                val cleanItem = if (item.economicNumber.contains("_")) {
                    item.copy(economicNumber = item.economicNumber.split("_")[0])
                } else item
                repository.addContainerItem(cleanItem)
            }
            Log.d(TAG, "Loaded July 3 snapshot to report $reportId")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading snapshot", e)
        }
    }

    suspend fun loadToCurrentReport(repository: ReportRepository, reportId: String): Int {
        var insertedCount = 0
        try {
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

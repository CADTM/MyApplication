package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportRepository(private val dao: ReportDao) {

    fun getAllReportsFlow(): Flow<List<DailyReport>> = dao.getAllDailyReportsFlow()

    fun getAllContainersFlow(): Flow<List<ContainerItem>> = dao.getAllContainersFlow()

    fun getContainersFlow(reportId: String): Flow<List<ContainerItem>> =
        dao.getContainersForReportFlow(reportId)

    fun getDamagedBoxesFlow(reportId: String): Flow<List<DamagedBox>> =
        dao.getDamagedBoxesFlow(reportId)

    fun getDockConditionsFlow(reportId: String): Flow<List<DockCondition>> =
        dao.getDockConditionsFlow(reportId)

    suspend fun getOrCreateReport(date: String): DailyReport {
        val existing = dao.getDailyReport(date)
        if (existing != null) return existing

        val newReport = DailyReport(date = date)
        dao.insertDailyReport(newReport)

        // Pre-populate all 14 docks as GOOD condition to make data entry easy
        for (i in 1..14) {
            val cond = DockCondition(
                id = "${date}_$i",
                reportId = date,
                dockNumber = i,
                isRampGood = true,
                isRampLockGood = true,
                isDockHookGood = true,
                isCurtainGood = true,
                isShelterGood = true,
                isRubbersGood = true,
                isSafetyStoppersGood = true,
                isTrafficLightsGood = true,
                isLightsGood = true,
                notes = ""
            )
            dao.insertDockCondition(cond)
        }
        return newReport
    }

    suspend fun updateReport(report: DailyReport) = dao.updateDailyReport(report)

    suspend fun addContainerItem(item: ContainerItem) = dao.insertContainerItem(item)

    suspend fun removeContainerItem(item: ContainerItem) = dao.deleteContainerItem(item)

    suspend fun removeContainerItemById(id: Long) = dao.deleteContainerItemById(id)

    suspend fun addDamagedBox(box: DamagedBox) = dao.insertDamagedBox(box)

    suspend fun removeDamagedBoxById(id: Long) = dao.deleteDamagedBoxById(id)

    suspend fun updateDockCondition(cond: DockCondition) = dao.insertDockCondition(cond)

    suspend fun generateCsvString(reportId: String): String {
        val containers = dao.getContainersForReport(reportId)
        val dockConditions = dao.getDockConditions(reportId)
        val damagedBoxes = dao.getDamagedBoxes(reportId)

        val csv = StringBuilder()

        // Section 1: Header
        csv.append("REPORTE DIARIO DE CONTROL DE ANDENES E INVENTARIO\n")
        csv.append("Fecha:,$reportId\n")
        csv.append("Generado:,,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        // Section 2: Containers
        csv.append("1. INVENTARIO DE CAJAS SECAS, INTERMODALES Y MARITIMOS (ANDENES Y PATIO)\n")
        csv.append("Ubicacion,Anden,Categoria,Tipo,No. Economico,Placas,Lugar Placas,Compania,Medida Contenedor,Estado,Progreso de Carga,Codigo Sello,Destino,Delivery o Documento,Fecha Procesado\n")
        for (c in containers) {
            val location = c.locationType
            val dockNum = c.dockNumber?.toString() ?: ""
            val category = c.dockCategory ?: ""
            val type = c.type
            val eco = c.economicNumber.replace(",", ";").replace("\n", " ")
            val plates = c.plates.replace(",", ";").replace("\n", " ")
            val platesLoc = c.platesLocation.replace(",", ";").replace("\n", " ")
            val comp = c.company
            val size = c.containerSize ?: ""
            val status = c.status
            val loadStatus = c.loadStatus ?: ""
            val seal = c.sealCode ?: ""
            val dest = c.destination ?: ""
            val doc = c.deliveryDocument ?: ""
            val procDate = c.processedDate ?: ""

            csv.append("$location,$dockNum,$category,$type,$eco,$plates,$platesLoc,$comp,$size,$status,$loadStatus,$seal,$dest,$doc,$procDate\n")
        }
        csv.append("\n")

        // Section 3: Dock Conditions
        csv.append("2. REPORTE DE CONDICION DE ANDENES (1 AL 14)\n")
        csv.append("Anden,Tipo Anden,Fuera de Servicio,Rampa,Seguro de Rampa,Gancho,Cortina,Shelter,Gomas,Topes Seguridad,Semaforo,Luces,Notas\n")
        for (d in dockConditions) {
            val typeStr = if (d.dockNumber in 1..9) "Exportacion (1-9)" else "Recibos/FG (10-14)"
            val oos = if (d.isOutOfService) "SI" else "NO"
            val rampa = if (d.isRampGood) "BIEN" else "MAL"
            val seguro = if (d.isRampLockGood) "BIEN" else "MAL"
            val gancho = if (d.isDockHookGood) "BIEN" else "MAL"
            val cortina = if (d.isCurtainGood) "BIEN" else "MAL"
            val shelter = if (d.isShelterGood) "BIEN" else "MAL"
            val gomas = if (d.isRubbersGood) "BIEN" else "MAL"
            val topes = if (d.isSafetyStoppersGood) "BIEN" else "MAL"
            val semaforo = if (d.isTrafficLightsGood) "BIEN" else "MAL"
            val luces = if (d.isLightsGood) "BIEN" else "MAL"
            val notes = d.notes.replace(",", ";").replace("\n", " ")

            csv.append("${d.dockNumber},$typeStr,$oos,$rampa,$seguro,$gancho,$cortina,$shelter,$gomas,$topes,$semaforo,$luces,$notes\n")
        }
        csv.append("\n")

        // Section 4: Damaged Boxes
        csv.append("3. REPORTE DE CAJAS Y CONTENEDORES DAÑADOS\n")
        csv.append("No. Economico,Tipo de Unidad,Descripcion del Danio,Fecha Reporte\n")
        for (b in damagedBoxes) {
            val eco = b.economicNumber.replace(",", ";").replace("\n", " ")
            val type = b.type
            val desc = b.damageDescription.replace(",", ";").replace("\n", " ")
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(b.timestamp))

            csv.append("$eco,$type,$desc,$dateStr\n")
        }

        return csv.toString()
    }

    suspend fun saveCsvToFile(context: Context, reportId: String, content: String): File {
        return withContext(Dispatchers.IO) {
            val fileName = "Reporte_Andenes_${reportId}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            file
        }
    }

    suspend fun generateContainersCsvString(reportId: String): String {
        val containers = dao.getContainersForReport(reportId)
        val csv = StringBuilder()

        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateOutFormatter = SimpleDateFormat("d-MMM-yy", Locale("es", "ES"))
        val formattedReportDate = try {
            val d = dateParser.parse(reportId)
            if (d != null) dateOutFormatter.format(d) else reportId
        } catch (e: Exception) {
            reportId
        }

        fun formatTs(ts: Long): String {
            return try {
                dateOutFormatter.format(Date(ts))
            } catch (e: Exception) {
                ""
            }
        }

        // --- SECCIÓN 1: INVENTARIO DE CAJAS 2026 (Andenes 1 al 14) ---
        csv.append("INVENTARIO DE CAJAS 2026\n")
        csv.append("Anden,Fecha De Entrada,Disposicion,No. Economico,Tipo Material,Placas,Origen de Placas,Compañia,Operacion,Destino,Fecha de Procesado,Deliverys,Numero de Sello colocado,Comentarios\n")
        
        // Group andenes
        val andenesMap = containers.filter { it.locationType == "Andén" && it.dockNumber != null }
            .associateBy { it.dockNumber!! }

        for (i in 1..14) {
            val c = andenesMap[i]
            if (c != null) {
                val dateIn = formatTs(c.timestamp)
                val status = c.status.uppercase()
                val eco = c.economicNumber.replace(",", ";").replace("\n", " ")
                val tipoMat = "FG"
                val plates = c.plates.replace(",", ";").replace("\n", " ")
                val platesLoc = c.platesLocation.replace(",", ";").replace("\n", " ")
                val comp = c.company.replace(",", ";").replace("\n", " ")
                
                val operacion = if (comp.uppercase().contains("MEX-CAL")) {
                    if (c.destination?.uppercase() == "EXPORTACION") "EXPORTACION" else "INTERPLANTA"
                } else {
                    "EXPORTACION"
                }

                val dest = c.destination?.replace(",", ";") ?: ""
                
                // processedDate should be empty for "Falta por completar" or "Falta por procesar"
                val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                    ""
                } else {
                    c.processedDate ?: ""
                }

                val doc = c.deliveryDocument?.replace(",", ";") ?: ""
                val seal = c.sealCode ?: ""
                
                val comments = when (c.loadStatus) {
                    "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                    "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                    "En espera de salida" -> "EN ESPERA DE SALIDA"
                    else -> "-"
                }

                csv.append("$i,$dateIn,$status,$eco,$tipoMat,$plates,$platesLoc,$comp,$operacion,$dest,$procDate,$doc,$seal,$comments\n")
            } else {
                // empty row
                csv.append("$i,-,-,-,-,-,-,-,-,-,-,-,-,-\n")
            }
        }
        
        csv.append("\n\n")

        // --- SECCIÓN 2: YARDA DE MXDC( PATIO) ---
        csv.append("YARDA DE MXDC( PATIO)\n")
        csv.append("#,Fecha,Disposicion,No. Economico,Tipo,Placas,Origen de Placas,Compañia,Operacion,DESTINO,Fecha de Procesado,Deliverys,Numero de Sello colocado,comentarios\n")
        
        val patioContainers = containers.filter { it.locationType == "Patio" }
        for (c in patioContainers) {
            val dateIn = formatTs(c.timestamp)
            val status = c.status.uppercase()
            val eco = c.economicNumber.replace(",", ";").replace("\n", " ")
            val tipoMat = "FG"
            val plates = c.plates.replace(",", ";").replace("\n", " ")
            val platesLoc = c.platesLocation.replace(",", ";").replace("\n", " ")
            val comp = c.company.replace(",", ";").replace("\n", " ")

            val operacion = if (comp.uppercase().contains("MEX-CAL")) {
                if (c.destination?.uppercase() == "EXPORTACION") "EXPORTACION" else "INTERPLANTA"
            } else {
                "EXPORTACION"
            }

            val dest = c.destination?.replace(",", ";") ?: ""
            
            val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                ""
            } else {
                c.processedDate ?: ""
            }

            val doc = c.deliveryDocument?.replace(",", ";") ?: ""
            val seal = c.sealCode ?: ""

            val comments = when (c.loadStatus) {
                "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                "En espera de salida" -> "EN ESPERA DE SALIDA"
                else -> "-"
            }

            csv.append(",$dateIn,$status,$eco,$tipoMat,$plates,$platesLoc,$comp,$operacion,$dest,$procDate,$doc,$seal,$comments\n")
        }

        csv.append("\n\n")

        // --- SECCIÓN 3: RESUMEN / TOTALES ---
        csv.append("Fecha de conteo,Descripcion,Cantidad\n")
        
        val countExportacion = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            !it.company.uppercase().contains("MEX-CAL")
        }
        val countAlmacenaje = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            it.company.uppercase().contains("ALMACENAJE")
        }
        val countFgRecibos = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            it.locationType == "Andén" &&
            (it.dockNumber ?: 0) in 10..14
        }
        val countIntermodalVacio = containers.count {
            it.type == "Intermodal" &&
            it.status.uppercase().startsWith("VAC")
        }
        val countMexCalMxdcVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            it.destination?.uppercase() == "MXDC"
        }
        val countMexCalExportVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            it.destination?.uppercase() != "MXDC" &&
            it.destination?.uppercase() != "PERRIS"
        }
        val countContenedorVacio = containers.count {
            it.type == "Contenedor Marítimo" &&
            it.status.uppercase().startsWith("VAC")
        }
        val countPendientes = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            (it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar")
        }

        csv.append("$formattedReportDate,Exportacion,$countExportacion\n")
        csv.append(",Almacenaje,$countAlmacenaje\n")
        csv.append(",FG Recibos,$countFgRecibos\n")
        csv.append(",Intermodal vacio,$countIntermodalVacio\n")
        csv.append(",Mex - cal mxdc vacio,$countMexCalMxdcVacio\n")
        csv.append(",Mex - cal export vacio,$countMexCalExportVacio\n")
        csv.append(",Contenedor vacio,$countContenedorVacio\n")
        csv.append(",Pendientes,$countPendientes\n")

        return csv.toString()
    }

    suspend fun generateDockConditionsCsvString(reportId: String): String {
        val dockConditions = dao.getDockConditions(reportId)
        val csv = StringBuilder()
        csv.append("REPORTE DIARIO DE CONDICION DE ANDENES (1 AL 14)\n")
        csv.append("Fecha:,$reportId\n")
        csv.append("Generado:,,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
        csv.append("Anden,Tipo Anden,Fuera de Servicio,Rampa,Seguro de Rampa,Gancho,Cortina,Shelter,Gomas,Topes Seguridad,Semaforo,Luces,Notas\n")
        for (d in dockConditions) {
            val typeStr = if (d.dockNumber in 1..9) "Exportacion (1-9)" else "Recibos/FG (10-14)"
            val oos = if (d.isOutOfService) "SI" else "NO"
            val rampa = if (d.isRampGood) "BIEN" else "MAL"
            val seguro = if (d.isRampLockGood) "BIEN" else "MAL"
            val gancho = if (d.isDockHookGood) "BIEN" else "MAL"
            val cortina = if (d.isCurtainGood) "BIEN" else "MAL"
            val shelter = if (d.isShelterGood) "BIEN" else "MAL"
            val gomas = if (d.isRubbersGood) "BIEN" else "MAL"
            val topes = if (d.isSafetyStoppersGood) "BIEN" else "MAL"
            val semaforo = if (d.isTrafficLightsGood) "BIEN" else "MAL"
            val luces = if (d.isLightsGood) "BIEN" else "MAL"
            val notes = d.notes.replace(",", ";").replace("\n", " ")

            csv.append("${d.dockNumber},$typeStr,$oos,$rampa,$seguro,$gancho,$cortina,$shelter,$gomas,$topes,$semaforo,$luces,$notes\n")
        }
        return csv.toString()
    }

    suspend fun generateDamagedBoxesCsvString(reportId: String): String {
        val damagedBoxes = dao.getDamagedBoxes(reportId)
        val csv = StringBuilder()
        csv.append("REPORTE DIARIO DE CAJAS Y CONTENEDORES DAÑADOS\n")
        csv.append("Fecha:,$reportId\n")
        csv.append("Generado:,,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
        csv.append("No. Economico,Tipo de Unidad,Descripcion del Danio,Fecha Reporte\n")
        for (b in damagedBoxes) {
            val eco = b.economicNumber.replace(",", ";").replace("\n", " ")
            val type = b.type
            val desc = b.damageDescription.replace(",", ";").replace("\n", " ")
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(b.timestamp))

            csv.append("$eco,$type,$desc,$dateStr\n")
        }
        return csv.toString()
    }

    suspend fun saveCsvToFileWithPrefix(context: Context, reportId: String, content: String, prefix: String): File {
        return withContext(Dispatchers.IO) {
            val fileName = "${prefix}_${reportId}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            file
        }
    }
}

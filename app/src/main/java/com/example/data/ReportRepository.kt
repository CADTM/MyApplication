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

class ReportRepository(internal val dao: ReportDao) {

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

        // Find the previous report to copy dock conditions
        val previousReport = dao.getAllContainers().asSequence().map { it.reportId }
            .filter { it != date && it != "HISTORIAL" }
            .distinct().maxOrNull()

        // Pre-populate all 14 docks
        for (i in 1..14) {
            val prevCond = if (previousReport != null) {
                dao.getDockConditionById("${previousReport}_$i")
            } else null

            val cond = DockCondition(
                id = "${date}_$i",
                reportId = date,
                dockNumber = i,
                isRampGood = prevCond?.isRampGood ?: true,
                isRampLockGood = prevCond?.isRampLockGood ?: true,
                isDockHookGood = prevCond?.isDockHookGood ?: true,
                isCurtainGood = prevCond?.isCurtainGood ?: true,
                isShelterGood = prevCond?.isShelterGood ?: true,
                isRubbersGood = prevCond?.isRubbersGood ?: true,
                isSafetyStoppersGood = prevCond?.isSafetyStoppersGood ?: true,
                isTrafficLightsGood = prevCond?.isTrafficLightsGood ?: true,
                isLightsGood = prevCond?.isLightsGood ?: true,
                isEmpaquesGood = prevCond?.isEmpaquesGood ?: true,
                isGatoHidraulicoGood = prevCond?.isGatoHidraulicoGood ?: true,
                isCadenaGood = prevCond?.isCadenaGood ?: true,
                notes = prevCond?.notes ?: "",
                isOutOfService = prevCond?.isOutOfService ?: false
            )
            dao.insertDockCondition(cond)
        }
        return newReport
    }

    suspend fun updateReport(report: DailyReport) = dao.updateDailyReport(report)

    suspend fun addContainerItem(item: ContainerItem): Long {
        val generatedId = dao.insertContainerItem(item)
        val itemToSync = if (item.id == 0L) item.copy(id = generatedId) else item
        FirestoreSync.syncContainer(itemToSync)
        return generatedId
    }

    suspend fun removeContainerItem(item: ContainerItem) {
        dao.deleteContainerItem(item)
        FirestoreSync.deleteContainer(item.id)
    }

    suspend fun removeContainerItemById(id: Long) {
        dao.deleteContainerItemById(id)
        FirestoreSync.deleteContainer(id)
    }

    suspend fun addDamagedBox(box: DamagedBox): Long {
        val generatedId = dao.insertDamagedBox(box)
        val boxToSync = if (box.id == 0L) box.copy(id = generatedId) else box
        FirestoreSync.syncDamagedBox(boxToSync)
        return generatedId
    }

    suspend fun removeDamagedBoxById(id: Long) {
        dao.deleteDamagedBoxById(id)
        FirestoreSync.deleteDamagedBox(id)
    }

    suspend fun updateDockCondition(cond: DockCondition) = dao.insertDockCondition(cond)

    suspend fun importUnitsFromCsv(csvContent: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val lines = csvContent.split(Regex("\r?\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return@withContext 0

            // Detect header or just skip first line if it looks like one
            val firstLine = lines[0].lowercase()
            val hasHeader = firstLine.contains("economico") || firstLine.contains("eco") || firstLine.contains("placa")

            lines.drop(if (hasHeader) 1 else 0).forEach { line ->
                // Split by comma, handling potential quotes
                val parts = line.split(",").map { it.trim().removeSurrounding("\"") }
                if (parts.isNotEmpty()) {
                    val eco = parts[0]
                    if (eco.isNotBlank()) {
                        val plates = if (parts.size > 1) parts[1] else ""
                        val loc = if (parts.size > 2) parts[2] else ""
                        val type = if (parts.size > 3) parts[3] else "Caja Seca"
                        val company = if (parts.size > 4) parts[4] else ""
                        
                        // If company is empty, try to infer it from type like in the UI
                        val finalCompany = when {
                            company.isNotBlank() -> company
                            type.contains("Caja", true) || type.contains("Seca", true) -> "MEX-CAL"
                            type.contains("Intermodal", true) -> "JBHUNT"
                            type.contains("Contenedor", true) || type.contains("Marítimo", true) -> "CONTENEDOR"
                            else -> "MEX-CAL"
                        }

                        val item = ContainerItem(
                            reportId = "HISTORIAL",
                            type = type.ifBlank { "Caja Seca" },
                            locationType = "Patio",
                            economicNumber = eco.uppercase(),
                            plates = plates.uppercase(),
                            platesLocation = loc.uppercase(),
                            status = "Vacía",
                            company = finalCompany.uppercase()
                        )
                        dao.insertContainerItem(item)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReportRepository", "Error importing CSV", e)
        }
        count
    }

    private fun getMexCalOperation(item: ContainerItem): String {
        val comp = item.company.uppercase()
        val dest = item.destination?.uppercase() ?: ""
        
        if (comp.contains("MEX-CAL")) {
            val type = item.mexCalType?.uppercase() ?: ""
            if (item.loadStatus == "En espera de descarga" || type.contains("INTER") || dest == "MXDC") return "INTERPLANTA"
            if (type.contains("EXPORT") || item.economicNumber.startsWith("2131") || item.economicNumber.startsWith("2132")) return "EXPORTACION"
        }
        
        return "EXPORTACION" // Default
    }

    suspend fun generateCsvString(reportId: String): String {
        val containers = dao.getContainersForReport(reportId)
        val dockConditions = dao.getDockConditions(reportId)
        val damagedBoxes = dao.getDamagedBoxes(reportId)

        val html = StringBuilder()
        html.append('\uFEFF') // UTF-8 BOM for Excel compatibility

        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateOutFormatter = SimpleDateFormat("d-MMM-yy", Locale.forLanguageTag("es-ES"))
        val formattedReportDate = try {
            val d = dateParser.parse(reportId)
            if (d != null) dateOutFormatter.format(d) else reportId
        } catch (_: Exception) {
            reportId
        }

        fun formatTs(ts: Long): String {
            return try {
                dateOutFormatter.format(Date(ts))
            } catch (_: Exception) {
                ""
            }
        }

        fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        // --- CALCULATE SUMMARY TOTALS ---
        val countAlmacenaje = containers.count {
            it.isStorageOnly || it.loadStatus == "Almacenaje" || it.company.uppercase().contains("ALMACENAJE")
        }
        val countFgRecibos = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de descarga"
        }
        val countExportacion = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            !it.isStorageOnly &&
            !it.company.uppercase().contains("ALMACENAJE") &&
            (!it.company.uppercase().contains("MEX-CAL") || !it.destination.isNullOrBlank())
        }
        val countPendientes = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            (it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar" || it.loadStatus == "Pendiente por completar" || it.loadStatus == "Pendiente por procesar")
        }
        val countIntermodalVacio = containers.count {
            it.type == "Intermodal" &&
            it.status.uppercase().startsWith("VAC")
        }
        val countMexCalExportVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            (it.economicNumber.startsWith("2132") || 
             it.economicNumber.startsWith("2131") || 
             it.economicNumber.startsWith("2130"))
        }
        val countMexCalMxdcVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            it.economicNumber.startsWith("2133")
        }
        val countContenedorVacio = containers.count {
            it.company.uppercase().contains("CONTENEDOR") &&
            it.status.uppercase().startsWith("VAC")
        }

        html.append("<html>\n")
        html.append("<head>\n")
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n")
        html.append("<!--[if gte mso 9]>\n")
        html.append("<xml>\n")
        html.append(" <x:ExcelWorkbook>\n")
        html.append("  <x:ExcelWorksheets>\n")
        html.append("   <x:ExcelWorksheet>\n")
        html.append("    <x:Name>Reporte Completo</x:Name>\n")
        html.append("    <x:WorksheetOptions>\n")
        html.append("     <x:DisplayGridlines/>\n")
        html.append("    </x:WorksheetOptions>\n")
        html.append("   </x:ExcelWorksheet>\n")
        html.append("  </x:ExcelWorksheets>\n")
        html.append(" </x:ExcelWorkbook>\n")
        html.append("</xml>\n")
        html.append("<![endif]-->\n")
        html.append("<style>\n")
        html.append("  body { background-color: #FFFFFF; padding: 20px; }\n")
        html.append("  table { border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12pt; border: 1px solid #000000; margin-bottom: 30px; background-color: #FFFFFF; width: 100%; }\n")
        html.append("  th { border: 1px solid #000000; padding: 5px; font-weight: bold; text-align: center; vertical-align: middle; background-color: #D9D9D9; color: #000000; text-transform: uppercase; }\n")
        html.append("  td { border: 1px solid #000000; padding: 5px; text-align: center; vertical-align: middle; color: #000000; }\n")
        html.append("  .title-main { font-size: 16pt; font-weight: bold; border: 1px solid #000000; text-align: center; height: 40px; background-color: #FFFFFF; color: #000000; text-transform: uppercase; }\n")
        html.append("  .title-sec { font-size: 14pt; font-weight: bold; border: 1px solid #000000; text-align: center; height: 35px; background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .metadata-cell { border: none; text-align: left; font-weight: bold; padding: 5px; font-size: 12pt; color: #000000; }\n")
        html.append("  .anden-cell { background-color: #FFFFFF; color: #000000; font-weight: bold; border: 1px solid #000000; }\n")
        
        // Custom Color Palette as requested
        html.append("  .color-exportacion { background-color: #91D04D; color: #000000; }\n")      // Verde claro
        html.append("  .color-almacenaje { background-color: #9DC3E6; color: #000000; }\n")       // Azul pastel
        html.append("  .color-fg-recibos { background-color: #FFC000; color: #000000; }\n")       // Amarillo/Dorado
        html.append("  .color-intermodal-vacio { background-color: #C7E0B6; color: #000000; }\n") // Verde salvia claro
        html.append("  .color-mexcal-mxdc-vacio { background-color: #FFE699; color: #000000; }\n")// Beige claro
        html.append("  .color-mexcal-export-vacio { background-color: #FFFF00; color: #000000; }\n")// Amarillo intenso
        html.append("  .color-contenedor-vacio { background-color: #D48AD5; color: #000000; }\n") // Lila
        html.append("  .color-pendientes { background-color: #FFFFFF; color: #000000; }\n")       
        html.append("  .color-empty { background-color: #FFFFFF; color: #000000; }\n")
        
        // Dock specific
        html.append("  .status-bien { background-color: #D4EFDF; color: #196F3D; font-weight: bold; border-radius: 4px; }\n")
        html.append("  .status-mal { background-color: #F9E79F; color: #9A7D0A; font-weight: bold; }\n")
        html.append("  .status-oos { background-color: #FAD7A0; color: #A04000; font-weight: bold; }\n")
        html.append("  .status-normal { background-color: #FFFFFF; color: #333333; }\n")
        
        // Damaged specific
        html.append("  .th-damaged { background-color: #E67E22; color: #FFFFFF; }\n")
        html.append("  .title-damaged { font-size: 18pt; font-weight: bold; border: none; text-align: center; height: 50px; background-color: #FFFFFF; color: #C0392B; border-bottom: 2px solid #E67E22; }\n")
        
        html.append("  .bold { font-weight: bold; }\n")
        html.append("  .justified { text-align: justify; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        // --- SECTION 1: INVENTARIO DE CAJAS 2026 ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" class=\"title-main\">INVENTARIO DE CAJAS Y CONTENEDORES</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>Anden</th>\n")
        html.append("    <th>Fecha De Entrada</th>\n")
        html.append("    <th>Disposicion</th>\n")
        html.append("    <th>No. Economico</th>\n")
        html.append("    <th>Tipo Material</th>\n")
        html.append("    <th>Placas</th>\n")
        html.append("    <th>Origen de Placas</th>\n")
        html.append("    <th>Compañía</th>\n")
        html.append("    <th>Operacion</th>\n")
        html.append("    <th>Destino</th>\n")
        html.append("    <th>Fecha de Procesado</th>\n")
        html.append("    <th>Deliverys</th>\n")
        html.append("    <th>Numero de Sello colocado</th>\n")
        html.append("    <th colspan=\"2\">Comentarios</th>\n")
        html.append("  </tr>\n")

        val andenesMap = containers.filter { it.locationType == "Andén" && it.dockNumber != null }
            .associateBy { it.dockNumber!! }

        for (i in 1..14) {
            val c = andenesMap[i]
            if (c != null) {
                val dateIn = if (!c.receptionDate.isNullOrBlank()) escapeHtml(c.receptionDate) else formatTs(c.timestamp)
                val status = c.status.uppercase()
                val eco = escapeHtml(c.economicNumber)
                val tipoMat = escapeHtml(c.materialType ?: "FG")
                val plates = escapeHtml(c.plates)
                val platesLoc = escapeHtml(c.platesLocation)
                val comp = escapeHtml(c.company)
                
                val operacion = getMexCalOperation(c)

                val dest = escapeHtml(c.destination ?: "")
                
                val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                    ""
                } else {
                    c.processedDate ?: ""
                }

                val doc = escapeHtml(c.deliveryDocument ?: "")
                val seal = escapeHtml(c.sealCode ?: "")
                
                val comments = when {
                    c.loadStatus == "Almacenaje" || (c.isStorageOnly && (c.loadStatus == "Pendiente por completar" || c.loadStatus == "Pendiente por procesar")) -> "PENDIENTE POR TERMINAR"
                    c.loadStatus == "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                    c.loadStatus == "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                    c.loadStatus == "En espera de descarga" -> "EN ESPERA DE DESCARGA"
                    c.loadStatus == "En espera de salida" -> "EN ESPERA DE SALIDA"
                    else -> "-"
                }

                val rowClass = when {
                    status.startsWith("VAC") -> {
                        when {
                            c.type == "Intermodal" -> "color-intermodal-vacio"
                            c.type == "Contenedor Marítimo" -> "color-contenedor-vacio"
                            comp.uppercase().contains("MEX-CAL") -> {
                                if (dest.uppercase() == "MXDC") "color-mexcal-mxdc-vacio" else "color-mexcal-export-vacio"
                            }
                            else -> "color-empty"
                        }
                    }
                    status.startsWith("CARG") -> {
                        val isFgRecibos = comp.contains("MEX-CAL") && c.loadStatus == "En espera de descarga"
                                
                        val isExport = c.loadStatus == "En espera de salida" &&
                                !c.isStorageOnly && !comp.contains("ALMACENAJE") && !isFgRecibos
                                
                        when {
                            c.loadStatus == "Almacenaje" || c.isStorageOnly || comp.contains("ALMACENAJE") -> "color-almacenaje"
                            isFgRecibos -> "color-fg-recibos"
                            isExport -> "color-exportacion"
                            else -> "color-pendientes"
                        }
                    }
                    else -> "color-empty"
                }

                html.append("  <tr class=\"$rowClass\">\n")
                html.append("    <td class=\"anden-cell\">$i</td>\n")
                html.append("    <td>$dateIn</td>\n")
                html.append("    <td>$status</td>\n")
                html.append("    <td>$eco</td>\n")
                html.append("    <td>$tipoMat</td>\n")
                html.append("    <td>$plates</td>\n")
                html.append("    <td>$platesLoc</td>\n")
                html.append("    <td>$comp</td>\n")
                html.append("    <td>$operacion</td>\n")
                html.append("    <td>$dest</td>\n")
                html.append("    <td>$procDate</td>\n")
                html.append("    <td>$doc</td>\n")
                html.append("    <td>$seal</td>\n")
                html.append("    <td colspan=\"2\">$comments</td>\n")
                html.append("  </tr>\n")
            } else {
                html.append("  <tr class=\"color-empty\">\n")
                html.append("    <td class=\"anden-cell\">$i</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td colspan=\"2\">-</td>\n")
                html.append("  </tr>\n")
            }
        }
        html.append("</table>\n")

        html.append("<br><br>\n")

        // --- SECTION 2: YARDA DE MXDC( PATIO) ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" class=\"title-sec\">YARDA DE MXDC( PATIO)</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>#</th>\n")
        html.append("    <th>Fecha</th>\n")
        html.append("    <th>Disposicion</th>\n")
        html.append("    <th>No. Economico</th>\n")
        html.append("    <th>Tipo</th>\n")
        html.append("    <th>Placas</th>\n")
        html.append("    <th>Origen de Placas</th>\n")
        html.append("    <th>Compañia</th>\n")
        html.append("    <th>Operacion</th>\n")
        html.append("    <th>DESTINO</th>\n")
        html.append("    <th>Fecha de Procesado</th>\n")
        html.append("    <th>Deliverys</th>\n")
        html.append("    <th>Numero de Sello colocado</th>\n")
        html.append("    <th colspan=\"2\">comentarios</th>\n")
        html.append("  </tr>\n")

        val patioContainers = containers.filter { it.locationType == "Patio" }
            .sortedWith(compareBy<ContainerItem> {
                // Ordering for Patio as requested:
                // 1. "En espera de salida"
                // 2. "Almacenaje"
                // 3. "Pendiente por completar" / "Pendiente por procesar"
                // 4. Intermodal (Vacíos)
                // 5. Caja Seca Mex-cal (Vacías + Exportación)
                // 6. Marítimos (Vacíos)
                when {
                    it.loadStatus == "En espera de salida" -> 1
                    it.isStorageOnly || it.company.uppercase().contains("ALMACENAJE") -> 2
                    it.loadStatus == "Pendiente por completar" || it.loadStatus == "Pendiente por procesar" || it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar" -> 3
                    it.type == "Intermodal" && it.status.uppercase().startsWith("VAC") -> 4
                    it.company.uppercase().contains("MEX-CAL") && it.status.uppercase().startsWith("VAC") -> 5
                    it.type == "Contenedor Marítimo" && it.status.uppercase().startsWith("VAC") -> 6
                    else -> 10
                }
            })

        for (c in patioContainers) {
            val dateIn = if (!c.receptionDate.isNullOrBlank()) escapeHtml(c.receptionDate) else formatTs(c.timestamp)
            val status = c.status.uppercase()
            val eco = escapeHtml(c.economicNumber)
            val tipoMat = escapeHtml(c.materialType ?: "FG")
            val plates = escapeHtml(c.plates)
            val platesLoc = escapeHtml(c.platesLocation)
            val comp = escapeHtml(c.company)

            val operacion = getMexCalOperation(c)

            val dest = escapeHtml(c.destination ?: "")
            
            val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar" || c.loadStatus == "Pendiente por completar" || c.loadStatus == "Pendiente por procesar") {
                ""
            } else {
                c.processedDate ?: ""
            }

            val doc = escapeHtml(c.deliveryDocument ?: "")
            val seal = escapeHtml(c.sealCode ?: "")

            val comments = when (c.loadStatus) {
                "Falta por completar", "Pendiente por completar" -> "PENDIENTE POR COMPLETAR"
                "Falta por procesar", "Pendiente por procesar" -> "PENDIENTE POR PROCESAR"
                "En espera de descarga" -> "EN ESPERA DE DESCARGA"
                "En espera de salida" -> "EN ESPERA DE SALIDA"
                "Almacenaje" -> "ALMACENAJE"
                else -> "-"
            }

            val rowClass = when {
                status.startsWith("VAC") -> {
                    when {
                        c.type == "Intermodal" -> "color-intermodal-vacio"
                        c.type == "Contenedor Marítimo" -> "color-contenedor-vacio"
                        comp.uppercase().contains("MEX-CAL") -> {
                            if (dest.uppercase() == "MXDC") "color-mexcal-mxdc-vacio" else "color-mexcal-export-vacio"
                        }
                        else -> "color-empty"
                    }
                }
                status.startsWith("CARG") -> {
                    val isFgRecibos = comp.contains("MEX-CAL") && c.loadStatus == "En espera de descarga"
                            
                    val isExport = c.loadStatus == "En espera de salida" &&
                            !c.isStorageOnly && !comp.contains("ALMACENAJE") && !isFgRecibos
                            
                    when {
                        c.loadStatus == "Almacenaje" || c.isStorageOnly || comp.contains("ALMACENAJE") -> "color-almacenaje"
                        isFgRecibos -> "color-fg-recibos"
                        isExport -> "color-exportacion"
                        else -> "color-pendientes"
                    }
                }
                else -> "color-empty"
            }

            html.append("  <tr class=\"$rowClass\">\n")
            html.append("    <td class=\"bold\"></td>\n")
            html.append("    <td>$dateIn</td>\n")
            html.append("    <td>$status</td>\n")
            html.append("    <td>$eco</td>\n")
            html.append("    <td>$tipoMat</td>\n")
            html.append("    <td>$plates</td>\n")
            html.append("    <td>$platesLoc</td>\n")
            html.append("    <td>$comp</td>\n")
            html.append("    <td>$operacion</td>\n")
            html.append("    <td>$dest</td>\n")
            html.append("    <td>$procDate</td>\n")
            html.append("    <td>$doc</td>\n")
            html.append("    <td>$seal</td>\n")
            html.append("    <td colspan=\"2\">$comments</td>\n")
            html.append("  </tr>\n")
        }
        html.append("</table>\n")

        html.append("<br><br>\n")

        // --- SECTION 3: RESUMEN / TOTALES ---
        html.append("<table style=\"width: 450px;\">\n")
        html.append("  <tr>\n")
        html.append("    <th style=\"width: 150px;\">Fecha de conteo</th>\n")
        html.append("    <th style=\"width: 200px;\">Descripcion</th>\n")
        html.append("    <th style=\"width: 100px;\">Cantidad</th>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td rowspan=\"8\" class=\"bold\" style=\"font-size: 11pt; background-color: #FFFFFF;\">$formattedReportDate</td>\n")
        html.append("    <td class=\"color-exportacion bold\">Exportacion</td>\n")
        html.append("    <td class=\"bold\">$countExportacion</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-almacenaje bold\">Almacenaje</td>\n")
        html.append("    <td class=\"bold\">$countAlmacenaje</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-fg-recibos bold\">FG Recibos</td>\n")
        html.append("    <td class=\"bold\">$countFgRecibos</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-intermodal-vacio bold\">Intermodal vacio</td>\n")
        html.append("    <td class=\"bold\">$countIntermodalVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-mexcal-mxdc-vacio bold\">Mex - cal interplanta vacio</td>\n")
        html.append("    <td class=\"bold\">$countMexCalMxdcVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-mexcal-export-vacio bold\">Mex - cal exportacion vacio</td>\n")
        html.append("    <td class=\"bold\">$countMexCalExportVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-contenedor-vacio bold\">Contenedor vacio</td>\n")
        html.append("    <td class=\"bold\">$countContenedorVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"bold\" style=\"background-color: #FFFFFF;\">Pendientes</td>\n")
        html.append("    <td class=\"bold\">$countPendientes</td>\n")
        html.append("  </tr>\n")
        html.append("</table>\n")

        html.append("<br><br>\n")

        // --- SECTION 4: CONDICION DE ANDENES ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"13\" class=\"title-sec\">REPORTE DIARIO DE CONDICION DE ANDENES (1 AL 14)</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>Anden</th>\n")
        html.append("    <th>Tipo Anden</th>\n")
        html.append("    <th>Fuera de Servicio</th>\n")
        html.append("    <th>Rampa</th>\n")
        html.append("    <th>Seguro de Rampa</th>\n")
        html.append("    <th>Gancho</th>\n")
        html.append("    <th>Cortina</th>\n")
        html.append("    <th>Shelter</th>\n")
        html.append("    <th>Gomas</th>\n")
        html.append("    <th>Topes Seguridad</th>\n")
        html.append("    <th>Semaforo</th>\n")
        html.append("    <th>Luces</th>\n")
        html.append("    <th>Notas</th>\n")
        html.append("  </tr>\n")

        for (d in dockConditions) {
            val typeStr = if (d.dockNumber in 1..9) "Exportacion (1-9)" else "Recibos/FG (10-14)"
            
            val oosClass = if (d.isOutOfService) "status-oos" else "status-normal"
            val oosText = if (d.isOutOfService) "SI" else "NO"
            
            fun getStatusClass(isGood: Boolean) = if (isGood) "status-bien" else "status-mal"
            fun getStatusText(isGood: Boolean) = if (isGood) "BIEN" else "MAL"

            val rampaClass = getStatusClass(d.isRampGood)
            val rampaText = getStatusText(d.isRampGood)

            val seguroClass = getStatusClass(d.isRampLockGood)
            val seguroText = getStatusText(d.isRampLockGood)

            val ganchoClass = getStatusClass(d.isDockHookGood)
            val ganchoText = getStatusText(d.isDockHookGood)

            val cortinaClass = getStatusClass(d.isCurtainGood)
            val cortinaText = getStatusText(d.isCurtainGood)

            val shelterClass = getStatusClass(d.isShelterGood)
            val shelterText = getStatusText(d.isShelterGood)

            val gomasClass = getStatusClass(d.isRubbersGood)
            val gomasText = getStatusText(d.isRubbersGood)

            val topesClass = getStatusClass(d.isSafetyStoppersGood)
            val topesText = getStatusText(d.isSafetyStoppersGood)

            val semaforoClass = getStatusClass(d.isTrafficLightsGood)
            val semaforoText = getStatusText(d.isTrafficLightsGood)

            val lucesClass = getStatusClass(d.isLightsGood)
            val lucesText = getStatusText(d.isLightsGood)

            val empaquesClass = getStatusClass(d.isEmpaquesGood)
            val empaquesText = getStatusText(d.isEmpaquesGood)

            val gatoClass = getStatusClass(d.isGatoHidraulicoGood)
            val gatoText = getStatusText(d.isGatoHidraulicoGood)

            val cadenaClass = getStatusClass(d.isCadenaGood)
            val cadenaText = getStatusText(d.isCadenaGood)

            val notes = escapeHtml(d.notes)

            html.append("  <tr>\n")
            html.append("    <td class=\"bold\">${d.dockNumber}</td>\n")
            html.append("    <td>$typeStr</td>\n")
            html.append("    <td class=\"$oosClass\">$oosText</td>\n")
            html.append("    <td class=\"$rampaClass\">$rampaText</td>\n")
            html.append("    <td class=\"$seguroClass\">$seguroText</td>\n")
            html.append("    <td class=\"$ganchoClass\">$ganchoText</td>\n")
            html.append("    <td class=\"$cortinaClass\">$cortinaText</td>\n")
            html.append("    <td class=\"$shelterClass\">$shelterText</td>\n")
            html.append("    <td class=\"$gomasClass\">$gomasText</td>\n")
            html.append("    <td class=\"$topesClass\">$topesText</td>\n")
            html.append("    <td class=\"$semaforoClass\">$semaforoText</td>\n")
            html.append("    <td class=\"$lucesClass\">$lucesText</td>\n")
            html.append("    <td class=\"$empaquesClass\">$empaquesText</td>\n")
            html.append("    <td class=\"$gatoClass\">$gatoText</td>\n")
            html.append("    <td class=\"$cadenaClass\">$cadenaText</td>\n")
            html.append("    <td style=\"text-align: left;\">$notes</td>\n")
            html.append("  </tr>\n")
        }
        html.append("</table>\n")

        html.append("<br><br>\n")

        // --- SECTION 5: CAJAS Y CONTENEDORES DAÑADOS ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"4\" class=\"title-damaged\">REPORTE DIARIO DE CAJAS Y CONTENEDORES DAÑADOS</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th class=\"th-damaged\">No. Economico</th>\n")
        html.append("    <th class=\"th-damaged\">Tipo de Unidad</th>\n")
        html.append("    <th class=\"th-damaged\">Descripcion del Daño</th>\n")
        html.append("    <th class=\"th-damaged\">Fecha Reporte</th>\n")
        html.append("  </tr>\n")

        for (b in damagedBoxes) {
            val eco = escapeHtml(b.economicNumber)
            val type = escapeHtml(b.type)
            val desc = escapeHtml(b.damageDescription)
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(b.timestamp))

            html.append("  <tr>\n")
            html.append("    <td class=\"bold\">$eco</td>\n")
            html.append("    <td>$type</td>\n")
            html.append("    <td style=\"text-align: left;\">$desc</td>\n")
            html.append("    <td>$dateStr</td>\n")
            html.append("  </tr>\n")
        }
        html.append("</table>\n")

        html.append("</body>\n")
        html.append("</html>\n")

        return html.toString()
    }


    suspend fun generateContainersCsvString(reportId: String): String {
        val containers = dao.getContainersForReport(reportId)

        val html = StringBuilder()
        html.append('\uFEFF') // UTF-8 BOM for Excel compatibility

        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateOutFormatter = SimpleDateFormat("d-MMM-yy", Locale.forLanguageTag("es-ES"))
        val formattedReportDate = try {
            val d = dateParser.parse(reportId)
            if (d != null) dateOutFormatter.format(d) else reportId
        } catch (_: Exception) {
            reportId
        }

        fun formatTs(ts: Long): String {
            return try {
                dateOutFormatter.format(Date(ts))
            } catch (_: Exception) {
                ""
            }
        }

        fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        // --- CALCULATE SUMMARY TOTALS ---
        val countAlmacenaje = containers.count {
            it.isStorageOnly || it.loadStatus == "Almacenaje" || it.company.uppercase().contains("ALMACENAJE")
        }
        val countFgRecibos = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de descarga"
        }
        val countExportacion = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            !it.isStorageOnly &&
            !it.company.uppercase().contains("ALMACENAJE") &&
            (!it.company.uppercase().contains("MEX-CAL") || !it.destination.isNullOrBlank())
        }
        val countPendientes = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            (it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar" || it.loadStatus == "Pendiente por completar" || it.loadStatus == "Pendiente por procesar")
        }
        val countIntermodalVacio = containers.count {
            it.type == "Intermodal" &&
            it.status.uppercase().startsWith("VAC")
        }
        val countMexCalExportVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            (it.economicNumber.startsWith("2132") || 
             it.economicNumber.startsWith("2131") || 
             it.economicNumber.startsWith("2130"))
        }
        val countMexCalMxdcVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            it.economicNumber.startsWith("2133")
        }
        val countContenedorVacio = containers.count {
            it.company.uppercase().contains("CONTENEDOR") &&
            it.status.uppercase().startsWith("VAC")
        }

        // Start HTML document
        html.append("<html>\n")
        html.append("<head>\n")
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n")
        html.append("<!--[if gte mso 9]>\n")
        html.append("<xml>\n")
        html.append(" <x:ExcelWorkbook>\n")
        html.append("  <x:ExcelWorksheets>\n")
        html.append("   <x:ExcelWorksheet>\n")
        html.append("    <x:Name>Inventario de Cajas</x:Name>\n")
        html.append("    <x:WorksheetOptions>\n")
        html.append("     <x:DisplayGridlines/>\n")
        html.append("    </x:WorksheetOptions>\n")
        html.append("   </x:ExcelWorksheet>\n")
        html.append("  </x:ExcelWorksheets>\n")
        html.append(" </x:ExcelWorkbook>\n")
        html.append("</xml>\n")
        html.append("<![endif]-->\n")
        html.append("<style>\n")
        html.append("  body { background-color: #F4F7F6; padding: 20px; }\n")
        html.append("  table { border-collapse: collapse; font-family: 'Segoe UI', Calibri, sans-serif; font-size: 10pt; border: 1px solid #D1D1D1; width: 100%; background-color: #FFFFFF; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n")
        html.append("  th { border: 1px solid #FFFFFF; padding: 10px 5px; font-weight: bold; text-align: center; vertical-align: middle; background-color: #2C3E50; color: #FFFFFF; text-transform: uppercase; letter-spacing: 0.5px; }\n")
        html.append("  td { border: 1px solid #F0F0F0; padding: 8px 5px; text-align: center; vertical-align: middle; color: #333333; }\n")
        html.append("  .title-main { font-size: 20pt; font-weight: bold; border: none; text-align: center; height: 60px; background-color: #FFFFFF; color: #2C3E50; text-transform: uppercase; border-bottom: 3px solid #00B0F0; }\n")
        html.append("  .title-sec { font-size: 18pt; font-weight: bold; border: none; text-align: center; height: 55px; background-color: #FFFFFF; color: #2C3E50; border-bottom: 2px solid #00B0F0; }\n")
        html.append("  .metadata-cell { border: none; text-align: left; font-weight: bold; padding: 10px; font-size: 12pt; color: #555555; }\n")
        html.append("  .anden-cell { background-color: #E8F4F8; color: #2C3E50; font-weight: bold; border: 1px solid #D1D1D1; }\n")
        
        // Color classes - More modern/softer palette
        html.append("  .color-exportacion { background-color: #E9F7EF; color: #1E8449; }\n")      // Soft green
        html.append("  .color-almacenaje { background-color: #EBF5FB; color: #2874A6; }\n")       // Soft blue
        html.append("  .color-fg-recibos { background-color: #FEF9E7; color: #B7950B; }\n")       // Soft yellow
        html.append("  .color-intermodal-vacio { background-color: #F4ECF7; color: #7D3C98; }\n") // Soft purple
        html.append("  .color-mexcal-mxdc-vacio { background-color: #FDF2E9; color: #A04000; }\n")// Soft orange
        html.append("  .color-mexcal-export-vacio { background-color: #E8F8F5; color: #117A65; }\n")// Soft teal
        html.append("  .color-contenedor-vacio { background-color: #FADBD8; color: #943126; }\n") // Soft red
        html.append("  .color-pendientes { background-color: #FFFFFF; color: #333333; }\n")       
        html.append("  .color-empty { background-color: #FAFAFA; color: #999999; }\n")
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        // --- SECTION 1: INVENTARIO DE CAJAS 2026 ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" class=\"title-main\">INVENTARIO DE CAJAS Y CONTENEDORES</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>Anden</th>\n")
        html.append("    <th>Fecha De Entrada</th>\n")
        html.append("    <th>Disposicion</th>\n")
        html.append("    <th>No. Economico</th>\n")
        html.append("    <th>Tipo Material</th>\n")
        html.append("    <th>Placas</th>\n")
        html.append("    <th>Origen de Placas</th>\n")
        html.append("    <th>Compañía</th>\n")
        html.append("    <th>Operacion</th>\n")
        html.append("    <th>Destino</th>\n")
        html.append("    <th>Fecha de Procesado</th>\n")
        html.append("    <th>Deliverys</th>\n")
        html.append("    <th>Numero de Sello colocado</th>\n")
        html.append("    <th colspan=\"2\">Comentarios</th>\n")
        html.append("  </tr>\n")

        // Group andenes
        val andenesMap = containers.filter { it.locationType == "Andén" && it.dockNumber != null }
            .associateBy { it.dockNumber!! }

        for (i in 1..14) {
            val c = andenesMap[i]
            if (c != null) {
                val dateIn = if (!c.receptionDate.isNullOrBlank()) escapeHtml(c.receptionDate) else formatTs(c.timestamp)
                val status = c.status.uppercase()
                val eco = escapeHtml(c.economicNumber)
                val tipoMat = escapeHtml(c.materialType ?: "FG")
                val plates = escapeHtml(c.plates)
                val platesLoc = escapeHtml(c.platesLocation)
                val comp = escapeHtml(c.company)
                
                val operacion = getMexCalOperation(c)

                val dest = escapeHtml(c.destination ?: "")
                
                val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar" || c.loadStatus == "Pendiente por completar" || c.loadStatus == "Pendiente por procesar") {
                    ""
                } else {
                    c.processedDate ?: ""
                }

                val doc = escapeHtml(c.deliveryDocument ?: "")
                val seal = escapeHtml(c.sealCode ?: "")
                
                val comments = when (c.loadStatus) {
                    "Falta por completar", "Pendiente por completar" -> "PENDIENTE POR COMPLETAR"
                    "Falta por procesar", "Pendiente por procesar" -> "PENDIENTE POR PROCESAR"
                    "En espera de salida" -> "EN ESPERA DE SALIDA"
                    "En espera de descarga" -> "EN ESPERA DE DESCARGA"
                    "Almacenaje" -> "ALMACENAJE"
                    else -> "-"
                }

                // Determine row background color class
                val rowClass = when {
                    status.startsWith("VAC") -> {
                        when {
                            c.type == "Intermodal" -> "color-intermodal-vacio"
                            c.type == "Contenedor Marítimo" -> "color-contenedor-vacio"
                            comp.uppercase().contains("MEX-CAL") -> {
                                if (dest.uppercase() == "MXDC") "color-mexcal-mxdc-vacio" else "color-mexcal-export-vacio"
                            }
                            else -> "color-empty"
                        }
                    }
                    status.startsWith("CARG") -> {
                        val isFgRecibos = (comp.contains("MEX-CAL") && c.destination.isNullOrBlank() && 
                                (c.loadStatus == "En espera de salida" || c.loadStatus == "Producto Terminado" || i in 10..14)) ||
                                (c.type == "Intermodal" && c.loadStatus == "Producto Terminado")
                                
                        val isExport = (c.loadStatus == "En espera de salida" || c.loadStatus == "Producto Terminado") &&
                                !c.isStorageOnly && !comp.contains("ALMACENAJE") && !isFgRecibos
                                
                        when {
                            c.isStorageOnly || comp.contains("ALMACENAJE") -> "color-almacenaje"
                            isFgRecibos -> "color-fg-recibos"
                            isExport -> "color-exportacion"
                            else -> "color-pendientes"
                        }
                    }
                    else -> "color-empty"
                }

                html.append("  <tr class=\"$rowClass\">\n")
                html.append("    <td class=\"anden-cell\">$i</td>\n")
                html.append("    <td>$dateIn</td>\n")
                html.append("    <td>$status</td>\n")
                html.append("    <td>$eco</td>\n")
                html.append("    <td>$tipoMat</td>\n")
                html.append("    <td>$plates</td>\n")
                html.append("    <td>$platesLoc</td>\n")
                html.append("    <td>$comp</td>\n")
                html.append("    <td>$operacion</td>\n")
                html.append("    <td>$dest</td>\n")
                html.append("    <td>$procDate</td>\n")
                html.append("    <td>$doc</td>\n")
                html.append("    <td>$seal</td>\n")
                html.append("    <td colspan=\"2\">$comments</td>\n")
                html.append("  </tr>\n")
            } else {
                html.append("  <tr class=\"color-empty\">\n")
                html.append("    <td class=\"anden-cell\">$i</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td>-</td>\n")
                html.append("    <td colspan=\"2\">-</td>\n")
                html.append("  </tr>\n")
            }
        }
        html.append("</table>\n")

        html.append("<br><br>\n")

        // --- SECTION 2: YARDA DE MXDC( PATIO) ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" class=\"title-sec\">YARDA DE MXDC( PATIO)</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>#</th>\n")
        html.append("    <th>Fecha</th>\n")
        html.append("    <th>Disposicion</th>\n")
        html.append("    <th>No. Economico</th>\n")
        html.append("    <th>Tipo</th>\n")
        html.append("    <th>Placas</th>\n")
        html.append("    <th>Origen de Placas</th>\n")
        html.append("    <th>Compañia</th>\n")
        html.append("    <th>Operacion</th>\n")
        html.append("    <th>DESTINO</th>\n")
        html.append("    <th>Fecha de Procesado</th>\n")
        html.append("    <th>Deliverys</th>\n")
        html.append("    <th>Numero de Sello colocado</th>\n")
        html.append("    <th colspan=\"2\">comentarios</th>\n")
        html.append("  </tr>\n")

        val patioContainers = containers.filter { it.locationType == "Patio" }
            .sortedWith(compareBy<ContainerItem> {
                // Ordering for Patio as requested:
                // 1. "En espera de salida"
                // 2. "Almacenaje"
                // 3. "Pendiente por completar" / "Pendiente por procesar"
                // 4. Intermodal (Vacíos)
                // 5. Caja Seca Mex-cal (Vacías + Exportación)
                // 6. Marítimos (Vacíos)
                when {
                    it.loadStatus == "En espera de salida" -> 1
                    it.isStorageOnly || it.company.uppercase().contains("ALMACENAJE") -> 2
                    it.loadStatus == "Pendiente por completar" || it.loadStatus == "Pendiente por procesar" || it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar" -> 3
                    it.type == "Intermodal" && it.status.uppercase().startsWith("VAC") -> 4
                    it.company.uppercase().contains("MEX-CAL") && it.status.uppercase().startsWith("VAC") -> 5
                    it.type == "Contenedor Marítimo" && it.status.uppercase().startsWith("VAC") -> 6
                    else -> 10
                }
            })

        for (c in patioContainers) {
            val dateIn = if (!c.receptionDate.isNullOrBlank()) escapeHtml(c.receptionDate) else formatTs(c.timestamp)
            val status = c.status.uppercase()
            val eco = escapeHtml(c.economicNumber)
            val tipoMat = escapeHtml(c.materialType ?: "FG")
            val plates = escapeHtml(c.plates)
            val platesLoc = escapeHtml(c.platesLocation)
            val comp = escapeHtml(c.company)

            val operacion = getMexCalOperation(c)

            val dest = escapeHtml(c.destination ?: "")
            
            val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar" || c.loadStatus == "Pendiente por completar" || c.loadStatus == "Pendiente por procesar") {
                ""
            } else {
                c.processedDate ?: ""
            }

            val doc = escapeHtml(c.deliveryDocument ?: "")
            val seal = escapeHtml(c.sealCode ?: "")

            val comments = when (c.loadStatus) {
                "Falta por completar", "Pendiente por completar" -> "PENDIENTE POR COMPLETAR"
                "Falta por procesar", "Pendiente por procesar" -> "PENDIENTE POR PROCESAR"
                "En espera de descarga" -> "EN ESPERA DE DESCARGA"
                "En espera de salida" -> "EN ESPERA DE SALIDA"
                "Almacenaje" -> "ALMACENAJE"
                else -> "-"
            }

            val rowClass = when {
                status.startsWith("VAC") -> {
                    when {
                        c.type == "Intermodal" -> "color-intermodal-vacio"
                        c.type == "Contenedor Marítimo" -> "color-contenedor-vacio"
                        comp.uppercase().contains("MEX-CAL") -> {
                            if (dest.uppercase() == "MXDC") "color-mexcal-mxdc-vacio" else "color-mexcal-export-vacio"
                        }
                        else -> "color-empty"
                    }
                }
                status.startsWith("CARG") -> {
                    val isFgRecibos = (comp.contains("MEX-CAL") && c.destination?.uppercase() == "MXDC" &&
                            (c.loadStatus == "En espera de salida" || c.loadStatus == "Producto Terminado")) ||
                            (c.type == "Intermodal" && c.loadStatus == "Producto Terminado")
                            
                    val isExport = (c.loadStatus == "En espera de salida" || c.loadStatus == "Producto Terminado") &&
                            !c.isStorageOnly && !comp.contains("ALMACENAJE") && !isFgRecibos
                            
                    when {
                        c.isStorageOnly || comp.contains("ALMACENAJE") -> "color-almacenaje"
                        isFgRecibos -> "color-fg-recibos"
                        isExport -> "color-exportacion"
                        else -> "color-pendientes"
                    }
                }
                else -> "color-empty"
            }

            html.append("  <tr class=\"$rowClass\">\n")
            html.append("    <td class=\"bold\"></td>\n")
            html.append("    <td>$dateIn</td>\n")
            html.append("    <td>$status</td>\n")
            html.append("    <td>$eco</td>\n")
            html.append("    <td>$tipoMat</td>\n")
            html.append("    <td>$plates</td>\n")
            html.append("    <td>$platesLoc</td>\n")
            html.append("    <td>$comp</td>\n")
            html.append("    <td>$operacion</td>\n")
            html.append("    <td>$dest</td>\n")
            html.append("    <td>$procDate</td>\n")
            html.append("    <td>$doc</td>\n")
            html.append("    <td>$seal</td>\n")
            html.append("    <td colspan=\"2\">$comments</td>\n")
            html.append("  </tr>\n")
        }
        html.append("</table>\n")

        html.append("<br><br>\n")

        // --- SECTION 3: RESUMEN / TOTALES ---
        html.append("<table style=\"width: 450px;\">\n")
        html.append("  <tr>\n")
        html.append("    <th style=\"width: 150px;\">Fecha de conteo</th>\n")
        html.append("    <th style=\"width: 200px;\">Descripcion</th>\n")
        html.append("    <th style=\"width: 100px;\">Cantidad</th>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td rowspan=\"8\" class=\"bold\" style=\"font-size: 11pt; background-color: #FFFFFF;\">$formattedReportDate</td>\n")
        html.append("    <td class=\"color-exportacion bold\">Exportacion</td>\n")
        html.append("    <td class=\"bold\">$countExportacion</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-almacenaje bold\">Almacenaje</td>\n")
        html.append("    <td class=\"bold\">$countAlmacenaje</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-fg-recibos bold\">FG Recibos</td>\n")
        html.append("    <td class=\"bold\">$countFgRecibos</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-intermodal-vacio bold\">Intermodal vacio</td>\n")
        html.append("    <td class=\"bold\">$countIntermodalVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-mexcal-mxdc-vacio bold\">Mex - cal interplanta vacio</td>\n")
        html.append("    <td class=\"bold\">$countMexCalMxdcVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-mexcal-export-vacio bold\">Mex - cal exportacion vacio</td>\n")
        html.append("    <td class=\"bold\">$countMexCalExportVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-contenedor-vacio bold\">Contenedor vacio</td>\n")
        html.append("    <td class=\"bold\">$countContenedorVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"bold\" style=\"background-color: #FFFFFF;\">Pendientes</td>\n")
        html.append("    <td class=\"bold\">$countPendientes</td>\n")
        html.append("  </tr>\n")
        html.append("</table>\n")

        html.append("</body>\n")
        html.append("</html>\n")

        return html.toString()
    }

    suspend fun generateDockConditionsCsvString(reportId: String): String {
        val dockConditions = dao.getDockConditions(reportId)
        val report = dao.getDailyReport(reportId)
        val shift = report?.shift ?: ""
        val supervisorName = report?.supervisorName ?: ""
        val html = StringBuilder()
        html.append('\uFEFF') // UTF-8 BOM for Excel

        fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        html.append("<html>\n")
        html.append("<head>\n")
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n")
        html.append("<!--[if gte mso 9]>\n")
        html.append("<xml>\n")
        html.append(" <x:ExcelWorkbook>\n")
        html.append("  <x:ExcelWorksheets>\n")
        html.append("   <x:ExcelWorksheet>\n")
        html.append("    <x:Name>Condicion de Andenes</x:Name>\n")
        html.append("    <x:WorksheetOptions>\n")
        html.append("     <x:DisplayGridlines/>\n")
        html.append("    </x:WorksheetOptions>\n")
        html.append("   </x:ExcelWorksheet>\n")
        html.append("  </x:ExcelWorksheets>\n")
        html.append(" </x:ExcelWorkbook>\n")
        html.append("</xml>\n")
        html.append("<![endif]-->\n")
        html.append("<style>\n")
        html.append("  body { background-color: #F4F7F6; padding: 20px; }\n")
        html.append("  table { border-collapse: collapse; font-family: 'Segoe UI', Calibri, sans-serif; font-size: 10pt; border: 1px solid #D1D1D1; width: 100%; background-color: #FFFFFF; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n")
        html.append("  th { border: 1px solid #FFFFFF; padding: 10px 5px; font-weight: bold; text-align: center; background-color: #2C3E50; color: #FFFFFF; text-transform: uppercase; letter-spacing: 0.5px; }\n")
        html.append("  td { border: 1px solid #F0F0F0; padding: 8px 5px; text-align: center; color: #333333; }\n")
        html.append("  .title { font-size: 20pt; font-weight: bold; border: none; text-align: center; height: 60px; background-color: #FFFFFF; color: #2C3E50; text-transform: uppercase; border-bottom: 3px solid #00B0F0; }\n")
        html.append("  .metadata-cell { border: none; text-align: left; font-weight: bold; padding: 10px; font-size: 12pt; color: #555555; }\n")
        html.append("  .status-bien { background-color: #D4EFDF; color: #196F3D; font-weight: bold; }\n")
        html.append("  .status-mal { background-color: #F9E79F; color: #9A7D0A; font-weight: bold; }\n")
        html.append("  .status-oos { background-color: #FAD7A0; color: #A04000; font-weight: bold; }\n")
        html.append("  .status-normal { background-color: #FFFFFF; color: #333333; }\n")
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"16\" class=\"title\">REPORTE DIARIO DE CONDICION DE ANDENES (1 AL 14)</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"16\" class=\"metadata-cell\">Fecha: ${escapeHtml(reportId)} &nbsp;&nbsp;&nbsp;&nbsp; Turno: ${escapeHtml(shift)} &nbsp;&nbsp;&nbsp;&nbsp; Elaboró: ${escapeHtml(supervisorName)}</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>Anden</th>\n")
        html.append("    <th>Tipo Anden</th>\n")
        html.append("    <th>Fuera de Servicio</th>\n")
        html.append("    <th>Rampa</th>\n")
        html.append("    <th>Seguro de Rampa</th>\n")
        html.append("    <th>Gancho</th>\n")
        html.append("    <th>Cortina</th>\n")
        html.append("    <th>Shelter</th>\n")
        html.append("    <th>Gomas</th>\n")
        html.append("    <th>Topes Seguridad</th>\n")
        html.append("    <th>Semaforo</th>\n")
        html.append("    <th>Luces</th>\n")
        html.append("    <th>Empaques</th>\n")
        html.append("    <th>Gato Hidraulico</th>\n")
        html.append("    <th>Cadena</th>\n")
        html.append("    <th>Notas</th>\n")
        html.append("  </tr>\n")

        for (d in dockConditions) {
            val typeStr = if (d.dockNumber in 1..9) "Exportacion (1-9)" else "Recibos/FG (10-14)"
            
            val oosClass = if (d.isOutOfService) "status-oos" else "status-normal"
            val oosText = if (d.isOutOfService) "SI" else "NO"
            
            fun getStatusClass(isGood: Boolean) = if (isGood) "status-bien" else "status-mal"
            fun getStatusText(isGood: Boolean) = if (isGood) "BIEN" else "MAL"

            val rampaClass = getStatusClass(d.isRampGood)
            val rampaText = getStatusText(d.isRampGood)

            val seguroClass = getStatusClass(d.isRampLockGood)
            val seguroText = getStatusText(d.isRampLockGood)

            val ganchoClass = getStatusClass(d.isDockHookGood)
            val ganchoText = getStatusText(d.isDockHookGood)

            val cortinaClass = getStatusClass(d.isCurtainGood)
            val cortinaText = getStatusText(d.isCurtainGood)

            val shelterClass = getStatusClass(d.isShelterGood)
            val shelterText = getStatusText(d.isShelterGood)

            val gomasClass = getStatusClass(d.isRubbersGood)
            val gomasText = getStatusText(d.isRubbersGood)

            val topesClass = getStatusClass(d.isSafetyStoppersGood)
            val topesText = getStatusText(d.isSafetyStoppersGood)

            val semaforoClass = getStatusClass(d.isTrafficLightsGood)
            val semaforoText = getStatusText(d.isTrafficLightsGood)

            val lucesClass = getStatusClass(d.isLightsGood)
            val lucesText = getStatusText(d.isLightsGood)

            val empaquesClass = getStatusClass(d.isEmpaquesGood)
            val empaquesText = getStatusText(d.isEmpaquesGood)

            val gatoClass = getStatusClass(d.isGatoHidraulicoGood)
            val gatoText = getStatusText(d.isGatoHidraulicoGood)

            val cadenaClass = getStatusClass(d.isCadenaGood)
            val cadenaText = getStatusText(d.isCadenaGood)

            val notes = escapeHtml(d.notes)

            html.append("  <tr>\n")
            html.append("    <td class=\"bold\">${d.dockNumber}</td>\n")
            html.append("    <td>$typeStr</td>\n")
            html.append("    <td class=\"$oosClass\">$oosText</td>\n")
            html.append("    <td class=\"$rampaClass\">$rampaText</td>\n")
            html.append("    <td class=\"$seguroClass\">$seguroText</td>\n")
            html.append("    <td class=\"$ganchoClass\">$ganchoText</td>\n")
            html.append("    <td class=\"$cortinaClass\">$cortinaText</td>\n")
            html.append("    <td class=\"$shelterClass\">$shelterText</td>\n")
            html.append("    <td class=\"$gomasClass\">$gomasText</td>\n")
            html.append("    <td class=\"$topesClass\">$topesText</td>\n")
            html.append("    <td class=\"$semaforoClass\">$semaforoText</td>\n")
            html.append("    <td class=\"$lucesClass\">$lucesText</td>\n")
            html.append("    <td class=\"$empaquesClass\">$empaquesText</td>\n")
            html.append("    <td class=\"$gatoClass\">$gatoText</td>\n")
            html.append("    <td class=\"$cadenaClass\">$cadenaText</td>\n")
            html.append("    <td style=\"text-align: left;\">$notes</td>\n")
            html.append("  </tr>\n")
        }

        html.append("</table>\n")
        html.append("</body>\n")
        html.append("</html>\n")

        return html.toString()
    }

    suspend fun generateDamagedBoxesCsvString(reportId: String): String {
        val damagedBoxes = dao.getDamagedBoxes(reportId)
        val html = StringBuilder()
        html.append('\uFEFF') // UTF-8 BOM for Excel

        fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        html.append("<html>\n")
        html.append("<head>\n")
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n")
        html.append("<!--[if gte mso 9]>\n")
        html.append("<xml>\n")
        html.append(" <x:ExcelWorkbook>\n")
        html.append("  <x:ExcelWorksheets>\n")
        html.append("   <x:ExcelWorksheet>\n")
        html.append("    <x:Name>Cajas Danadas</x:Name>\n")
        html.append("    <x:WorksheetOptions>\n")
        html.append("     <x:DisplayGridlines/>\n")
        html.append("    </x:WorksheetOptions>\n")
        html.append("   </x:ExcelWorksheet>\n")
        html.append("  </x:ExcelWorksheets>\n")
        html.append(" </x:ExcelWorkbook>\n")
        html.append("</xml>\n")
        html.append("<![endif]-->\n")
        html.append("<style>\n")
        html.append("  body { background-color: #F4F7F6; padding: 20px; }\n")
        html.append("  table { border-collapse: collapse; font-family: 'Segoe UI', Calibri, sans-serif; font-size: 10pt; border: 1px solid #D1D1D1; width: 100%; background-color: #FFFFFF; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n")
        html.append("  th { border: 1px solid #FFFFFF; padding: 10px 5px; font-weight: bold; text-align: center; background-color: #E67E22; color: #FFFFFF; text-transform: uppercase; letter-spacing: 0.5px; }\n")
        html.append("  td { border: 1px solid #F0F0F0; padding: 8px 5px; text-align: center; color: #333333; }\n")
        html.append("  .title { font-size: 20pt; font-weight: bold; border: none; text-align: center; height: 60px; background-color: #FFFFFF; color: #C0392B; text-transform: uppercase; border-bottom: 3px solid #E67E22; }\n")
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"4\" class=\"title\">REPORTE DIARIO DE CAJAS Y CONTENEDORES DAÑADOS</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"4\" style=\"border: none; text-align: left; font-weight: bold; padding: 5px;\">Fecha: ${escapeHtml(reportId)} &nbsp;&nbsp;&nbsp;&nbsp; Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>No. Economico</th>\n")
        html.append("    <th>Tipo de Unidad</th>\n")
        html.append("    <th>Descripcion del Daño</th>\n")
        html.append("    <th>Fecha Reporte</th>\n")
        html.append("  </tr>\n")

        for (b in damagedBoxes) {
            val eco = escapeHtml(b.economicNumber)
            val type = escapeHtml(b.type)
            val desc = escapeHtml(b.damageDescription)
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(b.timestamp))

            html.append("  <tr>\n")
            html.append("    <td class=\"bold\">$eco</td>\n")
            html.append("    <td>$type</td>\n")
            html.append("    <td style=\"text-align: left;\">$desc</td>\n")
            html.append("    <td>$dateStr</td>\n")
            html.append("  </tr>\n")
        }

        html.append("</table>\n")
        html.append("</body>\n")
        html.append("</html>\n")

        return html.toString()
    }


    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        if (clean.contains(",") || clean.contains("\"") || clean.contains("\n") || clean.contains("\r")) {
            return "\"$clean\""
        }
        return clean
    }

    suspend fun saveContentToFile(context: Context, reportId: String, content: String, prefix: String, extension: String): File {
        return withContext(Dispatchers.IO) {
            val fileName = "${prefix}_${reportId}$extension"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            file
        }
    }

    suspend fun generateContainersCsvPlain(reportId: String): String {
        val containers = dao.getContainersForReport(reportId)
        
        val csv = java.lang.StringBuilder()
        csv.append('\uFEFF')
        
        csv.append("INVENTARIO DE CAJAS Y CONTENEDORES\n")
        csv.append("\n")
        
        csv.append("Anden,Fecha De Entrada,Disposicion,No. Economico,Tipo Material,Placas,Origen de Placas,Compañía,Operacion,Destino,Fecha de Procesado,Deliverys,Numero de Sello colocado,Comentarios\n")
        
        val andenesMap = containers.filter { it.locationType == "Andén" && it.dockNumber != null }
            .associateBy { it.dockNumber!! }
            
        for (i in 1..14) {
            val c = andenesMap[i]
            if (c != null) {
                val dateIn = if (!c.receptionDate.isNullOrBlank()) c.receptionDate else {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.format(Date(c.timestamp))
                }
                val status = c.status
                val eco = c.economicNumber
                val tipoMat = c.materialType ?: "FG"
                val plates = c.plates
                val platesLoc = c.platesLocation
                val comp = c.company
                val operacion = getMexCalOperation(c)
                val dest = c.destination ?: ""
                val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar" || c.loadStatus == "Pendiente por completar" || c.loadStatus == "Pendiente por procesar") "" else (c.processedDate ?: "")
                val doc = c.deliveryDocument ?: ""
                val seal = c.sealCode ?: ""
                val comments = when (c.loadStatus) {
                    "Falta por completar", "Pendiente por completar" -> "PENDIENTE POR COMPLETAR"
                    "Falta por procesar", "Pendiente por procesar" -> "PENDIENTE POR PROCESAR"
                    "En espera de salida" -> "EN ESPERA DE SALIDA"
                    "En espera de descarga" -> "EN ESPERA DE DESCARGA"
                    "Almacenaje" -> "ALMACENAJE"
                    else -> "-"
                }
                
                csv.append("$i,${escapeCsv(dateIn)},${escapeCsv(status)},${escapeCsv(eco)},${escapeCsv(tipoMat)},${escapeCsv(plates)},${escapeCsv(platesLoc)},${escapeCsv(comp)},${escapeCsv(operacion)},${escapeCsv(dest)},${escapeCsv(procDate)},${escapeCsv(doc)},${escapeCsv(seal)},${escapeCsv(comments)}\n")
            } else {
                csv.append("$i,-,-,-,-,-,-,-,-,-,-,-,-,-\n")
            }
        }
        
        csv.append("\n\n")
        csv.append("YARDA DE MXDC (PATIO)\n")
        csv.append("#,Fecha,Disposicion,No. Economico,Tipo,Placas,Origen de Placas,Compañia,Operacion,DESTINO,Fecha de Procesado,Deliverys,Numero de Sello colocado,comentarios\n")
        
        val patioContainers = containers.filter { it.locationType == "Patio" }
        var index = 1
        for (c in patioContainers) {
            val dateIn = if (!c.receptionDate.isNullOrBlank()) c.receptionDate else {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(c.timestamp))
            }
            val status = c.status
            val eco = c.economicNumber
            val tipoMat = c.materialType ?: "FG"
            val plates = c.plates
            val platesLoc = c.platesLocation
            val comp = c.company
            val operacion = getMexCalOperation(c)
            val dest = c.destination ?: ""
            val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar" || c.loadStatus == "Pendiente por completar" || c.loadStatus == "Pendiente por procesar") "" else (c.processedDate ?: "")
            val doc = c.deliveryDocument ?: ""
            val seal = c.sealCode ?: ""
            val comments = when (c.loadStatus) {
                "Falta por completar", "Pendiente por completar" -> "PENDIENTE POR COMPLETAR"
                "Falta por procesar", "Pendiente por procesar" -> "PENDIENTE POR PROCESAR"
                "En espera de salida" -> "EN ESPERA DE SALIDA"
                "En espera de descarga" -> "EN ESPERA DE DESCARGA"
                "Almacenaje" -> "ALMACENAJE"
                else -> "-"
            }
            
            csv.append("${index++},${escapeCsv(dateIn)},${escapeCsv(status)},${escapeCsv(eco)},${escapeCsv(tipoMat)},${escapeCsv(plates)},${escapeCsv(platesLoc)},${escapeCsv(comp)},${escapeCsv(operacion)},${escapeCsv(dest)},${escapeCsv(procDate)},${escapeCsv(doc)},${escapeCsv(seal)},${escapeCsv(comments)}\n")
        }
        
        csv.append("\n\n")
        csv.append("RESUMEN / TOTALES\n")
        csv.append("Fecha de conteo,Descripcion,Cantidad\n")
        
        val countAlmacenaje = containers.count {
            it.isStorageOnly || it.company.uppercase().contains("ALMACENAJE")
        }
        val countFgRecibos = containers.count {
            val isMexCalPT = it.company.uppercase().contains("MEX-CAL") &&
                    it.status.uppercase().startsWith("CARG") &&
                    it.destination.isNullOrBlank() &&
                    (it.loadStatus == "En espera de salida" || it.loadStatus == "Producto Terminado" || (it.dockNumber ?: 0) in 10..14)
            
            val isIntermodalPT = it.type == "Intermodal" &&
                    it.status.uppercase().startsWith("CARG") &&
                    it.loadStatus == "Producto Terminado"
                    
            isMexCalPT || isIntermodalPT
        }
        val countExportacion = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            (it.loadStatus == "En espera de salida" || it.loadStatus == "Producto Terminado") &&
            !it.isStorageOnly &&
            !it.company.uppercase().contains("ALMACENAJE") &&
            (
                !it.company.uppercase().contains("MEX-CAL") || 
                !it.destination.isNullOrBlank()
            ) &&
            !(it.type == "Intermodal" && it.loadStatus == "Producto Terminado")
        }
        val countPendientes = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            (it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar" || it.loadStatus == "Pendiente por completar" || it.loadStatus == "Pendiente por procesar")
        }
        val countIntermodalVacio = containers.count {
            it.type == "Intermodal" &&
            it.status.uppercase().startsWith("VAC")
        }
        val countMexCalExportVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            (it.economicNumber.startsWith("2132") || 
             it.economicNumber.startsWith("2131") || 
             it.economicNumber.startsWith("2130"))
        }
        val countMexCalMxdcVacio = containers.count {
            it.company.uppercase().contains("MEX-CAL") &&
            it.status.uppercase().startsWith("VAC") &&
            it.economicNumber.startsWith("2133")
        }
        val countContenedorVacio = containers.count {
            it.company.uppercase().contains("CONTENEDOR") &&
            it.status.uppercase().startsWith("VAC")
        }
        
        csv.append("${escapeCsv(reportId)},Exportacion,$countExportacion\n")
        csv.append(",Almacenaje,$countAlmacenaje\n")
        csv.append(",FG Recibos,$countFgRecibos\n")
        csv.append(",Intermodal vacio,$countIntermodalVacio\n")
        csv.append(",Mex - cal interplanta vacio,$countMexCalMxdcVacio\n")
        csv.append(",Mex - cal exportacion vacio,$countMexCalExportVacio\n")
        csv.append(",Contenedor vacio,$countContenedorVacio\n")
        csv.append(",Pendientes,$countPendientes\n")
        
        return csv.toString()
    }

    suspend fun generateDockConditionsCsvPlain(reportId: String): String {
        val dockConditions = dao.getDockConditions(reportId)
        val report = dao.getDailyReport(reportId)
        val shift = report?.shift ?: ""
        val supervisorName = report?.supervisorName ?: ""
        
        val csv = java.lang.StringBuilder()
        csv.append('\uFEFF')
        
        csv.append("REPORTE DIARIO DE CONDICION DE ANDENES (1 AL 14)\n")
        csv.append("Fecha:,${escapeCsv(reportId)},Turno:,${escapeCsv(shift)},Elaboró:,${escapeCsv(supervisorName)}\n")
        csv.append("\n")
        
        csv.append("Anden,Rampa,Seguro de Rampa,Gancho,Cortina,Shelter,Gomas,Topes Seguridad,Semaforo,Luces,Empaques,Gato Hidraulico,Cadena,Notas\n")
        
        for (d in dockConditions) {
            fun getStatusText(isGood: Boolean) = if (isGood) "✔" else "✘"
            
            val rampa = getStatusText(d.isRampGood)
            val seguro = getStatusText(d.isRampLockGood)
            val gancho = getStatusText(d.isDockHookGood)
            val cortina = getStatusText(d.isCurtainGood)
            val shelter = getStatusText(d.isShelterGood)
            val gomas = getStatusText(d.isRubbersGood)
            val topes = getStatusText(d.isSafetyStoppersGood)
            val semaforo = getStatusText(d.isTrafficLightsGood)
            val luces = getStatusText(d.isLightsGood)
            val empaques = getStatusText(d.isEmpaquesGood)
            val gato = getStatusText(d.isGatoHidraulicoGood)
            val cadena = getStatusText(d.isCadenaGood)
            val notes = d.notes
            
            csv.append("${d.dockNumber},$rampa,$seguro,$gancho,$cortina,$shelter,$gomas,$topes,$semaforo,$luces,$empaques,$gato,$cadena,${escapeCsv(notes)}\n")
        }
        
        return csv.toString()
    }

    suspend fun generateDamagedBoxesCsvPlain(reportId: String): String {
        val damagedBoxes = dao.getDamagedBoxes(reportId)
        
        val csv = java.lang.StringBuilder()
        csv.append('\uFEFF')
        
        csv.append("REPORTE DIARIO DE CAJAS Y CONTENEDORES DAÑADOS\n")
        csv.append("Fecha:,${escapeCsv(reportId)}\n")
        csv.append("\n")
        
        csv.append("No. Economico,Tipo de Unidad,Descripcion del Daño,Fecha Reporte\n")
        
        for (b in damagedBoxes) {
            val eco = b.economicNumber
            val type = b.type
            val desc = b.damageDescription
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(b.timestamp))
            
            csv.append("${escapeCsv(eco)},${escapeCsv(type)},${escapeCsv(desc)},${escapeCsv(dateStr)}\n")
        }
        
        return csv.toString()
    }

    suspend fun generateCsvPlain(reportId: String): String {
        val csv = java.lang.StringBuilder()
        csv.append('\uFEFF')
        
        csv.append("REPORTE COMPLETO DIARIO DE OPERACIONES\n")
        csv.append("=========================================\n\n")
        
        val containersPart = generateContainersCsvPlain(reportId)
        val cleanContainers = if (containersPart.startsWith('\uFEFF')) containersPart.substring(1) else containersPart
        csv.append(cleanContainers)
        
        csv.append("\n\n=========================================\n\n")
        
        val docksPart = generateDockConditionsCsvPlain(reportId)
        val cleanDocks = if (docksPart.startsWith('\uFEFF')) docksPart.substring(1) else docksPart
        csv.append(cleanDocks)
        
        csv.append("\n\n=========================================\n\n")
        
        val damagedPart = generateDamagedBoxesCsvPlain(reportId)
        val cleanDamaged = if (damagedPart.startsWith('\uFEFF')) damagedPart.substring(1) else damagedPart
        csv.append(cleanDamaged)
        
        return csv.toString()
    }
}

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
                isEmpaquesGood = true,
                isGatoHidraulicoGood = true,
                isCadenaGood = true,
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
        val report = dao.getDailyReport(reportId)
        val shift = report?.shift ?: ""
        val supervisorName = report?.supervisorName ?: ""

        val html = StringBuilder()

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

        fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        // --- CALCULATE SUMMARY TOTALS ---
        val countExportacion = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            !it.company.uppercase().contains("MEX-CAL") &&
            !it.company.uppercase().contains("ALMACENAJE") &&
            !it.isStorageOnly
        }
        val countAlmacenaje = containers.count {
            it.isStorageOnly || (it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            it.company.uppercase().contains("ALMACENAJE"))
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
        html.append("  table { border-collapse: collapse; font-family: Calibri, sans-serif; font-size: 11pt; border: 1px solid #000000; margin-bottom: 25px; }\n")
        html.append("  th { border: 1px solid #000000; padding: 6px; font-weight: bold; text-align: center; vertical-align: middle; background-color: #00B0F0; color: #FFFFFF; }\n")
        html.append("  td { border: 1px solid #7F7F7F; padding: 5px; text-align: center; vertical-align: middle; }\n")
        html.append("  .title-main { font-size: 18pt; font-weight: bold; border: none; text-align: center; height: 45px; background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .title-sec { font-size: 18pt; font-weight: bold; border: none; text-align: center; height: 45px; background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .anden-cell { background-color: #00B0F0; color: #FFFFFF; font-weight: bold; border: 1px solid #000000; }\n")
        
        // Color classes
        html.append("  .color-exportacion { background-color: #92D050; color: #000000; }\n")      // Light green
        html.append("  .color-almacenaje { background-color: #9BC2E6; color: #000000; }\n")       // Light blue
        html.append("  .color-fg-recibos { background-color: #FFC000; color: #000000; }\n")       // Amber/Orange
        html.append("  .color-intermodal-vacio { background-color: #C6E0B4; color: #000000; }\n") // Pale green
        html.append("  .color-mexcal-mxdc-vacio { background-color: #FCE4D6; color: #000000; }\n")// Soft peach
        html.append("  .color-mexcal-export-vacio { background-color: #FFFF00; color: #000000; }\n")// Yellow
        html.append("  .color-contenedor-vacio { background-color: #E19CD2; color: #000000; }\n") // Lavender
        html.append("  .color-pendientes { background-color: #FFFFFF; color: #000000; }\n")       // White
        html.append("  .color-empty { background-color: #FFFFFF; color: #000000; }\n")
        
        // Dock specific
        html.append("  .status-bien { background-color: #C6E0B4; color: #375623; font-weight: bold; }\n")
        html.append("  .status-mal { background-color: #F8CBAD; color: #C65911; font-weight: bold; }\n")
        html.append("  .status-oos { background-color: #FFC000; color: #000000; font-weight: bold; }\n")
        html.append("  .status-normal { background-color: #FFFFFF; color: #000000; }\n")
        
        // Damaged specific
        html.append("  .th-damaged { background-color: #ED7D31; color: #FFFFFF; }\n")
        html.append("  .title-damaged { font-size: 16pt; font-weight: bold; border: none; text-align: center; height: 40px; background-color: #FFFFFF; color: #C00000; }\n")
        
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        // --- SECTION 1: INVENTARIO DE CAJAS 2026 ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" class=\"title-main\">REPORTE COMPLETO: INVENTARIO DE CAJAS 2026</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" style=\"border: none; text-align: left; font-weight: bold; padding: 5px;\">Fecha: $reportId &nbsp;&nbsp;&nbsp;&nbsp; Turno: $shift &nbsp;&nbsp;&nbsp;&nbsp; Elaboró: $supervisorName &nbsp;&nbsp;&nbsp;&nbsp; Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</td>\n")
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
                
                val operacion = if (comp.uppercase().contains("MEX-CAL")) {
                    c.mexCalType?.uppercase() ?: if (c.destination?.uppercase() == "EXPORTACION") "EXPORTACION" else "INTERPLANTA"
                } else {
                    "EXPORTACION"
                }

                val dest = escapeHtml(c.destination ?: "")
                
                val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                    ""
                } else {
                    c.processedDate ?: ""
                }

                val doc = escapeHtml(c.deliveryDocument ?: "")
                val seal = escapeHtml(c.sealCode ?: "")
                
                val comments = when (c.loadStatus) {
                    "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                    "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                    "En espera de salida" -> "EN ESPERA DE SALIDA"
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
                        if (c.loadStatus == "En espera de salida") {
                            when {
                                c.isStorageOnly || comp.uppercase().contains("ALMACENAJE") -> "color-almacenaje"
                                i in 10..14 -> "color-fg-recibos"
                                else -> "color-exportacion"
                            }
                        } else {
                            "color-pendientes"
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
        for (c in patioContainers) {
            val dateIn = if (!c.receptionDate.isNullOrBlank()) escapeHtml(c.receptionDate) else formatTs(c.timestamp)
            val status = c.status.uppercase()
            val eco = escapeHtml(c.economicNumber)
            val tipoMat = escapeHtml(c.materialType ?: "FG")
            val plates = escapeHtml(c.plates)
            val platesLoc = escapeHtml(c.platesLocation)
            val comp = escapeHtml(c.company)

            val operacion = if (comp.uppercase().contains("MEX-CAL")) {
                c.mexCalType?.uppercase() ?: if (c.destination?.uppercase() == "EXPORTACION") "EXPORTACION" else "INTERPLANTA"
            } else {
                "EXPORTACION"
            }

            val dest = escapeHtml(c.destination ?: "")
            
            val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                ""
            } else {
                c.processedDate ?: ""
            }

            val doc = escapeHtml(c.deliveryDocument ?: "")
            val seal = escapeHtml(c.sealCode ?: "")

            val comments = when (c.loadStatus) {
                "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                "En espera de salida" -> "EN ESPERA DE SALIDA"
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
                    if (c.loadStatus == "En espera de salida") {
                        if (c.isStorageOnly || comp.uppercase().contains("ALMACENAJE")) "color-almacenaje" else "color-exportacion"
                    } else {
                        "color-pendientes"
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
        html.append("    <td class=\"color-mexcal-mxdc-vacio bold\">Mex - cal mxdc vacio</td>\n")
        html.append("    <td class=\"bold\">$countMexCalMxdcVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-mexcal-export-vacio bold\">Mex - cal export vacio</td>\n")
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
        html.append("    <td colspan=\"14\" class=\"title-sec\">REPORTE DIARIO DE CONDICION DE ANDENES (1 AL 14)</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>Anden</th>\n")
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
            fun getStatusClass(isGood: Boolean) = if (isGood) "status-bien" else "status-mal"
            fun getStatusText(isGood: Boolean) = if (isGood) "✔" else "✘"

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

    suspend fun saveCsvToFile(context: Context, reportId: String, content: String): File {
        return withContext(Dispatchers.IO) {
            val fileName = "Reporte_Andenes_${reportId}.xls"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            file
        }
    }

    suspend fun generateContainersCsvString(reportId: String): String {
        val containers = dao.getContainersForReport(reportId)
        val report = dao.getDailyReport(reportId)
        val shift = report?.shift ?: ""
        val supervisorName = report?.supervisorName ?: ""
        val html = StringBuilder()

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

        fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        // --- CALCULATE SUMMARY TOTALS ---
        val countExportacion = containers.count {
            it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            !it.company.uppercase().contains("MEX-CAL") &&
            !it.company.uppercase().contains("ALMACENAJE") &&
            !it.isStorageOnly
        }
        val countAlmacenaje = containers.count {
            it.isStorageOnly || (it.status.uppercase().startsWith("CARG") &&
            it.loadStatus == "En espera de salida" &&
            it.company.uppercase().contains("ALMACENAJE"))
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
        html.append("  table { border-collapse: collapse; font-family: Calibri, sans-serif; font-size: 11pt; border: 1px solid #000000; }\n")
        html.append("  th { border: 1px solid #000000; padding: 6px; font-weight: bold; text-align: center; vertical-align: middle; background-color: #00B0F0; color: #FFFFFF; }\n")
        html.append("  td { border: 1px solid #7F7F7F; padding: 5px; text-align: center; vertical-align: middle; }\n")
        html.append("  .title-main { font-size: 18pt; font-weight: bold; border: none; text-align: center; height: 45px; background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .title-sec { font-size: 18pt; font-weight: bold; border: none; text-align: center; height: 45px; background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .anden-cell { background-color: #00B0F0; color: #FFFFFF; font-weight: bold; border: 1px solid #000000; }\n")
        
        // Color classes
        html.append("  .color-exportacion { background-color: #92D050; color: #000000; }\n")      // Light green
        html.append("  .color-almacenaje { background-color: #9BC2E6; color: #000000; }\n")       // Light blue
        html.append("  .color-fg-recibos { background-color: #FFC000; color: #000000; }\n")       // Amber/Orange
        html.append("  .color-intermodal-vacio { background-color: #C6E0B4; color: #000000; }\n") // Pale green
        html.append("  .color-mexcal-mxdc-vacio { background-color: #FCE4D6; color: #000000; }\n")// Soft peach
        html.append("  .color-mexcal-export-vacio { background-color: #FFFF00; color: #000000; }\n")// Yellow
        html.append("  .color-contenedor-vacio { background-color: #E19CD2; color: #000000; }\n") // Lavender
        html.append("  .color-pendientes { background-color: #FFFFFF; color: #000000; }\n")       // White
        html.append("  .color-empty { background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        // --- SECTION 1: INVENTARIO DE CAJAS 2026 ---
        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" class=\"title-main\">INVENTARIO DE CAJAS 2026</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"15\" style=\"border: none; text-align: left; font-weight: bold; padding: 5px;\">Fecha: $reportId &nbsp;&nbsp;&nbsp;&nbsp; Turno: $shift &nbsp;&nbsp;&nbsp;&nbsp; Elaboró: $supervisorName &nbsp;&nbsp;&nbsp;&nbsp; Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</td>\n")
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
                
                val operacion = if (comp.uppercase().contains("MEX-CAL")) {
                    c.mexCalType?.uppercase() ?: if (c.destination?.uppercase() == "EXPORTACION") "EXPORTACION" else "INTERPLANTA"
                } else {
                    "EXPORTACION"
                }

                val dest = escapeHtml(c.destination ?: "")
                
                val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                    ""
                } else {
                    c.processedDate ?: ""
                }

                val doc = escapeHtml(c.deliveryDocument ?: "")
                val seal = escapeHtml(c.sealCode ?: "")
                
                val comments = when (c.loadStatus) {
                    "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                    "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                    "En espera de salida" -> "EN ESPERA DE SALIDA"
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
                        if (c.loadStatus == "En espera de salida") {
                            when {
                                c.isStorageOnly || comp.uppercase().contains("ALMACENAJE") -> "color-almacenaje"
                                i in 10..14 -> "color-fg-recibos"
                                else -> "color-exportacion"
                            }
                        } else {
                            "color-pendientes"
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
        for (c in patioContainers) {
            val dateIn = if (!c.receptionDate.isNullOrBlank()) escapeHtml(c.receptionDate) else formatTs(c.timestamp)
            val status = c.status.uppercase()
            val eco = escapeHtml(c.economicNumber)
            val tipoMat = escapeHtml(c.materialType ?: "FG")
            val plates = escapeHtml(c.plates)
            val platesLoc = escapeHtml(c.platesLocation)
            val comp = escapeHtml(c.company)

            val operacion = if (comp.uppercase().contains("MEX-CAL")) {
                c.mexCalType?.uppercase() ?: if (c.destination?.uppercase() == "EXPORTACION") "EXPORTACION" else "INTERPLANTA"
            } else {
                "EXPORTACION"
            }

            val dest = escapeHtml(c.destination ?: "")
            
            val procDate = if (c.loadStatus == "Falta por completar" || c.loadStatus == "Falta por procesar") {
                ""
            } else {
                c.processedDate ?: ""
            }

            val doc = escapeHtml(c.deliveryDocument ?: "")
            val seal = escapeHtml(c.sealCode ?: "")

            val comments = when (c.loadStatus) {
                "Falta por completar" -> "PENDIENTE POR COMPLETAR"
                "Falta por procesar" -> "PENDIENTE POR PROCESAR"
                "En espera de salida" -> "EN ESPERA DE SALIDA"
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
                    if (c.loadStatus == "En espera de salida") {
                        if (c.isStorageOnly || comp.uppercase().contains("ALMACENAJE")) "color-almacenaje" else "color-exportacion"
                    } else {
                        "color-pendientes"
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
        html.append("    <td class=\"color-mexcal-mxdc-vacio bold\">Mex - cal mxdc vacio</td>\n")
        html.append("    <td class=\"bold\">$countMexCalMxdcVacio</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td class=\"color-mexcal-export-vacio bold\">Mex - cal export vacio</td>\n")
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
        html.append("  table { border-collapse: collapse; font-family: Calibri, sans-serif; font-size: 11pt; border: 1px solid #000000; }\n")
        html.append("  th { border: 1px solid #000000; padding: 6px; font-weight: bold; text-align: center; background-color: #00B0F0; color: #FFFFFF; }\n")
        html.append("  td { border: 1px solid #7F7F7F; padding: 5px; text-align: center; }\n")
        html.append("  .title { font-size: 16pt; font-weight: bold; border: none; text-align: center; height: 40px; background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .status-bien { background-color: #C6E0B4; color: #375623; font-weight: bold; }\n")
        html.append("  .status-mal { background-color: #F8CBAD; color: #C65911; font-weight: bold; }\n")
        html.append("  .status-oos { background-color: #FFC000; color: #000000; font-weight: bold; }\n")
        html.append("  .status-normal { background-color: #FFFFFF; color: #000000; }\n")
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"14\" class=\"title\">REPORTE DIARIO DE CONDICION DE ANDENES (1 AL 14)</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"14\" style=\"border: none; text-align: left; font-weight: bold; padding: 5px;\">Fecha: $reportId &nbsp;&nbsp;&nbsp;&nbsp; Turno: $shift &nbsp;&nbsp;&nbsp;&nbsp; Elaboró: $supervisorName &nbsp;&nbsp;&nbsp;&nbsp; Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <th>Anden</th>\n")
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
            fun getStatusClass(isGood: Boolean) = if (isGood) "status-bien" else "status-mal"
            fun getStatusText(isGood: Boolean) = if (isGood) "✔" else "✘"

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
        val report = dao.getDailyReport(reportId)
        val shift = report?.shift ?: ""
        val supervisorName = report?.supervisorName ?: ""
        val html = StringBuilder()

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
        html.append("  table { border-collapse: collapse; font-family: Calibri, sans-serif; font-size: 11pt; border: 1px solid #000000; }\n")
        html.append("  th { border: 1px solid #000000; padding: 6px; font-weight: bold; text-align: center; background-color: #ED7D31; color: #FFFFFF; }\n")
        html.append("  td { border: 1px solid #7F7F7F; padding: 5px; text-align: center; }\n")
        html.append("  .title { font-size: 16pt; font-weight: bold; border: none; text-align: center; height: 40px; background-color: #FFFFFF; color: #C00000; }\n")
        html.append("  .bold { font-weight: bold; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")

        html.append("<table>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"4\" class=\"title\">REPORTE DIARIO DE CAJAS Y CONTENEDORES DAÑADOS</td>\n")
        html.append("  </tr>\n")
        html.append("  <tr>\n")
        html.append("    <td colspan=\"4\" style=\"border: none; text-align: left; font-weight: bold; padding: 5px;\">Fecha: $reportId &nbsp;&nbsp;&nbsp;&nbsp; Turno: $shift &nbsp;&nbsp;&nbsp;&nbsp; Elaboró: $supervisorName &nbsp;&nbsp;&nbsp;&nbsp; Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</td>\n")
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

    suspend fun saveCsvToFileWithPrefix(context: Context, reportId: String, content: String, prefix: String): File {
        return withContext(Dispatchers.IO) {
            val extension = if (prefix == "Inventario_Cajas" || prefix == "Condicion_Andenes" || prefix == "Reporte_Cajas_Danadas" || prefix == "Reporte_Completo") ".xls" else ".csv"
            val fileName = "${prefix}_${reportId}$extension"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            file
        }
    }
}

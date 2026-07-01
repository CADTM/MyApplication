package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.DriveUploader
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed interface DriveSyncState {
    object Idle : DriveSyncState
    object Syncing : DriveSyncState
    data class Success(val fileId: String) : DriveSyncState
    data class Error(val message: String) : DriveSyncState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportViewModel(private val repository: ReportRepository) : ViewModel() {

    private val _activeDate = MutableStateFlow(getTodayDateString())
    val activeDate: StateFlow<String> = _activeDate.asStateFlow()

    val activeReport: StateFlow<DailyReport?> = _activeDate.flatMapLatest { date ->
        repository.getAllReportsFlow().map { list -> list.find { it.date == date } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeContainers: StateFlow<List<ContainerItem>> = _activeDate.flatMapLatest { date ->
        repository.getContainersFlow(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allContainersHistory: StateFlow<List<ContainerItem>> = repository.getAllContainersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDamagedBoxes: StateFlow<List<DamagedBox>> = _activeDate.flatMapLatest { date ->
        repository.getDamagedBoxesFlow(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDockConditions: StateFlow<List<DockCondition>> = _activeDate.flatMapLatest { date ->
        repository.getDockConditionsFlow(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Suppress("DEPRECATION")
    private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    @Suppress("DEPRECATION")
    val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount.asStateFlow()

    private val _driveSyncState = MutableStateFlow<DriveSyncState>(DriveSyncState.Idle)
    val driveSyncState: StateFlow<DriveSyncState> = _driveSyncState.asStateFlow()

    private val _showLoadInventoryDialog = MutableStateFlow(false)
    val showLoadInventoryDialog: StateFlow<Boolean> = _showLoadInventoryDialog.asStateFlow()

    init {
        // Initialize active report for today and seed master data
        viewModelScope.launch {
            val today = getTodayDateString()
            repository.getOrCreateReport(today)
            PreloadedDatabase.seedToHistory(repository)
            
            // If the current report has NO containers, suggest loading the snapshot
            val containers = repository.dao.getContainersForReport(today)
            if (containers.isEmpty()) {
                _showLoadInventoryDialog.value = true
            }
        }
    }

    fun loadInitialInventory() {
        viewModelScope.launch {
            val today = _activeDate.value
            PreloadedDatabase.loadSnapshotJuly7(repository, today)
            _showLoadInventoryDialog.value = false
        }
    }

    fun dismissLoadInventoryDialog() {
        _showLoadInventoryDialog.value = false
    }

    fun setActiveDate(date: String) {
        _activeDate.value = date
        viewModelScope.launch {
            repository.getOrCreateReport(date)
        }
    }

    @Suppress("DEPRECATION")
    fun checkUserSignedIn(context: Context) {
        _googleAccount.value = GoogleSignIn.getLastSignedInAccount(context)
    }

    @Suppress("DEPRECATION")
    fun setGoogleAccount(account: GoogleSignInAccount?) {
        _googleAccount.value = account
    }

    // Containers Management
    fun addContainer(
        type: String,
        locationType: String,
        dockNumber: Int?,
        economicNumber: String,
        plates: String,
        platesLocation: String,
        status: String,
        loadStatus: String?,
        sealCode: String?,
        containerSize: String? = null,
        destination: String? = null,
        deliveryDocument: String? = null,
        processedDate: String? = null,
        isStorageOnly: Boolean = false,
        mexCalType: String? = null,
        receptionDate: String? = null,
        materialType: String? = "FG"
    ) {
        viewModelScope.launch {
            val reportId = _activeDate.value
            val category = if (locationType == "Andén" && dockNumber != null) {
                if (dockNumber in 1..9) "Exportación" else "Recibos/FG"
            } else {
                null
            }

            val companyValue = when (type) {
                "Caja Seca" -> "MEX-CAL"
                "Intermodal" -> "JBHUNT"
                "Contenedor Marítimo" -> "CONTENEDOR"
                else -> ""
            }

            val isRecibo = (locationType == "Andén" && dockNumber != null && dockNumber >= 10) || loadStatus == "En espera de descarga"
            val isExport = !isRecibo
            
            // Allow destination and delivery for "Recibos" too if they are Cargado + certain statuses
            val canHaveExportData = isExport || (status == "Cargada" && (loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar" || loadStatus == "En espera de salida"))
            
            val canHaveSeal = canHaveExportData || loadStatus == "Almacenaje" || (isStorageOnly && (loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar"))

            val item = ContainerItem(
                reportId = reportId,
                type = type,
                locationType = locationType,
                dockNumber = dockNumber,
                dockCategory = category,
                economicNumber = economicNumber.trim().uppercase(),
                plates = plates.trim().uppercase(),
                platesLocation = platesLocation.trim().uppercase(),
                status = status,
                loadStatus = if (status == "Cargada") loadStatus else null,
                sealCode = if (canHaveSeal) sealCode?.trim() else null,
                company = companyValue,
                containerSize = if (type == "Contenedor Marítimo" && status == "Vacía") containerSize?.trim() else null,
                destination = if (canHaveExportData) destination?.trim()?.uppercase() else null,
                deliveryDocument = if (canHaveExportData) deliveryDocument?.trim()?.uppercase() else null,
                processedDate = if (canHaveExportData && loadStatus != "Almacenaje") processedDate?.trim()?.uppercase() else null,
                isStorageOnly = isStorageOnly,
                mexCalType = mexCalType,
                receptionDate = receptionDate,
                materialType = materialType
            )
            repository.addContainerItem(item)
        }
    }

    fun deleteContainer(item: ContainerItem) {
        viewModelScope.launch {
            repository.removeContainerItem(item)
        }
    }

    fun deleteContainerById(id: Long) {
        viewModelScope.launch {
            repository.removeContainerItemById(id)
        }
    }

    fun editContainer(
        id: Long,
        type: String,
        locationType: String,
        dockNumber: Int?,
        economicNumber: String,
        plates: String,
        platesLocation: String,
        status: String,
        loadStatus: String?,
        sealCode: String?,
        containerSize: String? = null,
        destination: String? = null,
        deliveryDocument: String? = null,
        processedDate: String? = null,
        isStorageOnly: Boolean = false,
        mexCalType: String? = null,
        receptionDate: String? = null,
        materialType: String? = "FG"
    ) {
        viewModelScope.launch {
            val reportId = _activeDate.value
            val category = if (locationType == "Andén" && dockNumber != null) {
                if (dockNumber in 1..9) "Exportación" else "Recibos/FG"
            } else {
                null
            }

            val companyValue = when (type) {
                "Caja Seca" -> "MEX-CAL"
                "Intermodal" -> "JBHUNT"
                "Contenedor Marítimo" -> "CONTENEDOR"
                else -> ""
            }

            val isRecibo = (locationType == "Andén" && dockNumber != null && dockNumber >= 10) || loadStatus == "En espera de descarga"
            val isExport = !isRecibo
            
            // Allow destination and delivery for "Recibos" too if they are Cargado + certain statuses
            val canHaveExportData = isExport || (status == "Cargada" && (loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar" || loadStatus == "En espera de salida"))
            
            val canHaveSeal = canHaveExportData || loadStatus == "Almacenaje" || (isStorageOnly && (loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar"))

            val item = ContainerItem(
                id = id,
                reportId = reportId,
                type = type,
                locationType = locationType,
                dockNumber = dockNumber,
                dockCategory = category,
                economicNumber = economicNumber.trim().uppercase(),
                plates = plates.trim().uppercase(),
                platesLocation = platesLocation.trim().uppercase(),
                status = status,
                loadStatus = if (status == "Cargada") loadStatus else null,
                sealCode = if (canHaveSeal) sealCode?.trim() else null,
                company = companyValue,
                containerSize = if (type == "Contenedor Marítimo" && status == "Vacía") containerSize?.trim() else null,
                destination = if (canHaveExportData) destination?.trim()?.uppercase() else null,
                deliveryDocument = if (canHaveExportData) deliveryDocument?.trim()?.uppercase() else null,
                processedDate = if (canHaveExportData && loadStatus != "Almacenaje") processedDate?.trim()?.uppercase() else null,
                isStorageOnly = isStorageOnly,
                mexCalType = mexCalType,
                receptionDate = receptionDate,
                materialType = materialType
            )
            repository.addContainerItem(item)
        }
    }

    // Damaged Boxes
    fun addDamagedBox(economicNumber: String, type: String, description: String) {
        viewModelScope.launch {
            val reportId = _activeDate.value
            val box = DamagedBox(
                reportId = reportId,
                economicNumber = economicNumber.trim().uppercase(),
                type = type,
                damageDescription = description.trim()
            )
            repository.addDamagedBox(box)
        }
    }

    fun deleteDamagedBoxById(id: Long) {
        viewModelScope.launch {
            repository.removeDamagedBoxById(id)
        }
    }

    fun editDamagedBox(id: Long, economicNumber: String, type: String, description: String) {
        viewModelScope.launch {
            val reportId = _activeDate.value
            val box = DamagedBox(
                id = id,
                reportId = reportId,
                economicNumber = economicNumber.trim().uppercase(),
                type = type,
                damageDescription = description.trim()
            )
            repository.addDamagedBox(box)
        }
    }

    fun importMasterData(csvContent: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.importUnitsFromCsv(csvContent)
            onComplete(count)
        }
    }

    fun updateActiveReportMetadata(shift: String, supervisorName: String) {
        viewModelScope.launch {
            val date = _activeDate.value
            val report = repository.getOrCreateReport(date)
            val updated = report.copy(shift = shift, supervisorName = supervisorName)
            repository.updateReport(updated)
        }
    }

    // Dock Conditions
    fun updateDockCondition(
        dockNumber: Int,
        isRampGood: Boolean,
        isRampLockGood: Boolean,
        isDockHookGood: Boolean,
        isCurtainGood: Boolean,
        isShelterGood: Boolean,
        isRubbersGood: Boolean,
        isSafetyStoppersGood: Boolean,
        isTrafficLightsGood: Boolean,
        isLightsGood: Boolean,
        isEmpaquesGood: Boolean,
        isGatoHidraulicoGood: Boolean,
        isCadenaGood: Boolean,
        notes: String,
        isOutOfService: Boolean = false
    ) {
        viewModelScope.launch {
            val reportId = _activeDate.value
            val cond = DockCondition(
                id = "${reportId}_$dockNumber",
                reportId = reportId,
                dockNumber = dockNumber,
                isRampGood = isRampGood,
                isRampLockGood = isRampLockGood,
                isDockHookGood = isDockHookGood,
                isCurtainGood = isCurtainGood,
                isShelterGood = isShelterGood,
                isRubbersGood = isRubbersGood,
                isSafetyStoppersGood = isSafetyStoppersGood,
                isTrafficLightsGood = isTrafficLightsGood,
                isLightsGood = isLightsGood,
                isEmpaquesGood = isEmpaquesGood,
                isGatoHidraulicoGood = isGatoHidraulicoGood,
                isCadenaGood = isCadenaGood,
                notes = notes.trim(),
                isOutOfService = isOutOfService
            )
            repository.updateDockCondition(cond)
        }
    }

    // Drive Sync
    fun syncWithDrive(context: Context) {
        val account = _googleAccount.value
        if (account == null) {
            _driveSyncState.value = DriveSyncState.Error("No se ha iniciado sesión con Google.")
            return
        }

        _driveSyncState.value = DriveSyncState.Syncing
        viewModelScope.launch {
            try {
                val reportId = _activeDate.value
                val token = DriveUploader.getAccessToken(context, account)
                if (token == null) {
                    _driveSyncState.value = DriveSyncState.Error("No se pudo obtener el token de acceso de Google.")
                    return@launch
                }

                val csvContent = repository.generateCsvPlain(reportId)
                val fileName = "Reporte_Andenes_y_Patio_${reportId}.csv"

                val fileId = DriveUploader.uploadCsvToDrive(token, fileName, csvContent)
                if (fileId != null) {
                    // Update report status in DB
                    activeReport.value?.let { report ->
                        repository.updateReport(report.copy(syncedToDrive = true, driveFileId = fileId))
                    }
                    _driveSyncState.value = DriveSyncState.Success(fileId)
                } else {
                    _driveSyncState.value = DriveSyncState.Error("Error al subir el archivo a Google Drive.")
                }
            } catch (e: Exception) {
                Log.e("ReportViewModel", "Sync error", e)
                _driveSyncState.value = DriveSyncState.Error("Excepción de red: ${e.localizedMessage}")
            }
        }
    }

    fun resetDriveSyncState() {
        _driveSyncState.value = DriveSyncState.Idle
    }

    // Local CSV/Excel Export
    suspend fun exportCsvLocally(context: Context, format: String = "excel"): File {
        val reportId = _activeDate.value
        return if (format == "csv") {
            val content = repository.generateCsvPlain(reportId)
            repository.saveContentToFile(context, reportId, content, "Reporte_Completo", ".csv")
        } else {
            val content = repository.generateCsvString(reportId)
            repository.saveContentToFile(context, reportId, content, "Reporte_Completo", ".xls")
        }
    }

    suspend fun exportContainersCsvLocally(context: Context, format: String = "excel"): File {
        val reportId = _activeDate.value
        return if (format == "csv") {
            val content = repository.generateContainersCsvPlain(reportId)
            repository.saveContentToFile(context, reportId, content, "Inventario_Cajas", ".csv")
        } else {
            val content = repository.generateContainersCsvString(reportId)
            repository.saveContentToFile(context, reportId, content, "Inventario_Cajas", ".xls")
        }
    }

    suspend fun exportDockConditionsCsvLocally(context: Context, format: String = "excel"): File {
        val reportId = _activeDate.value
        return if (format == "csv") {
            val content = repository.generateDockConditionsCsvPlain(reportId)
            repository.saveContentToFile(context, reportId, content, "Condicion_Andenes", ".csv")
        } else {
            val content = repository.generateDockConditionsCsvString(reportId)
            repository.saveContentToFile(context, reportId, content, "Condicion_Andenes", ".xls")
        }
    }

    suspend fun exportDamagedBoxesCsvLocally(context: Context, format: String = "excel"): File {
        val reportId = _activeDate.value
        return if (format == "csv") {
            val content = repository.generateDamagedBoxesCsvPlain(reportId)
            repository.saveContentToFile(context, reportId, content, "Reporte_Cajas_Danadas", ".csv")
        } else {
            val content = repository.generateDamagedBoxesCsvString(reportId)
            repository.saveContentToFile(context, reportId, content, "Reporte_Cajas_Danadas", ".xls")
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}

class ReportViewModelFactory(private val repository: ReportRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

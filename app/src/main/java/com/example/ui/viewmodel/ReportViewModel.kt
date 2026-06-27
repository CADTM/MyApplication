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

class ReportViewModel(private val repository: ReportRepository) : ViewModel() {

    private val _activeDate = MutableStateFlow(getTodayDateString())
    val activeDate: StateFlow<String> = _activeDate.asStateFlow()

    val allReports: StateFlow<List<DailyReport>> = repository.getAllReportsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _googleAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val googleAccount: StateFlow<GoogleSignInAccount?> = _googleAccount.asStateFlow()

    private val _driveSyncState = MutableStateFlow<DriveSyncState>(DriveSyncState.Idle)
    val driveSyncState: StateFlow<DriveSyncState> = _driveSyncState.asStateFlow()

    init {
        // Initialize active report for today
        viewModelScope.launch {
            repository.getOrCreateReport(getTodayDateString())
        }
    }

    fun setTodayDate() {
        setActiveDate(getTodayDateString())
    }

    fun setActiveDate(date: String) {
        _activeDate.value = date
        viewModelScope.launch {
            repository.getOrCreateReport(date)
        }
    }

    fun checkUserSignedIn(context: Context) {
        _googleAccount.value = GoogleSignIn.getLastSignedInAccount(context)
    }

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
        processedDate: String? = null
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

            val isDepartureWaiting = status == "Cargada" && loadStatus == "En espera de salida"

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
                sealCode = if (isDepartureWaiting) sealCode?.trim() else null,
                company = companyValue,
                containerSize = if (type == "Contenedor Marítimo" && status == "Vacía") containerSize?.trim() else null,
                destination = if (isDepartureWaiting) destination?.trim()?.uppercase() else null,
                deliveryDocument = if (isDepartureWaiting) deliveryDocument?.trim()?.uppercase() else null,
                processedDate = if (isDepartureWaiting) processedDate?.trim()?.uppercase() else null
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
        processedDate: String? = null
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

            val isDepartureWaiting = status == "Cargada" && loadStatus == "En espera de salida"

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
                sealCode = if (isDepartureWaiting) sealCode?.trim() else null,
                company = companyValue,
                containerSize = if (type == "Contenedor Marítimo" && status == "Vacía") containerSize?.trim() else null,
                destination = if (isDepartureWaiting) destination?.trim()?.uppercase() else null,
                deliveryDocument = if (isDepartureWaiting) deliveryDocument?.trim()?.uppercase() else null,
                processedDate = if (isDepartureWaiting) processedDate?.trim()?.uppercase() else null
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

                val csvContent = repository.generateCsvString(reportId)
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

    // Local CSV Export
    suspend fun exportCsvLocally(context: Context): File {
        val reportId = _activeDate.value
        val csvContent = repository.generateCsvString(reportId)
        return repository.saveCsvToFile(context, reportId, csvContent)
    }

    suspend fun exportContainersCsvLocally(context: Context): File {
        val reportId = _activeDate.value
        val csvContent = repository.generateContainersCsvString(reportId)
        return repository.saveCsvToFileWithPrefix(context, reportId, csvContent, "Inventario_Cajas")
    }

    suspend fun exportDockConditionsCsvLocally(context: Context): File {
        val reportId = _activeDate.value
        val csvContent = repository.generateDockConditionsCsvString(reportId)
        return repository.saveCsvToFileWithPrefix(context, reportId, csvContent, "Condicion_Andenes")
    }

    suspend fun exportDamagedBoxesCsvLocally(context: Context): File {
        val reportId = _activeDate.value
        val csvContent = repository.generateDamagedBoxesCsvString(reportId)
        return repository.saveCsvToFileWithPrefix(context, reportId, csvContent, "Reporte_Cajas_Danadas")
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

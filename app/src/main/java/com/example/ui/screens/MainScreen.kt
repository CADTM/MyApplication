@file:Suppress("DEPRECATION")

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.*
import com.example.network.DriveUploader
import com.example.ui.viewmodel.DriveSyncState
import com.example.ui.viewmodel.ReportViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ReportViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeDate by viewModel.activeDate.collectAsState()
    val containers by viewModel.activeContainers.collectAsState()
    val allContainersHistory by viewModel.allContainersHistory.collectAsState()
    val damagedBoxes by viewModel.activeDamagedBoxes.collectAsState()
    val dockConditions by viewModel.activeDockConditions.collectAsState()
    val googleAccount by viewModel.googleAccount.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()
    val showLoadInventoryDialog by viewModel.showLoadInventoryDialog.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Andenes, 1: Patio, 2: Resumen, 3: Inspección, 4: Dañadas, 5: Sincronizar

    var showAddContainerDialog by remember { mutableStateOf(false) }
    var editingContainer by remember { mutableStateOf<ContainerItem?>(null) }
    var presetDockNumber by remember { mutableStateOf<Int?>(null) }
    var presetLocationType by remember { mutableStateOf("Andén") }

    var showAddDamagedBoxDialog by remember { mutableStateOf(false) }
    var editingDamagedBox by remember { mutableStateOf<DamagedBox?>(null) }
    var showDockInspectionDialog by remember { mutableStateOf<DockCondition?>(null) }

    // State for filtering by summary card
    var selectedSummaryFilter by remember { mutableStateOf<String?>(null) }

    // State variables for moving/transferring a unit already registered in another location
    var showMoveConfirmationDialog by remember { mutableStateOf(false) }
    var pendingMoveContainer by remember { mutableStateOf<ContainerItem?>(null) }
    var pendingMoveType by remember { mutableStateOf("") }
    var pendingMoveLocationType by remember { mutableStateOf("") }
    var pendingMoveDockNumber by remember { mutableStateOf<Int?>(null) }
    var pendingMoveEconomicNumber by remember { mutableStateOf("") }
    var pendingMovePlates by remember { mutableStateOf("") }
    var pendingMovePlatesLocation by remember { mutableStateOf("") }
    var pendingMoveStatus by remember { mutableStateOf("") }
    var pendingMoveLoadStatus by remember { mutableStateOf<String?>(null) }
    var pendingMoveSealCode by remember { mutableStateOf<String?>(null) }
    var pendingMoveContainerSize by remember { mutableStateOf<String?>(null) }
    var pendingMoveDestination by remember { mutableStateOf<String?>(null) }
    var pendingMoveDeliveryDocument by remember { mutableStateOf<String?>(null) }
    var pendingMoveProcessedDate by remember { mutableStateOf<String?>(null) }
    var pendingMoveIsStorageOnly by remember { mutableStateOf(false) }
    var pendingMoveMexCalType by remember { mutableStateOf<String?>(null) }
    var pendingMoveReceptionDate by remember { mutableStateOf<String?>(null) }
    var pendingMoveMaterialType by remember { mutableStateOf<String?>(null) }

    // State variables for duplicate checking in Damaged Boxes
    var showDamagedDuplicateDialog by remember { mutableStateOf(false) }
    var pendingDamagedBox by remember { mutableStateOf<DamagedBox?>(null) }
    var pendingDamagedEconomicNumber by remember { mutableStateOf("") }
    var pendingDamagedType by remember { mutableStateOf("") }
    var pendingDamagedDescription by remember { mutableStateOf("") }

    // Init check
    LaunchedEffect(Unit) {
        viewModel.checkUserSignedIn(context)
    }

    // Date picker setup
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            viewModel.setActiveDate(sdf.format(cal.time))
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Sign-In launcher
    val gso = DriveUploader.getGoogleSignInOptions()
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.setGoogleAccount(account)
            Toast.makeText(context, "Sincronizado con Google: ${account.email}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainScreen", "Google Sign-In failed", e)
            Toast.makeText(context, "Error de autenticación Google: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Control de Andenes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Logística e Inventario",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { datePickerDialog.show() },
                            modifier = Modifier.testTag("date_picker_button")
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = "Seleccionar fecha",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 0 || activeTab == 1 || activeTab == 2) {
                FloatingActionButton(
                    onClick = {
                        presetLocationType = "" // Start without location to avoid overwriting
                        presetDockNumber = null
                        showAddContainerDialog = true
                    },
                    modifier = Modifier.testTag("add_item_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Registrar unidad")
                }
            } else if (activeTab == 4) {
                FloatingActionButton(
                    onClick = { showAddDamagedBoxDialog = true },
                    modifier = Modifier.testTag("add_damaged_fab"),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(Icons.Default.ReportProblem, contentDescription = "Registrar daño")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Hero section with logistics banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_logistics),
                    contentDescription = "Banner de Logística",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "INVENTARIO DE CAJAS Y CONTENEDORES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { datePickerDialog.show() }
                    ) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = "Fecha",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDatePretty(activeDate),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Cambiar fecha",
                            tint = Color.White
                        )
                    }
                }
            }

            // Tabs bar
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = MaterialTheme.colorScheme.onPrimary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Andenes", maxLines = 1) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Patio", maxLines = 1) },
                    icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Resumen", maxLines = 1) },
                    icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Tab(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    text = { Text("Inspección", maxLines = 1) },
                    icon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Tab(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    text = { Text("Rechazadas", maxLines = 1) },
                    icon = { Icon(Icons.Default.CancelPresentation, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Tab(
                    selected = activeTab == 5,
                    onClick = { activeTab = 5 },
                    text = { Text("Exportar", maxLines = 1) },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }

            // Tab contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> DocksTabContent(
                        containers = containers,
                        dockConditions = dockConditions,
                        onAddContainer = { dockNum ->
                            editingContainer = null
                            presetLocationType = "Andén"
                            presetDockNumber = dockNum
                            showAddContainerDialog = true
                        },
                        onEditContainer = { item ->
                            editingContainer = item
                            presetLocationType = item.locationType
                            presetDockNumber = item.dockNumber
                            showAddContainerDialog = true
                        },
                        onDeleteContainer = { viewModel.deleteContainer(it) }
                    )
                    1 -> YardTabContent(
                        containers = containers,
                        onEditContainer = { item ->
                            editingContainer = item
                            presetLocationType = item.locationType
                            presetDockNumber = item.dockNumber
                            showAddContainerDialog = true
                        },
                        onDeleteContainer = { viewModel.deleteContainer(it) }
                    )
                    2 -> SummaryTabContent(
                        containers = containers,
                        selectedFilter = selectedSummaryFilter,
                        onFilterSelected = { 
                            selectedSummaryFilter = if (selectedSummaryFilter == it) null else it 
                        },
                        onEditContainer = { item ->
                            editingContainer = item
                            presetLocationType = item.locationType
                            presetDockNumber = item.dockNumber
                            showAddContainerDialog = true
                        },
                        onDeleteContainer = { viewModel.deleteContainer(it) }
                    )
                    3 -> DockConditionsTabContent(
                        conditions = dockConditions,
                        onInspectDock = { showDockInspectionDialog = it }
                    )
                    4 -> DamagedBoxesTabContent(
                        damagedBoxes = damagedBoxes,
                        onEditDamaged = { box ->
                            editingDamagedBox = box
                            showAddDamagedBoxDialog = true
                        },
                        onDeleteDamaged = { viewModel.deleteDamagedBoxById(it) }
                    )
                    5 -> SyncAndExportTabContent(
                        viewModel = viewModel,
                        googleAccount = googleAccount,
                        driveSyncState = driveSyncState,
                        onSignIn = { signInLauncher.launch(googleSignInClient.signInIntent) },
                        onSignOut = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                viewModel.setGoogleAccount(null)
                                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Dialogs
        if (showAddContainerDialog) {
            AddContainerDialog(
                locationTypePreset = presetLocationType,
                dockNumberPreset = presetDockNumber,
                unitToEdit = editingContainer,
                containersHistory = allContainersHistory,
                onDismiss = {
                    showAddContainerDialog = false
                    editingContainer = null
                },
                onConfirm = { type, locType, dockNum, ecoNum, plates, platesLoc, status, loadStatus, seal, size, dest, deliv, procDate, isStorage, mexCal, recepDate, matType ->
                    val currentEdit = editingContainer
                    val duplicateUnit = containers.find {
                        it.economicNumber.trim().equals(ecoNum.trim(), ignoreCase = true) &&
                        (it.locationType != locType || it.dockNumber != dockNum) &&
                        (currentEdit == null || it.id != currentEdit.id)
                    }

                    if (duplicateUnit != null) {
                        pendingMoveContainer = duplicateUnit
                        pendingMoveType = type
                        pendingMoveLocationType = locType
                        pendingMoveDockNumber = dockNum
                        pendingMoveEconomicNumber = ecoNum
                        pendingMovePlates = plates
                        pendingMovePlatesLocation = platesLoc
                        pendingMoveStatus = status
                        pendingMoveLoadStatus = loadStatus
                        pendingMoveSealCode = seal
                        pendingMoveContainerSize = size
                        pendingMoveDestination = dest
                        pendingMoveDeliveryDocument = deliv
                        pendingMoveProcessedDate = procDate
                        pendingMoveIsStorageOnly = isStorage
                        pendingMoveMexCalType = mexCal
                        pendingMoveReceptionDate = recepDate
                        pendingMoveMaterialType = matType
                        showMoveConfirmationDialog = true
                    } else {
                        if (currentEdit != null) {
                            viewModel.editContainer(
                                id = currentEdit.id,
                                type = type,
                                locationType = locType,
                                dockNumber = dockNum,
                                economicNumber = ecoNum,
                                plates = plates,
                                platesLocation = platesLoc,
                                status = status,
                                loadStatus = loadStatus,
                                sealCode = seal,
                                containerSize = size,
                                destination = dest,
                                deliveryDocument = deliv,
                                processedDate = procDate,
                                isStorageOnly = isStorage,
                                mexCalType = mexCal,
                                receptionDate = recepDate,
                                materialType = matType
                            )
                            Toast.makeText(context, "Unidad modificada con éxito", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addContainer(
                                type = type,
                                locationType = locType,
                                dockNumber = dockNum,
                                economicNumber = ecoNum,
                                plates = plates,
                                platesLocation = platesLoc,
                                status = status,
                                loadStatus = loadStatus,
                                sealCode = seal,
                                containerSize = size,
                                destination = dest,
                                deliveryDocument = deliv,
                                processedDate = procDate,
                                isStorageOnly = isStorage,
                                mexCalType = mexCal,
                                receptionDate = recepDate,
                                materialType = matType
                            )
                            Toast.makeText(context, "Unidad registrada con éxito", Toast.LENGTH_SHORT).show()
                        }
                        showAddContainerDialog = false
                        editingContainer = null
                    }
                }
            )
        }

        if (showMoveConfirmationDialog && pendingMoveContainer != null) {
            val duplicate = pendingMoveContainer!!
            AlertDialog(
                onDismissRequest = {
                    showMoveConfirmationDialog = false
                    pendingMoveContainer = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Unidad ya registrada",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "La unidad con el número económico \"${duplicate.economicNumber}\" ya está registrada en ${duplicate.locationType.uppercase()}.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "¿Deseas mover/transferir esta unidad a ${pendingMoveLocationType.uppercase()}? Esto la removerá de ${duplicate.locationType.uppercase()}.",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            coroutineScope.launch {
                                // Delete the duplicate (old location)
                                viewModel.deleteContainerById(duplicate.id)
                                
                                // Insert/Update the new one
                                val currentEdit = editingContainer
                                if (currentEdit != null) {
                                    viewModel.editContainer(
                                        id = currentEdit.id,
                                        type = pendingMoveType,
                                        locationType = pendingMoveLocationType,
                                        dockNumber = pendingMoveDockNumber,
                                        economicNumber = pendingMoveEconomicNumber,
                                        plates = pendingMovePlates,
                                        platesLocation = pendingMovePlatesLocation,
                                        status = pendingMoveStatus,
                                        loadStatus = pendingMoveLoadStatus,
                                        sealCode = pendingMoveSealCode,
                                        containerSize = pendingMoveContainerSize,
                                        destination = pendingMoveDestination,
                                        deliveryDocument = pendingMoveDeliveryDocument,
                                        processedDate = pendingMoveProcessedDate,
                                        isStorageOnly = pendingMoveIsStorageOnly,
                                        mexCalType = pendingMoveMexCalType,
                                        receptionDate = pendingMoveReceptionDate,
                                        materialType = pendingMoveMaterialType
                                    )
                                    Toast.makeText(context, "Unidad movida y modificada con éxito", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addContainer(
                                        type = pendingMoveType,
                                        locationType = pendingMoveLocationType,
                                        dockNumber = pendingMoveDockNumber,
                                        economicNumber = pendingMoveEconomicNumber,
                                        plates = pendingMovePlates,
                                        platesLocation = pendingMovePlatesLocation,
                                        status = pendingMoveStatus,
                                        loadStatus = pendingMoveLoadStatus,
                                        sealCode = pendingMoveSealCode,
                                        containerSize = pendingMoveContainerSize,
                                        destination = pendingMoveDestination,
                                        deliveryDocument = pendingMoveDeliveryDocument,
                                        processedDate = pendingMoveProcessedDate,
                                        isStorageOnly = pendingMoveIsStorageOnly,
                                        mexCalType = pendingMoveMexCalType,
                                        receptionDate = pendingMoveReceptionDate,
                                        materialType = pendingMoveMaterialType
                                    )
                                    Toast.makeText(context, "Unidad movida y registrada con éxito", Toast.LENGTH_SHORT).show()
                                }
                                
                                // Reset state
                                showMoveConfirmationDialog = false
                                pendingMoveContainer = null
                                showAddContainerDialog = false
                                editingContainer = null
                            }
                        }
                    ) {
                        Text("Sí, mover")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showMoveConfirmationDialog = false
                            pendingMoveContainer = null
                        }
                    ) {
                        Text("No, cancelar")
                    }
                }
            )
        }

        if (showAddDamagedBoxDialog) {
            AddDamagedBoxDialog(
                boxToEdit = editingDamagedBox,
                containersHistory = allContainersHistory,
                onDismiss = {
                    showAddDamagedBoxDialog = false
                    editingDamagedBox = null
                },
                onConfirm = { ecoNum, type, description ->
                    val currentEdit = editingDamagedBox
                    val duplicateDamaged = damagedBoxes.find {
                        it.economicNumber.trim().equals(ecoNum.trim(), ignoreCase = true) &&
                        (currentEdit == null || it.id != currentEdit.id)
                    }

                    if (duplicateDamaged != null) {
                        pendingDamagedBox = duplicateDamaged
                        pendingDamagedEconomicNumber = ecoNum
                        pendingDamagedType = type
                        pendingDamagedDescription = description
                        showDamagedDuplicateDialog = true
                    } else {
                        if (currentEdit != null) {
                            viewModel.editDamagedBox(currentEdit.id, ecoNum, type, description)
                            Toast.makeText(context, "Reporte de daño modificado", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addDamagedBox(ecoNum, type, description)
                            Toast.makeText(context, "Reporte de daño registrado", Toast.LENGTH_SHORT).show()
                        }
                        showAddDamagedBoxDialog = false
                        editingDamagedBox = null
                    }
                }
            )
        }

        if (showDamagedDuplicateDialog && pendingDamagedBox != null) {
            val duplicate = pendingDamagedBox!!
            AlertDialog(
                onDismissRequest = {
                    showDamagedDuplicateDialog = false
                    pendingDamagedBox = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Unidad ya reportada con daño",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "La unidad con el número económico \"${duplicate.economicNumber}\" ya está registrada en el inventario de cajas dañadas de hoy.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "¿Deseas actualizar el reporte de daño existente con la nueva descripción y tipo?",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.editDamagedBox(
                                    id = duplicate.id,
                                    economicNumber = pendingDamagedEconomicNumber,
                                    type = pendingDamagedType,
                                    description = pendingDamagedDescription
                                )
                                Toast.makeText(context, "Reporte de daño actualizado con éxito", Toast.LENGTH_SHORT).show()
                                
                                // Reset state
                                showDamagedDuplicateDialog = false
                                pendingDamagedBox = null
                                showAddDamagedBoxDialog = false
                                editingDamagedBox = null
                            }
                        }
                    ) {
                        Text("Sí, actualizar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDamagedDuplicateDialog = false
                            pendingDamagedBox = null
                        }
                    ) {
                        Text("No, cancelar")
                    }
                }
            )
        }

        if (showLoadInventoryDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissLoadInventoryDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cargar Inventario")
                    }
                },
                text = {
                    Text("Se ha detectado que el inventario del día de hoy está vacío.\n\n¿Deseas cargar el inventario base (Snapshot del 7 de Julio) para comenzar a trabajar?")
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.loadInitialInventory()
                    }) {
                        Text("Sí, cargar")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.dismissLoadInventoryDialog() }) {
                        Text("No, dejar vacío")
                    }
                }
            )
        }

        showDockInspectionDialog?.let { condition ->
            InspectDockDialog(
                condition = condition,
                onDismiss = { showDockInspectionDialog = null },
                onConfirm = { ramp, rLock, hook, curtain, shelter, rubbers, stoppers, traffic, lights, empaques, gato, cadena, notes, isOutOfService ->
                    viewModel.updateDockCondition(
                        dockNumber = condition.dockNumber,
                        isRampGood = ramp,
                        isRampLockGood = rLock,
                        isDockHookGood = hook,
                        isCurtainGood = curtain,
                        isShelterGood = shelter,
                        isRubbersGood = rubbers,
                        isSafetyStoppersGood = stoppers,
                        isTrafficLightsGood = traffic,
                        isLightsGood = lights,
                        isEmpaquesGood = empaques,
                        isGatoHidraulicoGood = gato,
                        isCadenaGood = cadena,
                        notes = notes,
                        isOutOfService = isOutOfService
                    )
                    showDockInspectionDialog = null
                    Toast.makeText(context, "Estatus de andén ${condition.dockNumber} actualizado", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// --------------------------------------------------------
// SUB-TAB COMPOSABLES
// --------------------------------------------------------

@Composable
fun DocksTabContent(
    containers: List<ContainerItem>,
    dockConditions: List<DockCondition>,
    onAddContainer: (Int) -> Unit,
    onEditContainer: (ContainerItem) -> Unit,
    onDeleteContainer: (ContainerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockContainersMap = remember(containers) {
        containers.filter { it.locationType == "Andén" }.associateBy { it.dockNumber ?: 0 }
    }

    val dockConditionsMap = remember(dockConditions) {
        dockConditions.associateBy { it.dockNumber }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Group 1: Exportación (Andenes 1 al 9)
        item {
            SectionHeader(title = "Andenes de Exportación (1 al 9)", icon = Icons.Default.FileUpload, color = MaterialTheme.colorScheme.primary)
        }

        items((1..9).toList()) { dockNum ->
            val unit = dockContainersMap[dockNum]
            val cond = dockConditionsMap[dockNum]
            DockRowCard(
                dockNumber = dockNum,
                unit = unit,
                onAdd = { onAddContainer(dockNum) },
                onEdit = { unit?.let { onEditContainer(it) } },
                onDelete = { unit?.let { onDeleteContainer(it) } },
                isOutOfService = cond?.isOutOfService == true
            )
        }

        // Group 2: Recibos / Finish Goods (Andenes 10 al 14)
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "Andenes de Recibos y FG (10 al 14)", icon = Icons.Default.FileDownload, color = MaterialTheme.colorScheme.secondary)
        }

        items((10..14).toList()) { dockNum ->
            val unit = dockContainersMap[dockNum]
            val cond = dockConditionsMap[dockNum]
            DockRowCard(
                dockNumber = dockNum,
                unit = unit,
                onAdd = { onAddContainer(dockNum) },
                onEdit = { unit?.let { onEditContainer(it) } },
                onDelete = { unit?.let { onDeleteContainer(it) } },
                isOutOfService = cond?.isOutOfService == true
            )
        }
    }
}

@Composable
fun SummaryTabContent(
    containers: List<ContainerItem>,
    selectedFilter: String?,
    onFilterSelected: (String) -> Unit,
    onEditContainer: (ContainerItem) -> Unit,
    onDeleteContainer: (ContainerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredContainers = remember(containers, selectedFilter) {
        if (selectedFilter == null) emptyList()
        else containers.filter { it.matchesFilter(selectedFilter) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            InventorySummaryCards(
                allContainers = containers,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected
            )
        }

        if (selectedFilter != null) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detalle de: $selectedFilter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (filteredContainers.isEmpty()) {
                item {
                    Text(
                        "No hay unidades que coincidan con este criterio.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = Color.Gray
                    )
                }
            } else {
                items(filteredContainers) { container ->
                    ContainerItemCard(
                        container = container,
                        onEdit = { onEditContainer(container) },
                        onDelete = { onDeleteContainer(container) }
                    )
                }
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Toca una tarjeta para ver el detalle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YardTabContent(
    containers: List<ContainerItem>,
    onEditContainer: (ContainerItem) -> Unit,
    onDeleteContainer: (ContainerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val patioContainers = remember(containers) {
        containers.filter { it.locationType == "Patio" }
            .sortedWith(compareBy {
                // Ordering as requested:
                // 1. En espera de salida (Intermodales, luego Cajas, Mex-cal, Marítimos)
                // 2. Intermodales vacíos
                // 3. Mex-cal vacías
                // 4. Contenedores vacíos
                // 5. Almacenaje
                when {
                    it.loadStatus == "En espera de salida" -> {
                        when (it.type) {
                            "Intermodal" -> 10
                            "Caja Seca" -> if (it.company.contains("MEX-CAL", ignoreCase = true)) 12 else 11
                            "Contenedor Marítimo" -> 13
                            else -> 14
                        }
                    }
                    it.type == "Intermodal" && it.status.startsWith("Vac", ignoreCase = true) -> 20
                    it.company.contains("MEX-CAL", ignoreCase = true) && it.status.startsWith("Vac", ignoreCase = true) -> 30
                    it.type == "Contenedor Marítimo" && it.status.startsWith("Vac", ignoreCase = true) -> 40
                    it.loadStatus == "Almacenaje" || it.isStorageOnly -> 50
                    else -> 100
                }
            })
    }

    if (patioContainers.isEmpty()) {
        EmptyStateView(
            title = "Sin unidades en Patio",
            subtitle = "Registra cajas secas o contenedores para llevar el conteo del patio.",
            icon = Icons.Outlined.LocalShipping
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detalle de unidades en patio:",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            items(patioContainers) { container ->
                ContainerItemCard(
                    container = container,
                    onEdit = { onEditContainer(container) },
                    onDelete = { onDeleteContainer(container) }
                )
            }
        }
    }
}

private fun ContainerItem.matchesFilter(filter: String): Boolean {
    return when (filter) {
        "LISTOS PARA EXPORTACIÓN" -> {
            status.startsWith("Carg", ignoreCase = true) &&
            loadStatus == "En espera de salida" &&
            !isStorageOnly &&
            !company.contains("ALMACENAJE", ignoreCase = true) &&
            (!company.contains("MEX-CAL", ignoreCase = true) || !destination.isNullOrBlank())
        }
        "Mex-cal export" -> {
            company.equals("MEX-CAL", ignoreCase = true) && 
            status.startsWith("Vac", ignoreCase = true) &&
            (economicNumber.startsWith("2130") || economicNumber.startsWith("2131") || economicNumber.startsWith("2132") || mexCalType == "Exportación")
        }
        "Mex-cal mxdc" -> {
            company.equals("MEX-CAL", ignoreCase = true) && 
            status.startsWith("Vac", ignoreCase = true) &&
            (economicNumber.startsWith("2133") || mexCalType == "Interplanta")
        }
        "Intermodal" -> {
            type == "Intermodal" && status.startsWith("Vac", ignoreCase = true)
        }
        "Contenedor vacio" -> {
            type == "Contenedor Marítimo" && status.startsWith("Vac", ignoreCase = true)
        }
        "FG recibos" -> {
            company.contains("MEX-CAL", ignoreCase = true) &&
            status.startsWith("Carg", ignoreCase = true) &&
            loadStatus == "En espera de descarga"
        }
        "Pendientes" -> {
            status.startsWith("Carg", ignoreCase = true) &&
            (loadStatus == "Falta por completar" || loadStatus == "Falta por procesar" || loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar")
        }
        "Almacenaje" -> {
            isStorageOnly || loadStatus == "Almacenaje" || company.contains("ALMACENAJE", ignoreCase = true)
        }
        "Total unidades vacias" -> {
            status.startsWith("Vac", ignoreCase = true)
        }
        else -> true
    }
}

@Composable
fun InventorySummaryCards(
    allContainers: List<ContainerItem>,
    selectedFilter: String?,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Mex-cal export (Vacíos de exportación)
    val totalMexCalExportVacio = allContainers.count { 
        it.company.equals("MEX-CAL", ignoreCase = true) && 
        it.status.startsWith("Vac", ignoreCase = true) &&
        (it.economicNumber.startsWith("2130") || it.economicNumber.startsWith("2131") || it.economicNumber.startsWith("2132") || it.mexCalType == "Exportación")
    }

    // 2. Mex-cal mxdc (Vacíos de interplanta)
    val totalMexCalMxdcVacio = allContainers.count { 
        it.company.equals("MEX-CAL", ignoreCase = true) && 
        it.status.startsWith("Vac", ignoreCase = true) &&
        (it.economicNumber.startsWith("2133") || it.mexCalType == "Interplanta")
    }
    
    // 3. Intermodal (Vacíos)
    val totalIntermodalVacio = allContainers.count { 
        it.type == "Intermodal" && it.status.startsWith("Vac", ignoreCase = true) 
    }
    
    // 4. Contenedor vacio (Marítimos)
    val totalContenedorVacio = allContainers.count { 
        it.type == "Contenedor Marítimo" && it.status.startsWith("Vac", ignoreCase = true) 
    }

    // --- ALIGNING WITH ReportRepository.kt logic ---
    
    // 5. FG recibos
    val totalFgRecibos = allContainers.count {
        it.company.contains("MEX-CAL", ignoreCase = true) &&
        it.status.startsWith("Carg", ignoreCase = true) &&
        it.loadStatus == "En espera de descarga"
    }
    
    // 6. Exportación (Ready to go)
    val totalExportacionReady = allContainers.count { 
        it.status.startsWith("Carg", ignoreCase = true) &&
        (it.loadStatus == "En espera de salida") &&
        !it.isStorageOnly &&
        !it.company.contains("ALMACENAJE", ignoreCase = true) &&
        (
            !it.company.contains("MEX-CAL", ignoreCase = true) || 
            !it.destination.isNullOrBlank()
        )
    }

    // 7. Pendientes
    val totalPendientes = allContainers.count {
        it.status.startsWith("Carg", ignoreCase = true) &&
        (it.loadStatus == "Falta por completar" || it.loadStatus == "Falta por procesar" || it.loadStatus == "Pendiente por completar" || it.loadStatus == "Pendiente por procesar")
    }

    // 8. Almacenaje
    val totalAlmacenaje = allContainers.count {
        it.isStorageOnly || it.loadStatus == "Almacenaje" || it.company.contains("ALMACENAJE", ignoreCase = true)
    }

    val totalVaciasHoy = allContainers.count { it.status.startsWith("Vac", ignoreCase = true) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- SECCIÓN DE LISTOS (ÉNFAISIS) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFilterSelected("LISTOS PARA EXPORTACIÓN") },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedFilter == "LISTOS PARA EXPORTACIÓN") Color(0xFF1565C0) else Color(0xFFE3F2FD)
            ),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(12.dp),
            border = if (selectedFilter == "LISTOS PARA EXPORTACIÓN") BorderStroke(2.dp, Color.White) else null
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(45.dp).background(
                        if (selectedFilter == "LISTOS PARA EXPORTACIÓN") Color.White else Color(0xFF1565C0), 
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocalShipping, 
                        null, 
                        tint = if (selectedFilter == "LISTOS PARA EXPORTACIÓN") Color(0xFF1565C0) else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val mainColor = if (selectedFilter == "LISTOS PARA EXPORTACIÓN") Color.White else Color(0xFF1565C0)
                    Text("LISTOS PARA EXPORTACIÓN", fontWeight = FontWeight.Black, fontSize = 13.sp, color = mainColor)
                    Text("Unidades cargadas en espera de salida", fontSize = 11.sp, color = mainColor.copy(0.7f))
                }
                Text(
                    text = "$totalExportacionReady",
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = if (selectedFilter == "LISTOS PARA EXPORTACIÓN") Color.White else Color(0xFF1565C0)
                )
            }
        }

        // --- FILA 1: MEX-CAL ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummarySmallCard(
                title = "Mex-cal export",
                count = totalMexCalExportVacio,
                color = if (selectedFilter == "Mex-cal export") Color(0xFF2E7D32) else Color(0xFFE8F5E9), 
                textColor = if (selectedFilter == "Mex-cal export") Color.White else Color(0xFF2E7D32),
                isSelected = selectedFilter == "Mex-cal export",
                onClick = { onFilterSelected("Mex-cal export") },
                modifier = Modifier.weight(1f)
            )
            SummarySmallCard(
                title = "Mex-cal mxdc",
                count = totalMexCalMxdcVacio,
                color = if (selectedFilter == "Mex-cal mxdc") Color(0xFF558B2F) else Color(0xFFF1F8E9), 
                textColor = if (selectedFilter == "Mex-cal mxdc") Color.White else Color(0xFF558B2F),
                isSelected = selectedFilter == "Mex-cal mxdc",
                onClick = { onFilterSelected("Mex-cal mxdc") },
                modifier = Modifier.weight(1f)
            )
        }

        // --- FILA 2: OTROS VACÍOS ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummarySmallCard(
                title = "Intermodal",
                count = totalIntermodalVacio,
                color = if (selectedFilter == "Intermodal") Color(0xFF7B1FA2) else Color(0xFFF3E5F5), 
                textColor = if (selectedFilter == "Intermodal") Color.White else Color(0xFF7B1FA2),
                isSelected = selectedFilter == "Intermodal",
                onClick = { onFilterSelected("Intermodal") },
                modifier = Modifier.weight(1f)
            )
            SummarySmallCard(
                title = "Contenedor vacio",
                count = totalContenedorVacio,
                color = if (selectedFilter == "Contenedor vacio") Color(0xFFC62828) else Color(0xFFFFEBEE), 
                textColor = if (selectedFilter == "Contenedor vacio") Color.White else Color(0xFFC62828),
                isSelected = selectedFilter == "Contenedor vacio",
                onClick = { onFilterSelected("Contenedor vacio") },
                modifier = Modifier.weight(1f)
            )
        }

        // --- FILA 3: RECIBOS / PENDIENTES ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummarySmallCard(
                title = "FG recibos",
                count = totalFgRecibos,
                color = if (selectedFilter == "FG recibos") Color(0xFFE65100) else Color(0xFFFFF3E0), 
                textColor = if (selectedFilter == "FG recibos") Color.White else Color(0xFFE65100),
                isSelected = selectedFilter == "FG recibos",
                onClick = { onFilterSelected("FG recibos") },
                modifier = Modifier.weight(1f)
            )
            SummarySmallCard(
                title = "Pendientes",
                count = totalPendientes,
                color = if (selectedFilter == "Pendientes") Color(0xFF424242) else Color(0xFFF5F5F5), 
                textColor = if (selectedFilter == "Pendientes") Color.White else Color(0xFF424242),
                isSelected = selectedFilter == "Pendientes",
                onClick = { onFilterSelected("Pendientes") },
                modifier = Modifier.weight(1f)
            )
        }

        // --- FILA 4: ALMACENAJE / TOTAL ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummarySmallCard(
                title = "Almacenaje",
                count = totalAlmacenaje,
                color = if (selectedFilter == "Almacenaje") Color(0xFF1976D2) else Color(0xFFE3F2FD), 
                textColor = if (selectedFilter == "Almacenaje") Color.White else Color(0xFF1976D2),
                isSelected = selectedFilter == "Almacenaje",
                onClick = { onFilterSelected("Almacenaje") },
                modifier = Modifier.weight(1f)
            )
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onFilterSelected("Total unidades vacias") },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedFilter == "Total unidades vacias") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (selectedFilter == "Total unidades vacias") BorderStroke(2.dp, MaterialTheme.colorScheme.onSecondary) else null
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val textColor = if (selectedFilter == "Total unidades vacias") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
                    Text("Total unidades vacias", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = textColor)
                    Text("$totalVaciasHoy", fontSize = 22.sp, fontWeight = FontWeight.Black, color = textColor)
                }
            }
        }
    }
}

@Composable
fun SummarySmallCard(
    title: String,
    count: Int,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, textColor) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = count.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        }
    }
}

@Composable
fun DockConditionsTabContent(
    conditions: List<DockCondition>,
    onInspectDock: (DockCondition) -> Unit,
    modifier: Modifier = Modifier
) {
    if (conditions.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Inspección Diaria de Condiciones de Seguridad",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            items(conditions) { cond ->
                DockConditionCard(
                    condition = cond,
                    onClick = { onInspectDock(cond) }
                )
            }
        }
    }
}

@Composable
fun DamagedBoxesTabContent(
    damagedBoxes: List<DamagedBox>,
    onEditDamaged: (DamagedBox) -> Unit,
    onDeleteDamaged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (damagedBoxes.isEmpty()) {
        EmptyStateView(
            title = "Sin unidades rechazadas reportadas",
            subtitle = "Usa el botón flotante (+) para registrar una unidad rechazada detectada el día de hoy.",
            icon = Icons.Outlined.CancelPresentation,
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Unidades rechazadas el día de hoy: ${damagedBoxes.size} unidades",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(damagedBoxes) { box ->
                DamagedBoxCard(
                    box = box,
                    onEdit = { onEditDamaged(box) },
                    onDelete = { onDeleteDamaged(box.id) }
                )
            }
        }
    }
}

@Composable
fun SyncAndExportTabContent(
    viewModel: ReportViewModel,
    googleAccount: GoogleSignInAccount?,
    driveSyncState: DriveSyncState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeReport by viewModel.activeReport.collectAsState()
    val shiftState = remember(activeReport) { mutableStateOf(activeReport?.shift ?: "") }
    val supervisorState = remember(activeReport) { mutableStateOf(activeReport?.supervisorName ?: "") }

    var selectedFormat by remember { mutableStateOf("excel") } // "excel" o "csv"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Metadata Identification Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Datos de Identificación del Reporte",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = shiftState.value,
                        onValueChange = { shiftState.value = it },
                        label = { Text("Turno") },
                        placeholder = { Text("Ej: Matutino, Vespertino, Nocturno") },
                        modifier = Modifier.fillMaxWidth().testTag("report_shift_input"),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = supervisorState.value,
                        onValueChange = { supervisorState.value = it },
                        label = { Text("Nombre de quien llena el reporte") },
                        placeholder = { Text("Ej: Supervisor Juan Pérez") },
                        modifier = Modifier.fillMaxWidth().testTag("report_supervisor_input"),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            viewModel.updateActiveReportMetadata(shiftState.value, supervisorState.value)
                            Toast.makeText(context, "Datos del reporte guardados correctamente", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_metadata_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Datos del Reporte")
                    }
                }
            }
        }

        // Local Export Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FilePresent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Exportación Local",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Selecciona el formato de exportación:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFormat == "excel",
                            onClick = { selectedFormat = "excel" },
                            label = { Text("Excel (.xls)") },
                            leadingIcon = if (selectedFormat == "excel") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedFormat == "csv",
                            onClick = { selectedFormat = "csv" },
                            label = { Text("CSV (.csv)") },
                            leadingIcon = if (selectedFormat == "csv") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Button 1: Inventario de Cajas
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val file = viewModel.exportContainersCsvLocally(context, selectedFormat)
                                    shareFile(context, file)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al generar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_containers_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar Inventario de Cajas")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 2: Condición de Andenes
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val file = viewModel.exportDockConditionsCsvLocally(context, selectedFormat)
                                    shareFile(context, file)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al generar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_dock_conditions_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar Condición de Andenes")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 3: Cajas Dañadas
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val file = viewModel.exportDamagedBoxesCsvLocally(context, selectedFormat)
                                    shareFile(context, file)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al generar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_damaged_boxes_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar Unidades Rechazadas")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Button 4: Reporte General Combinado
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val file = viewModel.exportCsvLocally(context, selectedFormat)
                                    shareFile(context, file)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al generar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_local_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar Reporte Completo")
                    }
                }
            }
        }

        // Google Drive Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sincronización con Google Drive",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sube automáticamente tus reportes diarios a tu cuenta de Google Drive. El sistema lo guardará directamente como una Hoja de Cálculo de Google editable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (googleAccount == null) {
                        Button(
                            onClick = onSignIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_signin_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iniciar sesión con Google")
                        }
                    } else {
                        // User Info Card
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = googleAccount.displayName ?: "Usuario de Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = googleAccount.email ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active sync button
                        Button(
                            onClick = { viewModel.syncWithDrive(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drive_sync_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizar ahora con Drive")
                        }
                    }

                    // Status Indicator
                    Spacer(modifier = Modifier.height(16.dp))
                    SyncStatusCard(state = driveSyncState, onDismiss = { viewModel.resetDriveSyncState() })
                }
            }
        }

        // Credits Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logicont_logo),
                    contentDescription = "Logicont Chain Logo",
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mauricio Marquez",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "@cybercadtm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --------------------------------------------------------
// SMALL REUSABLE UI ELEMENTS
// --------------------------------------------------------

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DockRowCard(
    dockNumber: Int,
    unit: ContainerItem?,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isOutOfService: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val cardColor = if (isOutOfService) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
    } else if (unit == null) {
        MaterialTheme.colorScheme.surface
    } else {
        when (unit.type) {
            "Caja Seca" -> Color(0xFFE8F5E9) // Verde suave
            "Intermodal" -> Color(0xFFF3E5F5) // Morado suave
            "Contenedor Marítimo" -> Color(0xFFFFEBEE) // Rojo suave
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { 
                if (unit == null) onAdd() else expanded = !expanded 
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (unit != null) 2.dp else 0.dp),
        border = if (unit == null) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dock indicator circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOutOfService) MaterialTheme.colorScheme.error
                            else if (unit == null) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dockNumber.toString(),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isOutOfService || unit != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (unit == null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOutOfService) "FUERA DE SERVICIO" else "Andén Disponible",
                            fontWeight = FontWeight.Bold,
                            color = if (isOutOfService) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Toca para registrar", fontSize = 11.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dockIcon = when (unit.type) {
                                "Caja Seca" -> Icons.Default.LocalShipping
                                "Intermodal" -> Icons.Default.Garage
                                "Contenedor Marítimo" -> Icons.Default.ViewInAr
                                else -> Icons.Default.Inventory
                            }
                            Icon(dockIcon, null, Modifier.size(16.dp), tint = Color.DarkGray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(unit.economicNumber, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }
                        Text("${unit.type} • ${unit.company}", fontSize = 11.sp, color = Color.DarkGray)
                    }

                    Surface(
                        color = if (unit.status.startsWith("Carg", ignoreCase = true)) Color(0xFFC62828) else Color(0xFF2E7D32),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            unit.status.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (unit != null && expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.Black.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow(label = "Placas", value = "${unit.plates} (${unit.platesLocation})")
                    if (!unit.receptionDate.isNullOrBlank()) DetailRow(label = "Entrada", value = unit.receptionDate)
                    if (unit.status.startsWith("Carg", ignoreCase = true)) {
                        DetailRow(label = "Estatus", value = unit.loadStatus ?: "Cargada")
                        if (!unit.destination.isNullOrBlank()) DetailRow(label = "Destino", value = unit.destination)
                        if (!unit.sealCode.isNullOrBlank()) DetailRow(label = "Sello", value = unit.sealCode)
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            } else if (unit != null) {
                // Vista corta en andén
                Row(Modifier.padding(top = 6.dp, start = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    val shortDesc = if (unit.status.startsWith("Carg", ignoreCase = true)) 
                        unit.loadStatus ?: "Cargada" 
                        else "Vacía / Disponible"
                    Text(shortDesc, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ContainerItemCard(
    container: ContainerItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val cardColor = when (container.type) {
        "Caja Seca" -> Color(0xFFE8F5E9) // Verde suave
        "Intermodal" -> Color(0xFFF3E5F5) // Morado suave
        "Contenedor Marítimo" -> Color(0xFFFFEBEE) // Rojo suave
        else -> MaterialTheme.colorScheme.surface
    }

    val icon = when (container.type) {
        "Caja Seca" -> Icons.Default.LocalShipping
        "Intermodal" -> Icons.Default.Garage
        "Contenedor Marítimo" -> Icons.Default.ViewInAr
        else -> Icons.Default.Help
    }

    val statusColor = if (container.status.startsWith("Carg", ignoreCase = true)) {
        Color(0xFFC62828) // Rojo fuerte
    } else {
        Color(0xFF2E7D32) // Verde fuerte
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = container.economicNumber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "${container.type} • ${container.company}",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }

                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = container.status.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.Black.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow(label = "Placas", value = "${container.plates} (${container.platesLocation})")
                    if (!container.receptionDate.isNullOrBlank()) {
                        DetailRow(label = "Entrada", value = container.receptionDate)
                    }
                    if (container.status.startsWith("Carg", ignoreCase = true)) {
                        DetailRow(label = "Carga", value = container.loadStatus ?: "N/A")
                        if (!container.destination.isNullOrBlank()) DetailRow(label = "Destino", value = container.destination)
                        if (!container.sealCode.isNullOrBlank()) DetailRow(label = "Sello", value = container.sealCode)
                    }
                    if (container.isStorageOnly) {
                        Surface(
                            color = Color(0xFFFFB300),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "MODO ALMACENAJE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar de patio", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Eliminar de patio", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // Vista corta
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    val locationDesc = if (container.locationType == "Andén") "en Andén ${container.dockNumber}" else "en Patio"
                    Text(
                        text = if (container.status.startsWith("Carg", ignoreCase = true)) 
                            "Cargada: ${container.loadStatus ?: "Pendiente"}" 
                            else "Vacía / Disponible $locationDesc",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row {
        Text(text = "$label: ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Text(text = value, fontSize = 13.sp, color = Color.Black)
    }
}

@Composable
fun DockConditionCard(
    condition: DockCondition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Pair("Rampa", condition.isRampGood),
        Pair("Seguro", condition.isRampLockGood),
        Pair("Gancho", condition.isDockHookGood),
        Pair("Cortina", condition.isCurtainGood),
        Pair("Shelter", condition.isShelterGood),
        Pair("Gomas", condition.isRubbersGood),
        Pair("Topes", condition.isSafetyStoppersGood),
        Pair("Semáforo", condition.isTrafficLightsGood),
        Pair("Luces", condition.isLightsGood),
        Pair("Empaques", condition.isEmpaquesGood),
        Pair("Gato", condition.isGatoHidraulicoGood),
        Pair("Cadena", condition.isCadenaGood)
    )

    val badItems = items.filter { !it.second }
    val isOos = condition.isOutOfService

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = if (isOos) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOos) MaterialTheme.colorScheme.error
                            else if (badItems.isEmpty()) Color(0xFFE8F5E9)
                            else Color(0xFFFFEBEE)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = condition.dockNumber.toString(),
                        fontWeight = FontWeight.Bold,
                        color = if (isOos) MaterialTheme.colorScheme.onError
                        else if (badItems.isEmpty()) Color(0xFF2E7D32)
                        else Color(0xFFC62828)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Andén ${condition.dockNumber} - " + if (condition.dockNumber in 1..9) "Exportación" else "Recibos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (isOos) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "FUERA DE SERVICIO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isOos) "FUERA DE SERVICIO" else if (badItems.isEmpty()) "Buenas condiciones generales" else "Detalles encontrados: ${badItems.size}",
                        fontSize = 12.sp,
                        color = if (isOos) MaterialTheme.colorScheme.error else if (badItems.isEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
                Icon(
                    Icons.Default.ArrowForwardIos,
                    contentDescription = "Inspeccionar",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (badItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badItems.forEach { item ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(item.first, fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFEBEE),
                                labelColor = Color(0xFFC62828)
                            )
                        )
                    }
                }
            }

            if (condition.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nota: ${condition.notes}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DamagedBoxCard(
    box: DamagedBox,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F8))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFC62828))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "No. Económico: ${box.economicNumber}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(box.type, fontSize = 11.sp) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = box.damageDescription,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar reporte de daño", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar reporte de daño", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SyncStatusCard(state: DriveSyncState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state !is DriveSyncState.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    is DriveSyncState.Syncing -> MaterialTheme.colorScheme.surfaceVariant
                    is DriveSyncState.Success -> Color(0xFFE8F5E9)
                    is DriveSyncState.Error -> Color(0xFFFFEBEE)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    is DriveSyncState.Syncing -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sincronizando con Google Drive...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is DriveSyncState.Success -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "¡Reporte Sincronizado!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "El archivo se guardó correctamente como una Hoja de Cálculo en tu Drive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF2E7D32))
                        }
                    }
                    is DriveSyncState.Error -> {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Error de sincronización",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB71C1C)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFC62828))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

// --------------------------------------------------------
// DIALOG COMPOSABLES
// --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContainerDialog(
    locationTypePreset: String,
    dockNumberPreset: Int?,
    unitToEdit: ContainerItem? = null,
    containersHistory: List<ContainerItem> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (
        type: String,
        locationType: String,
        dockNumber: Int?,
        economicNumber: String,
        plates: String,
        platesLocation: String,
        status: String,
        loadStatus: String?,
        sealCode: String?,
        containerSize: String?,
        destination: String?,
        deliveryDocument: String?,
        processedDate: String?,
        isStorageOnly: Boolean,
        mexCalType: String?,
        receptionDate: String?,
        materialType: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(unitToEdit?.type ?: "Caja Seca") }
    var locationType by remember { mutableStateOf(unitToEdit?.locationType ?: locationTypePreset) }
    var dockNumber by remember { mutableIntStateOf(unitToEdit?.dockNumber ?: dockNumberPreset ?: 1) }
    var economicNumber by remember { mutableStateOf(unitToEdit?.economicNumber ?: "") }
    var plates by remember { mutableStateOf(unitToEdit?.plates ?: "") }
    var platesLocation by remember { mutableStateOf(unitToEdit?.platesLocation ?: "") }
    var status by remember { mutableStateOf(unitToEdit?.status ?: "Vacía") }
    var loadStatus by remember { mutableStateOf(unitToEdit?.loadStatus ?: "Pendiente por completar") }
    var sealCode by remember { mutableStateOf(unitToEdit?.sealCode ?: "") }
    var containerSize by remember { mutableStateOf(unitToEdit?.containerSize ?: "") }
    var destination by remember { mutableStateOf(unitToEdit?.destination ?: "") }
    var deliveryDocument by remember { mutableStateOf(unitToEdit?.deliveryDocument ?: "") }
    var processedDate by remember { mutableStateOf(unitToEdit?.processedDate ?: "") }
    
    // ENSURE isStorageOnly is strictly tied to "Almacenaje" loadStatus on initialization
    var isStorageOnly by remember { 
        mutableStateOf(unitToEdit?.isStorageOnly == true || unitToEdit?.loadStatus == "Almacenaje") 
    }
    
    var mexCalType by remember { mutableStateOf(unitToEdit?.mexCalType ?: "Interplanta") }
    
    val yesterday = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
    }
    val yesterdayStr = remember(yesterday) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday)
    }
    var receptionDate by remember { mutableStateOf(unitToEdit?.receptionDate ?: yesterdayStr) }
    
    var materialType by remember { mutableStateOf(unitToEdit?.materialType ?: "FG") }

    val unitTypes = listOf("Caja Seca", "Intermodal", "Contenedor Marítimo")
    val statusTypes = listOf("Vacía", "Cargada")
    val loadStatusTypes = listOf("En espera de salida", "En espera de descarga", "Pendiente por completar", "Pendiente por procesar", "Almacenaje")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = if (unitToEdit != null) "Editar Unidad" else "Registrar Nueva Unidad",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Ubicación (Andén o Patio)
                item {
                    Text("Ubicación", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = locationType == "Andén",
                            onClick = { locationType = "Andén" },
                            label = { Text("Andén") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = locationType == "Patio",
                            onClick = { locationType = "Patio" },
                            label = { Text("Patio") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Número de Andén (solo si es Andén)
                if (locationType == "Andén") {
                    item {
                        Text("Número de Andén (1 al 14)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = "Andén $dockNumber" + if (dockNumber in 1..9) " (Exportación)" else " (Recibo)",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                (1..14).forEach { num ->
                                    DropdownMenuItem(
                                        text = { Text("Andén $num" + if (num in 1..9) " (Exportación)" else " (Recibo)") },
                                        onClick = {
                                            dockNumber = num
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Tipo de Unidad
                item {
                    Text("Tipo de Unidad", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        unitTypes.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { 
                                    type = t 
                                    if (t != "Caja Seca" && loadStatus == "En espera de descarga") {
                                        loadStatus = "En espera de salida"
                                    }
                                },
                                label = { Text(t, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }


                // Compañía (No modificable)
                item {
                    val computedCompany = when (type) {
                        "Caja Seca" -> "MEX-CAL"
                        "Intermodal" -> "JBHUNT"
                        "Contenedor Marítimo" -> "CONTENEDOR"
                        else -> ""
                    }
                    OutlinedTextField(
                        value = computedCompany,
                        onValueChange = {},
                        label = { Text("Compañía (No modificable)") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (type == "Caja Seca") {
                    item {
                        Text("Operación (MÉX-CAL)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mexCalType == "Exportación",
                                onClick = { mexCalType = "Exportación" },
                                label = { Text("Exportación") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = mexCalType == "Interplanta",
                                onClick = { mexCalType = "Interplanta" },
                                label = { Text("Interplanta") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // No. Económico
                item {
                    Column {
                        OutlinedTextField(
                            value = economicNumber,
                            onValueChange = { 
                                economicNumber = it
                                if (type == "Caja Seca") {
                                    if (it.startsWith("2133")) {
                                        mexCalType = "Interplanta"
                                    } else if (it.startsWith("2131") || it.startsWith("2132")) {
                                        mexCalType = "Exportación"
                                    }
                                }
                            },
                            label = { Text("Número Económico") },
                            placeholder = { Text("Ej. E-123") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("econ_num_input"),
                            singleLine = true
                        )

                        val exactMatch = remember(economicNumber, containersHistory) {
                            if (economicNumber.trim().isBlank()) null
                            else containersHistory
                                .filter { it.economicNumber.equals(economicNumber.trim(), ignoreCase = true) }
                                .maxByOrNull { it.timestamp }
                        }

                        if (exactMatch != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    // Load matching data
                                    economicNumber = exactMatch.economicNumber
                                    plates = exactMatch.plates
                                    platesLocation = exactMatch.platesLocation
                                    type = exactMatch.type
                                    receptionDate = exactMatch.receptionDate ?: yesterdayStr
                                    
                                    // Update status and its details
                                    status = exactMatch.status
                                    loadStatus = exactMatch.loadStatus ?: "Pendiente por completar"
                                    
                                    // FORCE RESET isStorageOnly unless the new loadStatus is Almacenaje
                                    isStorageOnly = (loadStatus == "Almacenaje")

                                    sealCode = exactMatch.sealCode ?: ""
                                    containerSize = exactMatch.containerSize ?: ""
                                    destination = exactMatch.destination ?: ""
                                    deliveryDocument = exactMatch.deliveryDocument ?: ""
                                    processedDate = exactMatch.processedDate ?: ""
                                    mexCalType = exactMatch.mexCalType ?: "Interplanta"
                                    materialType = exactMatch.materialType ?: "FG"

                                    // DO NOT restore location if it was "Andén" to avoid overwriting
                                    if (exactMatch.locationType == "Patio") {
                                        locationType = "Patio"
                                    } else {
                                        // Reset to avoid errors when registering new unit that was in a dock before
                                        locationType = ""
                                        dockNumber = 1
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("autofill_exact_match_btn")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copiar datos de registro anterior", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val suggestions = remember(economicNumber, containersHistory) {
                            if (economicNumber.isBlank()) {
                                emptyList()
                            } else {
                                val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                                containersHistory
                                    .filter { 
                                        it.economicNumber.startsWith(economicNumber, ignoreCase = true) && 
                                        !it.economicNumber.equals(economicNumber, ignoreCase = true) &&
                                        (it.company.equals("MEX-CAL", ignoreCase = true) || it.timestamp > oneMonthAgo)
                                    }
                                    .sortedByDescending { it.timestamp }
                                    .distinctBy { it.economicNumber }
                                    .take(5)
                            }
                        }

                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Coincidencias encontradas (toca para autocompletar):",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                suggestions.forEach { match ->
                                    SuggestionChip(
                                        onClick = {
                                            economicNumber = match.economicNumber
                                            plates = match.plates
                                            platesLocation = match.platesLocation
                                            type = match.type
                                            receptionDate = match.receptionDate ?: yesterdayStr
                                            
                                            status = match.status
                                            loadStatus = match.loadStatus ?: "Pendiente por completar"
                                            
                                            // FORCE RESET isStorageOnly unless the new loadStatus is Almacenaje
                                            isStorageOnly = (loadStatus == "Almacenaje")
                                            
                                            sealCode = match.sealCode ?: ""
                                            containerSize = match.containerSize ?: ""
                                            destination = match.destination ?: ""
                                            deliveryDocument = match.deliveryDocument ?: ""
                                            processedDate = match.processedDate ?: ""
                                            mexCalType = match.mexCalType ?: "Interplanta"
                                            materialType = match.materialType ?: "FG"

                                            if (match.locationType == "Patio") {
                                                locationType = "Patio"
                                            } else {
                                                locationType = ""
                                                dockNumber = 1
                                            }
                                        },
                                        label = {
                                            Column {
                                                Text(match.economicNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("Placas: ${match.plates} (${match.platesLocation})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Placas
                item {
                    Column {
                        OutlinedTextField(
                            value = plates,
                            onValueChange = { plates = it },
                            label = { Text("Placas") },
                            placeholder = { Text("Ej. AB-1234-CD") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plates_input"),
                            singleLine = true
                        )

                        val exactPlatesMatch = remember(plates, containersHistory) {
                            if (plates.trim().isBlank()) null
                            else containersHistory
                                .filter { it.plates.equals(plates.trim(), ignoreCase = true) }
                                .maxByOrNull { it.timestamp }
                        }

                        if (exactPlatesMatch != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    economicNumber = exactPlatesMatch.economicNumber
                                    plates = exactPlatesMatch.plates
                                    platesLocation = exactPlatesMatch.platesLocation
                                    type = exactPlatesMatch.type
                                    receptionDate = exactPlatesMatch.receptionDate ?: yesterdayStr
                                    
                                    status = exactPlatesMatch.status
                                    loadStatus = exactPlatesMatch.loadStatus ?: "Pendiente por completar"
                                    
                                    // FORCE RESET isStorageOnly unless the new loadStatus is Almacenaje
                                    isStorageOnly = (loadStatus == "Almacenaje")
                                    
                                    sealCode = exactPlatesMatch.sealCode ?: ""
                                    containerSize = exactPlatesMatch.containerSize ?: ""
                                    destination = exactPlatesMatch.destination ?: ""
                                    deliveryDocument = exactPlatesMatch.deliveryDocument ?: ""
                                    processedDate = exactPlatesMatch.processedDate ?: ""
                                    mexCalType = exactPlatesMatch.mexCalType ?: "Interplanta"
                                    materialType = exactPlatesMatch.materialType ?: "FG"

                                    if (exactPlatesMatch.locationType == "Patio") {
                                        locationType = "Patio"
                                    } else {
                                        locationType = ""
                                        dockNumber = 1
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("autofill_exact_plates_match_btn")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copiar datos de registro anterior", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val platesSuggestions = remember(plates, containersHistory) {
                            if (plates.isBlank()) {
                                emptyList()
                            } else {
                                val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                                containersHistory
                                    .filter { 
                                        it.plates.startsWith(plates, ignoreCase = true) && 
                                        !it.plates.equals(plates, ignoreCase = true) &&
                                        (it.company.equals("MEX-CAL", ignoreCase = true) || it.timestamp > oneMonthAgo)
                                    }
                                    .sortedByDescending { it.timestamp }
                                    .distinctBy { it.plates }
                                    .take(5)
                            }
                        }

                        if (platesSuggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Coincidencias de placas (toca para autocompletar):",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                platesSuggestions.forEach { match ->
                                    SuggestionChip(
                                        onClick = {
                                            plates = match.plates
                                            platesLocation = match.platesLocation
                                            economicNumber = match.economicNumber
                                            type = match.type
                                            receptionDate = match.receptionDate ?: yesterdayStr
                                            
                                            status = match.status
                                            loadStatus = match.loadStatus ?: "Pendiente por completar"
                                            
                                            // FORCE RESET isStorageOnly unless the new loadStatus is Almacenaje
                                            isStorageOnly = (loadStatus == "Almacenaje")

                                            sealCode = match.sealCode ?: ""
                                            containerSize = match.containerSize ?: ""
                                            destination = match.destination ?: ""
                                            deliveryDocument = match.deliveryDocument ?: ""
                                            processedDate = match.processedDate ?: ""
                                            mexCalType = match.mexCalType ?: "Interplanta"
                                            materialType = match.materialType ?: "FG"

                                            if (match.locationType == "Patio") {
                                                locationType = "Patio"
                                            } else {
                                                locationType = ""
                                                dockNumber = 1
                                            }
                                        },
                                        label = {
                                            Column {
                                                Text(match.plates, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("Económico: ${match.economicNumber}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Lugar de Placas
                item {
                    Text("ORIGEN DE PLACAS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    var platesLocExpanded by remember { mutableStateOf(false) }
                    val allowedStates = listOf("California", "Maine", "Tennessee", "Oklahoma", "Indiana")
                    ExposedDropdownMenuBox(
                        expanded = platesLocExpanded,
                        onExpandedChange = { platesLocExpanded = !platesLocExpanded }
                    ) {
                        OutlinedTextField(
                            value = platesLocation.uppercase(),
                            onValueChange = { platesLocation = it.uppercase() },
                            label = { Text("Origen de placas") },
                            placeholder = { Text("Seleccione o escriba el estado") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platesLocExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("plates_loc_input"),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = platesLocExpanded,
                            onDismissRequest = { platesLocExpanded = false }
                        ) {
                            val filteredStates = allowedStates.filter {
                                it.contains(platesLocation, ignoreCase = true)
                            }
                            if (filteredStates.isNotEmpty()) {
                                filteredStates.forEach { state ->
                                    DropdownMenuItem(
                                        text = { Text(state) },
                                        onClick = {
                                            platesLocation = state
                                            platesLocExpanded = false
                                        }
                                    )
                                }
                            } else {
                                allowedStates.forEach { state ->
                                    DropdownMenuItem(
                                        text = { Text(state) },
                                        onClick = {
                                            platesLocation = state
                                            platesLocExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Fecha de Recepción
                item {
                    OutlinedTextField(
                        value = receptionDate,
                        onValueChange = { receptionDate = it },
                        label = { Text("Fecha de Recepción") },
                        placeholder = { Text("Ej. 2026-06-27") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reception_date_input"),
                        singleLine = true
                    )
                }

                // Tipo de Material
                item {
                    OutlinedTextField(
                        value = materialType,
                        onValueChange = { materialType = it },
                        label = { Text("Tipo de Material") },
                        placeholder = { Text("Ej. FG") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("material_type_input"),
                        singleLine = true
                    )
                }

                // Estado (Vacía o Cargada)
                item {
                    Text("Estado de la Unidad", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        statusTypes.forEach { s ->
                            FilterChip(
                                selected = status == s,
                                onClick = { status = s },
                                label = { Text(s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Progreso de Carga (solo si es Cargada)
                if (status == "Cargada") {
                    item {
                        Text("Progreso de Carga", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            loadStatusTypes.forEach { ls ->
                                // "En espera de descarga" is only for MEX-CAL
                                val isVisible = if (ls == "En espera de descarga") type == "Caja Seca" else true
                                
                                if (isVisible) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                loadStatus = ls
                                                isStorageOnly = (ls == "Almacenaje")
                                            }
                                            .padding(vertical = 4.dp)
                                      ) {
                                        RadioButton(selected = loadStatus == ls, onClick = { 
                                            loadStatus = ls
                                            isStorageOnly = (ls == "Almacenaje")
                                        })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(ls)
                                    }
                                }
                            }
                        }
                    }

                    // Código de Sello
                    val canShowSeal = loadStatus == "En espera de salida" || 
                                     loadStatus == "Almacenaje" ||
                                     (isStorageOnly && (loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar"))

                    if (canShowSeal) {
                        item {
                            OutlinedTextField(
                                value = sealCode,
                                onValueChange = { sealCode = it },
                                label = { Text("Código de Sello Numérico") },
                                placeholder = { Text("Ej. 981245") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("seal_code_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                // Destino, Delivery, Fecha de procesado
                val isReciboOrAlmacenaje = (locationType == "Andén" && dockNumber >= 10) || loadStatus == "En espera de descarga"
                val canShowExtraFields = !isReciboOrAlmacenaje

                if (canShowExtraFields) {
                    item {
                        Text("Destino de Exportación", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        var destExpanded by remember { mutableStateOf(false) }
                        val exportDestinations = listOf(
                            "CANADÁ", "UNITED KINGDOM", "FRANCE", "COREA", "JAPÓN", 
                            "CHINA", "COLOMBIA", "KENTUKY", "GERMANY", "SWEDEN", 
                            "KN", "NZ", "PERRIS", "POLONIA", "DSV", "MXDC"
                        )
                        ExposedDropdownMenuBox(
                            expanded = destExpanded,
                            onExpandedChange = { destExpanded = !destExpanded }
                        ) {
                            OutlinedTextField(
                                value = destination.uppercase(),
                                onValueChange = { destination = it.uppercase() },
                                label = { Text("Destino") },
                                placeholder = { Text("Seleccione o escriba el destino") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("destination_input"),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = destExpanded,
                                onDismissRequest = { destExpanded = false }
                            ) {
                                val filteredDestinations = exportDestinations.filter {
                                    it.contains(destination, ignoreCase = true)
                                }
                                if (filteredDestinations.isNotEmpty()) {
                                    filteredDestinations.forEach { destOption ->
                                        DropdownMenuItem(
                                            text = { Text(destOption) },
                                            onClick = {
                                                destination = destOption
                                                destExpanded = false
                                            }
                                        )
                                    }
                                } else {
                                    exportDestinations.forEach { destOption ->
                                        DropdownMenuItem(
                                            text = { Text(destOption) },
                                            onClick = {
                                                destination = destOption
                                                destExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = deliveryDocument,
                            onValueChange = { deliveryDocument = it },
                            label = { Text("Delivery / Documento") },
                            placeholder = { Text("Ej. D-982451") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delivery_doc_input"),
                            singleLine = true
                        )
                    }

                    if (loadStatus != "Pendiente por completar" && loadStatus != "Pendiente por procesar" && loadStatus != "Almacenaje") {
                        item {
                            OutlinedTextField(
                                value = processedDate,
                                onValueChange = { processedDate = it },
                                label = { Text("Fecha de Procesado") },
                                placeholder = { Text("Ej. 27/06/2026") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("processed_date_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                // Medida de contenedor (solo si es Contenedor Marítimo Vacío)
                if (type == "Contenedor Marítimo" && status == "Vacía") {
                    item {
                        OutlinedTextField(
                            value = containerSize,
                            onValueChange = { containerSize = it },
                            label = { Text("Medida del Contenedor") },
                            placeholder = { Text("Ej. 20ft, 40ft, 45ft") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("container_size_input"),
                            singleLine = true
                        )
                    }
                }

                // Action Buttons
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (locationType.isBlank()) {
                                    Toast.makeText(context, "Por favor seleccione una ubicación (Andén o Patio)", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                onConfirm(
                                    type,
                                    locationType,
                                    if (locationType == "Andén") dockNumber else null,
                                    economicNumber,
                                    plates,
                                    platesLocation,
                                    status,
                                    loadStatus,
                                    sealCode,
                                    containerSize,
                                    destination,
                                    deliveryDocument,
                                    if (loadStatus == "Pendiente por completar" || loadStatus == "Pendiente por procesar" || loadStatus == "En espera de descarga" || loadStatus == "Almacenaje") "" else processedDate,
                                    isStorageOnly || loadStatus == "Almacenaje",
                                    if (type == "Caja Seca") mexCalType else null,
                                    receptionDate,
                                    materialType
                                )
                            },
                            enabled = economicNumber.isNotBlank(),
                            modifier = Modifier.testTag("confirm_add_container_btn")
                        ) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddDamagedBoxDialog(
    boxToEdit: DamagedBox? = null,
    containersHistory: List<ContainerItem> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (economicNumber: String, type: String, description: String) -> Unit
) {
    var economicNumber by remember { mutableStateOf(boxToEdit?.economicNumber ?: "") }
    var type by remember { mutableStateOf(boxToEdit?.type ?: "Caja Seca") }
    var description by remember { mutableStateOf(boxToEdit?.damageDescription ?: "") }

    val unitTypes = listOf("Caja Seca", "Intermodal", "Contenedor Marítimo")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (boxToEdit != null) "Editar Reporte de Daño" else "Reportar Caja Dañada",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                Column {
                    OutlinedTextField(
                        value = economicNumber,
                        onValueChange = { economicNumber = it },
                        label = { Text("Número Económico de Unidad") },
                        placeholder = { Text("Ej. C-889") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("damaged_econ_input"),
                        singleLine = true
                    )

                    val suggestions = remember(economicNumber, containersHistory) {
                        if (economicNumber.isBlank()) {
                            emptyList()
                        } else {
                            containersHistory
                                .filter { 
                                    it.economicNumber.startsWith(economicNumber, ignoreCase = true) && 
                                    !it.economicNumber.equals(economicNumber, ignoreCase = true)
                                }
                                .distinctBy { it.economicNumber }
                                .take(5)
                        }
                    }

                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Coincidencias encontradas (toca para autocompletar):",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            suggestions.forEach { match ->
                                SuggestionChip(
                                    onClick = {
                                        economicNumber = match.economicNumber
                                        type = match.type
                                    },
                                    label = {
                                        Column {
                                            Text(match.economicNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(match.type, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Text("Tipo de Unidad", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    unitTypes.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del Daño") },
                    placeholder = { Text("Ej. Goma de la puerta rota, bisagra suelta, abolladura lateral...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("damage_desc_input")
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(economicNumber, type, description) },
                        enabled = economicNumber.isNotBlank() && description.isNotBlank(),
                        modifier = Modifier.testTag("confirm_damaged_btn")
                    ) {
                        Text("Registrar")
                    }
                }
            }
        }
    }
}

@Composable
fun InspectDockDialog(
    condition: DockCondition,
    onDismiss: () -> Unit,
    onConfirm: (
        ramp: Boolean,
        rLock: Boolean,
        hook: Boolean,
        curtain: Boolean,
        shelter: Boolean,
        rubbers: Boolean,
        stoppers: Boolean,
        traffic: Boolean,
        lights: Boolean,
        empaques: Boolean,
        gatoHidraulico: Boolean,
        cadena: Boolean,
        notes: String,
        isOutOfService: Boolean
    ) -> Unit
) {
    var ramp by remember { mutableStateOf(condition.isRampGood) }
    var rLock by remember { mutableStateOf(condition.isRampLockGood) }
    var hook by remember { mutableStateOf(condition.isDockHookGood) }
    var curtain by remember { mutableStateOf(condition.isCurtainGood) }
    var shelter by remember { mutableStateOf(condition.isShelterGood) }
    var rubbers by remember { mutableStateOf(condition.isRubbersGood) }
    var stoppers by remember { mutableStateOf(condition.isSafetyStoppersGood) }
    var traffic by remember { mutableStateOf(condition.isTrafficLightsGood) }
    var lights by remember { mutableStateOf(condition.isLightsGood) }
    var empaques by remember { mutableStateOf(condition.isEmpaquesGood) }
    var gatoHidraulico by remember { mutableStateOf(condition.isGatoHidraulicoGood) }
    var cadena by remember { mutableStateOf(condition.isCadenaGood) }
    var notes by remember { mutableStateOf(condition.notes) }
    var isOutOfService by remember { mutableStateOf(condition.isOutOfService) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Estatus: Andén ${condition.dockNumber}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (condition.dockNumber in 1..9) "Andén de Exportación (1-9)" else "Andén de Recibos / FG (10-14)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Shortcut: Mark all good
                item {
                    Button(
                        onClick = {
                            ramp = true; rLock = true; hook = true; curtain = true
                            shelter = true; rubbers = true; stoppers = true
                            traffic = true; lights = true; empaques = true
                            gatoHidraulico = true; cadena = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Todo en BUEN Estado")
                    }
                }

                // Out of service toggle
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isOutOfService) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { isOutOfService = !isOutOfService }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Andén Fuera de Servicio",
                                fontWeight = FontWeight.Bold,
                                color = if (isOutOfService) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isOutOfService) "Reportado como FUERA DE SERVICIO" else "Operativo / Disponible",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOutOfService) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isOutOfService,
                            onCheckedChange = { isOutOfService = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.error,
                                checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    }
                }

                item { Divider() }

                // The 12 Checklist items
                item { InspectionCheckItem(label = "Rampa niveladora", checked = ramp, onCheckedChange = { ramp = it }) }
                item { InspectionCheckItem(label = "Seguro de rampa", checked = rLock, onCheckedChange = { rLock = it }) }
                item { InspectionCheckItem(label = "Gancho de retención de andén", checked = hook, onCheckedChange = { hook = it }) }
                item { InspectionCheckItem(label = "Cortina metálica", checked = curtain, onCheckedChange = { curtain = it }) }
                item { InspectionCheckItem(label = "Shelter (Sello de andén)", checked = shelter, onCheckedChange = { shelter = it }) }
                item { InspectionCheckItem(label = "Gomas protectoras", checked = rubbers, onCheckedChange = { rubbers = it }) }
                item { InspectionCheckItem(label = "Topes de seguridad para llantas", checked = stoppers, onCheckedChange = { stoppers = it }) }
                item { InspectionCheckItem(label = "Semáforo exterior de andén", checked = traffic, onCheckedChange = { traffic = it }) }
                item { InspectionCheckItem(label = "Luces interiores de andén", checked = lights, onCheckedChange = { lights = it }) }
                item { InspectionCheckItem(label = "Empaques", checked = empaques, onCheckedChange = { empaques = it }) }
                item { InspectionCheckItem(label = "Gato hidráulico", checked = gatoHidraulico, onCheckedChange = { gatoHidraulico = it }) }
                item { InspectionCheckItem(label = "Cadena", checked = cadena, onCheckedChange = { cadena = it }) }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas y comentarios de anomalías") },
                        placeholder = { Text("Ej. Gancho dañado por camión...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("dock_notes_input")
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onConfirm(ramp, rLock, hook, curtain, shelter, rubbers, stoppers, traffic, lights, empaques, gatoHidraulico, cadena, notes, isOutOfService)
                            },
                            modifier = Modifier.testTag("confirm_dock_inspection_btn")
                        ) {
                            Text("Guardar Inspección")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InspectionCheckItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2E7D32),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFC62828)
            )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = if (checked) "Estado: BIEN" else "Estado: ANOMALÍA / DAÑADO",
                fontSize = 11.sp,
                color = if (checked) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

// --------------------------------------------------------
// UTILITY FUNCTIONS
// --------------------------------------------------------

fun formatDatePretty(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr) ?: return dateStr
        val formatter = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
        formatter.format(date).capitalize(Locale.getDefault())
    } catch (_: Exception) {
        dateStr
    }
}

fun shareFile(context: Context, file: File) {
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (file.name.endsWith(".xls")) "application/vnd.ms-excel" else "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Reporte de Control de Andenes - ${file.name}")
        putExtra(Intent.EXTRA_TEXT, "Adjunto se encuentra el reporte diario de control de andenes e inventario.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Compartir Reporte"))
}

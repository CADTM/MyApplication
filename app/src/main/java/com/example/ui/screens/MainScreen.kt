package com.example.ui.screens

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
    val activeReport by viewModel.activeReport.collectAsState()
    val containers by viewModel.activeContainers.collectAsState()
    val allContainersHistory by viewModel.allContainersHistory.collectAsState()
    val damagedBoxes by viewModel.activeDamagedBoxes.collectAsState()
    val dockConditions by viewModel.activeDockConditions.collectAsState()
    val googleAccount by viewModel.googleAccount.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Andenes, 1: Patio, 2: Inspección, 3: Dañadas, 4: Sincronizar

    var showAddContainerDialog by remember { mutableStateOf(false) }
    var editingContainer by remember { mutableStateOf<ContainerItem?>(null) }
    var presetDockNumber by remember { mutableStateOf<Int?>(null) }
    var presetLocationType by remember { mutableStateOf("Andén") }

    var showAddDamagedBoxDialog by remember { mutableStateOf(false) }
    var editingDamagedBox by remember { mutableStateOf<DamagedBox?>(null) }
    var showDockInspectionDialog by remember { mutableStateOf<DockCondition?>(null) }

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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Control de Andenes y Cajas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Logística de Inventario y Seguridad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.testTag("date_picker_button")
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            if (activeTab == 0 || activeTab == 1) {
                FloatingActionButton(
                    onClick = {
                        presetLocationType = if (activeTab == 0) "Andén" else "Patio"
                        presetDockNumber = if (activeTab == 0) 1 else null
                        showAddContainerDialog = true
                    },
                    modifier = Modifier.testTag("add_item_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Registrar unidad")
                }
            } else if (activeTab == 3) {
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
                        text = "REPORTE LOGÍSTICO",
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
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Andenes (1-14)", maxLines = 1) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("En Patio", maxLines = 1) },
                    icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Estatus Andenes", maxLines = 1) },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    text = { Text("Cajas Dañadas", maxLines = 1) },
                    icon = { Icon(Icons.Default.BrokenImage, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    text = { Text("Exportar / Drive", maxLines = 1) },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = null) }
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
                        containers = containers.filter { it.locationType == "Patio" },
                        onEditContainer = { item ->
                            editingContainer = item
                            presetLocationType = item.locationType
                            presetDockNumber = item.dockNumber
                            showAddContainerDialog = true
                        },
                        onDeleteContainer = { viewModel.deleteContainer(it) }
                    )
                    2 -> DockConditionsTabContent(
                        conditions = dockConditions,
                        onInspectDock = { showDockInspectionDialog = it }
                    )
                    3 -> DamagedBoxesTabContent(
                        damagedBoxes = damagedBoxes,
                        onEditDamaged = { box ->
                            editingDamagedBox = box
                            showAddDamagedBoxDialog = true
                        },
                        onDeleteDamaged = { viewModel.deleteDamagedBoxById(it) }
                    )
                    4 -> SyncAndExportTabContent(
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
            )
        }

        showDockInspectionDialog?.let { condition ->
            InspectDockDialog(
                condition = condition,
                onDismiss = { showDockInspectionDialog = null },
                onConfirm = { ramp, rLock, hook, curtain, shelter, rubbers, stoppers, traffic, lights, notes, isOutOfService ->
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
    onDeleteContainer: (ContainerItem) -> Unit
) {
    val dockContainersMap = remember(containers) {
        containers.filter { it.locationType == "Andén" }.associateBy { it.dockNumber ?: 0 }
    }

    val dockConditionsMap = remember(dockConditions) {
        dockConditions.associateBy { it.dockNumber }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                category = "Exportación",
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
                category = "Recibo/FG",
                onAdd = { onAddContainer(dockNum) },
                onEdit = { unit?.let { onEditContainer(it) } },
                onDelete = { unit?.let { onDeleteContainer(it) } },
                isOutOfService = cond?.isOutOfService == true
            )
        }
    }
}

@Composable
fun YardTabContent(
    containers: List<ContainerItem>,
    onEditContainer: (ContainerItem) -> Unit,
    onDeleteContainer: (ContainerItem) -> Unit
) {
    if (containers.isEmpty()) {
        EmptyStateView(
            title = "Sin unidades en Patio",
            subtitle = "Registra cajas secas o contenedores para llevar el conteo del patio.",
            icon = Icons.Outlined.LocalShipping
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Total en patio: ${containers.size} unidades",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(containers) { container ->
                ContainerItemCard(
                    container = container,
                    onEdit = { onEditContainer(container) },
                    onDelete = { onDeleteContainer(container) }
                )
            }
        }
    }
}

@Composable
fun DockConditionsTabContent(
    conditions: List<DockCondition>,
    onInspectDock: (DockCondition) -> Unit
) {
    if (conditions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
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
    onDeleteDamaged: (Long) -> Unit
) {
    if (damagedBoxes.isEmpty()) {
        EmptyStateView(
            title = "Sin cajas dañadas reportadas",
            subtitle = "Usa el botón flotante (+) para registrar una unidad dañada detectada el día de hoy.",
            icon = Icons.Outlined.BrokenImage
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Cajas dañadas el día de hoy: ${damagedBoxes.size} unidades",
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
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                            text = "Exportación a Formato Excel (CSV)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Genera reportes locales e independientes en formato de hoja de cálculo universal (CSV) para compartir de forma inmediata por WhatsApp, correo o guardar localmente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Button 1: Inventario de Cajas
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val file = viewModel.exportContainersCsvLocally(context)
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
                                    val file = viewModel.exportDockConditionsCsvLocally(context)
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
                                    val file = viewModel.exportDamagedBoxesCsvLocally(context)
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
                        Text("Exportar Cajas Dañadas")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Button 4: Reporte General Combinado
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val file = viewModel.exportCsvLocally(context)
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
    }
}

// --------------------------------------------------------
// SMALL REUSABLE UI ELEMENTS
// --------------------------------------------------------

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier
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
    category: String,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isOutOfService: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (unit == null) onAdd() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOutOfService) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else if (unit == null) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dock indicator circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOutOfService) MaterialTheme.colorScheme.error
                        else if (unit == null) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dockNumber.toString(),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (isOutOfService) MaterialTheme.colorScheme.onError
                    else if (unit == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (unit == null) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Andén Vacío", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (isOutOfService) {
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
                    Text(text = "Toca para registrar contenedor", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onAdd, modifier = Modifier.testTag("add_dock_unit_btn_$dockNumber")) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Añadir a andén $dockNumber", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(unit.type) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (unit.status == "Cargada") {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("Cargada", color = Color.White, modifier = Modifier.padding(2.dp))
                            }
                        } else {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("Vacía", color = Color.White, modifier = Modifier.padding(2.dp))
                            }
                        }
                        if (isOutOfService) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "F. DE SERVICIO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No. Económico: ${unit.economicNumber}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Placas: ${unit.plates} (${unit.platesLocation})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Compañía: ${unit.company}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!unit.containerSize.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• Medida: ${unit.containerSize}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (unit.status == "Cargada" && unit.loadStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Estatus: ${unit.loadStatus}",
                            color = when (unit.loadStatus) {
                                "En espera de salida" -> MaterialTheme.colorScheme.error
                                "Falta por completar" -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        val hasDetails = !unit.sealCode.isNullOrEmpty() || 
                                         !unit.destination.isNullOrEmpty() || 
                                         !unit.deliveryDocument.isNullOrEmpty() || 
                                         !unit.processedDate.isNullOrEmpty()
                        if (hasDetails) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                                    .padding(6.dp)
                            ) {
                                if (unit.loadStatus == "En espera de salida" && !unit.sealCode.isNullOrEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, size20(), tint = Color(0xFFC62828))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Sello No: ${unit.sealCode}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFC62828)
                                        )
                                    }
                                }
                                if (!unit.destination.isNullOrEmpty()) {
                                    if (unit.loadStatus == "En espera de salida" && !unit.sealCode.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = "Destino: ${unit.destination}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                }
                                if (!unit.deliveryDocument.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Delivery/Doc: ${unit.deliveryDocument}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                }
                                if (!unit.processedDate.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Procesado: ${unit.processedDate}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_dock_unit_btn_$dockNumber")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar unidad", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_dock_unit_btn_$dockNumber")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remover unidad", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ContainerItemCard(
    container: ContainerItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Garage, contentDescription = null)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(container.type) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(containerColor = if (container.status == "Cargada") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
                        Text(container.status, color = Color.White, modifier = Modifier.padding(2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No. Económico: ${container.economicNumber}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Placas: ${container.plates} (${container.platesLocation})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Compañía: ${container.company}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!container.containerSize.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• Medida: ${container.containerSize}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (container.status == "Cargada" && container.loadStatus != null) {
                    Text(
                        text = "Estatus de Carga: ${container.loadStatus}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val hasDetails = !container.sealCode.isNullOrEmpty() || 
                                     !container.destination.isNullOrEmpty() || 
                                     !container.deliveryDocument.isNullOrEmpty() || 
                                     !container.processedDate.isNullOrEmpty()
                    if (hasDetails) {
                        Column(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        ) {
                            if (container.loadStatus == "En espera de salida" && !container.sealCode.isNullOrEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, size20(), tint = Color(0xFFC62828))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Código de Sello: ${container.sealCode}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                            if (!container.destination.isNullOrEmpty()) {
                                if (container.loadStatus == "En espera de salida" && !container.sealCode.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = "Destino: ${container.destination}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }
                            if (!container.deliveryDocument.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Delivery/Doc: ${container.deliveryDocument}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }
                            if (!container.processedDate.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Procesado: ${container.processedDate}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar de patio", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar de patio", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun DockConditionCard(
    condition: DockCondition,
    onClick: () -> Unit
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
        Pair("Luces", condition.isLightsGood)
    )

    val badItems = items.filter { !it.second }
    val isOos = condition.isOutOfService

    Card(
        modifier = Modifier
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badItems.take(5).forEach { item ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(item.first, fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFEBEE),
                                labelColor = Color(0xFFC62828)
                            )
                        )
                    }
                    if (badItems.size > 5) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("+${badItems.size - 5}", fontSize = 10.sp) }
                        )
                    }
                }
            }

            if (condition.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nota: ${condition.notes}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DamagedBoxCard(
    box: DamagedBox,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar reporte de daño", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SyncStatusCard(state: DriveSyncState, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = state !is DriveSyncState.Idle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
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

// Helper icons
@Composable fun size20() = Modifier.size(20.dp)

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
    var type by remember { mutableStateOf(unitToEdit?.type ?: "Caja Seca") }
    var locationType by remember { mutableStateOf(unitToEdit?.locationType ?: locationTypePreset) }
    var dockNumber by remember { mutableStateOf(unitToEdit?.dockNumber ?: dockNumberPreset ?: 1) }
    var economicNumber by remember { mutableStateOf(unitToEdit?.economicNumber ?: "") }
    var plates by remember { mutableStateOf(unitToEdit?.plates ?: "") }
    var platesLocation by remember { mutableStateOf(unitToEdit?.platesLocation ?: "") }
    var status by remember { mutableStateOf(unitToEdit?.status ?: "Vacía") }
    var loadStatus by remember { mutableStateOf(unitToEdit?.loadStatus ?: "Falta por completar") }
    var sealCode by remember { mutableStateOf(unitToEdit?.sealCode ?: "") }
    var containerSize by remember { mutableStateOf(unitToEdit?.containerSize ?: "") }
    var destination by remember { mutableStateOf(unitToEdit?.destination ?: "") }
    var deliveryDocument by remember { mutableStateOf(unitToEdit?.deliveryDocument ?: "") }
    var processedDate by remember { mutableStateOf(unitToEdit?.processedDate ?: "") }
    var isStorageOnly by remember { mutableStateOf(unitToEdit?.isStorageOnly ?: false) }
    var mexCalType by remember { mutableStateOf(unitToEdit?.mexCalType ?: "Interplanta") }
    var receptionDate by remember { mutableStateOf(unitToEdit?.receptionDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var materialType by remember { mutableStateOf(unitToEdit?.materialType ?: "FG") }

    val unitTypes = listOf("Caja Seca", "Intermodal", "Contenedor Marítimo")
    val statusTypes = listOf("Vacía", "Cargada")
    val loadStatusTypes = listOf("Falta por completar", "Falta por procesar", "En espera de salida")

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
                                onClick = { type = t },
                                label = { Text(t, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Almacenaje
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isStorageOnly = !isStorageOnly }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = isStorageOnly,
                            onCheckedChange = { isStorageOnly = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Solo para Almacenaje", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                selected = mexCalType == "Importación",
                                onClick = { mexCalType = "Importación" },
                                label = { Text("Importación") },
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
                            onValueChange = { economicNumber = it },
                            label = { Text("Número Económico") },
                            placeholder = { Text("Ej. E-123") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("econ_num_input"),
                            singleLine = true
                        )

                        val suggestions = remember(economicNumber, containersHistory) {
                            if (economicNumber.isBlank()) {
                                emptyList()
                            } else {
                                containersHistory
                                    .filter { 
                                        it.economicNumber.startsWith(economicNumber, ignoreCase = true) && 
                                        it.economicNumber.lowercase() != economicNumber.lowercase()
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
                                            plates = match.plates
                                            platesLocation = match.platesLocation
                                            type = match.type
                                            status = match.status
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

                        val platesSuggestions = remember(plates, containersHistory) {
                            if (plates.isBlank()) {
                                emptyList()
                            } else {
                                containersHistory
                                    .filter { 
                                        it.plates.startsWith(plates, ignoreCase = true) && 
                                        it.plates.lowercase() != plates.lowercase()
                                    }
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
                                            status = match.status
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
                    Text("Procedencia de Placas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    var platesLocExpanded by remember { mutableStateOf(false) }
                    val allowedStates = listOf("California", "Maine", "Tennessee", "Oklahoma", "Indiana")
                    ExposedDropdownMenuBox(
                        expanded = platesLocExpanded,
                        onExpandedChange = { platesLocExpanded = !platesLocExpanded }
                    ) {
                        OutlinedTextField(
                            value = platesLocation,
                            onValueChange = { platesLocation = it },
                            label = { Text("Lugar de procedencia de placas") },
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
                        value = materialType ?: "FG",
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { loadStatus = ls }
                                        .padding(vertical = 4.dp)
                                  ) {
                                    RadioButton(selected = loadStatus == ls, onClick = { loadStatus = ls })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(ls)
                                }
                            }
                        }
                    }

                    // Código de Sello (solo si es "En espera de salida")
                    if (loadStatus == "En espera de salida") {
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

                    // Destino, Delivery, Fecha de procesado (for all loaded statuses)
                    item {
                        Text("Destino de Exportación", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        var destExpanded by remember { mutableStateOf(false) }
                        val exportDestinations = listOf(
                            "CANADÁ", "UNITED KINGDOM", "FRANCE", "COREA", "JAPÓN", 
                            "CHINA", "COLOMBIA", "KENTUKY", "GERMANY", "SWEDEN", 
                            "KN", "NZ", "PERRIS", "POLONIA", "DSV"
                        )
                        ExposedDropdownMenuBox(
                            expanded = destExpanded,
                            onExpandedChange = { destExpanded = !destExpanded }
                        ) {
                            OutlinedTextField(
                                value = destination,
                                onValueChange = { destination = it },
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

                    if (loadStatus != "Falta por completar" && loadStatus != "Falta por procesar") {
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
                                if (economicNumber.isEmpty()) {
                                    Log.e("AddContainerDialog", "No. Economico is required")
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
                                    if (loadStatus == "Falta por completar" || loadStatus == "Falta por procesar") "" else processedDate,
                                    isStorageOnly,
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
                                    it.economicNumber.lowercase() != economicNumber.lowercase()
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
                            traffic = true; lights = true
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

                // The 9 Checklist items
                item { InspectionCheckItem(label = "Rampa niveladora", checked = ramp, onCheckedChange = { ramp = it }) }
                item { InspectionCheckItem(label = "Seguro de rampa", checked = rLock, onCheckedChange = { rLock = it }) }
                item { InspectionCheckItem(label = "Gancho de retención de andén", checked = hook, onCheckedChange = { hook = it }) }
                item { InspectionCheckItem(label = "Cortina metálica", checked = curtain, onCheckedChange = { curtain = it }) }
                item { InspectionCheckItem(label = "Shelter (Sello de andén)", checked = shelter, onCheckedChange = { shelter = it }) }
                item { InspectionCheckItem(label = "Gomas protectoras", checked = rubbers, onCheckedChange = { rubbers = it }) }
                item { InspectionCheckItem(label = "Topes de seguridad para llantas", checked = stoppers, onCheckedChange = { stoppers = it }) }
                item { InspectionCheckItem(label = "Semáforo exterior de andén", checked = traffic, onCheckedChange = { traffic = it }) }
                item { InspectionCheckItem(label = "Luces interiores de andén", checked = lights, onCheckedChange = { lights = it }) }

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
                                onConfirm(ramp, rLock, hook, curtain, shelter, rubbers, stoppers, traffic, lights, notes, isOutOfService)
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
    } catch (e: Exception) {
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

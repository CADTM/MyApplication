package com.example.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ReportRepository
import com.example.ui.viewmodel.ReportViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: ReportViewModel
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = ReportRepository(db.reportDao())
        viewModel = ReportViewModel(repository)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun mainScreen_showsTitleAndHeroSection() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Control de Andenes y Cajas").assertIsDisplayed()
        composeTestRule.onNodeWithText("REPORTE LOGÍSTICO").assertIsDisplayed()
    }

    @Test
    fun mainScreen_tabSwitching_worksCorrectly() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        // 1. Initial tab: Andenes
        composeTestRule.onNodeWithText("Andenes de Exportación (1 al 9)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Andén 1").assertIsDisplayed()

        // 2. Switch to "En Patio"
        composeTestRule.onNodeWithText("En Patio").performClick()
        composeTestRule.onNodeWithText("Sin unidades en Patio").assertIsDisplayed()

        // 3. Switch to "Estatus Andenes"
        composeTestRule.onNodeWithText("Estatus Andenes").performClick()
        composeTestRule.onNodeWithText("Inspección Diaria de Condiciones de Seguridad").assertIsDisplayed()
        composeTestRule.onNodeWithText("Andén 1 - Exportación").assertIsDisplayed()

        // 4. Switch to "Cajas Dañadas"
        composeTestRule.onNodeWithText("Cajas Dañadas").performClick()
        composeTestRule.onNodeWithText("Sin cajas dañadas reportadas").assertIsDisplayed()

        // 5. Switch to "Exportar / Drive"
        composeTestRule.onNodeWithText("Exportar / Drive").performClick()
        composeTestRule.onNodeWithText("Datos de Identificación del Reporte").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exportación a Formato Excel (CSV)").assertIsDisplayed()
    }

    @Test
    fun mainScreen_clickAddFab_showsAddContainerDialog() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        // Click Add FAB on Andenes tab
        composeTestRule.onNodeWithTag("add_item_fab").performClick()

        // Verify dialog title and content
        composeTestRule.onNodeWithText("Registrar Nueva Unidad").assertIsDisplayed()
        composeTestRule.onNodeWithText("Número Económico").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ubicación").assertIsDisplayed()
    }

    @Test
    fun mainScreen_addDamagedFab_showsAddDamagedDialog() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        // Go to Cajas Dañadas tab
        composeTestRule.onNodeWithText("Cajas Dañadas").performClick()

        // Click Add Damaged FAB
        composeTestRule.onNodeWithTag("add_damaged_fab").performClick()

        // Verify dialog
        composeTestRule.onNodeWithText("Reportar Caja Dañada").assertIsDisplayed()
        composeTestRule.onNodeWithText("Descripción del Daño").assertIsDisplayed()
    }

    @Test
    fun mainScreen_addUnitFlow() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        // Open dialog
        composeTestRule.onNodeWithTag("add_item_fab").performClick()

        // Fill economic number
        composeTestRule.onNodeWithTag("econ_num_input").performTextInput("E-999")
        
        // Confirm
        composeTestRule.onNodeWithTag("confirm_add_container_btn").performClick()

        // Verify it appears in the list (Andén 1 by default if preset)
        composeTestRule.onNodeWithText("No. Económico: E-999").assertIsDisplayed()
    }
}

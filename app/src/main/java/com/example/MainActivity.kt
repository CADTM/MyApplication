package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.ReportRepository
import com.example.ui.screens.MainScreen
import com.google.firebase.FirebaseApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ReportViewModel
import com.example.ui.viewmodel.ReportViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize FirebaseApp", e)
        }

        enableEdgeToEdge()

        // Initialize DB, Repository and ViewModel
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ReportRepository(database.reportDao())
        val viewModel: ReportViewModel by viewModels {
            ReportViewModelFactory(repository)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    )
                }
            }
        }
    }
}

package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pdf.PdfProcessor
import com.example.pdf.ToolType
import com.example.ui.PdfViewModel
import com.example.ui.components.ProcessingDialog
import com.example.ui.components.ResultDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ToolDetailScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle opening PDF from external intent
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val isProcessing by viewModel.isProcessing.collectAsState()
                val processingMessage by viewModel.processingMessage.collectAsState()
                val lastResult by viewModel.lastResult.collectAsState()
                val activeViewingFile by viewModel.activeViewingFile.collectAsState()
                val errorMessage by viewModel.errorMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(errorMessage) {
                    errorMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearError()
                    }
                }

                // If active viewing file is set externally, navigate to viewer
                LaunchedEffect(activeViewingFile) {
                    if (activeViewingFile != null) {
                        navController.navigate("viewer")
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("home") {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onToolSelected = { tool ->
                                        if (tool == ToolType.READER) {
                                            navController.navigate("tool/${tool.id}")
                                        } else {
                                            navController.navigate("tool/${tool.id}")
                                        }
                                    },
                                    onOpenViewer = { file ->
                                        viewModel.setActiveViewingFile(file)
                                        navController.navigate("viewer")
                                    },
                                    onViewAllHistory = {
                                        navController.navigate("history")
                                    }
                                )
                            }

                            composable("tool/{toolId}") { backStackEntry ->
                                val toolId = backStackEntry.arguments?.getString("toolId")
                                val tool = ToolType.entries.find { it.id == toolId } ?: ToolType.MERGE
                                ToolDetailScreen(
                                    tool = tool,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { file ->
                                        viewModel.setActiveViewingFile(file)
                                        navController.navigate("viewer")
                                    }
                                )
                            }

                            composable("viewer") {
                                if (activeViewingFile != null) {
                                    PdfViewerScreen(
                                        file = activeViewingFile!!,
                                        onBack = {
                                            navController.popBackStack()
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable("history") {
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { file ->
                                        viewModel.setActiveViewingFile(file)
                                        navController.navigate("viewer")
                                    }
                                )
                            }
                        }

                        // Global Loading Dialog
                        if (isProcessing) {
                            ProcessingDialog(
                                message = processingMessage,
                                subMessage = "Running fast native PDF processing"
                            )
                        }

                        // Global Result Dialog
                        lastResult?.let { result ->
                            if (result.success) {
                                ResultDialog(
                                    result = result,
                                    onDismiss = { viewModel.clearResult() },
                                    onOpenViewer = { file ->
                                        viewModel.setActiveViewingFile(file)
                                        navController.navigate("viewer")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null && (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND)) {
            lifecycleScope.launch {
                try {
                    val file = PdfProcessor.copyUriToTempFile(applicationContext, uri, "opened_")
                    viewModel.setActiveViewingFile(file)
                } catch (e: Exception) {
                    // Ignore intent read failures gracefully
                }
            }
        }
    }
}

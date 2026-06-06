package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppDatabase
import com.example.data.CertificateRepository
import com.example.data.EmbarkationRepository
import com.example.notification.NotificationHelper
import com.example.ui.addedit.AddEditScreen
import com.example.ui.dashboard.MainTabShell
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CertificateViewModel
import com.example.ui.viewmodel.CertificateViewModelFactory
import com.example.ui.viewmodel.EmbarkationViewModel
import com.example.ui.viewmodel.EmbarkationViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize DB and Repositories on device local-first
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = CertificateRepository(database.certificateDao())
        val embarkationRepository = EmbarkationRepository(database.embarkationDao())
        val notificationHelper = NotificationHelper(applicationContext)

        // 2. Instantiate state ViewModel using factories
        val viewModel: CertificateViewModel by viewModels {
            CertificateViewModelFactory(repository, notificationHelper)
        }
        val embarkationViewModel: EmbarkationViewModel by viewModels {
            EmbarkationViewModelFactory(embarkationRepository, application)
        }

        setContent {
            val themeMode by embarkationViewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val view = androidx.compose.ui.platform.LocalView.current
            val context = androidx.compose.ui.platform.LocalContext.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (context as? android.app.Activity)?.window
                    if (window != null) {
                        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !useDarkTheme
                        controller.isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }
            }

            val currentLanguage by embarkationViewModel.currentLanguage.collectAsState()

            MyApplicationTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
                    // Handle notifications permissions elegantly on dynamic launch
                    NotificationPermissionHandler()

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        val uiState by viewModel.uiState.collectAsState()

                        NavHost(
                            navController = navController,
                            startDestination = "dashboard"
                        ) {
                            composable("dashboard") {
                                MainTabShell(
                                    certificateViewModel = viewModel,
                                    embarkationViewModel = embarkationViewModel,
                                    certificateUiState = uiState,
                                    onAddDocumentClick = {
                                        navController.navigate("add_edit?certId=-1")
                                    },
                                    onEditDocumentClick = { certId ->
                                        navController.navigate("add_edit?certId=$certId")
                                    }
                                )
                            }
                            composable(
                                route = "add_edit?certId={certId}",
                                arguments = listOf(
                                    navArgument("certId") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    }
                                )
                            ) { backStackEntry ->
                                val certId = backStackEntry.arguments?.getInt("certId") ?: -1
                                AddEditScreen(
                                    viewModel = viewModel,
                                    certId = certId,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionHandler() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d("NotificationPermission", "Expiry notifications permission has been granted!")
            } else {
                Log.w("NotificationPermission", "Expiry notifications permission has been denied!")
                Toast.makeText(
                    context,
                    "Post notification permission is required to receive on-device reminders when certificates are expiring.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        LaunchedEffect(Unit) {
            if (!hasPermission) {
                launcher.launch(permission)
            }
        }
    }
}

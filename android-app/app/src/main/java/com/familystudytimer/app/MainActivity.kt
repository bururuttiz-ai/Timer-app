package com.familystudytimer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familystudytimer.app.ui.HistoryScreen
import com.familystudytimer.app.ui.HomeScreen
import com.familystudytimer.app.ui.PermissionScreen
import com.familystudytimer.app.ui.SettingsScreen
import com.familystudytimer.app.ui.SetupScreen
import com.familystudytimer.app.ui.StudyViewModel
import com.familystudytimer.app.ui.theme.StudyTimerTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as StudyTimerApp

    private val viewModel: StudyViewModel by viewModels {
        StudyViewModel.Factory(app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StudyTimerTheme {
                var notificationGranted by remember { mutableStateOf(hasNotificationPermission()) }
                var exactAlarmGranted by remember { mutableStateOf(app.alarmScheduler.canScheduleExactAlarms()) }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> notificationGranted = granted }

                val isLoaded by viewModel.isLoaded.collectAsState()

                if (!isLoaded) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AppNavigation(
                        viewModel = viewModel,
                        app = app,
                        notificationGranted = notificationGranted,
                        exactAlarmGranted = exactAlarmGranted,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestExactAlarmPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                                )
                            }
                        },
                        refreshExactAlarmGranted = { exactAlarmGranted = app.alarmScheduler.canScheduleExactAlarms() },
                    )
                }
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun AppNavigation(
    viewModel: StudyViewModel,
    app: StudyTimerApp,
    notificationGranted: Boolean,
    exactAlarmGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    refreshExactAlarmGranted: () -> Unit,
) {
    val navController: NavHostController = rememberNavController()
    val needsSetup by viewModel.needsSetup.collectAsState()
    val childrenState by viewModel.children.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (needsSetup) "setup" else "permissions",
    ) {
        composable("setup") {
            SetupScreen(onCreate = { name1, name2 ->
                viewModel.createChildren(name1, name2)
                navController.navigate("permissions") { popUpTo("setup") { inclusive = true } }
            })
        }
        composable("permissions") {
            androidx.compose.runtime.LaunchedEffect(Unit) { refreshExactAlarmGranted() }
            PermissionScreen(
                needsNotificationPermission = !notificationGranted,
                needsExactAlarmPermission = !exactAlarmGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onContinue = {
                    navController.navigate("home") { popUpTo("permissions") { inclusive = true } }
                },
            )
        }
        composable("home") {
            HomeScreen(
                children = childrenState,
                onToggleStudy = { childId -> viewModel.toggleStudy(childId) {} },
                onManualAdjust = { childId, delta -> viewModel.manualAdjust(childId, delta) },
                onOpenSettings = { childId -> navController.navigate("settings/$childId") },
                onOpenHistory = { childId -> navController.navigate("history/$childId") },
            )
        }
        composable("settings/{childId}") { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId")?.toLongOrNull() ?: return@composable
            SettingsScreen(childId = childId, repository = app.repository, onBack = { navController.popBackStack() })
        }
        composable("history/{childId}") { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId")?.toLongOrNull() ?: return@composable
            HistoryScreen(childId = childId, repository = app.repository, onBack = { navController.popBackStack() })
        }
    }
}

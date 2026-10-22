package io.github.dallanoce.medreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.dallanoce.medreminder.alarm.AlarmScheduler
import io.github.dallanoce.medreminder.permissions.AlertPermission
import io.github.dallanoce.medreminder.ui.screens.HomeScreen
import io.github.dallanoce.medreminder.ui.screens.MedicationPlanScreen
import io.github.dallanoce.medreminder.ui.screens.PermissionsScreen
import io.github.dallanoce.medreminder.ui.screens.SettingsScreen
import io.github.dallanoce.medreminder.ui.screens.rememberMissingAlertPermissions
import io.github.dallanoce.medreminder.ui.theme.MedReminderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear the day if it ended while the phone was off, and restore today's alarms in case they
        // were lost (e.g. "Alarms & reminders" was revoked or the app was force-stopped).
        lifecycleScope.launch { AlarmScheduler(this@MainActivity).runDailyReset() }

        enableEdgeToEdge()
        setContent {
            MedReminderTheme {
                val missingPermissions = rememberMissingAlertPermissions()
                var showPermissions by rememberSaveable { mutableStateOf(missingPermissions.isNotEmpty()) }
                if (showPermissions) {
                    PermissionsScreen(missingPermissions, onContinue = { showPermissions = false })
                } else {
                    MedReminderApp(missingPermissions, onFixPermissions = { showPermissions = true })
                }
            }
        }
    }
}

@Composable
fun MedReminderApp(missingPermissions: List<AlertPermission>, onFixPermissions: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { screen ->
                item(
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = stringResource(screen.labelResId)
                        )
                    },
                    label = { Text(stringResource(screen.labelResId)) },
                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        // Each screen has its own Scaffold/TopAppBar that applies the status bar inset,
        // so no outer Scaffold here (it would apply the inset twice under edge-to-edge).
        NavHost(
            navController = navController,
            startDestination = AppDestinations.HOME.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestinations.HOME.route) {
                HomeScreen(missingPermissions = missingPermissions, onFixPermissions = onFixPermissions)
            }
            composable(AppDestinations.MEDICATION_PLAN.route) {
                MedicationPlanScreen()
            }
            composable(AppDestinations.SETTINGS.route) {
                SettingsScreen()
            }
        }
    }
}

enum class AppDestinations(
    val labelResId: Int,
    val icon: ImageVector,
    val route: String
) {
    HOME(R.string.home_label, Icons.Default.Home, "home"),
    MEDICATION_PLAN(R.string.med_plan_label, Icons.Default.Assignment, "medication_plan"),
    SETTINGS(R.string.settings_label, Icons.Default.Settings, "settings"),
}

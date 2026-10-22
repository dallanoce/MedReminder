package io.github.dallanoce.medreminder.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dallanoce.medreminder.R
import io.github.dallanoce.medreminder.permissions.AlertPermission

/** The alert permissions that are still missing, re-checked every time the app comes back to the foreground. */
@Composable
fun rememberMissingAlertPermissions(): List<AlertPermission> {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(AlertPermission.missing(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = AlertPermission.missing(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return missing
}

@SuppressLint("InlinedApi") // POST_NOTIFICATIONS is only requested on Android 13+
@Composable
fun PermissionsScreen(missing: List<AlertPermission>, onContinue: () -> Unit) {
    val context = LocalContext.current
    var notificationsPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context.findActivity()
        if (!isGranted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            // The system won't show the dialog again: the user has to allow it in Settings.
            notificationsPermanentlyDenied = true
        }
    }

    fun canRequestNotificationsAtRuntime(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsPermanentlyDenied &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    fun request(permission: AlertPermission) {
        if (permission == AlertPermission.NOTIFICATIONS && canRequestNotificationsAtRuntime()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.openSettings(permission.settingsIntent(context))
        }
    }

    // Ask for notifications straight away on first display, as the system dialog is the quickest path.
    LaunchedEffect(Unit) {
        if (AlertPermission.NOTIFICATIONS in missing && canRequestNotificationsAtRuntime()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(onBack = onContinue)

    Scaffold(
        bottomBar = {
            // Pinned so "Continue" stays visible however long the list is.
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                if (missing.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.perm_missing_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (missing.isEmpty()) R.string.perm_continue_btn else R.string.perm_continue_anyway_btn
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.perm_setup_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.perm_setup_intro),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            AlertPermission.entries.filter { it.isApplicable }.forEach { permission ->
                val granted = permission !in missing
                ListItem(
                    headlineContent = { Text(stringResource(permission.titleRes)) },
                    supportingContent = { Text(stringResource(permission.descriptionRes)) },
                    trailingContent = {
                        if (granted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.perm_granted),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            FilledTonalButton(onClick = { request(permission) }) {
                                val label = if (permission == AlertPermission.NOTIFICATIONS &&
                                    !canRequestNotificationsAtRuntime()
                                ) R.string.perm_open_settings_btn else R.string.perm_allow_btn
                                Text(stringResource(label))
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some devices don't have the specific page: fall back to the app's details page.
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package io.github.dallanoce.medreminder.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dallanoce.medreminder.R
import io.github.dallanoce.medreminder.ui.InputValidation
import io.github.dallanoce.medreminder.ui.MedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MedViewModel = viewModel()) {
    val context = LocalContext.current
    val ringtoneUri by viewModel.ringtoneUri.collectAsState()
    // The language actually applied; it can also be changed from the system's app settings.
    val language = AppCompatDelegate.getApplicationLocales()[0]?.language
    val userName by viewModel.userName.collectAsState()
    val reminderOffset by viewModel.reminderOffset.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let { viewModel.setRingtoneUri(it.toString()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.user_name_setting_label)) },
                    supportingContent = {
                        Text(userName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.user_name_not_set))
                    },
                    leadingContent = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showNameDialog = true
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reminder_offset_label)) },
                    supportingContent = {
                        Text(
                            if (reminderOffset == 0) stringResource(R.string.reminder_off)
                            else pluralStringResource(R.plurals.reminder_minutes_before, reminderOffset, reminderOffset)
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Timer, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showOffsetDialog = true
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ringtone_setting_label)) },
                    supportingContent = {
                        val name = ringtoneUri?.let {
                            RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context)
                        } ?: stringResource(R.string.default_ringtone)
                        Text(name)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.select_ringtone_title))
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { Uri.parse(it) })
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        }
                        ringtonePickerLauncher.launch(intent)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language_setting_label)) },
                    supportingContent = {
                        val languageName = when (language) {
                            "it" -> stringResource(R.string.lang_it)
                            "en" -> stringResource(R.string.lang_en)
                            else -> stringResource(R.string.lang_system)
                        }
                        Text(languageName)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Language, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showLanguageDialog = true
                    }
                )
            }
        }
    }

    if (showNameDialog) {
        var nameText by remember { mutableStateOf(userName ?: "") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.user_name_setting_label)) },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.user_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setUserName(nameText)
                    showNameDialog = false
                }) {
                    Text(stringResource(R.string.save_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

    if (showOffsetDialog) {
        var offsetText by remember { mutableStateOf(reminderOffset.toString()) }
        val offset = InputValidation.parseReminderOffset(offsetText)
        AlertDialog(
            onDismissRequest = { showOffsetDialog = false },
            title = { Text(stringResource(R.string.reminder_offset_label)) },
            text = {
                OutlinedTextField(
                    value = offsetText,
                    onValueChange = { offsetText = InputValidation.digitsOnly(it) },
                    label = { Text(stringResource(R.string.minutes_label)) },
                    singleLine = true,
                    isError = offset == null,
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.reminder_offset_hint,
                                InputValidation.MAX_REMINDER_OFFSET_MINUTES
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        offset?.let { viewModel.setReminderOffset(it) }
                        showOffsetDialog = false
                    },
                    enabled = offset != null
                ) {
                    Text(stringResource(R.string.save_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOffsetDialog = false }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_lang_title)) },
            text = {
                Column {
                    LanguageOption(stringResource(R.string.lang_system), "") {
                        viewModel.setLanguage("")
                        setAppLanguage("")
                        showLanguageDialog = false
                    }
                    LanguageOption(stringResource(R.string.lang_en), "en") {
                        viewModel.setLanguage("en")
                        setAppLanguage("en")
                        showLanguageDialog = false
                    }
                    LanguageOption(stringResource(R.string.lang_it), "it") {
                        viewModel.setLanguage("it")
                        setAppLanguage("it")
                        showLanguageDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }
}

@Composable
fun LanguageOption(label: String, code: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
    }
}

/** An empty [languageCode] goes back to the system language. */
private fun setAppLanguage(languageCode: String) {
    val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
    AppCompatDelegate.setApplicationLocales(appLocale)
}

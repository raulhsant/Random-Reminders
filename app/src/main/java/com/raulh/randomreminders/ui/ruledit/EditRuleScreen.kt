package com.raulh.randomreminders.ui.ruledit

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulh.randomreminders.R
import com.raulh.randomreminders.ui.activate.ActivationField
import com.raulh.randomreminders.domain.TimeText
import java.time.LocalTime

/**
 * Editor de regra (novo ou existente): nome + janela diária + parâmetros
 * de aleatoriedade, com validação ao vivo reutilizada da etapa 1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleScreen(
    onBack: () -> Unit,
    viewModel: EditRuleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            if (pickedUri == null) {
                viewModel.onSoundChange("silent")
            } else {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        pickedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.onSoundChange(pickedUri.toString())
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.ruleId == null) stringResource(R.string.rule_title_new)
                        else stringResource(R.string.rule_title_edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.field_name)) },
                supportingText = { Text(stringResource(R.string.field_hint_name)) },
                isError = ActivationField.NAME in state.fieldErrors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.field_description)) },
                supportingText = { Text(stringResource(R.string.field_hint_description)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    value = state.form.start,
                    label = stringResource(R.string.field_start),
                    defaultTime = LocalTime.of(7, 0),
                    onValueChange = { viewModel.onFieldChange(ActivationField.START, it) },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    value = state.form.end,
                    label = stringResource(R.string.field_end),
                    defaultTime = LocalTime.of(22, 0),
                    onValueChange = { viewModel.onFieldChange(ActivationField.END, it) },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.form.shots,
                onValueChange = { viewModel.onFieldChange(ActivationField.SHOTS, it) },
                label = { Text(stringResource(R.string.field_shots)) },
                supportingText = { Text(stringResource(R.string.field_hint_shots)) },
                isError = ActivationField.SHOTS in state.fieldErrors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.form.spacing,
                onValueChange = { viewModel.onFieldChange(ActivationField.SPACING, it) },
                label = { Text(stringResource(R.string.field_spacing)) },
                supportingText = { Text(stringResource(R.string.field_hint_spacing)) },
                isError = ActivationField.SPACING in state.fieldErrors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.field_repeat), modifier = Modifier.weight(1f))
                Switch(checked = state.repeats, onCheckedChange = viewModel::onRepeatsChange)
            }

            if (state.repeats) {
                OutlinedTextField(
                    value = state.form.intervalDays,
                    onValueChange = { viewModel.onFieldChange(ActivationField.INTERVAL, it) },
                    label = { Text(stringResource(R.string.field_interval)) },
                    supportingText = { Text(stringResource(R.string.field_hint_interval)) },
                    isError = ActivationField.INTERVAL in state.fieldErrors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (state.ruleId == null) R.string.field_create_enabled else R.string.field_active),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.isActive, onCheckedChange = viewModel::onActiveChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.field_show_next_time), modifier = Modifier.weight(1f))
                Switch(checked = state.showNextTime, onCheckedChange = viewModel::onShowNextTimeChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.field_sound), style = MaterialTheme.typography.labelLarge)
                Text(soundLabel(state.soundUri), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = {
                        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                            state.soundUri
                                ?.takeIf { it != "silent" }
                                ?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        soundPicker.launch(picker)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_select_sound), modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (state.ruleErrors.isNotEmpty()) {
                state.ruleErrors.forEach { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.ruleId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.rule_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.save(onBack) },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.rule_save),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TimeField(
    value: String,
    label: String,
    defaultTime: LocalTime,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = TimeText.parseOrNull(value) ?: defaultTime
    val dialog = remember(value) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onValueChange(TimeText.format(LocalTime.of(hour, minute))) },
            time.hour,
            time.minute,
            DateFormat.is24HourFormat(context),
        )
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        isError = TimeText.parseOrNull(value) == null,
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { dialog.show() }) {
                Icon(painterResource(R.drawable.ic_clock), contentDescription = label)
            }
        },
        modifier = modifier.clickable { dialog.show() },
    )
}

@Composable
private fun soundLabel(soundUri: String?): String {
    val context = LocalContext.current
    return when (soundUri) {
        null -> stringResource(R.string.sound_default)
        "silent" -> stringResource(R.string.sound_silent)
        else -> runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context)
        }.getOrNull() ?: stringResource(R.string.sound_selected_fallback)
    }
}

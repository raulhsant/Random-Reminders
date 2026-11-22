package com.raulh.randomreminders.ui.rulelist

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulh.randomreminders.R
import com.raulh.randomreminders.ui.ReminderPermissions

/**
 * Lista de lembretes: resumo da regra, próximo disparo, interruptor on/off,
 * editar (toque no cartão) e excluir (ícone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: RuleListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Reavalia as permissões ao voltar das telas de configuração do Android.
    var permissionsTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionsTick++ }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val needsNotificationPermission = remember(permissionsTick) {
        ReminderPermissions.needsNotificationPermission(context)
    }
    LaunchedEffect(needsNotificationPermission) {
        if (needsNotificationPermission && !notificationPermissionRequested) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val needsExactAlarm = remember(permissionsTick) {
        ReminderPermissions.needsExactAlarmPermission(context)
    }
    val needsFullScreen = remember(permissionsTick) {
        ReminderPermissions.needsFullScreenIntentPermission(context)
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleRules = state.rules.filter { item ->
        item.rule.name.contains(searchQuery, ignoreCase = true) ||
            item.rule.description.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rule_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = stringResource(R.string.history_title),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "mascot-header") { MascotHeader() }
                if (needsExactAlarm || needsFullScreen) {
                    item(key = "alarm-permissions") {
                        AlarmPermissionNotice(
                            needsExactAlarm = needsExactAlarm,
                            needsFullScreen = needsFullScreen,
                            onOpenExactAlarm = { ReminderPermissions.openExactAlarmSettings(context) },
                            onOpenFullScreen = {
                                ReminderPermissions.openFullScreenIntentSettings(context)
                            },
                        )
                    }
                }
                item(key = "alarm-search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.rule_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!state.isLoading && visibleRules.isEmpty()) {
                    item(key = "empty-state") {
                        Text(
                            text = stringResource(
                                if (searchQuery.isBlank()) R.string.rule_list_empty
                                else R.string.rule_list_no_results,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }
                items(visibleRules, key = { it.rule.id }) { item ->
                    RuleCard(
                        item = item,
                        onToggle = { viewModel.setEnabled(item.rule.id, it) },
                        onEdit = { onEditRule(item.rule.id) },
                        onDelete = { viewModel.deleteRule(item.rule.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MascotHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.dog_icon),
            contentDescription = stringResource(R.string.mascot_description),
            modifier = Modifier.height(150.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

@Composable
private fun AlarmPermissionNotice(
    needsExactAlarm: Boolean,
    needsFullScreen: Boolean,
    onOpenExactAlarm: () -> Unit,
    onOpenFullScreen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (needsExactAlarm) {
                Text(
                    text = stringResource(R.string.permission_exact_alarm),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenExactAlarm, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_allow_exact_alarm))
                }
            }
            if (needsFullScreen) {
                Text(
                    text = stringResource(R.string.permission_full_screen),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenFullScreen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_allow_full_screen))
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    item: RuleItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { onEdit() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.nextShotText?.let { next ->
                    Text(
                        text = stringResource(R.string.rule_next_shot, next),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.rule.active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = item.rule.active,
                onCheckedChange = onToggle,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.rule_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

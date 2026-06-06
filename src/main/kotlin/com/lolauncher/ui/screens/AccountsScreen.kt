package com.lolauncher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolauncher.data.models.AccountType
import com.lolauncher.data.models.StoredAccount
import com.lolauncher.ui.components.*
import com.lolauncher.ui.theme.AccentGreen
import com.lolauncher.ui.theme.AccentRed
import com.lolauncher.ui.theme.BrandYellow
import com.lolauncher.viewmodel.LauncherViewModel

@Composable
fun AccountsScreen(viewModel: LauncherViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(viewModel) {
        val listener: () -> Unit = { tick++ }
        viewModel.addStateListener(listener)
        onDispose { viewModel.removeStateListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE") val trigger = tick

    val accounts = viewModel.accounts
    val activeId = viewModel.activeAccountId
    val textures = viewModel.activeSkinTextures

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<StoredAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<StoredAccount?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Аккаунты (${accounts.size})")
            FilledTonalButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))
            ) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp), tint = BrandYellow)
                Spacer(Modifier.width(6.dp))
                Text("Добавить")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CharacterPreview(
                previewBytes = textures.previewBytes,
                skinBytes = textures.skinBytes,
                cloakBytes = textures.cloakBytes,
                username = viewModel.playerProfile?.username ?: "",
                modifier = Modifier.width(200.dp)
            )

            LauncherCard(Modifier.weight(1f)) {
                Text("Активный аккаунт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val active = accounts.find { it.id == activeId }
                if (active != null) {
                    Text(active.username, style = MaterialTheme.typography.headlineSmall, color = BrandYellow)
                    Spacer(Modifier.height(4.dp))
                    AccountTypeBadge(AccountType.valueOf(active.type))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Источник скина: ${textures.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                } else {
                    Text(
                        "Аккаунт не выбран — добавьте или выберите из списка",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LauncherCard(Modifier.weight(1f).fillMaxWidth()) {
            if (accounts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text("Нет аккаунтов", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        AccountListItem(
                            account = account,
                            isActive = account.id == activeId,
                            avatarBytes = if (account.id == activeId) textures.skinBytes else null,
                            onActivate = { viewModel.activateAccount(account.id) },
                            onEdit = { editTarget = account },
                            onDelete = { deleteTarget = account }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onAddOffline = { viewModel.addOfflineAccount(it); showAddDialog = false },
            onAddEly = { viewModel.addElyAccount(it); showAddDialog = false },
            onAddMicrosoft = { viewModel.addMicrosoftAccount(); showAddDialog = false }
        )
    }

    editTarget?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editTarget = null },
            onSave = { viewModel.editAccount(account.id, it); editTarget = null }
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Удалить «${account.username}» из менеджера?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(account.id)
                    deleteTarget = null
                }) { Text("Удалить", color = AccentRed) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AccountListItem(
    account: StoredAccount,
    isActive: Boolean,
    avatarBytes: ByteArray?,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) BrandYellow.copy(0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) BrandYellow.copy(0.5f) else MaterialTheme.colorScheme.outline.copy(0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinThumb(avatarBytes, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.username, fontWeight = FontWeight.SemiBold)
                AccountTypeBadge(AccountType.valueOf(account.type))
            }
            if (!isActive) {
                IconButton(onClick = onActivate) {
                    Icon(Icons.Default.CheckCircle, "Сделать активным", tint = AccentGreen)
                }
            } else {
                Icon(Icons.Default.Star, null, tint = BrandYellow, modifier = Modifier.size(22.dp))
            }
            if (account.type != AccountType.MICROSOFT.name) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Редактировать")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Удалить", tint = AccentRed.copy(0.7f))
            }
        }
    }
}

@Composable
private fun AccountTypeBadge(type: AccountType) {
    val (label, color) = when (type) {
        AccountType.MICROSOFT -> "Microsoft" to AccentGreen
        AccountType.ELY_BY -> "Ely.by" to BrandYellow
        AccountType.OFFLINE -> "Offline" to MaterialTheme.colorScheme.onSurface.copy(0.6f)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.12f)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAddOffline: (String) -> Unit,
    onAddEly: (String) -> Unit,
    onAddMicrosoft: () -> Unit
) {
    var nick by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.OFFLINE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить аккаунт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == AccountType.OFFLINE,
                        onClick = { selectedType = AccountType.OFFLINE },
                        label = { Text("Offline") }
                    )
                    FilterChip(
                        selected = selectedType == AccountType.ELY_BY,
                        onClick = { selectedType = AccountType.ELY_BY },
                        label = { Text("Ely.by") }
                    )
                    FilterChip(
                        selected = selectedType == AccountType.MICROSOFT,
                        onClick = { selectedType = AccountType.MICROSOFT },
                        label = { Text("Microsoft") }
                    )
                }
                if (selectedType != AccountType.MICROSOFT) {
                    OutlinedTextField(
                        value = nick,
                        onValueChange = { nick = it },
                        label = { Text("Ник") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "Откроется браузер для входа через Microsoft. После авторизации аккаунт сохранится автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (selectedType) {
                    AccountType.MICROSOFT -> onAddMicrosoft()
                    AccountType.ELY_BY -> if (nick.trim().length >= 2) onAddEly(nick.trim())
                    AccountType.OFFLINE -> if (nick.trim().length >= 2) onAddOffline(nick.trim())
                }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun EditAccountDialog(
    account: StoredAccount,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nick by remember { mutableStateOf(account.username) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать аккаунт") },
        text = {
            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it },
                label = { Text("Ник") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(nick.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

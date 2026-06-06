package com.lolauncher.ui.screens

import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolauncher.data.models.*
import com.lolauncher.ui.components.*
import com.lolauncher.ui.components.ContextMenuItem
import com.lolauncher.ui.theme.AccentGreen
import com.lolauncher.ui.theme.AccentRed
import com.lolauncher.ui.theme.BrandYellow
import com.lolauncher.ui.theme.BrandYellowDark
import com.lolauncher.viewmodel.LauncherViewModel

/**
 * Главный экран лаунчера.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(viewModel: LauncherViewModel, onOpenAccounts: () -> Unit = {}) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(viewModel) {
        val listener: () -> Unit = { tick++ }
        viewModel.addStateListener(listener)
        onDispose { viewModel.removeStateListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE") val trigger = tick

    val settings = viewModel.settings
    val profile = viewModel.playerProfile
    val isRunning = viewModel.launchStatus == LaunchStatus.RUNNING
    val isInstalled = viewModel.isSelectedVersionInstalled()
    val animationsEnabled = !settings.disableAnimations

    ErrorDialog(viewModel.errorDialogMessage, onDismiss = { viewModel.dismissError() })
    LaunchActionDialog(
        visible = viewModel.showLaunchActionDialog,
        selected = viewModel.pendingLaunchAction,
        rememberChoice = viewModel.rememberLaunchActionChoice,
        onSelectedChange = { viewModel.updatePendingLaunchAction(it) },
        onRememberChange = { viewModel.updateRememberLaunchActionChoice(it) },
        onConfirm = { viewModel.confirmLaunchActionDialog() },
        onDismiss = { viewModel.dismissLaunchActionDialog() }
    )
    AvatarPickerDialog(
        visible = viewModel.showAvatarPicker,
        options = viewModel.avatarPickerOptions,
        onOptionSelected = { viewModel.selectAvatarOption(it) },
        onDismiss = { viewModel.dismissAvatarPicker() }
    )

    var showAvatarContextMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        LauncherCard(Modifier.fillMaxWidth()) {
            Box {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(
                    viewModel.skinImageBytes,
                    settings.username,
                    72,
                    onClick = { viewModel.openAvatarPicker() },
                    onSecondaryClick = { showAvatarContextMenu = true }
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.username ?: settings.username.ifBlank { "Игрок" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val accountType = profile?.accountType ?: AccountType.OFFLINE
                        AccountTypeChip(accountType)
                        if (viewModel.isAccountAuthInProgress) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandYellow)
                            Text("Авторизация...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                FilledTonalButton(
                    onClick = onOpenAccounts,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))
                ) {
                    Icon(Icons.Default.ManageAccounts, null, Modifier.size(18.dp), tint = BrandYellow)
                    Spacer(Modifier.width(6.dp))
                    Text("Аккаунты")
                }
                }
                ContextMenuDropdown(
                    expanded = showAvatarContextMenu,
                    onDismiss = { showAvatarContextMenu = false },
                    items = listOf(
                        ContextMenuItem(
                            label = "Сбросить аватарку",
                            icon = Icons.Default.Restore,
                            onClick = { viewModel.resetAvatarToDefault() }
                        ),
                        ContextMenuItem(
                            label = "Выбрать аватарку",
                            icon = Icons.Default.PhotoLibrary,
                            onClick = { viewModel.openAvatarPicker() }
                        )
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        VersionTypeFilter(viewModel.selectedVersionType) { viewModel.setVersionType(it) }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.versionSearchQuery,
            onValueChange = { viewModel.setVersionSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск версии...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = BrandYellow) },
            trailingIcon = {
                if (viewModel.versionSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setVersionSearch("") }) {
                        Icon(Icons.Default.Clear, "Очистить")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandYellow,
                cursorColor = BrandYellow
            )
        )

        Spacer(Modifier.height(8.dp))

        VersionList(
            versions = viewModel.filteredVersions,
            selectedVersion = viewModel.selectedVersion,
            onVersionSelected = { viewModel.selectVersion(it) },
            onVersionDelete = { viewModel.deleteVersion(it) },
            isLoading = viewModel.isLoading,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.height(16.dp))

        DownloadProgressBar(viewModel.downloadProgress, animationsEnabled)

        if (viewModel.isInstalling) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.cancelInstall() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
            ) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = AccentRed)
                Spacer(Modifier.width(8.dp))
                Text("Отменить установку")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Версия:", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                Text(
                    viewModel.selectedVersion?.displayName ?: viewModel.selectedVersion?.id ?: "Не выбрана",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (viewModel.selectedVersion != null) {
                    val loader = loaderKindFor(viewModel.selectedVersion!!)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoaderKindBadge(loader)
                        Text(
                            if (isInstalled) "Установлена" else "Не установлена",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isInstalled) AccentGreen else MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            }

            when {
                isRunning -> PlayButton(
                    onClick = { viewModel.stopGame() },
                    isRunning = true,
                    animationsEnabled = animationsEnabled,
                    modifier = Modifier.width(220.dp)
                )
                isInstalled -> PlayButton(
                    onClick = { viewModel.requestLaunchGame() },
                    enabled = viewModel.selectedVersion != null && !viewModel.isLoading && !viewModel.isInstalling,
                    animationsEnabled = animationsEnabled,
                    modifier = Modifier.width(220.dp)
                )
                else -> InstallButton(
                    onClick = { viewModel.installVersion() },
                    enabled = viewModel.selectedVersion != null && !viewModel.isLoading,
                    isInstalling = viewModel.isInstalling,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
private fun VersionTypeFilter(selectedType: VersionType, onTypeSelected: (VersionType) -> Unit) {
    val types = listOf(
        VersionType.ALL, VersionType.INSTALLED, VersionType.RELEASE, VersionType.SNAPSHOT,
        VersionType.FORGE, VersionType.FABRIC, VersionType.OPTIFINE
    )

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type.displayName) },
                leadingIcon = if (selectedType == type) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = BrandYellow) }
                } else null,
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandYellow.copy(0.2f),
                    selectedLabelColor = BrandYellow
                )
            )
        }
    }
}

@Composable
private fun VersionList(
    versions: List<VersionItem>,
    selectedVersion: VersionItem?,
    onVersionSelected: (VersionItem) -> Unit,
    onVersionDelete: (VersionItem) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    LauncherCard(modifier.fillMaxHeight()) {
        SectionTitle("Версии Minecraft")
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandYellow)
            }
            versions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Версии не найдены",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                items(versions, key = { it.uniqueKey }) { version ->
                    VersionListItem(
                        version = version,
                        isSelected = selectedVersion?.uniqueKey == version.uniqueKey,
                        onClick = { onVersionSelected(version) },
                        onDelete = { onVersionDelete(version) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VersionListItem(
    version: VersionItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val brand = com.lolauncher.ui.theme.LocalBrandColors.current
    val bgColor = when {
        isSelected -> brand.selectedBg
        version.isInstalled -> AccentGreen.copy(0.06f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isSelected -> brand.selectedBorder
        version.isInstalled -> AccentGreen.copy(0.3f)
        else -> MaterialTheme.colorScheme.outline.copy(0.2f)
    }

    Box(Modifier.fillMaxWidth()) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Secondary) {
                    showContextMenu = true
                }
            }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    version.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoaderKindBadge(loaderKindFor(version))
                    if (version.isInstalled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Установлена", style = MaterialTheme.typography.bodySmall, color = AccentGreen)
                        }
                    }
                }
            }
            if (isSelected) {
                Icon(Icons.Default.Star, null, tint = brand.primary, modifier = Modifier.size(22.dp))
            }
        }
    }

    if (version.isInstalled) {
        ContextMenuDropdown(
            expanded = showContextMenu,
            onDismiss = { showContextMenu = false },
            items = listOf(
                ContextMenuItem(
                    label = "Удалить версию",
                    icon = Icons.Default.Delete,
                    destructive = true,
                    onClick = { showDeleteDialog = true }
                )
            )
        )
    }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить версию?") },
            text = {
                Text("Версия «${version.displayName}» и все её файлы будут удалены из папки versions. Это действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Удалить", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun AccountTypeChip(type: AccountType) {
    val (label, color) = when (type) {
        AccountType.MICROSOFT -> "Microsoft" to AccentGreen
        AccountType.ELY_BY -> "Ely.by" to BrandYellow
        AccountType.OFFLINE -> "Offline" to MaterialTheme.colorScheme.onSurface.copy(0.6f)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.12f)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

private fun loaderKindFor(version: VersionItem): LoaderKind = when (version.type) {
    VersionType.FORGE -> LoaderKind.FORGE
    VersionType.FABRIC -> LoaderKind.FABRIC
    VersionType.OPTIFINE -> LoaderKind.OPTIFINE
    else -> LoaderKind.VANILLA
}

@Composable
private fun LoaderKindBadge(kind: LoaderKind) {
    val (icon, color) = when (kind) {
        LoaderKind.VANILLA -> Icons.Default.Grass to AccentGreen
        LoaderKind.FORGE -> Icons.Default.Build to AccentRed.copy(0.85f)
        LoaderKind.FABRIC -> Icons.Default.Extension to BrandYellowDark
        LoaderKind.OPTIFINE -> Icons.Default.Visibility to BrandYellow
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.12f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(kind.label, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun VersionTypeBadge(type: VersionType) {
    val (label, color) = when (type) {
        VersionType.RELEASE -> "Release" to AccentGreen
        VersionType.SNAPSHOT -> "Snapshot" to BrandYellow
        VersionType.FORGE -> "Forge" to AccentRed.copy(0.8f)
        VersionType.FABRIC -> "Fabric" to BrandYellowDark
        VersionType.OPTIFINE -> "OptiFine" to BrandYellow
        VersionType.INSTALLED -> "Установлена" to AccentGreen
        else -> type.displayName to MaterialTheme.colorScheme.onSurface.copy(0.5f)
    }

    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.12f)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

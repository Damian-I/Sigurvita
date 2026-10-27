package com.zemmy.sigurvita.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.AppThemeMode
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.ui.components.settings.ReaderSettingsActions
import com.zemmy.sigurvita.ui.components.settings.ReaderSettingsContent
import com.zemmy.sigurvita.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val appearance by viewModel.appearance.collectAsState()

    val readerActions = remember(viewModel) {
        ReaderSettingsActions(
            onFitMode = viewModel::updateFitMode,
            onReaderBackground = viewModel::updateReaderBackground,
            onOrientation = viewModel::updateOrientation,
            onVolumeKeyNavigation = viewModel::updateVolumeKeyNav,
            onVolumeKeyInvert = viewModel::updateVolumeKeyInvert,
            onTouchEdgeNavigation = viewModel::updateTouchEdgeNav,
            onTapZoneLayout = viewModel::updateTapZoneLayout,
            onShowTapZones = viewModel::updateShowTapZones,
            onCustomTapZones = viewModel::updateCustomTapZones,
            onCustomZonesMirrorInRtl = viewModel::updateCustomZonesMirrorInRtl,
            onShowPageNumber = viewModel::updateShowPageNumber,
            onKeepScreenOn = viewModel::updateKeepScreenOn,
            onSpeechBubbleDetection = viewModel::updateSpeechBubbleDetectionEnabled,
            onSpeechBubbleBoxes = viewModel::updateSpeechBubbleBoxesEnabled,
            onSpeechBubbleConfidence = viewModel::updateSpeechBubbleConfidence,
            onPanelAwareOrder = viewModel::updatePanelAwareOrder,
            onPanelDebugOverlay = viewModel::updatePanelDebugOverlay
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            SettingsSectionTitle(stringResource(R.string.appearance))

            SettingsCard {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = stringResource(R.string.appearance_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(AppThemeMode.entries) { mode ->
                            val selected = appearance.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateThemeMode(mode) },
                                shape = MaterialTheme.shapes.small,
                                label = {
                                    Text(
                                        text = stringResource(mode.stringRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }

                SettingsDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.appearance_dynamic_color),
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        stringResource(R.string.appearance_dynamic_color_subtitle)
                    } else {
                        "Requires Android 12 or newer"
                    },
                    checked = appearance.dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = viewModel::updateDynamicColor
                )

                SettingsDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.colorscape_title),
                    subtitle = stringResource(R.string.colorscape_subtitle),
                    checked = settings.colorScapeEnabled,
                    onCheckedChange = viewModel::updateColorScapeEnabled
                )
            }

            SettingsSectionTitle(stringResource(R.string.reader_configuration))
            ReaderSettingsContent(
                settings = settings,
                actions = readerActions
            )

            SettingsSectionTitle(stringResource(R.string.offline_downloads))

            SettingsCard {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = stringResource(R.string.auto_delete_local_downloads),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.auto_delete_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(AutoDeleteOption.entries) { option ->
                            val selected = settings.autoDeleteOption == option
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateAutoDeleteOption(option) },
                                shape = MaterialTheme.shapes.small,
                                label = {
                                    Text(
                                        text = stringResource(option.stringRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            SettingsSectionTitle("Account")

            SettingsCard {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    var refreshMessage by remember { mutableStateOf<String?>(null) }
                    var isRefreshing by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = {
                            isRefreshing = true
                            refreshMessage = null
                            viewModel.refreshLogin { success, error ->
                                isRefreshing = false
                                refreshMessage = if (success) "Login refreshed successfully!" else (error ?: "Refresh failed")
                            }
                        },
                        enabled = !isRefreshing,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRefreshing) "Refreshing Login..." else "Refresh Login")
                    }

                    if (refreshMessage != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = refreshMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (refreshMessage!!.contains("successfully")) {
                                com.zemmy.sigurvita.ui.theme.SigurvitaTheme.colors.completed
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    OutlinedButton(
                        onClick = {
                            viewModel.logout()
                            onLogoutClick()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.disconnect_logout))
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(16.dp)
                .width(3.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
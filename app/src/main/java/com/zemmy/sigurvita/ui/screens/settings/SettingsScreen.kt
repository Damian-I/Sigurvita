package com.zemmy.sigurvita.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.zemmy.sigurvita.R
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.FitMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_configuration),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.fit_mode), style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = (settings.fitMode == mode),
                        onClick = { viewModel.updateFitMode(mode) },
                        label = { Text(stringResource(mode.stringRes)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.volume_key_nav), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.volume_key_nav_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.volumeKeyNavigationEnabled,
                    onCheckedChange = { viewModel.updateVolumeKeyNav(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.invert_volume_keys), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.invert_volume_keys_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.volumeKeyInvert,
                    onCheckedChange = { viewModel.updateVolumeKeyInvert(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.touch_edge_nav), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.touch_edge_nav_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.touchEdgeNavigationEnabled,
                    onCheckedChange = { viewModel.updateTouchEdgeNav(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.keep_screen_on_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.speech_bubble_detection), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.speech_bubble_detection_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.speechBubbleDetectionEnabled,
                    onCheckedChange = { viewModel.updateSpeechBubbleDetectionEnabled(it) }
                )
            }

            if (settings.speechBubbleDetectionEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.speech_bubble_boxes), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.speech_bubble_boxes_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.speechBubbleBoxesEnabled,
                        onCheckedChange = { viewModel.updateSpeechBubbleBoxesEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                var sliderConfidence by remember(settings.speechBubbleConfidence) { mutableFloatStateOf(settings.speechBubbleConfidence) }
                Text(
                    stringResource(R.string.detection_confidence, (sliderConfidence * 100).toInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = sliderConfidence,
                    onValueChange = { sliderConfidence = it },
                    onValueChangeFinished = { viewModel.updateSpeechBubbleConfidence(sliderConfidence) },
                    valueRange = 0.02f..0.50f,
                    steps = 48,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.panel_aware_order), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.panel_aware_order_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.panelAwareOrderEnabled,
                        onCheckedChange = { viewModel.updatePanelAwareOrder(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.panel_debug_overlay), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.panel_debug_overlay_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.panelDebugOverlayEnabled,
                        onCheckedChange = { viewModel.updatePanelDebugOverlay(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.offline_downloads),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.auto_delete_local_downloads), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.auto_delete_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AutoDeleteOption.entries) { option ->
                    FilterChip(
                        selected = (settings.autoDeleteOption == option),
                        onClick = { viewModel.updateAutoDeleteOption(option) },
                        label = { Text(stringResource(option.stringRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRefreshing) "Refreshing Login..." else "Refresh Login")
            }

            if (refreshMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = refreshMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (refreshMessage!!.contains("successfully")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogoutClick()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.disconnect_logout))
            }
        }
    }
}

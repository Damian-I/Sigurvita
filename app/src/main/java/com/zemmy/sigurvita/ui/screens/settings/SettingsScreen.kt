package com.zemmy.sigurvita.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.DownloadFormat
import com.zemmy.sigurvita.core.model.DownloadLocation
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.ui.components.FolderPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var showFolderPicker by remember { mutableStateOf(false) }

    if (showFolderPicker) {
        FolderPickerDialog(
            initialPath = settings.downloadPath,
            onDismiss = { showFolderPicker = false },
            onFolderSelected = { path ->
                viewModel.updateDownloadPath(path)
                showFolderPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                text = "Reader Configuration",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Reading Mode", style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                ReadingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = (settings.readingMode == mode),
                        onClick = { viewModel.updateReadingMode(mode) },
                        label = { Text(mode.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Fit Mode", style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = (settings.fitMode == mode),
                        onClick = { viewModel.updateFitMode(mode) },
                        label = { Text(mode.name) },
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
                    Text("Volume Key Page Navigation", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Use hardware volume keys to flip pages",
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
                    Text("Invert Volume Keys", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Invert volume up/down direction",
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
                    Text("Keep Screen On", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Prevent display timeout while reading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Offline Downloads",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Default Storage Folder", style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                DownloadLocation.entries.forEach { loc ->
                    FilterChip(
                        selected = (settings.downloadLocation == loc),
                        onClick = { viewModel.updateDownloadLocation(loc) },
                        label = { Text(loc.displayName) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Download File Format", style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                DownloadFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = (settings.downloadFormat == fmt),
                        onClick = { viewModel.updateDownloadFormat(fmt) },
                        label = { Text(fmt.displayName) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = settings.downloadPath,
                    onValueChange = { viewModel.updateDownloadPath(it) },
                    label = { Text("Custom Download Folder Path") },
                    placeholder = { Text("Default: Documents/Sigurvita") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showFolderPicker = true }) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Browse Folder",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Auto-Delete Local Downloads", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Automatically clean up local files after reading (never affects Kavita server files)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AutoDeleteOption.entries) { option ->
                    FilterChip(
                        selected = (settings.autoDeleteOption == option),
                        onClick = { viewModel.updateAutoDeleteOption(option) },
                        label = { Text(option.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogoutClick()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect Server & Logout")
            }
        }
    }
}

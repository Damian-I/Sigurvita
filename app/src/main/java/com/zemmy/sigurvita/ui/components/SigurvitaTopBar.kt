package com.zemmy.sigurvita.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.zemmy.sigurvita.R

/**
 * Shared TopAppBar composable across Sigurvita screens providing standardized search handling,
 * incognito toggle icon, navigation icon, and custom action slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigurvitaTopBar(
    title: String,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: (() -> Unit)? = null,
    isIncognitoMode: Boolean = false,
    onToggleIncognito: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                )
            } else {
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        navigationIcon = navigationIcon,
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            } else {
                if (onToggleSearch != null) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                }
                if (onToggleIncognito != null) {
                    IconButton(onClick = onToggleIncognito) {
                        Icon(
                            imageVector = if (isIncognitoMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Incognito Mode",
                            tint = if (isIncognitoMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            actions()
        }
    )
}

package com.zemmy.sigurvita.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zemmy.sigurvita.R
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderBackground
import com.zemmy.sigurvita.core.model.ReaderOrientation
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.ui.screens.reader.fixedColor

/**
 * Every reader option the user can change. Each host screen (Settings, in-reader sheet)
 * builds one of these from its own ViewModel, so side effects stay in the ViewModel.
 */
data class ReaderSettingsActions(
    val onFitMode: (FitMode) -> Unit,
    val onReaderBackground: (ReaderBackground) -> Unit,
    val onOrientation: (ReaderOrientation) -> Unit,
    val onVolumeKeyNavigation: (Boolean) -> Unit,
    val onVolumeKeyInvert: (Boolean) -> Unit,
    val onTouchEdgeNavigation: (Boolean) -> Unit,
    val onShowPageNumber: (Boolean) -> Unit,
    val onKeepScreenOn: (Boolean) -> Unit,
    val onSpeechBubbleDetection: (Boolean) -> Unit,
    val onSpeechBubbleBoxes: (Boolean) -> Unit,
    val onSpeechBubbleConfidence: (Float) -> Unit,
    val onPanelAwareOrder: (Boolean) -> Unit,
    val onPanelDebugOverlay: (Boolean) -> Unit,
    val onReadingMode: (ReadingMode) -> Unit = {},
    // Only the in-reader sheet supplies this; see [ReaderSettingsContent.bubbleNavActive].
    val onBubbleNavActive: (Boolean) -> Unit = {}
)

/**
 * Full reader settings: Reading, Navigation, Display, Speech bubbles, Debug (last).
 * Colors come from LocalContentColor, so it works on the dark reader sheet and the themed Settings screen.
 */
@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    actions: ReaderSettingsActions,
    modifier: Modifier = Modifier,
    showReadingMode: Boolean = false,
    libraryDefaultMode: ReadingMode? = null,
    /**
     * Live state of speech-bubble navigation mode, or null to hide its switch. Only the in-reader sheet passes it:
     * the mode is a reader-session state (never persisted), so it has no place on the global Settings screen.
     */
    bubbleNavActive: Boolean? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ReaderReadingSection(
            settings = settings,
            actions = actions,
            showReadingMode = showReadingMode,
            libraryDefaultMode = libraryDefaultMode
        )
        SettingsDivider()
        ReaderNavigationSection(settings, actions)
        SettingsDivider()
        ReaderDisplaySection(settings, actions)
        SettingsDivider()
        ReaderSpeechBubbleSection(settings, actions, bubbleNavActive = bubbleNavActive)
        if (settings.speechBubbleDetectionEnabled) {
            SettingsDivider()
            ReaderDebugSection(settings, actions)
        }
    }
}

@Composable
fun ReaderReadingSection(
    settings: ReaderSettings,
    actions: ReaderSettingsActions,
    modifier: Modifier = Modifier,
    showReadingMode: Boolean = false,
    libraryDefaultMode: ReadingMode? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.reader_section_reading))

        if (showReadingMode) {
            SettingChipGroup(
                title = stringResource(R.string.reading_mode),
                options = ReadingMode.entries.toList(),
                selected = settings.readingMode,
                onSelected = actions.onReadingMode,
                optionLabel = { stringResource(it.stringRes) },
                optionCaption = {
                    if (it == libraryDefaultMode) stringResource(R.string.library_default_label) else null
                }
            )
        }

        SettingChipGroup(
            title = stringResource(R.string.fit_mode),
            options = FitMode.entries.toList(),
            selected = settings.fitMode,
            onSelected = actions.onFitMode,
            optionLabel = { stringResource(it.stringRes) }
        )

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.reader_background), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ReaderBackground.entries.forEach { bg ->
                    OptionTile(
                        selected = settings.readerBackground == bg,
                        label = stringResource(bg.stringRes),
                        onClick = { actions.onReaderBackground(bg) }
                    ) {
                        val swatch = bg.fixedColor()
                        if (swatch != null) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(swatch, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.reader_orientation), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ReaderOrientation.entries.forEach { orientation ->
                    OptionTile(
                        selected = settings.orientation == orientation,
                        label = stringResource(orientation.stringRes),
                        onClick = { actions.onOrientation(orientation) }
                    ) {
                        OrientationIcon(orientation)
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderNavigationSection(
    settings: ReaderSettings,
    actions: ReaderSettingsActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.reader_section_navigation))
        SettingSwitchRow(
            title = stringResource(R.string.volume_key_nav),
            subtitle = stringResource(R.string.volume_key_nav_subtitle),
            checked = settings.volumeKeyNavigationEnabled,
            onCheckedChange = actions.onVolumeKeyNavigation
        )
        SettingSwitchRow(
            title = stringResource(R.string.invert_volume_keys),
            subtitle = stringResource(R.string.invert_volume_keys_subtitle),
            checked = settings.volumeKeyInvert,
            onCheckedChange = actions.onVolumeKeyInvert
        )
        SettingSwitchRow(
            title = stringResource(R.string.touch_edge_nav),
            subtitle = stringResource(R.string.touch_edge_nav_subtitle),
            checked = settings.touchEdgeNavigationEnabled,
            onCheckedChange = actions.onTouchEdgeNavigation
        )
    }
}

@Composable
fun ReaderDisplaySection(
    settings: ReaderSettings,
    actions: ReaderSettingsActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.reader_section_display))
        SettingSwitchRow(
            title = stringResource(R.string.show_page_number),
            subtitle = stringResource(R.string.show_page_number_subtitle),
            checked = settings.showPageNumber,
            onCheckedChange = actions.onShowPageNumber
        )
        SettingSwitchRow(
            title = stringResource(R.string.keep_screen_on),
            subtitle = stringResource(R.string.keep_screen_on_subtitle),
            checked = settings.keepScreenOn,
            onCheckedChange = actions.onKeepScreenOn
        )
    }
}

@Composable
fun ReaderSpeechBubbleSection(
    settings: ReaderSettings,
    actions: ReaderSettingsActions,
    modifier: Modifier = Modifier,
    bubbleNavActive: Boolean? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.reader_section_speech_bubbles))
        SettingSwitchRow(
            title = stringResource(R.string.speech_bubble_detection),
            subtitle = stringResource(R.string.speech_bubble_detection_subtitle),
            checked = settings.speechBubbleDetectionEnabled,
            onCheckedChange = actions.onSpeechBubbleDetection
        )

        if (settings.speechBubbleDetectionEnabled) {
            if (bubbleNavActive != null) {
                // Not a stored setting: it mirrors the reader's live bubble navigation mode, so it also flips when the
                // mode is entered or left by long-pressing a bubble, tapping the centre or holding both volume keys.
                val pagedMode = settings.readingMode != ReadingMode.WEBTOON
                SettingSwitchRow(
                    title = stringResource(R.string.bubble_nav_mode),
                    subtitle = stringResource(
                        if (pagedMode) R.string.bubble_nav_mode_subtitle else R.string.bubble_nav_mode_webtoon_subtitle
                    ),
                    checked = bubbleNavActive && pagedMode,
                    onCheckedChange = actions.onBubbleNavActive,
                    indent = true,
                    enabled = pagedMode
                )
            }

            SettingSwitchRow(
                title = stringResource(R.string.speech_bubble_boxes),
                subtitle = stringResource(R.string.speech_bubble_boxes_subtitle),
                checked = settings.speechBubbleBoxesEnabled,
                onCheckedChange = actions.onSpeechBubbleBoxes,
                indent = true
            )

            var sliderConfidence by remember(settings.speechBubbleConfidence) {
                mutableFloatStateOf(settings.speechBubbleConfidence)
            }
            Text(
                stringResource(R.string.detection_confidence, (sliderConfidence * 100).toInt()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp)
            )
            Slider(
                value = sliderConfidence,
                onValueChange = { sliderConfidence = it },
                onValueChangeFinished = { actions.onSpeechBubbleConfidence(sliderConfidence) },
                valueRange = 0.02f..0.50f,
                steps = 48,
                modifier = Modifier.padding(start = 16.dp)
            )

            SettingSwitchRow(
                title = stringResource(R.string.panel_aware_order),
                subtitle = stringResource(R.string.panel_aware_order_subtitle),
                checked = settings.panelAwareOrderEnabled,
                onCheckedChange = actions.onPanelAwareOrder,
                indent = true
            )
        }
    }
}

@Composable
fun ReaderDebugSection(
    settings: ReaderSettings,
    actions: ReaderSettingsActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.reader_section_debug))
        SettingSwitchRow(
            title = stringResource(R.string.panel_debug_overlay),
            subtitle = stringResource(R.string.panel_debug_overlay_subtitle),
            checked = settings.panelDebugOverlayEnabled,
            onCheckedChange = actions.onPanelDebugOverlay,
            indent = true
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Reusable building blocks
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 10.dp))
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    indent: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (indent) 16.dp else 0.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun <T> SettingChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    optionCaption: @Composable (T) -> String? = { null }
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelected(option) },
                        label = { Text(optionLabel(option)) }
                    )
                    val caption = optionCaption(option)
                    if (caption != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrientationIcon(orientation: ReaderOrientation) {
    val modifier = Modifier.size(24.dp)
    when (orientation) {
        ReaderOrientation.FREE -> Icon(Icons.Default.ScreenRotation, null, modifier = modifier)
        ReaderOrientation.PORTRAIT -> Icon(Icons.Default.StayCurrentPortrait, null, modifier = modifier)
        ReaderOrientation.LOCKED_PORTRAIT -> Icon(Icons.Default.ScreenLockPortrait, null, modifier = modifier)
        ReaderOrientation.REVERSE_PORTRAIT -> Icon(Icons.Default.StayCurrentPortrait, null, modifier = modifier.rotate(180f))
        ReaderOrientation.LANDSCAPE -> Icon(Icons.Default.StayCurrentLandscape, null, modifier = modifier)
        ReaderOrientation.LOCKED_LANDSCAPE -> Icon(Icons.Default.ScreenLockLandscape, null, modifier = modifier)
    }
}

@Composable
private fun OptionTile(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        icon()
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
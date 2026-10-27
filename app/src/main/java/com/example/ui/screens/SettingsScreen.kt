package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileInputStream
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import com.example.ui.components.CastDeviceSelectionDialog
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import com.example.ui.components.DebugLogsDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.HighLatitudeRule
import com.example.model.JuristicMethod
import com.example.ui.components.BatteryGuideDialog
import com.example.ui.components.CitySelectionDialog
import com.example.ui.theme.GoldPrimary
import com.example.viewmodel.AthanUiState
import com.example.viewmodel.AthanViewModel

@Composable
fun SettingsScreen(
    uiState: AthanUiState,
    viewModel: AthanViewModel
) {
    val context = LocalContext.current
    var showCityDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showJuristicDialog by remember { mutableStateOf(false) }
    var showHighLatDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showHijriOffsetDialog by remember { mutableStateOf(false) }
    var showBatteryGuide by remember { mutableStateOf(false) }
    var showPreAlertMinutesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showCastDeviceDialog by remember { mutableStateOf(false) }
    var showCastSoundDialog by remember { mutableStateOf(false) }
    var showDebugLogsDialog by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings & Calculation",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure prayer times rules, sound, and alarms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTimestamp > 2500L) {
                            tapCount = 1
                        } else {
                            tapCount++
                        }
                        lastTapTimestamp = now
                        if (tapCount >= 5) {
                            tapCount = 0
                            showDebugLogsDialog = true
                            Toast.makeText(context, "Diagnostic logs opened", Toast.LENGTH_SHORT).show()
                        } else if (tapCount >= 3) {
                            val remaining = 5 - tapCount
                            Toast.makeText(context, "$remaining taps to open logs", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("settings_top_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Section: Location & Timezone
        item {
            SettingsSectionHeader(title = "Location")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SettingsClickableItem(
                    icon = Icons.Default.LocationOn,
                    title = "City & Location",
                    subtitle = "${uiState.settings.city.displayName} (Lat: %.4f, Lon: %.4f, Alt: %dm, UTC %+.1f)".format(
                        uiState.settings.city.latitude,
                        uiState.settings.city.longitude,
                        uiState.settings.city.elevation.toInt(),
                        uiState.settings.city.timezone
                    ),
                    onClick = { showCityDialog = true }
                )
            }
        }

        // Section: Calculation Rules
        item {
            SettingsSectionHeader(title = "Fiqh & Calculation Rules")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsClickableItem(
                        icon = Icons.Default.Calculate,
                        title = "Calculation Method",
                        subtitle = uiState.settings.calculationMethod.title,
                        onClick = { showMethodDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsClickableItem(
                        icon = Icons.Default.School,
                        title = "Asr Juristic Method",
                        subtitle = uiState.settings.juristicMethod.title,
                        onClick = { showJuristicDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsClickableItem(
                        icon = Icons.Default.Public,
                        title = "High Latitude Adjustment",
                        subtitle = uiState.settings.highLatitudeRule.title,
                        onClick = { showHighLatDialog = true }
                    )
                }
            }
        }

        // Section: Audio & Alarms
        item {
            SettingsSectionHeader(title = "Audio & Alarm Tone")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    val soundSubtitle = when {
                        uiState.settings.athanSound == "chime" -> "Gentle Ding / Chime (Default)"
                        uiState.settings.athanSound == "adhan" || uiState.settings.athanSound == "athan" -> "Athan"
                        uiState.settings.athanSound == "system_notification" -> "System Notification Sound"
                        uiState.settings.athanSound == "system_alarm" -> "System Alarm Ringtone"
                        uiState.settings.athanSound.startsWith("custom_file:") -> {
                            val path = uiState.settings.athanSound.removePrefix("custom_file:")
                            val rawName = File(path).name
                            val originalName = if (rawName.startsWith("custom_") && rawName.indexOf('_', 7) != -1) {
                                rawName.substring(rawName.indexOf('_', 7) + 1)
                            } else {
                                rawName
                            }
                            "Custom MP3: $originalName"
                        }
                        uiState.settings.athanSound.startsWith("file://") -> {
                            val path = Uri.parse(uiState.settings.athanSound).path ?: ""
                            val rawName = File(path).name
                            val originalName = if (rawName.startsWith("custom_") && rawName.indexOf('_', 7) != -1) {
                                rawName.substring(rawName.indexOf('_', 7) + 1)
                            } else {
                                rawName
                            }
                            "Custom MP3: $originalName"
                        }
                        uiState.settings.athanSound.startsWith("content://") -> {
                            try {
                                val title = RingtoneManager.getRingtone(context, Uri.parse(uiState.settings.athanSound))?.getTitle(context)
                                if (!title.isNullOrBlank()) "System: $title" else "Custom Audio"
                            } catch (_: Exception) {
                                "Custom Audio"
                            }
                        }
                        else -> "Gentle Ding / Chime (Default)"
                    }
                    SettingsClickableItem(
                        icon = Icons.Default.MusicNote,
                        title = "Athan / Alert Sound",
                        subtitle = soundSubtitle,
                        onClick = { showSoundDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Pre-Prayer Reminder Alert
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPreAlertMinutesDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Pre-Prayer Reminder Alert",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.settings.prePrayerReminderEnabled)
                                        "${uiState.settings.prePrayerReminderMinutes} min before prayer (live countdown)"
                                    else
                                        "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.settings.prePrayerReminderEnabled,
                            onCheckedChange = {
                                viewModel.updatePrePrayerAlert(context, it, uiState.settings.prePrayerReminderMinutes)
                            },
                            modifier = Modifier.testTag("pre_prayer_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    SettingsClickableItem(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Battery & Background Whitelist",
                        subtitle = "Ensure Android does not kill prayer alarms in Doze mode",
                        onClick = { showBatteryGuide = true }
                    )
                }
            }
        }

        // Section: Google / Chromecast Audio
        item {
            SettingsSectionHeader(title = "Google / Chromecast Audio")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Master Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (uiState.settings.castEnabled) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = null,
                                tint = if (uiState.settings.castEnabled) GoldPrimary else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Cast to Google/Chromecast",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.settings.castEnabled)
                                        "Broadcasts prayer call to smart speaker (and always on phone)"
                                    else
                                        "Disabled (phone audio only)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.settings.castEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateCastEnabled(enabled)
                                if (enabled) {
                                    com.example.cast.CastMediaManager.startDiscovery(context)
                                    showCastDeviceDialog = true
                                }
                            },
                            modifier = Modifier.testTag("cast_toggle_switch")
                        )
                    }

                    if (uiState.settings.castEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Device Selection Row
                        SettingsClickableItem(
                            icon = Icons.Default.Speaker,
                            title = "Cast Target (Speaker or Group)",
                            subtitle = uiState.settings.castDeviceName ?: "Tap to choose speaker or Speaker Group",
                            onClick = { showCastDeviceDialog = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Alert Sound Selection Row
                        val castSoundSubtitle = when {
                            uiState.settings.castAlertSound == "chime" -> "Gentle Ding / Chime"
                            uiState.settings.castAlertSound == "adhan" || uiState.settings.castAlertSound == "athan" -> "Athan"
                            uiState.settings.castAlertSound == "system_notification" -> "System Notification Sound (Ding/Ping)"
                            uiState.settings.castAlertSound == "system_alarm" -> "System Alarm Ringtone"
                            uiState.settings.castAlertSound.startsWith("custom_file:") || uiState.settings.castAlertSound.startsWith("file://") -> {
                                val path = if (uiState.settings.castAlertSound.startsWith("custom_file:")) {
                                    uiState.settings.castAlertSound.removePrefix("custom_file:")
                                } else {
                                    Uri.parse(uiState.settings.castAlertSound).path ?: ""
                                }
                                val rawName = File(path).name
                                val originalName = if (rawName.startsWith("custom_") && rawName.indexOf('_', 7) != -1) {
                                    rawName.substring(rawName.indexOf('_', 7) + 1)
                                } else {
                                    rawName
                                }
                                "Custom MP3: $originalName"
                            }
                            uiState.settings.castAlertSound.startsWith("content://") -> {
                                try {
                                    val title = RingtoneManager.getRingtone(context, Uri.parse(uiState.settings.castAlertSound))?.getTitle(context)
                                    if (!title.isNullOrBlank()) "System: $title" else "Custom Audio"
                                } catch (_: Exception) {
                                    "Custom Audio"
                                }
                            }
                            else -> "Athan"
                        }

                        SettingsClickableItem(
                            icon = Icons.Default.MusicNote,
                            title = "Cast Alert Sound",
                            subtitle = castSoundSubtitle,
                            onClick = { showCastSoundDialog = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Test Play Notification Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Test Notification Playback",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.isTestingCast)
                                        "Testing live playback on ${uiState.settings.castDeviceName ?: "Google / Chromecast speaker"}..."
                                    else
                                        "Verify sound on ${uiState.settings.castDeviceName ?: "Google / Chromecast speaker"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isTestingCast) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    if (uiState.isTestingCast) {
                                        viewModel.stopTestCastPlayback(context)
                                    } else {
                                        viewModel.testCastPlayback(context)
                                    }
                                },
                                modifier = Modifier.testTag("test_cast_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isTestingCast) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isTestingCast) "Stop" else "Test Play")
                            }
                        }
                    }
                }
            }
        }

        // Section: Display & Calendar
        item {
            SettingsSectionHeader(title = "Display & Calendar")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "24-Hour Time Format",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.settings.is24HourFormat) "24h (e.g. 15:45)" else "12h AM/PM (e.g. 3:45 PM)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = uiState.settings.is24HourFormat,
                            onCheckedChange = { viewModel.update24HourFormat(it) },
                            modifier = Modifier.testTag("format_24h_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    SettingsClickableItem(
                        icon = Icons.Default.CalendarMonth,
                        title = "Hijri Date Adjustment",
                        subtitle = if (uiState.settings.hijriDayOffset == 0) "Standard (0 days)" else "%+d days".format(uiState.settings.hijriDayOffset),
                        onClick = { showHijriOffsetDialog = true }
                    )
                }
            }
        }

        // Local Masjid Timetable Disclaimer Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Check Local Masjid Timetable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Important Note: Calculated prayer times are theoretical astronomical models based on celestial algorithms. Actual prayer and Iqamah times depend on your local masjid / community consensus. Always check your local masjid timetable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // About Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Athan Alarm v${com.example.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keep us in your prayers and du'as.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Privacy Policy Item
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsClickableItem(
                    icon = Icons.Default.Security,
                    title = "Privacy Policy",
                    subtitle = "100% on-device & offline. No data collected.",
                    onClick = { showPrivacyDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogs
    if (showDebugLogsDialog) {
        DebugLogsDialog(onDismiss = { showDebugLogsDialog = false })
    }
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }
    if (showCityDialog) {
        CitySelectionDialog(
            currentCity = uiState.settings.city,
            onCitySelected = { viewModel.setCity(context, it) },
            onUseGps = { viewModel.refreshLocation(context) },
            onDismiss = { showCityDialog = false }
        )
    }

    if (showMethodDialog) {
        CalculationMethodDialog(
            selectedMethod = uiState.settings.calculationMethod,
            onSelect = {
                viewModel.updateCalculationMethod(context, it)
                showMethodDialog = false
            },
            onDismiss = { showMethodDialog = false }
        )
    }

    if (showJuristicDialog) {
        JuristicMethodDialog(
            selectedMethod = uiState.settings.juristicMethod,
            onSelect = {
                viewModel.updateJuristicMethod(context, it)
                showJuristicDialog = false
            },
            onDismiss = { showJuristicDialog = false }
        )
    }

    if (showHighLatDialog) {
        HighLatRuleDialog(
            selectedRule = uiState.settings.highLatitudeRule,
            onSelect = {
                viewModel.updateHighLatitudeRule(context, it)
                showHighLatDialog = false
            },
            onDismiss = { showHighLatDialog = false }
        )
    }

    if (showSoundDialog) {
        AthanSoundDialog(
            currentSound = uiState.settings.athanSound,
            onSelect = {
                viewModel.updateAthanSound(it)
                showSoundDialog = false
            },
            onDismiss = { showSoundDialog = false }
        )
    }

    if (showHijriOffsetDialog) {
        HijriOffsetDialog(
            currentOffset = uiState.settings.hijriDayOffset,
            onSelect = {
                viewModel.updateHijriOffset(it)
                showHijriOffsetDialog = false
            },
            onDismiss = { showHijriOffsetDialog = false }
        )
    }

    if (showBatteryGuide) {
        BatteryGuideDialog(onDismiss = { showBatteryGuide = false })
    }

    if (showPreAlertMinutesDialog) {
        PreAlertMinutesDialog(
            currentMinutes = uiState.settings.prePrayerReminderMinutes,
            onSelect = { minutes ->
                viewModel.updatePrePrayerAlert(context, true, minutes)
                showPreAlertMinutesDialog = false
            },
            onDismiss = { showPreAlertMinutesDialog = false }
        )
    }

    if (showCastDeviceDialog) {
        CastDeviceSelectionDialog(
            context = context,
            currentSelectedId = uiState.settings.castDeviceId,
            onDeviceSelected = { deviceId, deviceName ->
                viewModel.setSelectedCastDevice(deviceId, deviceName)
                showCastDeviceDialog = false
            },
            onDismiss = { showCastDeviceDialog = false }
        )
    }

    if (showCastSoundDialog) {
        AthanSoundDialog(
            currentSound = uiState.settings.castAlertSound,
            onSelect = { sound ->
                viewModel.updateCastAlertSound(sound)
                showCastSoundDialog = false
            },
            onDismiss = { showCastSoundDialog = false }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CalculationMethodDialog(
    selectedMethod: CalculationMethod,
    onSelect: (CalculationMethod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculation Method") },
        text = {
            LazyColumn {
                items(CalculationMethod.entries.toList()) { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(method) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = method == selectedMethod,
                            onClick = { onSelect(method) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = method.title,
                                fontWeight = if (method == selectedMethod) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Fajr: ${method.fajrAngle}° • Isha: ${if (method.ishaIntervalMin > 0) "+${method.ishaIntervalMin}m" else "${method.ishaAngle}°"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JuristicMethodDialog(
    selectedMethod: JuristicMethod,
    onSelect: (JuristicMethod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asr Juristic Method") },
        text = {
            Column {
                JuristicMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(method) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = method == selectedMethod,
                            onClick = { onSelect(method) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = method.title,
                                fontWeight = if (method == selectedMethod) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = if (method == JuristicMethod.STANDARD)
                                    "Shadow factor = 1 (Shafi'i, Maliki, Hanbali, Ja'fari)"
                                else
                                    "Shadow factor = 2 (Hanafi school)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun HighLatRuleDialog(
    selectedRule: HighLatitudeRule,
    onSelect: (HighLatitudeRule) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("High Latitude Rule") },
        text = {
            Column {
                HighLatitudeRule.entries.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(rule) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = rule == selectedRule,
                            onClick = { onSelect(rule) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rule.title,
                            fontWeight = if (rule == selectedRule) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun saveCustomAudioUri(context: Context, sourceUri: Uri): String? {
    return try {
        val contentResolver = context.contentResolver
        var fileName = "custom_athan.mp3"
        contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrBlank()) {
                    fileName = name
                }
            }
        }
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val soundsDir = File(context.filesDir, "custom_sounds")
        if (!soundsDir.exists()) {
            soundsDir.mkdirs()
        }
        val targetFile = File(soundsDir, "custom_${System.currentTimeMillis()}_$safeFileName")
        contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        "custom_file:${targetFile.absolutePath}"
    } catch (e: Exception) {
        Log.e("SettingsScreen", "Error saving custom audio: ${e.message}", e)
        null
    }
}

@Composable
fun AthanSoundDialog(
    currentSound: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingSoundId by remember { mutableStateOf<String?>(null) }

    // Direct audio/MP3 file picker to avoid "Unable to add custom ringtone" system error
    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val saved = saveCustomAudioUri(context, uri)
            if (saved != null) {
                previewPlayer?.release()
                onSelect(saved)
                Toast.makeText(context, "Custom audio file added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not load selected audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                previewPlayer?.release()
                val saved = saveCustomAudioUri(context, uri)
                if (saved != null) {
                    onSelect(saved)
                } else {
                    onSelect(uri.toString())
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewPlayer?.release()
        }
    }

    fun playPreview(soundId: String) {
        previewPlayer?.release()
        if (playingSoundId == soundId || (soundId == "athan" && playingSoundId == "adhan")) {
            playingSoundId = null
            return
        }

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener {
                    playingSoundId = null
                }
                setOnErrorListener { _, _, _ ->
                    playingSoundId = null
                    true
                }
            }

            when {
                soundId.startsWith("custom_file:") -> {
                    val file = File(soundId.removePrefix("custom_file:"))
                    if (file.exists()) {
                        FileInputStream(file).use { fis ->
                            mp.setDataSource(fis.fd)
                        }
                    } else return
                }
                soundId.startsWith("file://") -> {
                    val path = Uri.parse(soundId).path ?: ""
                    val file = File(path)
                    if (file.exists()) {
                        FileInputStream(file).use { fis ->
                            mp.setDataSource(fis.fd)
                        }
                    } else return
                }
                soundId.startsWith("content://") -> {
                    mp.setDataSource(context, Uri.parse(soundId))
                }
                soundId == "chime" -> {
                    mp.setDataSource(context, Uri.parse("android.resource://${context.packageName}/${R.raw.athan_chime}"))
                }
                soundId == "adhan" || soundId == "athan" -> {
                    mp.setDataSource(context, Uri.parse("android.resource://${context.packageName}/${R.raw.athan_general}"))
                }
                soundId == "system_notification" -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    if (uri != null) mp.setDataSource(context, uri) else return
                }
                soundId == "system_alarm" -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    if (uri != null) mp.setDataSource(context, uri) else return
                }
                else -> {
                    mp.setDataSource(context, Uri.parse("android.resource://${context.packageName}/${R.raw.athan_chime}"))
                }
            }

            mp.prepare()
            mp.start()
            previewPlayer = mp
            playingSoundId = soundId
        } catch (_: Exception) {
            playingSoundId = null
        }
    }

    val customFileTitle = remember(currentSound) {
        if (currentSound.startsWith("custom_file:") || currentSound.startsWith("file://")) {
            val path = if (currentSound.startsWith("custom_file:")) {
                currentSound.removePrefix("custom_file:")
            } else {
                Uri.parse(currentSound).path ?: ""
            }
            val rawName = File(path).name
            val originalName = if (rawName.startsWith("custom_") && rawName.indexOf('_', 7) != -1) {
                rawName.substring(rawName.indexOf('_', 7) + 1)
            } else {
                rawName
            }
            originalName.ifBlank { "Custom Audio" }
        } else null
    }

    val customSoundTitle = remember(currentSound) {
        if (currentSound.startsWith("content://")) {
            try {
                val title = RingtoneManager.getRingtone(context, Uri.parse(currentSound))?.getTitle(context)
                if (!title.isNullOrBlank()) title else "Selected Device Ringtone"
            } catch (_: Exception) {
                "Selected Device Ringtone"
            }
        } else null
    }

    val standardOptions = listOf(
        "chime" to "Gentle Ding / Chime (Default)",
        "athan" to "Athan",
        "system_notification" to "System Notification Sound (Ding/Ping)",
        "system_alarm" to "System Alarm Ringtone"
    )

    AlertDialog(
        onDismissRequest = {
            previewPlayer?.release()
            onDismiss()
        },
        title = { Text("Choose Alert Sound") },
        text = {
            LazyColumn {
                items(standardOptions) { (id, label) ->
                    val isSelected = if (id == "athan") {
                        currentSound == "athan" || currentSound == "adhan"
                    } else {
                        currentSound == id
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                previewPlayer?.release()
                                onSelect(id)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    previewPlayer?.release()
                                    onSelect(id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        IconButton(onClick = { playPreview(id) }) {
                            Icon(
                                imageVector = if (playingSoundId == id || (id == "athan" && playingSoundId == "adhan")) {
                                    Icons.Default.Stop
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = "Preview",
                                tint = GoldPrimary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }

                // If currently using a custom imported file
                if (customFileTitle != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    previewPlayer?.release()
                                    onSelect(currentSound)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = true,
                                    onClick = {
                                        previewPlayer?.release()
                                        onSelect(currentSound)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Custom MP3: $customFileTitle",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { playPreview(currentSound) }) {
                                Icon(
                                    imageVector = if (playingSoundId == currentSound) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = GoldPrimary
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }

                // If currently using a custom ringtone from the system picker
                if (customSoundTitle != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    previewPlayer?.release()
                                    onSelect(currentSound)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                RadioButton(
                                    selected = true,
                                    onClick = {
                                        previewPlayer?.release()
                                        onSelect(currentSound)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "System: $customSoundTitle",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { playPreview(currentSound) }) {
                                Icon(
                                    imageVector = if (playingSoundId == currentSound) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = GoldPrimary
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }

                // Add Custom MP3 / Audio File button
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            previewPlayer?.release()
                            audioFilePickerLauncher.launch("audio/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_custom_mp3_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Custom MP3 / Audio File...")
                    }
                }

                // Browse System Notifications / Alarms button
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            previewPlayer?.release()
                            val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification or Alarm Sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                if (currentSound.startsWith("content://")) {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSound))
                                }
                            }
                            ringtonePickerLauncher.launch(pickerIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("browse_system_sounds_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse System Sounds & Alarms...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                previewPlayer?.release()
                onDismiss()
            }) {
                Text("Done")
            }
        }
    )
}

@Composable
fun HijriOffsetDialog(
    currentOffset: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val offsets = listOf(-2, -1, 0, 1, 2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hijri Calendar Moon Adjustment") },
        text = {
            Column {
                Text(
                    text = "Adjust the Islamic calendar by -2 to +2 days based on local moon sighting declarations in your region.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                offsets.forEach { offset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(offset) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = offset == currentOffset,
                            onClick = { onSelect(offset) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (offset == 0) "Standard (0 days)" else "%+d days".format(offset),
                            fontWeight = if (offset == currentOffset) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PreAlertMinutesDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val minuteOptions = listOf(5, 10, 15, 20, 25, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pre-Prayer Alert Timing") },
        text = {
            Column {
                Text(
                    text = "Choose how many minutes before each prayer the countdown alert notification appears:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                minuteOptions.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentMinutes,
                            onClick = { onSelect(minutes) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (minutes == 20) "$minutes minutes (Default)" else "$minutes minutes",
                            fontWeight = if (minutes == currentMinutes) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val privacyUrl = "https://jmasalma.github.io/AthanAlarm2/privacy.html"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Privacy Policy")
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = "100% On-Device & Offline Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Athan Alarm is engineered as a zero-data-collection, offline-first application. We do not require registration, we do not operate tracking servers, and we never collect, store, or sell your personal information.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Location & Permissions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Location: Used exclusively on-device to calculate astronomical prayer times and Qibla direction. Your coordinates are never sent over the internet.\n• Notifications & Alarms: Used strictly to sound timely Athan alerts.\n• No Third-Party Trackers or Ads: No analytical tracking SDKs or ad networks are included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full Policy Webpage")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

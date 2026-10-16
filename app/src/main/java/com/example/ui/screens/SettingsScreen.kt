package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
                        uiState.settings.athanSound == "adhan" -> "Melodic Takbir Adhan (Built-in)"
                        uiState.settings.athanSound == "system_notification" -> "System Notification Ding / Tone"
                        uiState.settings.athanSound == "system_alarm" -> "System Alarm Ringtone"
                        uiState.settings.athanSound.startsWith("content://") -> {
                            try {
                                RingtoneManager.getRingtone(context, Uri.parse(uiState.settings.athanSound))?.getTitle(context)
                                    ?: "Custom System Sound"
                            } catch (_: Exception) {
                                "Custom System Sound"
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
                            text = "Important Note: Calculated prayer times are theoretical astronomical models based on celestial algorithms. Actual prayer and Iqamah times depend on your local mosque / community consensus. Always check your local masjid timetable.",
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
                        text = "Build ${com.example.BuildConfig.VERSION_CODE} • Offline prayer calculations, home screen widgets, Qibla compass & background alarm reliability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogs
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

@Composable
fun AthanSoundDialog(
    currentSound: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingSoundId by remember { mutableStateOf<String?>(null) }

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
                onSelect(uri.toString())
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
        if (playingSoundId == soundId) {
            playingSoundId = null
            return
        }

        try {
            val uri: Uri = when {
                soundId.startsWith("content://") || soundId.startsWith("file://") -> Uri.parse(soundId)
                soundId == "chime" -> Uri.parse("android.resource://${context.packageName}/${R.raw.athan_chime}")
                soundId == "adhan" -> Uri.parse("android.resource://${context.packageName}/${R.raw.athan_adhan}")
                soundId == "system_notification" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                soundId == "system_alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> Uri.parse("android.resource://${context.packageName}/${R.raw.athan_chime}")
            }
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener {
                    playingSoundId = null
                }
                prepare()
                start()
            }
            previewPlayer = mp
            playingSoundId = soundId
        } catch (_: Exception) {
            playingSoundId = null
        }
    }

    val customSoundTitle = remember(currentSound) {
        if (currentSound.startsWith("content://")) {
            try {
                RingtoneManager.getRingtone(context, Uri.parse(currentSound))?.getTitle(context)
                    ?: "Selected Device Ringtone"
            } catch (_: Exception) {
                "Selected Device Ringtone"
            }
        } else null
    }

    val standardOptions = listOf(
        "chime" to "Gentle Ding / Chime (Default)",
        "adhan" to "Melodic Takbir Adhan (Built-in)",
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
                                selected = id == currentSound,
                                onClick = {
                                    previewPlayer?.release()
                                    onSelect(id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontWeight = if (id == currentSound) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        IconButton(onClick = { playPreview(id) }) {
                            Icon(
                                imageVector = if (playingSoundId == id) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Preview",
                                tint = GoldPrimary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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

                // Pick from System Notifications / Alarms button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
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

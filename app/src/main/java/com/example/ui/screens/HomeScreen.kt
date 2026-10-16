package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlarmMode
import com.example.model.City
import com.example.model.PrayerTimeItem
import com.example.model.PrayerType
import com.example.ui.components.BatteryGuideDialog
import com.example.ui.components.CitySelectionDialog
import com.example.ui.components.MonthlyTimetableDialog
import com.example.ui.theme.GoldPrimary
import com.example.viewmodel.AthanUiState
import com.example.viewmodel.AthanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.delay
import android.widget.Toast

@Composable
fun HomeScreen(
    uiState: AthanUiState,
    viewModel: AthanViewModel
) {
    val context = LocalContext.current
    var showCityDialog by remember { mutableStateOf(false) }
    var showBatteryGuide by remember { mutableStateOf(false) }
    var showMonthlyTimetable by remember { mutableStateOf(false) }
    var editingOffsetPrayer by remember { mutableStateOf<PrayerType?>(null) }
    var alertPopupMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(alertPopupMessage) {
        if (alertPopupMessage != null) {
            delay(2200L)
            alertPopupMessage = null
        }
    }

    val todayGregorian = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // Clean Clean Header Card (No background graphic)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Location & Timetable bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showCityDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("location_selector_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = uiState.settings.city.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uiState.settings.city.elevation > 0) {
                                        Text(
                                            text = "Alt: ${uiState.settings.city.elevation.toInt()}m • ${uiState.settings.calculationMethod.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change City",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showMonthlyTimetable = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .testTag("monthly_timetable_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Monthly Timetable",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dates
                    Text(
                        text = uiState.hijriDate.formattedArabic,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                    Text(
                        text = "${uiState.hijriDate.formattedEnglish} • $todayGregorian",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Next Prayer Countdown Block
                    uiState.nextPrayer?.let { next ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (next.type == PrayerType.NEXT_FAJR) "UPCOMING PRAYER (TOMORROW)" else "NEXT PRAYER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp
                                )
                                Text(
                                    text = "${next.type.englishName} (${next.type.arabicName})",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (uiState.settings.is24HourFormat) next.formattedTime24h else next.formattedTime12h,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.countdownFormatted,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { uiState.countdownProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        // Quick Controls & Audio Test Action Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Test Athan Audio Button
                FilledTonalButton(
                    onClick = {
                        if (uiState.isTestingAlarm) {
                            viewModel.stopTestAlarm(context)
                        } else {
                            viewModel.testAlarm(context)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_athan_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (uiState.isTestingAlarm)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isTestingAlarm) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Test Athan"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.isTestingAlarm) "Stop Audio" else "Test Athan")
                }

                // Battery Whitelist Fix
                OutlinedButton(
                    onClick = { showBatteryGuide = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_guide_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery Guide"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alarm Fix")
                }
            }
        }

        // Today's Prayer Times List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Prayer Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap bell to toggle",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Prayer list items (including Next Fajr at bottom!)
        items(uiState.prayerTimes) { prayer ->
            PrayerCard(
                prayer = prayer,
                is24h = uiState.settings.is24HourFormat,
                onToggleAlarm = {
                    performHaptic(context)
                    val targetType = if (prayer.type == PrayerType.NEXT_FAJR) PrayerType.FAJR else prayer.type
                    val newMode = viewModel.toggleAlarmMode(context, targetType)
                    alertPopupMessage = "${targetType.englishName}: ${newMode.title}"
                    Toast.makeText(
                        context,
                        "${targetType.englishName} alert: ${newMode.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onCardClick = {
                    editingOffsetPrayer = if (prayer.type == PrayerType.NEXT_FAJR) PrayerType.FAJR else prayer.type
                }
            )
        }

        // Local Masjid Timetable Disclaimer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("masjid_disclaimer_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Disclaimer",
                        tint = GoldPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Check Local Masjid Timetable",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Calculated prayer times are astronomical calculations based on your coordinates and fiqh parameters. Always consult your local mosque/masjid timetable for exact Iqamah and congregation times.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Bottom navigation padding
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Floating quick popup banner indicating alert mode change
    AnimatedVisibility(
        visible = alertPopupMessage != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 28.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = alertPopupMessage ?: "",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

    // City Selection Dialog
    if (showCityDialog) {
        CitySelectionDialog(
            currentCity = uiState.settings.city,
            onCitySelected = { selectedCity ->
                viewModel.setCity(context, selectedCity)
            },
            onUseGps = {
                viewModel.refreshLocation(context)
            },
            onDismiss = { showCityDialog = false }
        )
    }

    // Battery Guide Dialog
    if (showBatteryGuide) {
        BatteryGuideDialog(onDismiss = { showBatteryGuide = false })
    }

    // Monthly Timetable Dialog
    if (showMonthlyTimetable) {
        MonthlyTimetableDialog(
            settings = uiState.settings,
            onDismiss = { showMonthlyTimetable = false }
        )
    }

    // Prayer Minute Offset Dialog
    editingOffsetPrayer?.let { type ->
        val currentOffset = uiState.settings.offsets[type] ?: 0
        PrayerOffsetDialog(
            prayerType = type,
            initialOffset = currentOffset,
            onSave = { newOffset ->
                viewModel.updateOffset(context, type, newOffset)
                editingOffsetPrayer = null
            },
            onDismiss = { editingOffsetPrayer = null }
        )
    }
}

@Composable
fun PrayerCard(
    prayer: PrayerTimeItem,
    is24h: Boolean,
    onToggleAlarm: () -> Unit,
    onCardClick: () -> Unit
) {
    val isNext = prayer.isNext
    val isPassed = prayer.isPassed
    val isNextFajr = prayer.type == PrayerType.NEXT_FAJR

    val cardBorder = if (isNext) {
        androidx.compose.foundation.BorderStroke(2.dp, GoldPrimary)
    } else {
        null
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            isNext -> MaterialTheme.colorScheme.surfaceVariant
            isNextFajr -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            isPassed -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "cardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onCardClick() }
            .testTag("prayer_card_${prayer.type.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isNext) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon + Names
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isNext) GoldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getPrayerIcon(prayer.type),
                            contentDescription = prayer.type.englishName,
                            tint = if (isNext) GoldPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isNextFajr) "Next Fajr (Tomorrow)" else prayer.type.englishName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isPassed && !isNextFajr) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                        )
                        if (isNext) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldPrimary
                            ) {
                                Text(
                                    text = "NEXT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isNextFajr) "الفجر القادم (غداً)" else prayer.type.arabicName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Time & Alarm Mode Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (is24h) prayer.formattedTime24h else prayer.formattedTime12h,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isNext) MaterialTheme.colorScheme.primary else if (isPassed && !isNextFajr) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onToggleAlarm,
                    modifier = Modifier.testTag("alarm_toggle_${prayer.type.name.lowercase()}")
                ) {
                    val (icon, tint) = when (prayer.alarmMode) {
                        AlarmMode.ATHAN -> Icons.Default.VolumeUp to GoldPrimary
                        AlarmMode.BEEP -> Icons.Default.NotificationsActive to MaterialTheme.colorScheme.primary
                        AlarmMode.SILENT -> Icons.Default.Notifications to MaterialTheme.colorScheme.onSurfaceVariant
                        AlarmMode.MUTED -> Icons.Default.NotificationsOff to MaterialTheme.colorScheme.outline
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Alarm: ${prayer.alarmMode.title}",
                        tint = tint
                    )
                }
            }
        }
    }
}

@Composable
fun PrayerOffsetDialog(
    prayerType: PrayerType,
    initialOffset: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var offsetValue by remember { mutableFloatStateOf(initialOffset.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Adjust ${prayerType.englishName} Time")
        },
        text = {
            Column {
                Text(
                    text = "Manually fine-tune ${prayerType.englishName} prayer time by adding or subtracting minutes.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                val offsetInt = offsetValue.toInt()
                Text(
                    text = if (offsetInt > 0) "+$offsetInt minutes" else if (offsetInt < 0) "$offsetInt minutes" else "0 minutes (default)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = offsetValue,
                    onValueChange = { offsetValue = it },
                    valueRange = -30f..30f,
                    steps = 59
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(offsetValue.toInt()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getPrayerIcon(type: PrayerType): ImageVector {
    return when (type) {
        PrayerType.FAJR, PrayerType.NEXT_FAJR -> Icons.Default.WbTwilight
        PrayerType.SUNRISE -> Icons.Default.WbSunny
        PrayerType.DHUHR -> Icons.Default.WbSunny
        PrayerType.ASR -> Icons.Default.WbTwilight
        PrayerType.MAGHRIB -> Icons.Default.WbTwilight
        PrayerType.ISHA -> Icons.Default.Alarm
    }
}

fun performHaptic(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(30)
        }
    } catch (_: Exception) {}
}

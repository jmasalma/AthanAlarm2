package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.engine.PrayerCalculationEngine
import java.text.DateFormatSymbols
import java.util.Calendar

@Composable
fun MonthlyTimetableDialog(
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    val calendar = remember { Calendar.getInstance() }
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonth = calendar.get(Calendar.MONTH) + 1 // 1-12
    val todayDay = calendar.get(Calendar.DAY_OF_MONTH)
    val monthName = remember(currentMonth) {
        DateFormatSymbols().months[currentMonth - 1]
    }

    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    data class DaySchedule(
        val day: Int,
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String,
        val isToday: Boolean
    )

    fun format(time: Pair<Int, Int>): String {
        return if (settings.is24HourFormat) {
            "%02d:%02d".format(time.first, time.second)
        } else {
            val h = when {
                time.first == 0 -> 12
                time.first > 12 -> time.first - 12
                else -> time.first
            }
            "%d:%02d".format(h, time.second)
        }
    }

    val scheduleList = remember(settings, currentYear, currentMonth) {
        (1..daysInMonth).map { day ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth - 1)
                set(Calendar.DAY_OF_MONTH, day)
            }
            val result = PrayerCalculationEngine.calculate(
                date = cal,
                city = settings.city,
                method = settings.calculationMethod,
                juristic = settings.juristicMethod,
                highLatRule = settings.highLatitudeRule,
                offsets = settings.offsets
            )
            DaySchedule(
                day = day,
                fajr = format(result.fajr),
                sunrise = format(result.sunrise),
                dhuhr = format(result.dhuhr),
                asr = format(result.asr),
                maghrib = format(result.maghrib),
                isha = format(result.isha),
                isToday = (day == todayDay)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "$monthName $currentYear Timetable",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${settings.city.displayName} • ${settings.calculationMethod.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Day", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Fajr", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Sunr", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Dhuhr", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Asr", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Magh", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("Isha", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(scheduleList) { item ->
                            val rowColor = if (item.isToday) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowColor, RoundedCornerShape(4.dp))
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.isToday) "${item.day} ★" else "${item.day}",
                                    modifier = Modifier.weight(0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    color = if (item.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(item.fajr, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text(item.sunrise, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.dhuhr, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text(item.asr, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text(item.maghrib, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text(item.isha, modifier = Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

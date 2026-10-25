package com.example.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AthanAlarmScheduler
import com.example.alarm.AthanAudioService
import com.example.data.AppSettings
import com.example.data.AppSettingsRepository
import com.example.engine.HijriCalendarHelper
import com.example.engine.HijriDate
import com.example.engine.PrayerCalculationEngine
import com.example.engine.QiblaHelper
import com.example.model.AlarmMode
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.HighLatitudeRule
import com.example.model.JuristicMethod
import com.example.model.PrayerTimeItem
import com.example.model.PrayerType
import com.example.util.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

data class AthanUiState(
    val settings: AppSettings = AppSettings(),
    val prayerTimes: List<PrayerTimeItem> = emptyList(),
    val nextPrayer: PrayerTimeItem? = null,
    val countdownFormatted: String = "--:--:--",
    val countdownProgress: Float = 0f,
    val hijriDate: HijriDate = HijriCalendarHelper.getTodayHijri(),
    val qiblaAngle: Double = 0.0,
    val distanceToKaabaKm: Double = 0.0,
    val nextAlarmInfo: AthanAlarmScheduler.ScheduledAlarmInfo? = null,
    val isLocating: Boolean = false,
    val isTestingAlarm: Boolean = false,
    val isTestingCast: Boolean = false
)

class AthanViewModel(private val repository: AppSettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AthanUiState())
    val uiState: StateFlow<AthanUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                recalculatePrayers(settings)
            }
        }
        startCountdownTimer()
    }

    private fun recalculatePrayers(settings: AppSettings) {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val currentSeconds = currentMinutes * 60 + now.get(Calendar.SECOND)

        val result = PrayerCalculationEngine.calculate(
            date = now,
            city = settings.city,
            method = settings.calculationMethod,
            juristic = settings.juristicMethod,
            highLatRule = settings.highLatitudeRule,
            offsets = settings.offsets
        )

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowResult = PrayerCalculationEngine.calculate(
            date = tomorrow,
            city = settings.city,
            method = settings.calculationMethod,
            juristic = settings.juristicMethod,
            highLatRule = settings.highLatitudeRule,
            offsets = settings.offsets
        )

        val pairs = listOf(
            PrayerType.FAJR to result.fajr,
            PrayerType.SUNRISE to result.sunrise,
            PrayerType.DHUHR to result.dhuhr,
            PrayerType.ASR to result.asr,
            PrayerType.MAGHRIB to result.maghrib,
            PrayerType.ISHA to result.isha
        )

        // Find next prayer today
        var nextType: PrayerType? = null
        for ((type, time) in pairs) {
            val prayerMin = time.first * 60 + time.second
            if (prayerMin > currentMinutes) {
                nextType = type
                break
            }
        }

        // If all prayers passed today, next is tomorrow's Fajr
        val isTomorrowFajr = nextType == null
        if (isTomorrowFajr) {
            nextType = PrayerType.FAJR
        }

        val prayerItems = pairs.map { (type, time) ->
            val totalMin = time.first * 60 + time.second
            PrayerTimeItem(
                type = type,
                hour = time.first,
                minute = time.second,
                alarmMode = settings.alarmModes[type] ?: AlarmMode.ATHAN,
                isNext = (type == nextType && !isTomorrowFajr),
                isPassed = if (isTomorrowFajr) true else totalMin <= currentMinutes
            )
        }.toMutableList()

        // Append Next Fajr (tomorrow's Fajr) to bottom of prayers list
        val nextFajrItem = PrayerTimeItem(
            type = PrayerType.NEXT_FAJR,
            hour = tomorrowResult.fajr.first,
            minute = tomorrowResult.fajr.second,
            alarmMode = settings.alarmModes[PrayerType.FAJR] ?: AlarmMode.ATHAN,
            isNext = isTomorrowFajr,
            isPassed = false
        )
        prayerItems.add(nextFajrItem)

        val nextItem = if (isTomorrowFajr) nextFajrItem else prayerItems.firstOrNull { it.isNext }

        val qibla = QiblaHelper.calculateQiblaDirection(settings.city.latitude, settings.city.longitude)
        val distKm = QiblaHelper.calculateDistanceToKaaba(settings.city.latitude, settings.city.longitude)
        val hijri = HijriCalendarHelper.getTodayHijri(settings.hijriDayOffset)

        _uiState.value = _uiState.value.copy(
            settings = settings,
            prayerTimes = prayerItems,
            nextPrayer = nextItem,
            hijriDate = hijri,
            qiblaAngle = qibla,
            distanceToKaabaKm = distKm
        )
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                updateCountdown()
                delay(1000L)
            }
        }
    }

    private fun updateCountdown() {
        val state = _uiState.value
        val next = state.nextPrayer ?: return

        val now = Calendar.getInstance()
        val nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                now.get(Calendar.MINUTE) * 60 +
                now.get(Calendar.SECOND)

        val targetSec = next.hour * 3600 + next.minute * 60

        val diffSec = if (targetSec >= nowSec) {
            targetSec - nowSec
        } else {
            // Target is tomorrow
            (86400 - nowSec) + targetSec
        }

        val hours = diffSec / 3600
        val mins = (diffSec % 3600) / 60
        val secs = diffSec % 60

        val formatted = "%02d:%02d:%02d".format(hours, mins, secs)

        // Progress estimate over roughly 3-4 hours interval
        val intervalEstimateSec = 4 * 3600
        val progress = (1.0f - (diffSec.toFloat() / intervalEstimateSec.toFloat())).coerceIn(0f, 1f)

        _uiState.value = _uiState.value.copy(
            countdownFormatted = formatted,
            countdownProgress = progress
        )
    }

    fun toggleAlarmMode(context: Context, type: PrayerType): AlarmMode {
        val currentMode = _uiState.value.settings.alarmModes[type] ?: AlarmMode.ATHAN
        val nextMode = currentMode.next()
        viewModelScope.launch {
            repository.updateAlarmMode(type, nextMode)
            val updatedSettings = _uiState.value.settings.copy(
                alarmModes = _uiState.value.settings.alarmModes.toMutableMap().apply {
                    put(type, nextMode)
                }
            )
            AthanAlarmScheduler.scheduleNextAlarm(context, updatedSettings)
        }
        return nextMode
    }

    fun setCity(context: Context, city: City) {
        viewModelScope.launch {
            repository.updateCity(city)
            val updated = _uiState.value.settings.copy(city = city)
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun refreshLocation(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLocating = true)
            val loc = LocationHelper.getCurrentLocation(context)
            if (loc != null) {
                val tzHours = java.util.TimeZone.getDefault().rawOffset / (1000.0 * 60.0 * 60.0)
                val gpsCity = City(
                    name = String.format(java.util.Locale.US, "GPS (%.2f°, %.2f°)", loc.latitude, loc.longitude),
                    country = "Custom",
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    timezone = tzHours,
                    elevation = if (loc.hasAltitude()) loc.altitude else 0.0,
                    timeZoneId = java.util.TimeZone.getDefault().id
                )
                repository.updateCity(gpsCity)
                val updated = _uiState.value.settings.copy(city = gpsCity)
                AthanAlarmScheduler.scheduleNextAlarm(context, updated)
            }
            _uiState.value = _uiState.value.copy(isLocating = false)
        }
    }

    fun updateCalculationMethod(context: Context, method: CalculationMethod) {
        viewModelScope.launch {
            repository.updateCalculationMethod(method)
            val updated = _uiState.value.settings.copy(calculationMethod = method)
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun updateJuristicMethod(context: Context, juristic: JuristicMethod) {
        viewModelScope.launch {
            repository.updateJuristicMethod(juristic)
            val updated = _uiState.value.settings.copy(juristicMethod = juristic)
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun updateHighLatitudeRule(context: Context, rule: HighLatitudeRule) {
        viewModelScope.launch {
            repository.updateHighLatitudeRule(rule)
            val updated = _uiState.value.settings.copy(highLatitudeRule = rule)
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun update24HourFormat(is24: Boolean) {
        viewModelScope.launch {
            repository.update24HourFormat(is24)
        }
    }

    fun updateOffset(context: Context, type: PrayerType, offset: Int) {
        viewModelScope.launch {
            repository.updateOffset(type, offset)
            val updatedOffsets = _uiState.value.settings.offsets.toMutableMap().apply {
                put(type, offset)
            }
            val updated = _uiState.value.settings.copy(offsets = updatedOffsets)
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun updateAthanSound(sound: String) {
        viewModelScope.launch {
            repository.updateAthanSound(sound)
        }
    }

    fun updateHijriOffset(offset: Int) {
        viewModelScope.launch {
            repository.updateHijriOffset(offset)
        }
    }

    fun testAlarm(context: Context) {
        _uiState.value = _uiState.value.copy(isTestingAlarm = true)
        val serviceIntent = Intent(context, AthanAudioService::class.java).apply {
            action = AthanAudioService.ACTION_START_ALARM
            putExtra(AthanAudioService.EXTRA_PRAYER, PrayerType.FAJR.name)
            putExtra(AthanAudioService.EXTRA_MODE, AlarmMode.ATHAN.name)
            putExtra(AthanAudioService.EXTRA_SOUND, _uiState.value.settings.athanSound)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopTestAlarm(context: Context) {
        _uiState.value = _uiState.value.copy(isTestingAlarm = false)
        val stopIntent = Intent(context, AthanAudioService::class.java).apply {
            action = AthanAudioService.ACTION_STOP_SERVICE
        }
        context.startService(stopIntent)
    }

    fun setManualLocation(
        context: Context,
        name: String,
        country: String,
        latitude: Double,
        longitude: Double,
        timezone: Double,
        elevation: Double
    ) {
        viewModelScope.launch {
            val customCity = City(
                name = name,
                country = country,
                latitude = latitude,
                longitude = longitude,
                timezone = timezone,
                elevation = elevation
            )
            repository.updateCity(customCity)
            val updated = _uiState.value.settings.copy(city = customCity)
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun updatePrePrayerAlert(context: Context, enabled: Boolean, minutes: Int) {
        viewModelScope.launch {
            repository.updatePreReminder(enabled, minutes)
            val updated = _uiState.value.settings.copy(
                prePrayerReminderEnabled = enabled,
                prePrayerReminderMinutes = minutes
            )
            AthanAlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    fun updateCastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateCastEnabled(enabled)
        }
    }

    fun setSelectedCastDevice(deviceId: String, deviceName: String) {
        viewModelScope.launch {
            repository.updateCastDevice(deviceId, deviceName)
        }
    }

    fun updateCastAlertSound(sound: String) {
        viewModelScope.launch {
            repository.updateCastAlertSound(sound)
        }
    }

    fun testCastPlayback(context: Context) {
        _uiState.value = _uiState.value.copy(isTestingCast = true)
        val castSound = _uiState.value.settings.castAlertSound
        val targetDeviceId = _uiState.value.settings.castDeviceId
        val targetDeviceName = _uiState.value.settings.castDeviceName

        // Only test on Google/Chromecast (no local phone playback during this test)
        com.example.cast.CastMediaManager.playOnCast(
            context = context,
            soundChoice = castSound,
            prayerType = PrayerType.DHUHR,
            targetDeviceId = targetDeviceId,
            targetDeviceName = targetDeviceName,
            onStarted = {
                _uiState.value = _uiState.value.copy(isTestingCast = true)
            },
            onError = { errorMsg ->
                _uiState.value = _uiState.value.copy(isTestingCast = false)
                android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }

    fun stopTestCastPlayback(context: Context) {
        _uiState.value = _uiState.value.copy(isTestingCast = false)
        com.example.cast.CastMediaManager.stopCast(context)
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}

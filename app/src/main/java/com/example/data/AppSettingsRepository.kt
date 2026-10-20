package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AlarmMode
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.HighLatitudeRule
import com.example.model.JuristicMethod
import com.example.model.PrayerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "athan_settings")

data class AppSettings(
    val city: City = City.DEFAULT,
    val calculationMethod: CalculationMethod = CalculationMethod.ISNA,
    val juristicMethod: JuristicMethod = JuristicMethod.STANDARD,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val is24HourFormat: Boolean = false,
    val alarmModes: Map<PrayerType, AlarmMode> = mapOf(
        PrayerType.FAJR to AlarmMode.ATHAN,
        PrayerType.SUNRISE to AlarmMode.MUTED,
        PrayerType.DHUHR to AlarmMode.ATHAN,
        PrayerType.ASR to AlarmMode.ATHAN,
        PrayerType.MAGHRIB to AlarmMode.ATHAN,
        PrayerType.ISHA to AlarmMode.ATHAN
    ),
    val offsets: Map<PrayerType, Int> = mapOf(
        PrayerType.FAJR to 0,
        PrayerType.SUNRISE to 0,
        PrayerType.DHUHR to 0,
        PrayerType.ASR to 0,
        PrayerType.MAGHRIB to 0,
        PrayerType.ISHA to 0
    ),
    val athanSound: String = "chime", // "chime" (default ding/ping), "adhan", "system_notification", "system_alarm", or ringtone URI
    val prePrayerReminderEnabled: Boolean = false,
    val prePrayerReminderMinutes: Int = 20,
    val hijriDayOffset: Int = 0
)

class AppSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val CITY_NAME = stringPreferencesKey("city_name")
        val CITY_COUNTRY = stringPreferencesKey("city_country")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val TIMEZONE = doublePreferencesKey("timezone")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val ELEVATION = doublePreferencesKey("elevation")
        val CALC_METHOD = stringPreferencesKey("calc_method")
        val JURISTIC_METHOD = stringPreferencesKey("juristic_method")
        val HIGH_LAT_RULE = stringPreferencesKey("high_lat_rule")
        val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
        val ATHAN_SOUND = stringPreferencesKey("athan_sound")
        val PRE_REMINDER_ENABLED = booleanPreferencesKey("pre_reminder_enabled")
        val PRE_REMINDER_MINUTES = intPreferencesKey("pre_reminder_minutes")
        val HIJRI_DAY_OFFSET = intPreferencesKey("hijri_day_offset")

        fun alarmKey(type: PrayerType) = stringPreferencesKey("alarm_${type.name}")
        fun offsetKey(type: PrayerType) = intPreferencesKey("offset_${type.name}")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        val cityName = pref[PreferencesKeys.CITY_NAME] ?: City.DEFAULT.name
        val country = pref[PreferencesKeys.CITY_COUNTRY] ?: City.DEFAULT.country
        val lat = pref[PreferencesKeys.LATITUDE] ?: City.DEFAULT.latitude
        val lon = pref[PreferencesKeys.LONGITUDE] ?: City.DEFAULT.longitude
        val tz = pref[PreferencesKeys.TIMEZONE] ?: City.DEFAULT.timezone
        val elev = pref[PreferencesKeys.ELEVATION] ?: City.DEFAULT.elevation

        val calcMethod = CalculationMethod.entries.firstOrNull {
            it.name == pref[PreferencesKeys.CALC_METHOD]
        } ?: CalculationMethod.ISNA

        val juristicMethod = JuristicMethod.entries.firstOrNull {
            it.name == pref[PreferencesKeys.JURISTIC_METHOD]
        } ?: JuristicMethod.STANDARD

        val highLat = HighLatitudeRule.entries.firstOrNull {
            it.name == pref[PreferencesKeys.HIGH_LAT_RULE]
        } ?: HighLatitudeRule.MIDDLE_OF_NIGHT

        val is24 = pref[PreferencesKeys.IS_24_HOUR] ?: false
        val sound = pref[PreferencesKeys.ATHAN_SOUND] ?: "chime"
        val preReminder = pref[PreferencesKeys.PRE_REMINDER_ENABLED] ?: false
        val preMinutes = pref[PreferencesKeys.PRE_REMINDER_MINUTES] ?: 20
        val hijriOffset = pref[PreferencesKeys.HIJRI_DAY_OFFSET] ?: 0

        val alarmModes = PrayerType.entries.associateWith { type ->
            val modeStr = pref[PreferencesKeys.alarmKey(type)]
            if (modeStr != null) {
                AlarmMode.entries.firstOrNull { it.name == modeStr } ?: AlarmMode.ATHAN
            } else {
                if (type == PrayerType.SUNRISE) AlarmMode.MUTED else AlarmMode.ATHAN
            }
        }

        val offsets = PrayerType.entries.associateWith { type ->
            pref[PreferencesKeys.offsetKey(type)] ?: 0
        }

        val tzId = pref[PreferencesKeys.TIMEZONE_ID] ?: (
            City.CITIES.firstOrNull { it.name.equals(cityName, ignoreCase = true) }?.timeZoneId ?: City.DEFAULT.timeZoneId
        )

        AppSettings(
            city = City(cityName, country, lat, lon, tz, elev, tzId),
            calculationMethod = calcMethod,
            juristicMethod = juristicMethod,
            highLatitudeRule = highLat,
            is24HourFormat = is24,
            alarmModes = alarmModes,
            offsets = offsets,
            athanSound = sound,
            prePrayerReminderEnabled = preReminder,
            prePrayerReminderMinutes = preMinutes,
            hijriDayOffset = hijriOffset
        )
    }

    suspend fun updateCity(city: City) {
        context.dataStore.edit { pref ->
            pref[PreferencesKeys.CITY_NAME] = city.name
            pref[PreferencesKeys.CITY_COUNTRY] = city.country
            pref[PreferencesKeys.LATITUDE] = city.latitude
            pref[PreferencesKeys.LONGITUDE] = city.longitude
            pref[PreferencesKeys.TIMEZONE] = city.timezone
            pref[PreferencesKeys.TIMEZONE_ID] = city.timeZoneId
            pref[PreferencesKeys.ELEVATION] = city.elevation
        }
    }

    suspend fun updateCalculationMethod(method: CalculationMethod) {
        context.dataStore.edit { it[PreferencesKeys.CALC_METHOD] = method.name }
    }

    suspend fun updateJuristicMethod(method: JuristicMethod) {
        context.dataStore.edit { it[PreferencesKeys.JURISTIC_METHOD] = method.name }
    }

    suspend fun updateHighLatitudeRule(rule: HighLatitudeRule) {
        context.dataStore.edit { it[PreferencesKeys.HIGH_LAT_RULE] = rule.name }
    }

    suspend fun update24HourFormat(is24: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.IS_24_HOUR] = is24 }
    }

    suspend fun updateAlarmMode(type: PrayerType, mode: AlarmMode) {
        context.dataStore.edit { it[PreferencesKeys.alarmKey(type)] = mode.name }
    }

    suspend fun updateOffset(type: PrayerType, offsetMinutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.offsetKey(type)] = offsetMinutes }
    }

    suspend fun updateAthanSound(sound: String) {
        context.dataStore.edit { it[PreferencesKeys.ATHAN_SOUND] = sound }
    }

    suspend fun updatePreReminder(enabled: Boolean, minutes: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.PRE_REMINDER_ENABLED] = enabled
            it[PreferencesKeys.PRE_REMINDER_MINUTES] = minutes
        }
    }

    suspend fun updateHijriOffset(offset: Int) {
        context.dataStore.edit { it[PreferencesKeys.HIJRI_DAY_OFFSET] = offset }
    }
}

package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.City
import com.example.util.LocationHelper
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone

@Composable
fun CitySelectionDialog(
    currentCity: City,
    onCitySelected: (City) -> Unit,
    onUseGps: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Manual coordinates state
    val isPresetCity = City.CITIES.any { it.name.equals(currentCity.name, ignoreCase = true) && it.country.equals(currentCity.country, ignoreCase = true) }
    val initialNickname = if (!isPresetCity && !currentCity.name.startsWith("GPS (") && !currentCity.name.startsWith("Custom (")) currentCity.name else ""
    var locationNickname by remember { mutableStateOf(initialNickname) }
    var manualName by remember {
        mutableStateOf(
            if (isPresetCity) String.format(Locale.US, "Custom (%.2f°, %.2f°)", currentCity.latitude, currentCity.longitude)
            else currentCity.name
        )
    }
    var manualLat by remember { mutableStateOf(String.format(Locale.US, "%.5f", currentCity.latitude)) }
    var manualLon by remember { mutableStateOf(String.format(Locale.US, "%.5f", currentCity.longitude)) }
    var manualTz by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentCity.timezone)) }
    var manualElev by remember { mutableStateOf(currentCity.elevation.toInt().toString()) }
    var manualError by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDetectingGps by remember { mutableStateOf(false) }

    fun startLocationFetch() {
        isDetectingGps = true
        manualError = ""
        coroutineScope.launch {
            try {
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc != null) {
                    val latStr = String.format(Locale.US, "%.5f", loc.latitude)
                    val lonStr = String.format(Locale.US, "%.5f", loc.longitude)
                    manualLat = latStr
                    manualLon = lonStr
                    if (loc.hasAltitude()) {
                        manualElev = loc.altitude.toInt().toString()
                    }
                    val tzHours = TimeZone.getDefault().rawOffset / (1000.0 * 60.0 * 60.0)
                    manualTz = String.format(Locale.US, "%.1f", tzHours)
                    manualName = String.format(Locale.US, "GPS (%.2f°, %.2f°)", loc.latitude, loc.longitude)
                    manualError = ""
                } else {
                    manualError = "Could not obtain GPS fix. Please ensure location services are turned on."
                }
            } catch (e: Exception) {
                manualError = "GPS detection error: ${e.message}"
            } finally {
                isDetectingGps = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            startLocationFetch()
        } else {
            manualError = "Location permission is required to detect GPS coordinates."
        }
    }

    fun detectGps() {
        if (LocationHelper.hasLocationPermission(context)) {
            startLocationFetch()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun applyCustomLocation() {
        val lat = manualLat.toDoubleOrNull()
        val lon = manualLon.toDoubleOrNull()
        val tz = manualTz.toDoubleOrNull()
        val elev = manualElev.toDoubleOrNull() ?: 0.0

        if (lat == null || lat !in -90.0..90.0) {
            manualError = "Please enter valid latitude between -90 and 90"
            return
        }
        if (lon == null || lon !in -180.0..180.0) {
            manualError = "Please enter valid longitude between -180 and 180"
            return
        }
        if (tz == null || tz !in -12.0..14.0) {
            manualError = "Please enter valid timezone between -12 and +14"
            return
        }

        val defaultGpsLabel = String.format(Locale.US, "GPS (%.2f°, %.2f°)", lat, lon)
        val resolvedName = when {
            locationNickname.isNotBlank() -> locationNickname.trim()
            manualName.isNotBlank() && !manualName.startsWith("GPS (") && !manualName.startsWith("Custom (") && !(isPresetCity && manualName == currentCity.name) -> {
                manualName.trim()
            }
            else -> defaultGpsLabel
        }

        val custom = City(
            name = resolvedName,
            country = "Custom",
            latitude = lat,
            longitude = lon,
            timezone = tz,
            elevation = elev,
            timeZoneId = TimeZone.getDefault().id
        )
        onCitySelected(custom)
        onDismiss()
    }

    val filteredCities = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            City.CITIES
        } else {
            val q = searchQuery.trim().lowercase()
            City.CITIES.filter {
                it.name.lowercase().contains(q) || it.country.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Location & Coordinates", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Search Cities") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Manual Lat/Lon") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search city...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("city_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(filteredCities) { city ->
                            val isSelected = city.name == currentCity.name && city.country == currentCity.country
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCitySelected(city)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = city.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${city.country} • ${city.elevation.toInt()}m alt • UTC %+.1f".format(city.timezone),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                } else {
                    // Manual Lat / Long / Elevation Form
                    LazyColumn(modifier = Modifier.height(340.dp)) {
                        item {
                            Button(
                                onClick = { detectGps() },
                                enabled = !isDetectingGps,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detect_gps_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isDetectingGps) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Detecting coordinates...")
                                } else {
                                    Icon(Icons.Default.MyLocation, contentDescription = "Detect via GPS")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Detect via GPS / Network")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Or enter / adjust coordinates manually:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = locationNickname,
                                onValueChange = { locationNickname = it },
                                label = { Text("Location Nickname (Optional)") },
                                placeholder = { Text(if (manualName.isNotBlank()) manualName else "e.g. Home, Work, My Masjid") },
                                leadingIcon = { Icon(Icons.Default.EditLocation, contentDescription = null) },
                                supportingText = { Text("Display name instead of GPS coordinates (e.g. Home, Office)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("location_nickname_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = manualLat,
                                    onValueChange = { manualLat = it },
                                    label = { Text("Latitude (°)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = manualLon,
                                    onValueChange = { manualLon = it },
                                    label = { Text("Longitude (°)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = manualElev,
                                    onValueChange = { manualElev = it },
                                    label = { Text("Elevation (meters)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = manualTz,
                                    onValueChange = { manualTz = it },
                                    label = { Text("Timezone (UTC)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            if (manualError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = manualError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedTab == 1) {
                    Button(
                        onClick = { applyCustomLocation() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("apply_custom_location_button")
                    ) {
                        Text("Apply")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

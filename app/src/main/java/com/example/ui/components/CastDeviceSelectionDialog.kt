package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouter
import com.example.cast.CastMediaManager
import com.example.ui.theme.GoldPrimary

data class CastDeviceItem(
    val id: String,
    val name: String,
    val description: String = "Google Cast Audio"
)

@Composable
fun CastDeviceSelectionDialog(
    context: Context,
    currentSelectedId: String?,
    onDeviceSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    val selector = remember { CastMediaManager.getCastSelector() }

    fun getFilteredRoutes(): List<CastDeviceItem> {
        val routes = mediaRouter.routes.filter { route ->
            !route.isDefault && route.matchesSelector(selector)
        }.map { route ->
            CastDeviceItem(
                id = route.id,
                name = route.name,
                description = route.description ?: "Ready to connect"
            )
        }
        return routes
    }

    var discoveredDevices by remember { mutableStateOf(getFilteredRoutes()) }
    var isScanning by remember { mutableStateOf(true) }

    DisposableEffect(mediaRouter) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                discoveredDevices = getFilteredRoutes()
            }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                discoveredDevices = getFilteredRoutes()
            }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                discoveredDevices = getFilteredRoutes()
            }
        }

        mediaRouter.addCallback(
            selector,
            callback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        )

        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chromecast Devices",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = {
                    discoveredDevices = getFilteredRoutes()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Devices",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select a smart speaker or display on your Wi-Fi network for prayer notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (discoveredDevices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = GoldPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning local Wi-Fi...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ensure your Google Home / Nest speaker is on the same Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                // Provide preset preview device for emulator / offline testing
                                onDeviceSelected("preset_living_room", "Living Room Speaker")
                            }
                        ) {
                            Text("Use Living Room Speaker (Preview)")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(discoveredDevices) { device ->
                            val isSelected = device.id == currentSelectedId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onDeviceSelected(device.id, device.name)
                                    }
                                    .testTag("cast_device_${device.id}"),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (device.name.contains("tv", ignoreCase = true) || device.name.contains("display", ignoreCase = true)) {
                                            Icons.Default.Tv
                                        } else {
                                            Icons.Default.Speaker
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = device.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaRouter
import com.example.cast.CastMediaManager
import com.example.ui.theme.GoldPrimary

data class CastDeviceItem(
    val id: String,
    val name: String,
    val description: String = "Google Cast Audio",
    val isGroup: Boolean = false
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
        return mediaRouter.routes.filter { route ->
            !route.isDefault && route.matchesSelector(selector)
        }.map { route ->
            val isGroup = route.name.contains("group", ignoreCase = true) ||
                    (route.description?.contains("group", ignoreCase = true) == true)
            CastDeviceItem(
                id = route.id,
                name = route.name,
                description = route.description ?: if (isGroup) "Google Home Speaker Group" else "Ready to connect",
                isGroup = isGroup
            )
        }
    }

    var discoveredDevices by remember { mutableStateOf(getFilteredRoutes()) }

    // Parse existing selection (if legacy comma-separated, select group first or first match)
    val initialSelectedId = remember(currentSelectedId, discoveredDevices) {
        val ids = currentSelectedId?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        // If one of the saved IDs is a discovered Speaker Group, pick it
        val matchingGroup = discoveredDevices.firstOrNull { it.isGroup && ids.contains(it.id) }
        matchingGroup?.id ?: ids.firstOrNull() ?: discoveredDevices.firstOrNull { it.isGroup }?.id
    }

    var selectedDeviceId by remember { mutableStateOf(initialSelectedId) }
    var selectedDeviceName by remember {
        mutableStateOf(discoveredDevices.firstOrNull { it.id == initialSelectedId }?.name)
    }

    DisposableEffect(mediaRouter) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                val updated = getFilteredRoutes()
                discoveredDevices = updated
                if (selectedDeviceName == null && selectedDeviceId != null) {
                    updated.firstOrNull { it.id == selectedDeviceId }?.let {
                        selectedDeviceName = it.name
                    }
                }
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

    val speakerGroups = discoveredDevices.filter { it.isGroup }
    val individualDevices = discoveredDevices.filter { !it.isGroup }

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
                        text = "Cast Destination",
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
                // Informative Multi-Room Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Google Cast connects to one destination per stream. To play on multiple speakers together, select a Google Home 'Speaker Group' (e.g. Home group).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                if (discoveredDevices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = GoldPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Scanning local Wi-Fi...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure your Google Home / Nest speaker is powered on and on the same Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    val presetId = "preset_home_group"
                                    val presetName = "Home group"
                                    onDeviceSelected(presetId, presetName)
                                    onDismiss()
                                }
                            ) {
                                Text("Home Group (Preview)")
                            }

                            TextButton(
                                onClick = {
                                    val presetId = "preset_living_room"
                                    val presetName = "Living Room Speaker"
                                    onDeviceSelected(presetId, presetName)
                                    onDismiss()
                                }
                            ) {
                                Text("Living Room (Preview)")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        // 1. Speaker Groups Section (Multi-room playback)
                        if (speakerGroups.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SpeakerGroup,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SPEAKER GROUPS (MULTI-ROOM)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary
                                    )
                                }
                            }

                            items(speakerGroups) { group ->
                                val isSelected = selectedDeviceId == group.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedDeviceId = group.id
                                            selectedDeviceName = group.name
                                        }
                                        .testTag("cast_group_${group.id}"),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedDeviceId = group.id
                                                selectedDeviceName = group.name
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = Icons.Default.SpeakerGroup,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = group.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Multi-Room",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = GoldPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Broadcasts to all speakers in this group",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        // 2. Individual Speakers & TVs Section
                        if (individualDevices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "INDIVIDUAL SPEAKERS & TVS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(individualDevices) { device ->
                                val isSelected = selectedDeviceId == device.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedDeviceId = device.id
                                            selectedDeviceName = device.name
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
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedDeviceId = device.id
                                                selectedDeviceName = device.name
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = when {
                                                device.name.contains("tv", ignoreCase = true) || device.name.contains("display", ignoreCase = true) -> Icons.Default.Tv
                                                else -> Icons.Default.Speaker
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = selectedDeviceId
                    val name = selectedDeviceName ?: discoveredDevices.firstOrNull { it.id == id }?.name
                    if (id != null && name != null) {
                        onDeviceSelected(id, name)
                    }
                    onDismiss()
                },
                enabled = selectedDeviceId != null || discoveredDevices.isEmpty()
            ) {
                Text("Save Selection")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldPrimary
import com.example.util.CompassSensorManager
import com.example.viewmodel.AthanUiState
import kotlin.math.abs

@Composable
fun QiblaScreen(uiState: AthanUiState) {
    val context = LocalContext.current
    var deviceAzimuth by remember { mutableFloatStateOf(0f) }
    var hasHapticFired by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val manager = CompassSensorManager(context).apply {
            onAzimuthChanged = { azimuth ->
                deviceAzimuth = azimuth
            }
            start()
        }
        onDispose {
            manager.stop()
        }
    }

    val qiblaAngle = uiState.qiblaAngle.toFloat()
    val relativeAngle = (qiblaAngle - deviceAzimuth + 360f) % 360f
    val isFacingQibla = relativeAngle in 357f..360f || relativeAngle in 0f..3f

    // Trigger subtle haptic once when entering aligned zone
    if (isFacingQibla && !hasHapticFired) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
        hasHapticFired = true
    } else if (!isFacingQibla) {
        hasHapticFired = false
    }

    val alignedColor by animateColorAsState(
        targetValue = if (isFacingQibla) GoldPrimary else MaterialTheme.colorScheme.primary,
        label = "alignedColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("qibla_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Qibla Direction",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "اتجاه القبلة نحو الكعبة المشرفة",
                style = MaterialTheme.typography.bodyMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Compass Display
        Box(
            modifier = Modifier
                .size(310.dp)
                .testTag("compass_container"),
            contentAlignment = Alignment.Center
        ) {
            // Static outer tick ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 8.dp.toPx()

                // Outer circle
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.25f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw 12 hour/direction ticks
                for (i in 0 until 36) {
                    val angle = Math.toRadians((i * 10).toDouble())
                    val tickLen = if (i % 9 == 0) 14.dp.toPx() else 6.dp.toPx()
                    val startX = center.x + (radius - tickLen) * kotlin.math.cos(angle).toFloat()
                    val startY = center.y + (radius - tickLen) * kotlin.math.sin(angle).toFloat()
                    val endX = center.x + radius * kotlin.math.cos(angle).toFloat()
                    val endY = center.y + radius * kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color = if (i % 9 == 0) Color.Gray.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.3f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (i % 9 == 0) 2.5.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            // Rotating Dial (tracks deviceAzimuth)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .rotate(-deviceAzimuth)
            ) {
                // North indicator
                Text(
                    text = "N",
                    color = Color.Red,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
                Text(
                    text = "S",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
                Text(
                    text = "E",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "W",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                )

                // Qibla Indicator on the Dial
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(qiblaAngle),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 22.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Qibla Pointer",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Center Kaaba Emblem
            Surface(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isFacingQibla) 3.dp else 1.dp,
                        color = alignedColor,
                        shape = CircleShape
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_kaaba_qibla_1791977510766),
                        contentDescription = "Holy Kaaba",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        // Status alignment feedback banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isFacingQibla)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isFacingQibla) Icons.Default.CheckCircle else Icons.Default.Explore,
                    contentDescription = null,
                    tint = alignedColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFacingQibla)
                        "You are facing the Kaaba!"
                    else
                        "Turn ${if (relativeAngle > 180f) "left" else "right"} towards ${"%.0f°".format(qiblaAngle)}",
                    fontWeight = FontWeight.Bold,
                    color = if (isFacingQibla) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Qibla Angle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.1f°".format(uiState.qiblaAngle),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Heading",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.0f°".format(deviceAzimuth),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Distance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%,.0f km".format(uiState.distanceToKaabaKm),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }
            }
        }

        // Calibration tip
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Hold phone flat. Wave in a figure-8 motion if needle drifts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.rosk.remoteosukey.ui

import android.content.Context
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val OsuPink = Color(0xFFFF66AA)
private val NeonCyan = Color(0xFF00E5FF)
private val WarningYellow = Color(0xFFFFD600)
private val DarkBg = Color(0xFF121212)
private val DarkCard = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFFA0A0A0)
private val SuccessGreen = Color(0xFF00E676)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CalibrationDialog(
    onDismiss: () -> Unit,
    onCalibrated: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rosk_prefs", Context.MODE_PRIVATE) }

    var step by remember { mutableIntStateOf(1) } // 1: Touch 3 fingers, 2: Calibrated Success
    var capturedPointers by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var screenWidth by remember { mutableFloatStateOf(1f) }

    var calKey1X by remember { mutableFloatStateOf(-1f) }
    var calMidX by remember { mutableFloatStateOf(-1f) }
    var calKey2X by remember { mutableFloatStateOf(-1f) }

    var deadMinRatio by remember { mutableFloatStateOf(-1f) }
    var deadMaxRatio by remember { mutableFloatStateOf(-1f) }

    fun processTouch(event: MotionEvent) {
        val count = event.pointerCount
        val width = screenWidth
        if (width <= 0f) return

        val points = mutableListOf<Offset>()
        for (i in 0 until count) {
            points.add(Offset(event.getX(i), event.getY(i)))
        }
        capturedPointers = points

        if (count >= 3 && step == 1) {
            // Sort by X coordinate
            val sorted = points.sortedBy { it.x }
            val p1 = sorted[0]
            val p2 = sorted[1] // Middle finger
            val p3 = sorted[2]

            calKey1X = p1.x
            calMidX = p2.x
            calKey2X = p3.x

            // Calculate dead zone ratios
            val minDead = (p1.x + p2.x) / 2f
            val maxDead = (p2.x + p3.x) / 2f

            deadMinRatio = (minDead / width).coerceIn(0f, 1f)
            deadMaxRatio = (maxDead / width).coerceIn(0f, 1f)

            step = 2
        }
    }

    fun saveCalibration() {
        if (deadMinRatio >= 0f && deadMaxRatio >= 0f) {
            prefs.edit()
                .putFloat("calibrated_key1_ratio", (calKey1X / screenWidth).coerceIn(0f, 1f))
                .putFloat("calibrated_key2_ratio", (calKey2X / screenWidth).coerceIn(0f, 1f))
                .putFloat("calibrated_deadzone_min_ratio", deadMinRatio)
                .putFloat("calibrated_deadzone_max_ratio", deadMaxRatio)
                .putBoolean("is_calibrated", true)
                .apply()
            onCalibrated()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg.copy(alpha = 0.95f))
                .pointerInteropFilter { event ->
                    screenWidth = event.device?.getMotionRange(MotionEvent.AXIS_X)?.range ?: 1080f
                    processTouch(event)
                    true
                }
        ) {
            // Background Visual Canvas showing touch points & Dead Zone
            Canvas(modifier = Modifier.fillMaxSize()) {
                screenWidth = size.width

                // Draw Calibrated Dead Zone Overlay
                if (step == 2 && deadMinRatio >= 0f && deadMaxRatio >= 0f) {
                    val minX = deadMinRatio * size.width
                    val maxX = deadMaxRatio * size.width
                    drawRect(
                        color = WarningYellow.copy(alpha = 0.25f),
                        topLeft = Offset(minX, 0f),
                        size = androidx.compose.ui.geometry.Size(maxX - minX, size.height)
                    )
                }

                // Draw Touch Circles
                capturedPointers.forEachIndexed { idx, offset ->
                    val color = when (idx) {
                        0 -> NeonCyan
                        1 -> WarningYellow
                        else -> OsuPink
                    }
                    drawCircle(
                        color = color.copy(alpha = 0.4f),
                        radius = 90f,
                        center = offset
                    )
                    drawCircle(
                        color = color,
                        radius = 20f,
                        center = offset
                    )
                }
            }

            // Foreground UI Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Option C Hand Calibration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (step == 1) {
                            Text(
                                text = "Place 3 fingers (Key 1, Middle Finger, Key 2) simultaneously on the screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (capturedPointers.isEmpty()) "Waiting for 3 fingers..."
                                    else "${capturedPointers.size} finger(s) detected",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = SuccessGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hand Calibration Complete!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Calibrated Middle Finger Rejection Dead Zone: ${(deadMinRatio * 100).toInt()}% - ${(deadMaxRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        step = 1
                                        capturedPointers = emptyList()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }

                                Button(
                                    onClick = { saveCalibration() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("Save Calibration", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

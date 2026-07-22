package com.rosk.remoteosukey.ui

import android.content.Context
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosk.remoteosukey.ui.theme.*

data class FingerPair(
    val name: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rosk_prefs", Context.MODE_PRIVATE) }

    var selectedFingerPair by remember { mutableIntStateOf(prefs.getInt("fingerPair", 1)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }
    var vibrationIntensity by remember { mutableFloatStateOf(prefs.getFloat("vib_intensity", 0.5f)) }
    var visualFeedback by remember { mutableStateOf(prefs.getBoolean("visual", true)) }
    var touchMode by remember { mutableIntStateOf(prefs.getInt("touchMode", 0)) }
    var useCalibration by remember { mutableStateOf(prefs.getBoolean("useCalibration", true)) }
    var preventThirdFinger by remember { mutableStateOf(prefs.getBoolean("preventThirdFinger", false)) }

    val fingerPairs = remember {
        listOf(
            FingerPair("Index + Middle", "Trỏ + Giữa"),
            FingerPair("Index + Ring", "Trỏ + Nhẫn (Recommended)"),
            FingerPair("Index + Pinky", "Trỏ + Út"),
            FingerPair("Middle + Ring", "Giữa + Nhẫn"),
            FingerPair("Thumb + Index", "Cái + Trỏ"),
            FingerPair("Thumb + Middle", "Cái + Giữa"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(OsuPurple.copy(alpha = 0.1f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.1f),
                        radius = size.minDimension * 0.5f
                    ),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = size.minDimension * 0.5f
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextSecondary
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // ===== TOUCH MODE =====
                SettingsSection(title = "TOUCH MODE") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            RadioOption(
                                title = "Split Screen",
                                description = "Left half = Key 1, Right half = Key 2. Extra touches are ignored.",
                                isSelected = touchMode == 0,
                                onClick = {
                                    touchMode = 0
                                    prefs.edit().putInt("touchMode", 0).apply()
                                }
                            )
                            HorizontalDivider(color = DarkSurfaceVariant)
                            RadioOption(
                                title = "Full Screen Floating",
                                description = "Tap anywhere on screen. Dynamic 2-finger anchor tracking for Key 1 & Key 2.",
                                isSelected = touchMode == 1,
                                onClick = {
                                    touchMode = 1
                                    prefs.edit().putInt("touchMode", 1).apply()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ===== Prevent Third Finger (Experimental) =====
                SettingsSection(title = "PREVENT THIRD FINGER (EXPERIMENTAL)") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Prevent Third Finger",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    "When enabled, the app attempts to reject accidental touches from other fingers using advanced calibration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = preventThirdFinger,
                                onCheckedChange = {
                                    preventThirdFinger = it
                                    prefs.edit().putBoolean("preventThirdFinger", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = WarningYellow,
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ===== Calibration Section (visible when Prevent Third Finger selected) =====
                if (preventThirdFinger) {
                    SettingsSection(title = "CALIBRATION") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Use Calibration",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "Place 5 fingers to calibrate before playing. Recommended for accurate finger detection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = useCalibration,
                                    onCheckedChange = {
                                        useCalibration = it
                                        prefs.edit().putBoolean("useCalibration", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = NeonCyan,
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }
                        }
                        if (!useCalibration) {
                            Text(
                                text = "Without calibration, finger positions are estimated in real-time. Less accurate but no setup needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ===== Finger Pair Section =====
                    SettingsSection(title = "FINGER PAIR") {
                        Text(
                            text = "Choose which two fingers you play with. This helps the app ignore accidental touches from other fingers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                fingerPairs.forEachIndexed { index, pair ->
                                    if (index > 0) {
                                        HorizontalDivider(color = DarkSurfaceVariant)
                                    }
                                    RadioOption(
                                        title = pair.name,
                                        description = pair.description,
                                        isSelected = selectedFingerPair == index,
                                        onClick = {
                                            selectedFingerPair = index
                                            prefs.edit().putInt("fingerPair", index).apply()
                                        },
                                        accentColor = if (pair.description.contains("Recommended"))
                                            SuccessGreen else null
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ===== Haptic Feedback =====
                SettingsSection(title = "HAPTIC FEEDBACK") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Vibration",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "Vibrate on key press",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = vibrationEnabled,
                                    onCheckedChange = {
                                        vibrationEnabled = it
                                        prefs.edit().putBoolean("vibration", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = OsuPink,
                                        checkedThumbColor = Color.White
                                    )
                                )
                            }

                            if (vibrationEnabled) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Intensity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Slider(
                                    value = vibrationIntensity,
                                    onValueChange = {
                                        vibrationIntensity = it
                                        prefs.edit().putFloat("vib_intensity", it).apply()
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = OsuPink,
                                        activeTrackColor = OsuPink
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Light", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text("Strong", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ===== Visual Feedback =====
                SettingsSection(title = "VISUAL FEEDBACK") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Touch Ripple Effect",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    "Show visual feedback on touch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = visualFeedback,
                                onCheckedChange = {
                                    visualFeedback = it
                                    prefs.edit().putBoolean("visual", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = NeonCyan,
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ===== About =====
                SettingsSection(title = "ABOUT") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Remote osu! Keyboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Version 1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Ultra-low latency touch-to-keyboard input relay for osu! players.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 2.sp,
                color = TextMuted
            ),
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        content()
    }
}

@Composable
fun RadioOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accentColor ?: OsuPink,
                unselectedColor = TextMuted
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (accentColor != null && description.contains("Recommended"))
                    accentColor.copy(alpha = 0.8f) else TextMuted
            )
        }
    }
}

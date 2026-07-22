package com.rosk.remoteosukey.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosk.remoteosukey.input.TouchProcessor
import com.rosk.remoteosukey.network.ConnectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.collectAsState

private val Key1Color = Color(0xFF00C8FF) // Cyan
private val Key2Color = Color(0xFFFF006A) // Pink
private val Key1Pressed = Color(0xFF80E4FF)
private val Key2Pressed = Color(0xFFFF80B5)
private val DarkBg = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkCard = Color(0xFF242424)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFFA0A0A0)
private val TextMuted = Color(0xFF606060)
private val SuccessGreen = Color(0xFF00E676)
private val WarningYellow = Color(0xFFFFD600)
private val ErrorRed = Color(0xFFFF1744)
private val NeonCyan = Color(0xFF00E5FF)
private val OsuPink = Color(0xFFFF66AA)

enum class SnapEdge {
    LEFT, RIGHT, TOP, BOTTOM
}

enum class ConnectionState {
    CONNECTING, CONNECTED, ERROR
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayScreen(
    connectionType: String,
    serverAddress: String,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connectionManager = remember { com.rosk.remoteosukey.network.ConnectionManager() }
    val prefs = remember { context.getSharedPreferences("rosk_prefs", android.content.Context.MODE_PRIVATE) }
    
    val touchMode = remember { prefs.getInt("touchMode", 0) }
    val fingerPair = remember { prefs.getInt("fingerPair", 0) }
    val preventThirdFinger = remember { prefs.getBoolean("preventThirdFinger", false) }
    val isCalibrated = remember { prefs.getBoolean("is_calibrated", false) }
    val deadMinRatio = remember { prefs.getFloat("calibrated_deadzone_min_ratio", -1f) }
    val deadMaxRatio = remember { prefs.getFloat("calibrated_deadzone_max_ratio", -1f) }
    val calKey1Ratio = remember { prefs.getFloat("calibrated_key1_ratio", -1f) }
    val calKey2Ratio = remember { prefs.getFloat("calibrated_key2_ratio", -1f) }

    val calibratedBounds = remember(isCalibrated, deadMinRatio, deadMaxRatio) {
        TouchProcessor.CalibratedHandBounds(
            key1XRatio = calKey1Ratio,
            key2XRatio = calKey2Ratio,
            deadZoneMinXRatio = deadMinRatio,
            deadZoneMaxXRatio = deadMaxRatio,
            isCalibrated = isCalibrated
        )
    }

    val view = LocalView.current
    var isUiVisible by remember { mutableStateOf(true) }

    // State
    var connectionState by remember { mutableStateOf(ConnectionState.CONNECTING) }
    var latencyMs by remember { mutableStateOf<Int?>(null) }
    var showOverlay by remember { mutableStateOf(true) }

    // Touch state
    var key1Pressed by remember { mutableStateOf(false) }
    var key2Pressed by remember { mutableStateOf(false) }
    val touchProcessor = remember { TouchProcessor() }

    // Touch ripple animations
    var key1RippleCenter by remember { mutableStateOf<Offset?>(null) }
    var key2RippleCenter by remember { mutableStateOf<Offset?>(null) }

    // Connect on launch
    LaunchedEffect(connectionType, serverAddress) {
        connectionState = ConnectionState.CONNECTING
        val success = connectionManager.connect(connectionType, serverAddress)
        connectionState = if (success) ConnectionState.CONNECTED else ConnectionState.ERROR

        // Start latency monitoring
        if (success) {
            connectionManager.startLatencyMonitor { ms ->
                latencyMs = ms
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        // Keep screen on
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            connectionManager.disconnect()
        }
    }

    // Hide overlay after connection
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            delay(1500)
            showOverlay = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val buttonSizeDp = 44.dp
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val screenWPx = with(density) { maxWidth.toPx() }
        val screenHPx = with(density) { maxHeight.toPx() }

        val scope = rememberCoroutineScope()
        val animX = remember { Animatable(0f) }
        val animY = remember { Animatable(screenHPx * 0.7f) }

        var isDragging by remember { mutableStateOf(false) }
        var totalDragDistance by remember { mutableFloatStateOf(0f) }
        var snapEdge by remember { mutableStateOf(SnapEdge.LEFT) }

        val dropShape = when {
            isDragging -> RoundedCornerShape(22.dp)
            snapEdge == SnapEdge.LEFT -> RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp, topStart = 4.dp, bottomStart = 4.dp)
            snapEdge == SnapEdge.RIGHT -> RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp)
            snapEdge == SnapEdge.TOP -> RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = 4.dp, topEnd = 4.dp)
            else -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        }

        val snapToClosestEdge = {
            val cx = animX.value + buttonSizePx / 2f
            val cy = animY.value + buttonSizePx / 2f

            val distLeft = cx
            val distRight = screenWPx - cx
            val distTop = cy
            val distBottom = screenHPx - cy

            val minDist = minOf(distLeft, distRight, distTop, distBottom)

            val edge = when (minDist) {
                distLeft -> SnapEdge.LEFT
                distRight -> SnapEdge.RIGHT
                distTop -> SnapEdge.TOP
                else -> SnapEdge.BOTTOM
            }
            snapEdge = edge

            val targetX = when (edge) {
                SnapEdge.LEFT -> 0f
                SnapEdge.RIGHT -> (screenWPx - buttonSizePx).coerceAtLeast(0f)
                else -> animX.value.coerceIn(16f, (screenWPx - buttonSizePx - 16f).coerceAtLeast(16f))
            }

            val targetY = when (edge) {
                SnapEdge.TOP -> 16f
                SnapEdge.BOTTOM -> (screenHPx - buttonSizePx - 16f).coerceAtLeast(16f)
                else -> animY.value.coerceIn(60f, (screenHPx - buttonSizePx - 60f).coerceAtLeast(60f))
            }

            scope.launch {
                animX.animateTo(targetX, spring(stiffness = Spring.StiffnessMediumLow))
            }
            scope.launch {
                animY.animateTo(targetY, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }

        // Touch surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    val screenWidth = view.width.toFloat()
                    val screenHeight = view.height.toFloat()
                    val events = if (touchMode == 1) {
                        touchProcessor.processFullScreenTouches(event, screenWidth, screenHeight)
                    } else {
                        touchProcessor.processSplitScreenTouches(event, screenWidth, screenHeight)
                    }

                    for (e in events) {
                        if (e.keyIndex == 0) {
                            key1Pressed = e.isDown
                            if (e.isDown && event.pointerCount > 0) {
                                val idx = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                                key1RippleCenter = Offset(event.getX(idx), event.getY(idx))
                            }
                        } else {
                            key2Pressed = e.isDown
                            if (e.isDown && event.pointerCount > 0) {
                                val idx = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                                key2RippleCenter = Offset(event.getX(idx), event.getY(idx))
                            }
                        }
                        connectionManager.sendKeyEvent(e.keyIndex, e.isDown)
                    }

                    if (event.actionMasked == MotionEvent.ACTION_MOVE && touchMode == 1) {
                        for (i in 0 until event.pointerCount) {
                            val pid = event.getPointerId(i)
                            if (pid == touchProcessor.getPointerIdForKey(0)) {
                                key1RippleCenter = Offset(event.getX(i), event.getY(i))
                            } else if (pid == touchProcessor.getPointerIdForKey(1)) {
                                key2RippleCenter = Offset(event.getX(i), event.getY(i))
                            }
                        }
                    }

                    if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount == 0) {
                        val resetEvents = touchProcessor.reset()
                        for (e in resetEvents) {
                            if (e.keyIndex == 0) key1Pressed = false
                            else key2Pressed = false
                            connectionManager.sendKeyEvent(e.keyIndex, false)
                        }
                    }
                    true
                }
        ) {
            // Draw split line if in split mode
            if (touchMode == 0) {
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 2f
                )
            }

            // Draw feedback ripples
            val r = size.height * 0.15f
            if (key1Pressed && key1RippleCenter != null) {
                drawCircle(
                    color = Key1Color.copy(alpha = 0.2f),
                    radius = r,
                    center = key1RippleCenter!!
                )
                drawCircle(
                    color = Key1Color,
                    radius = r,
                    center = key1RippleCenter!!,
                    style = Stroke(width = 8f)
                )
            }
            if (key2Pressed && key2RippleCenter != null) {
                drawCircle(
                    color = Key2Color.copy(alpha = 0.2f),
                    radius = r,
                    center = key2RippleCenter!!
                )
                drawCircle(
                    color = Key2Color,
                    radius = r,
                    center = key2RippleCenter!!,
                    style = Stroke(width = 8f)
                )
            }
        }

        // Top UI Bar
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Left: Back & Direct Settings Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = {
                            connectionManager.disconnect()
                            onBack()
                        },
                        modifier = Modifier.background(DarkBg.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }

                    // Direct Settings Button (Gear Icon)
                    IconButton(
                        onClick = {
                            onNavigateToSettings()
                        },
                        modifier = Modifier.background(DarkBg.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                }

                // Connection & Latency badge
                Surface(
                    color = DarkBg.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val icon = when (connectionType) {
                            "wifi", "usb_tethering" -> Icons.Filled.Wifi
                            "usb_adb" -> Icons.Filled.Usb
                            "bluetooth" -> Icons.Filled.Bluetooth
                            else -> Icons.Filled.Wifi
                        }
                        val color = when (connectionState) {
                            ConnectionState.CONNECTED -> SuccessGreen
                            ConnectionState.CONNECTING -> WarningYellow
                            ConnectionState.ERROR -> ErrorRed
                        }
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                        
                        Text(
                            text = if (connectionState == ConnectionState.CONNECTED) {
                                latencyMs?.let { "${it}ms" } ?: "Connected"
                            } else {
                                connectionState.name
                            },
                            color = color,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Floating Edge-Snapping Water-Drop UI Toggle Button (4 Edges: Left, Right, Top, Bottom)
        Box(
            modifier = Modifier
                .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                .size(buttonSizeDp)
                .shadow(6.dp, dropShape)
                .background(DarkCard.copy(alpha = if (isDragging) 0.9f else 0.75f), dropShape)
                .border(1.dp, OsuPink.copy(alpha = 0.6f), dropShape)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            totalDragDistance = 0f
                            val pointerId = down.id

                            var dragChange: PointerInputChange? = null
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change != null) {
                                    if (change.pressed) {
                                        val delta = change.positionChange()
                                        totalDragDistance += delta.getDistance()
                                        if (totalDragDistance > 5f) {
                                            val newX = (animX.value + delta.x).coerceIn(0f, (screenWPx - buttonSizePx).coerceAtLeast(0f))
                                            val newY = (animY.value + delta.y).coerceIn(0f, (screenHPx - buttonSizePx).coerceAtLeast(0f))
                                            scope.launch {
                                                animX.snapTo(newX)
                                                animY.snapTo(newY)
                                            }
                                        }
                                        change.consume()
                                    }
                                    dragChange = change
                                }
                            } while (dragChange != null && dragChange.pressed)

                            isDragging = false
                            if (totalDragDistance < 15f) {
                                isUiVisible = !isUiVisible
                            } else {
                                snapToClosestEdge()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isUiVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = "Toggle UI Visibility",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Connecting Overlay
        AnimatedVisibility(
            visible = showOverlay && connectionState != ConnectionState.CONNECTED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = DarkSurface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.width(300.dp)
                ) {
                    when (connectionState) {
                        ConnectionState.CONNECTING -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = NeonCyan)
                                Text("Connecting to server...", color = TextPrimary)
                            }
                        }
                        ConnectionState.ERROR -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Connection Failed",
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val errorText = if (connectionType == "bluetooth") {
                                    "Check that the PC server is running\nand Bluetooth is paired/enabled."
                                } else {
                                    "Check that the server is running\nand the IP address is correct."
                                }
                                Text(
                                    errorText,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        connectionManager.disconnect()
                                        onBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = OsuPink)
                                ) {
                                    Text("Go Back")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // "Connected!" flash
        AnimatedVisibility(
            visible = showOverlay && connectionState == ConnectionState.CONNECTED,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(800)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "Connected!",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap left and right to play",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Key labels at bottom
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "KEY 1",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (key1Pressed) Key1Pressed else Key1Color.copy(alpha = 0.3f),
                    fontWeight = if (key1Pressed) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "KEY 2",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (key2Pressed) Key2Pressed else Key2Color.copy(alpha = 0.3f),
                    fontWeight = if (key2Pressed) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

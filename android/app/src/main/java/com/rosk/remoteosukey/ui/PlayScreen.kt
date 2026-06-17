package com.rosk.remoteosukey.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosk.remoteosukey.input.TouchProcessor
import com.rosk.remoteosukey.network.ConnectionManager
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

private val Key1Color = Color(0xFF00C8FF) // Cyan
private val Key2Color = Color(0xFFFF006A) // Pink
private val Key1Pressed = Color(0xFF80E4FF)
private val Key2Pressed = Color(0xFFFF80B5)
private val DarkBg = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFFA0A0A0)
private val TextMuted = Color(0xFF606060)
private val SuccessGreen = Color(0xFF00E676)
private val WarningYellow = Color(0xFFFFD600)
private val ErrorRed = Color(0xFFFF1744)
private val NeonCyan = Color(0xFF00E5FF)
private val OsuPink = Color(0xFFFF66AA)

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
    val useCalibration = remember { prefs.getBoolean("useCalibration", false) }

    val view = androidx.compose.ui.platform.LocalView.current
    var isUiVisible by remember { mutableStateOf(true) }

    // State
    var connectionState by remember { mutableStateOf(ConnectionState.CONNECTING) }
    var latencyMs by remember { mutableStateOf<Int?>(null) }
    var showOverlay by remember { mutableStateOf(true) }

    // Touch state
    var key1Pressed by remember { mutableStateOf(false) }
    var key2Pressed by remember { mutableStateOf(false) }
    val touchProcessor = remember { TouchProcessor() }

    val (key1FingerIndex, key2FingerIndex) = when (fingerPair) {
        0 -> Pair(2, 3) // Trỏ + Giữa
        1 -> Pair(1, 3) // Ngón thứ 2 và 4
        else -> Pair(2, 3)
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Touch surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    val screenWidth = view.width.toFloat()
                    val screenHeight = view.height.toFloat()
                    if (touchMode == 1) {
                        // Full Screen 2-Finger Floating Mode
                        val events = touchProcessor.processFullScreenTouches(event, screenWidth, screenHeight)
                        for (e in events) {
                            if (e.keyIndex == 0) {
                                key1Pressed = e.isDown
                                if (e.isDown) key1RippleCenter = Offset(event.getX(event.actionIndex), event.getY(event.actionIndex))
                            } else {
                                key2Pressed = e.isDown
                                if (e.isDown) key2RippleCenter = Offset(event.getX(event.actionIndex), event.getY(event.actionIndex))
                            }
                            connectionManager.sendKeyEvent(e.keyIndex, e.isDown)
                        }
                        
                        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                            for (i in 0 until event.pointerCount) {
                                val pid = event.getPointerId(i)
                                if (pid == touchProcessor.getPointerIdForKey(0)) {
                                    key1RippleCenter = Offset(event.getX(i), event.getY(i))
                                } else if (pid == touchProcessor.getPointerIdForKey(1)) {
                                    key2RippleCenter = Offset(event.getX(i), event.getY(i))
                                }
                            }
                        }

                        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            touchProcessor.reset()
                            key1Pressed = false
                            key2Pressed = false
                        }
                        return@pointerInteropFilter true
                    } else {
                        // Split Screen Mode
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                val pointerIndex = event.actionIndex
                                val pointerId = event.getPointerId(pointerIndex)
                                val x = event.getX(pointerIndex)
                                val y = event.getY(pointerIndex)
                                val isLeftSide = x < screenWidth / 2

                                val result = touchProcessor.onFingerDown(pointerId, x, y, isLeftSide)
                                if (result != null) {
                                    if (result.keyIndex == 0) {
                                        key1Pressed = true
                                        key1RippleCenter = Offset(x, y)
                                    } else {
                                        key2Pressed = true
                                        key2RippleCenter = Offset(x, y)
                                    }
                                    connectionManager.sendKeyEvent(result.keyIndex, true)
                                }
                                true
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_POINTER_UP -> {
                                val pointerIndex = event.actionIndex
                                val pointerId = event.getPointerId(pointerIndex)

                                val result = touchProcessor.onFingerUp(pointerId)
                                if (result != null) {
                                    if (result.keyIndex == 0) key1Pressed = false
                                    else key2Pressed = false
                                    connectionManager.sendKeyEvent(result.keyIndex, false)
                                }
                                true
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                touchProcessor.reset()
                                if (key1Pressed) { connectionManager.sendKeyEvent(0, false); key1Pressed = false }
                                if (key2Pressed) { connectionManager.sendKeyEvent(1, false); key2Pressed = false }
                                true
                            }
                            else -> true
                        }
                    }
                }
        ) {
            // Draw split line if in split mode
            if (touchMode == 0) {
                drawLine(
                    color = Color.DarkGray.copy(alpha = 0.3f),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
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

        // Tap to hide/show UI zone (center of screen)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .size(200.dp, 100.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isUiVisible = !isUiVisible
                }
        )

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

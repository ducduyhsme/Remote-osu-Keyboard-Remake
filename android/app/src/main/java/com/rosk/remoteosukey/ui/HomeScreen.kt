package com.rosk.remoteosukey.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosk.remoteosukey.network.DiscoveredServer
import com.rosk.remoteosukey.network.ServerDiscovery
import com.rosk.remoteosukey.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlay: (connectionType: String, serverAddress: String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var isDiscovering by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Background animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val bgOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .drawBehind {
                // Animated gradient orbs in background
                val radius = size.minDimension * 0.4f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OsuPink.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = size.width * 0.3f + (kotlin.math.sin(Math.toRadians(bgOffset.toDouble())) * 100).toFloat(),
                            y = size.height * 0.2f + (kotlin.math.cos(Math.toRadians(bgOffset.toDouble())) * 50).toFloat()
                        ),
                        radius = radius
                    ),
                    center = Offset(size.width * 0.3f, size.height * 0.2f),
                    radius = radius
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = size.width * 0.7f + (kotlin.math.cos(Math.toRadians(bgOffset.toDouble())) * 80).toFloat(),
                            y = size.height * 0.7f + (kotlin.math.sin(Math.toRadians(bgOffset.toDouble())) * 60).toFloat()
                        ),
                        radius = radius
                    ),
                    center = Offset(size.width * 0.7f, size.height * 0.7f),
                    radius = radius
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App title
            Text(
                text = "Remote osu!",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    brush = Brush.linearGradient(
                        colors = listOf(OsuPink, NeonCyan)
                    )
                )
            )
            Text(
                text = "Keyboard",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ultra-low latency touch input relay",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Connection mode cards
            Text(
                text = "SELECT CONNECTION",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 3.sp,
                    color = TextMuted
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // WiFi option
            ConnectionCard(
                icon = Icons.Filled.Wifi,
                title = "WiFi",
                subtitle = "Connect over local network",
                accentColor = NeonCyan,
                isSelected = selectedMode == "wifi",
                onClick = {
                    selectedMode = "wifi"
                    // Start discovery
                    if (!isDiscovering) {
                        isDiscovering = true
                        scope.launch {
                            discoveredServers = ServerDiscovery.discover()
                            isDiscovering = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // USB ADB option
            ConnectionCard(
                icon = Icons.Filled.Usb,
                title = "USB (ADB)",
                subtitle = "Connect via USB cable - 0ms latency",
                accentColor = SuccessGreen,
                isSelected = selectedMode == "usb_adb",
                onClick = { selectedMode = "usb_adb" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // USB Tethering option
            ConnectionCard(
                icon = Icons.Filled.Usb,
                title = "USB (Tethering)",
                subtitle = "Connect via USB Tethering IP",
                accentColor = NeonCyan,
                isSelected = selectedMode == "usb_tethering",
                onClick = { selectedMode = "usb_tethering" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bluetooth option
            ConnectionCard(
                icon = Icons.Filled.Bluetooth,
                title = "Bluetooth",
                subtitle = "Connect wirelessly",
                accentColor = Color(0xFF4488FF),
                isSelected = selectedMode == "bluetooth",
                badge = "Not Recommended",
                badgeColor = WarningYellow,
                onClick = { selectedMode = "bluetooth" }
            )

            // Expanded content based on selection
            AnimatedVisibility(
                visible = selectedMode != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (selectedMode) {
                        "wifi" -> WifiConnectionPanel(
                            servers = discoveredServers,
                            isDiscovering = isDiscovering,
                            onRefresh = {
                                scope.launch {
                                    isDiscovering = true
                                    discoveredServers = ServerDiscovery.discover()
                                    isDiscovering = false
                                }
                            },
                            onConnect = { address ->
                                onNavigateToPlay("wifi", address)
                            }
                        )

                        "usb_adb" -> UsbConnectionPanel(
                            onConnect = {
                                onNavigateToPlay("usb_adb", "127.0.0.1")
                            }
                        )

                        "usb_tethering" -> UsbTetheringPanel(
                            onConnect = { ip ->
                                onNavigateToPlay("usb_tethering", ip)
                            }
                        )

                        "bluetooth" -> BluetoothConnectionPanel(
                            onConnect = { address ->
                                onNavigateToPlay("bluetooth", address)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ConnectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    isSelected: Boolean,
    badge: String? = null,
    badgeColor: Color = WarningYellow,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(300),
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.08f) else DarkCard,
        animationSpec = tween(300),
        label = "bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) borderColor else DarkSurfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun WifiConnectionPanel(
    servers: List<DiscoveredServer>,
    isDiscovering: Boolean,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovered Servers",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan
                    )
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (servers.isEmpty() && !isDiscovering) {
                Text(
                    text = "No servers found. Make sure the PC server is running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            servers.forEach { server ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onConnect(server.endpoint) },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Computer,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${server.address}:${server.tcpPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = "Connect",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun UsbConnectionPanel(
    onConnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "USB (ADB) Setup",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StepItem(1, "Enable USB Debugging on your phone")
                    StepItem(2, "Connect phone to PC via USB cable")
                    StepItem(3, "Ensure PC Server is running (it sets up ADB automatically)")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Filled.Usb, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect via USB", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UsbTetheringPanel(
    onConnect: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverEndpoint by remember { mutableStateOf("") }
    var phoneIp by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    fun refreshOverTetherNetwork() {
        scope.launch {
            isScanning = true
            val server = ServerDiscovery.discover().firstOrNull()
            if (server != null) {
                serverEndpoint = server.endpoint
            }
            isScanning = false
        }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.rosk.remoteosukey.TETHER_READY") {
                    val pcIp = intent.getStringExtra("pc_ip")
                    val receivedPhoneIp = intent.getStringExtra("phone_ip")
                    if (!pcIp.isNullOrBlank()) {
                        serverEndpoint = pcIp
                    }
                    if (!receivedPhoneIp.isNullOrBlank()) {
                        phoneIp = receivedPhoneIp
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("com.rosk.remoteosukey.TETHER_READY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(Unit) {
        refreshOverTetherNetwork()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "USB Tethering Setup",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StepItem(1, "Enable USB Debugging on your phone")
                    StepItem(2, "Connect phone to PC via USB cable")
                    StepItem(3, "Enable 'USB Tethering' in phone settings")
                    StepItem(4, "Wait for the PC Server to detect the tether link")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (serverEndpoint.isBlank()) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = "Waiting for tethering IP...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        Text(
                            text = "PC: $serverEndpoint",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (phoneIp.isNotBlank()) {
                            Text(
                                text = "Phone: $phoneIp",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { refreshOverTetherNetwork() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TextMuted)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh")
                }

                Button(
                    onClick = { onConnect(serverEndpoint) },
                    modifier = Modifier.weight(1f),
                    enabled = serverEndpoint.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Filled.Usb, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



@Composable
fun StepItem(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = OsuPink.copy(alpha = 0.2f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelSmall,
                    color = OsuPink,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun BluetoothConnectionPanel(
    onConnect: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warning banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WarningYellow.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bluetooth has higher latency (20-50ms+). Use WiFi or USB for competitive play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Make sure your PC's Bluetooth is on and the server is running.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onConnect("bluetooth") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4488FF),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan & Connect", fontWeight = FontWeight.Bold)
            }
        }
    }
}

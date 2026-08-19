package com.example.ridercomms.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ridercomms.R
import com.example.ridercomms.network.AudioStreamManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Join Group, 1 = Host Group
    var isConnected by remember { mutableStateOf(false) }
    var isMicMuted by remember { mutableStateOf(true) }
    var hostIpAddress by remember { mutableStateOf("192.168.43.1") }
    var volume by remember { mutableFloatStateOf(0.8f) }
    val connectedPeers = remember { mutableStateListOf<String>() }

    val audioManager = remember {
        AudioStreamManager(
            onPeerDiscovered = { ip ->
                if (!connectedPeers.contains(ip)) {
                    connectedPeers.add(ip)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            audioManager.stop()
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasMicPermission = isGranted }
    )

    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected && connectedPeers.isNotEmpty() -> Color(0xFF4CAF50) // Green: Active peer
            isConnected -> Color(0xFFFF9800) // Orange: Server Active / Waiting
            else -> Color(0xFFE53935) // Red: Off
        },
        label = "status_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_ridercomms_logo),
                            contentDescription = "RiderComms Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .padding(end = 8.dp)
                        )
                        Text("RiderComms Intercom", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Tab Selector
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { if (!isConnected) selectedTabIndex = 0 },
                    text = { Text("Join Group") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { if (!isConnected) selectedTabIndex = 1 },
                    text = { Text("Host Group") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection Status & Peer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = when {
                                    isConnected && connectedPeers.isNotEmpty() -> "REAL-TIME LINK LIVE"
                                    isConnected -> "LISTENING ON UDP 50005"
                                    else -> "DISCONNECTED"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = statusColor
                            )
                            Text(
                                text = when {
                                    isConnected && connectedPeers.isNotEmpty() -> "${connectedPeers.size} Active Voice Stream(s)"
                                    isConnected -> if (selectedTabIndex == 1) "Waiting for incoming voice packets..." else "Sending UDP packets to $hostIpAddress"
                                    else -> "Join or Host a local audio channel"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                                .then(if (isConnected) Modifier.alpha(animatedAlpha) else Modifier)
                        )
                    }

                    if (isConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Detected Network Peers:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (connectedPeers.isEmpty()) {
                            Text(
                                text = "No audio received yet. Ensure client IP matches Gateway ($hostIpAddress).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            connectedPeers.forEach { peerIp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Peer at $peerIp",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mic Toggle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (isConnected) {
                            isMicMuted = !isMicMuted
                            audioManager.setMuted(isMicMuted)
                        }
                    },
                    enabled = isConnected || !hasMicPermission,
                    modifier = Modifier.size(110.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            !hasMicPermission -> MaterialTheme.colorScheme.error
                            !isMicMuted && isConnected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Text(
                        text = when {
                            !hasMicPermission -> "GRANT\nMIC"
                            !isMicMuted && isConnected -> "MIC\nLIVE"
                            else -> "MIC\nMUTED"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (!isMicMuted && isConnected && hasMicPermission) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        !hasMicPermission -> "Microphone Permission Required"
                        !isConnected -> "Connect session to activate voice"
                        isMicMuted -> "Microphone Muted"
                        else -> "Streaming Microphone Data..."
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (!isMicMuted && isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            // Connection Inputs & Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedTabIndex == 0 && !isConnected) {
                    OutlinedTextField(
                        value = hostIpAddress,
                        onValueChange = {
                            hostIpAddress = it
                            audioManager.setTargetIp(it)
                        },
                        label = { Text("Host Gateway IP Address") },
                        supportingText = { Text("Enter Hotspot IP (e.g. 192.168.43.1 or 192.168.50.1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Earpiece Volume: ${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (isConnected) {
                            // Stop UDP Audio Socket
                            audioManager.stop()
                            isConnected = false
                            connectedPeers.clear()
                            isMicMuted = true
                        } else {
                            // Start UDP Audio Socket
                            val target = if (selectedTabIndex == 0) hostIpAddress else null
                            audioManager.start(target)
                            isConnected = true
                            isMicMuted = false
                            audioManager.setMuted(false)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = when {
                            isConnected -> "Disconnect Voice Stream"
                            selectedTabIndex == 1 -> "Start Server & Listen"
                            else -> "Connect to Host IP"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

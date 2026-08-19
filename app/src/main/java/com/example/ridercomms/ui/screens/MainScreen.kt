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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PeerRider(
    val id: String,
    val name: String,
    val ipAddress: String,
    val isSpeaking: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Join Group, 1 = Host Group
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isMicMuted by remember { mutableStateOf(true) }
    var hostIpAddress by remember { mutableStateOf("192.168.43.1") }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var connectedPeers by remember { mutableStateOf(listOf<PeerRider>()) }

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
            isConnected && connectedPeers.isNotEmpty() -> Color(0xFF4CAF50) // Green: Connected to peer
            isConnected -> Color(0xFFFF9800) // Orange: Hosting / Waiting for peer
            isConnecting -> Color(0xFF2196F3) // Blue: Handshaking
            else -> Color(0xFFE53935) // Red: Disconnected
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
            // Mode Selector: Join vs Host
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { if (!isConnected && !isConnecting) selectedTabIndex = 0 },
                    text = { Text("Join Group") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { if (!isConnected && !isConnecting) selectedTabIndex = 1 },
                    text = { Text("Host Group") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Enhanced Connection Status & Peer Confirmation Card
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
                                    isConnecting -> "CONNECTING..."
                                    isConnected && connectedPeers.isNotEmpty() -> "PEER CONNECTED"
                                    isConnected -> "HOSTING (WAITING FOR PEERS)"
                                    else -> "DISCONNECTED"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = statusColor
                            )
                            Text(
                                text = when {
                                    isConnecting -> "Exchanging handshake with $hostIpAddress..."
                                    isConnected && connectedPeers.isNotEmpty() -> "${connectedPeers.size} Rider(s) Active in Channel"
                                    isConnected -> "Server Active on Port 50005"
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
                                .then(if (isConnected || isConnecting) Modifier.alpha(animatedAlpha) else Modifier)
                        )
                    }

                    // Connected Peers List Section
                    if (isConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connected Peers:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (connectedPeers.isEmpty()) {
                            Text(
                                text = "No riders connected yet. Ensure other rider enters IP: $hostIpAddress",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            connectedPeers.forEach { peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${peer.name} (${peer.ipAddress})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = if (peer.isSpeaking) "AUDIO LIVE" else "IDLE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (peer.isSpeaking) Color(0xFF4CAF50) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Central Mic Button
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
                        else -> "Transmitting Audio Streams..."
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (!isMicMuted && isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            // Controls & Connection Triggers
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedTabIndex == 0 && !isConnected) {
                    OutlinedTextField(
                        value = hostIpAddress,
                        onValueChange = { hostIpAddress = it },
                        label = { Text("Host Gateway IP Address") },
                        supportingText = { Text("Common Hotspot IPs: 192.168.43.1 or 192.168.50.1") },
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
                            // Disconnect
                            isConnected = false
                            isConnecting = false
                            connectedPeers = emptyList()
                            isMicMuted = true
                        } else {
                            // Start Connection / Hosting Handshake
                            coroutineScope.launch {
                                isConnecting = true
                                delay(1200) // Simulate network handshake verification
                                isConnecting = false
                                isConnected = true
                                isMicMuted = false

                                // Confirm active peer connection feedback
                                connectedPeers = if (selectedTabIndex == 1) {
                                    listOf(
                                        PeerRider(
                                            id = "peer_1",
                                            name = "Rider 2",
                                            ipAddress = "192.168.43.15",
                                            isSpeaking = true
                                        )
                                    )
                                } else {
                                    listOf(
                                        PeerRider(
                                            id = "host_1",
                                            name = "Host Rider",
                                            ipAddress = hostIpAddress,
                                            isSpeaking = false
                                        )
                                    )
                                }
                            }
                        }
                    },
                    enabled = !isConnecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = when {
                            isConnecting -> "Verifying Handshake..."
                            isConnected -> "Disconnect Intercom"
                            selectedTabIndex == 1 -> "Start Hosting & Listen for Peers"
                            else -> "Connect to Host"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

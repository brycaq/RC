package com.example.ridercomms.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ridercomms.network.AudioStreamManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioStreamManager = remember { AudioStreamManager() }

    var targetIp by remember { mutableStateOf("192.168.1.100") }
    var targetPort by remember { mutableStateOf("50005") }
    var isStreaming by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    
    var peerTimestamps by remember { mutableStateOf(mapOf<String, Long>()) }
    val discoveredPeers = peerTimestamps.keys.toList()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    // UDP Peer Discovery Engine (Broadcaster & Receiver)
    LaunchedEffect(Unit) {
        val discoveryPort = 50006
        val beaconMessage = "RIDER_COMMS_BEACON".toByteArray()

        // 1. Broadcaster Coroutine
        launch(Dispatchers.IO) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = wifiManager?.createMulticastLock("RiderCommsDiscovery")
            multicastLock?.acquire()

            try {
                val socket = DatagramSocket()
                socket.broadcast = true

                while (isActive) {
                    try {
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(beaconMessage, beaconMessage.size, broadcastAddr, discoveryPort)
                        socket.send(packet)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(2000)
                }
                socket.close()
            } finally {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }

        // 2. Listener Coroutine
        launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(discoveryPort)
                socket.broadcast = true
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val receivedStr = String(packet.data, 0, packet.length)

                    if (receivedStr.startsWith("RIDER_COMMS_BEACON")) {
                        val senderIp = packet.address.hostAddress
                        if (senderIp != null && !isLocalIp(senderIp)) {
                            val currentTime = System.currentTimeMillis()
                            peerTimestamps = peerTimestamps.toMutableMap().apply {
                                put(senderIp, currentTime)
                            }
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Stale Peer Cleanup Coroutine
        launch(Dispatchers.IO) {
            while (isActive) {
                delay(4000)
                val now = System.currentTimeMillis()
                peerTimestamps = peerTimestamps.filterValues { now - it < 6000 }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioStreamManager.stopRecording()
            audioStreamManager.stopPlaying()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RiderComms") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasAudioPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Microphone permission is required for audio communication.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connection Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("Target IP Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = targetPort,
                        onValueChange = { targetPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Audio Controls",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transmit Audio")
                        Button(
                            onClick = {
                                if (isStreaming) {
                                    audioStreamManager.stopRecording()
                                    isStreaming = false
                                } else {
                                    if (hasAudioPermission) {
                                        val port = targetPort.toIntOrNull() ?: 50005
                                        audioStreamManager.startRecording(targetIp, port)
                                        isStreaming = true
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = if (isStreaming) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(if (isStreaming) "Stop Transmitting" else "Start Transmitting")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Receive Audio")
                        Button(
                            onClick = {
                                if (isListening) {
                                    audioStreamManager.stopPlaying()
                                    isListening = false
                                } else {
                                    val port = targetPort.toIntOrNull() ?: 50005
                                    audioStreamManager.startPlaying(port)
                                    isListening = true
                                }
                            },
                            colors = if (isListening) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(if (isListening) "Stop Listening" else "Start Listening")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mute Microphone")
                        Switch(
                            checked = isMuted,
                            onCheckedChange = { muted ->
                                isMuted = muted
                                if (muted && isStreaming) {
                                    audioStreamManager.stopRecording()
                                } else if (!muted && isStreaming) {
                                    val port = targetPort.toIntOrNull() ?: 50005
                                    audioStreamManager.startRecording(targetIp, port)
                                }
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Discovered Peers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (discoveredPeers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Scanning for peers on local network...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredPeers) { peerIp ->
                                val isConnected = targetIp == peerIp && (isStreaming || isListening)
                                Card(
                                    onClick = {
                                        targetIp = peerIp
                                        val port = targetPort.toIntOrNull() ?: 50005

                                        audioStreamManager.stopRecording()
                                        audioStreamManager.stopPlaying()

                                        if (hasAudioPermission && !isMuted) {
                                            audioStreamManager.startRecording(peerIp, port)
                                            isStreaming = true
                                        }
                                        audioStreamManager.startPlaying(port)
                                        isListening = true
                                    },
                                    colors = if (isConnected) {
                                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        CardDefaults.cardColors()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = peerIp,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = if (isConnected) "Connected & Streaming" else "Available",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                targetIp = peerIp
                                                val port = targetPort.toIntOrNull() ?: 50005

                                                audioStreamManager.stopRecording()
                                                audioStreamManager.stopPlaying()

                                                if (hasAudioPermission && !isMuted) {
                                                    audioStreamManager.startRecording(peerIp, port)
                                                    isStreaming = true
                                                }
                                                audioStreamManager.startPlaying(port)
                                                isListening = true
                                            }
                                        ) {
                                            Text(if (isConnected) "Active" else "Connect")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isLocalIp(ip: String): Boolean {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr.hostAddress == ip) {
                    return true
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}

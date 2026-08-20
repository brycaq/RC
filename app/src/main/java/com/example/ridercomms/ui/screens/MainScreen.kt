package com.example.ridercomms.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private val RIDER_COMMS_UUID: UUID = UUID.fromString("fa842880-a88f-11ed-afa1-0242ac120002")

enum class UserRole {
    HOST, CLIENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager.adapter }

    var selectedRole by remember { mutableStateOf(UserRole.HOST) }
    var isMuted by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var pendingConnectionDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    var discoveredDevices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var activeSocket by remember { mutableStateOf<BluetoothSocket?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var audioRecordJob by remember { mutableStateOf<Job?>(null) }
    var audioPlayJob by remember { mutableStateOf<Job?>(null) }
    var hostServerJob by remember { mutableStateOf<Job?>(null) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    // Auto-prompt permissions on launch if not granted
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    @SuppressLint("MissingPermission")
    fun startAudioStreams(socket: BluetoothSocket) {
        val sampleRate = 16000
        val channelRecord = AudioFormat.CHANNEL_IN_MONO
        val channelPlay = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minRecBufSize = AudioRecord.getMinBufferSize(sampleRate, channelRecord, audioEncoding)
        val minPlayBufSize = AudioTrack.getMinBufferSize(sampleRate, channelPlay, audioEncoding)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelRecord,
            audioEncoding,
            minRecBufSize
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelPlay)
                    .build()
            )
            .setBufferSizeInBytes(minPlayBufSize)
            .build()

        audioRecord.startRecording()
        audioTrack.play()

        audioRecordJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val outputStream: OutputStream = socket.outputStream
                val buffer = ByteArray(minRecBufSize)
                while (isActive && socket.isConnected) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0 && !isMuted) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        audioPlayJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream = socket.inputStream
                val buffer = ByteArray(minPlayBufSize)
                while (isActive && socket.isConnected) {
                    val read = inputStream.read(buffer)
                    if (read > 0) {
                        audioTrack.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnectSession() {
        audioRecordJob?.cancel()
        audioPlayJob?.cancel()
        hostServerJob?.cancel()
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeSocket = null
        isConnected = false
        connectedDeviceName = null
    }

    @SuppressLint("MissingPermission")
    fun startHosting() {
        disconnectSession()
        if (!hasPermissions) return
        hostServerJob = coroutineScope.launch(Dispatchers.IO) {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("RiderCommsHost", RIDER_COMMS_UUID)
                val socket = serverSocket?.accept()
                if (socket != null) {
                    pendingConnectionDevice = socket.remoteDevice
                    activeSocket = socket
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanAndConnectToHost(device: BluetoothDevice) {
        if (!hasPermissions) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val socket = device.createInsecureRfcommSocketToServiceRecord(RIDER_COMMS_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                activeSocket = socket
                connectedDeviceName = device.name ?: device.address
                isConnected = true
                startAudioStreams(socket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanBluetoothDevices() {
        if (!hasPermissions || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        val paired = bluetoothAdapter.bondedDevices ?: emptySet()
        discoveredDevices = paired.toList()
    }

    DisposableEffect(selectedRole, hasPermissions) {
        if (hasPermissions) {
            if (selectedRole == UserRole.HOST) {
                startHosting()
            } else {
                scanBluetoothDevices()
            }
        }
        onDispose {
            disconnectSession()
        }
    }

    if (pendingConnectionDevice != null) {
        AlertDialog(
            onDismissRequest = {
                disconnectSession()
                pendingConnectionDevice = null
                startHosting()
            },
            title = { Text("Connection Request") },
            text = {
                Text("${pendingConnectionDevice?.name ?: "A Rider"} wants to connect to your audio stream.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val socket = activeSocket
                        if (socket != null) {
                            connectedDeviceName = pendingConnectionDevice?.name ?: pendingConnectionDevice?.address
                            isConnected = true
                            startAudioStreams(socket)
                        }
                        pendingConnectionDevice = null
                    }
                ) {
                    Text("Accept")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        disconnectSession()
                        pendingConnectionDevice = null
                        startHosting()
                    }
                ) {
                    Text("Decline")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsMotorsports,
                            contentDescription = "RiderComms Helmet Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("RiderComms")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasPermissions) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bluetooth and Audio permissions are required to scan and connect.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedRole.ordinal) {
                Tab(
                    selected = selectedRole == UserRole.HOST,
                    onClick = {
                        selectedRole = UserRole.HOST
                        startHosting()
                    },
                    text = { Text("Host Session") }
                )
                Tab(
                    selected = selectedRole == UserRole.CLIENT,
                    onClick = {
                        selectedRole = UserRole.CLIENT
                        scanBluetoothDevices()
                    },
                    text = { Text("Join Session") }
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isConnected) "Connected with $connectedDeviceName" else if (selectedRole == UserRole.HOST) "Waiting for incoming rider request..." else "Select a host rider to connect",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            disconnectSession()
                            if (selectedRole == UserRole.HOST) startHosting()
                        }) {
                            Text("End Session")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute Microphone" else "Mute Microphone",
                            modifier = Modifier.size(56.dp),
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (isMuted) "Microphone Muted" else "Microphone Live",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            if (selectedRole == UserRole.CLIENT && !isConnected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Available Bluetooth Riders", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { scanBluetoothDevices() }) {
                                Icon(Icons.Default.Bluetooth, contentDescription = "Refresh")
                            }
                        }

                        if (discoveredDevices.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No Bluetooth devices found. Ensure Bluetooth is ON.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(discoveredDevices) { device ->
                                    Card(
                                        onClick = { scanAndConnectToHost(device) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            @SuppressLint("MissingPermission")
                                            val deviceName = device.name ?: device.address
                                            Text(text = deviceName, style = MaterialTheme.typography.bodyLarge)
                                            Button(onClick = { scanAndConnectToHost(device) }) {
                                                Text("Request Connection")
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
}

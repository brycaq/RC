package com.example.ridercomms.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.GraphicEq
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

private val RIDER_COMMS_UUID: UUID = UUID.fromString("fa842880-a88f-11ed-afa1-0242ac120002")

enum class UserRole {
    HOST, CLIENT
}

data class DiscoveredRider(
    val customName: String,
    val device: BluetoothDevice
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rider_comms_prefs", Context.MODE_PRIVATE) }
    
    // Persistent App Rider Name
    var riderName by remember {
        mutableStateOf(
            prefs.getString("rider_name", null) ?: ("Rider " + (100..999).random()).also {
                prefs.edit().putString("rider_name", it).apply()
            }
        )
    }

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager.adapter }

    var selectedRole by remember { mutableStateOf(UserRole.HOST) }
    var isMuted by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var pendingConnectionDevice by remember { mutableStateOf<Pair<String, BluetoothDevice>?>(null) }

    var discoveredRiders by remember { mutableStateOf(listOf<DiscoveredRider>()) }
    var isScanning by remember { mutableStateOf(false) }
    var activeSocket by remember { mutableStateOf<BluetoothSocket?>(null) }

    // Audio Testing State
    var isAudioTesting by remember { mutableStateOf(false) }
    var audioTestStatus by remember { mutableStateOf("") }

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
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
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

    val advertiseCallback = remember {
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {}
            override fun onStartFailure(errorCode: Int) {}
        }
    }

    val scanCallback = remember {
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { res ->
                    val device = res.device
                    val pUuid = ParcelUuid(RIDER_COMMS_UUID)
                    val serviceData = res.scanRecord?.getServiceData(pUuid)
                    
                    val name = if (serviceData != null && serviceData.isNotEmpty()) {
                        String(serviceData, StandardCharsets.UTF_8)
                    } else {
                        res.scanRecord?.deviceName ?: "Rider (${device.address.takeLast(5)})"
                    }

                    val existingIndex = discoveredRiders.indexOfFirst { it.device.address == device.address }
                    if (existingIndex >= 0) {
                        val currentList = discoveredRiders.toMutableList()
                        currentList[existingIndex] = DiscoveredRider(customName = name, device = device)
                        discoveredRiders = currentList
                    } else {
                        discoveredRiders = discoveredRiders + DiscoveredRider(customName = name, device = device)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleAdvertising() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScanning() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    fun startBleAdvertising() {
        if (!hasPermissions || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        stopBleAdvertising()

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val pUuid = ParcelUuid(RIDER_COMMS_UUID)
        val nameData = riderName.toByteArray(StandardCharsets.UTF_8)

        val advertisementData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(pUuid)
            .build()

        val scanResponseData = AdvertiseData.Builder()
            .addServiceData(pUuid, nameData)
            .build()

        advertiser.startAdvertising(settings, advertisementData, scanResponseData, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun startBleScanning() {
        if (!hasPermissions || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        stopBleScanning()
        discoveredRiders = emptyList()

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(RIDER_COMMS_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
    }

    fun playNotificationTone() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun runAudioHardwareTest() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(requiredPermissions)
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isAudioTesting = true
                audioTestStatus = "Recording 3s clip..."
            }

            val sampleRate = 16000
            val channelRecord = AudioFormat.CHANNEL_IN_MONO
            val channelPlay = AudioFormat.CHANNEL_OUT_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

            val minRecBuf = AudioRecord.getMinBufferSize(sampleRate, channelRecord, audioEncoding)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelRecord,
                audioEncoding,
                minRecBuf * 2
            )

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(minRecBuf)

            try {
                audioRecord.startRecording()
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val recordedBytes = outputStream.toByteArray()

            withContext(Dispatchers.Main) {
                audioTestStatus = "Playing back audio..."
            }

            if (recordedBytes.isNotEmpty()) {
                val minPlayBuf = AudioTrack.getMinBufferSize(sampleRate, channelPlay, audioEncoding)
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
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
                    .setBufferSizeInBytes(maxOf(minPlayBuf, recordedBytes.size))
                    .build()

                try {
                    audioTrack.play()
                    audioTrack.write(recordedBytes, 0, recordedBytes.size)
                    delay(3000)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                isAudioTesting = false
                audioTestStatus = ""
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAudioStreams(socket: BluetoothSocket) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val sampleRate = 16000
        val channelRecord = AudioFormat.CHANNEL_IN_MONO
        val channelPlay = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minRecBufSize = AudioRecord.getMinBufferSize(sampleRate, channelRecord, audioEncoding)
        val minPlayBufSize = AudioTrack.getMinBufferSize(sampleRate, channelPlay, audioEncoding)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelRecord,
            audioEncoding,
            minRecBufSize * 2
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
            .setBufferSizeInBytes(minPlayBufSize * 2)
            .build()

        try {
            audioRecord.startRecording()
            audioTrack.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectSession() {
        audioRecordJob?.cancel()
        audioPlayJob?.cancel()
        hostServerJob?.cancel()
        stopBleAdvertising()
        stopBleScanning()
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
        startBleAdvertising()

        hostServerJob = coroutineScope.launch(Dispatchers.IO) {
            var serverSocket: BluetoothServerSocket? = null
            try {
                serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("RiderCommsHost", RIDER_COMMS_UUID)
                val socket = serverSocket?.accept()
                if (socket != null) {
                    playNotificationTone()

                    val inputStream = socket.inputStream
                    val nameBuffer = ByteArray(256)
                    val bytesRead = try {
                        inputStream.read(nameBuffer)
                    } catch (e: Exception) {
                        -1
                    }

                    val clientRiderName = if (bytesRead > 0) {
                        String(nameBuffer, 0, bytesRead, StandardCharsets.UTF_8).trim()
                    } else {
                        "Nearby Rider"
                    }

                    withContext(Dispatchers.Main) {
                        activeSocket = socket
                        pendingConnectionDevice = Pair(clientRiderName, socket.remoteDevice)
                    }
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
    fun connectToRider(rider: DiscoveredRider) {
        if (!hasPermissions) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                stopBleScanning()
                val targetDevice = bluetoothAdapter?.getRemoteDevice(rider.device.address) ?: rider.device
                val socket = targetDevice.createInsecureRfcommSocketToServiceRecord(RIDER_COMMS_UUID)
                socket.connect()

                val nameBytes = riderName.toByteArray(StandardCharsets.UTF_8)
                socket.outputStream.write(nameBytes)
                socket.outputStream.flush()

                withContext(Dispatchers.Main) {
                    activeSocket = socket
                    connectedDeviceName = rider.customName
                    isConnected = true
                    startAudioStreams(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(selectedRole, hasPermissions, riderName) {
        if (hasPermissions) {
            if (selectedRole == UserRole.HOST) {
                startHosting()
            } else {
                startBleScanning()
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
            title = { Text("Rider Connection Request") },
            text = {
                Text("${pendingConnectionDevice?.first ?: "A nearby Rider"} wants to connect to your audio stream.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val socket = activeSocket
                        if (socket != null) {
                            connectedDeviceName = pendingConnectionDevice?.first ?: "Connected Rider"
                            isConnected = true
                            startAudioStreams(socket)
                        }
                        pendingConnectionDevice = null
                    }
                ) {
                    Text("Accept Connection")
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
                            text = "Bluetooth, Location, and Audio permissions are required to scan and connect.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Custom App Rider Identity with persistence
            OutlinedTextField(
                value = riderName,
                onValueChange = { newName ->
                    riderName = newName
                    prefs.edit().putString("rider_name", newName).apply()
                },
                label = { Text("Your App Rider Name") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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
                        startBleScanning()
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
                        text = if (isConnected) "Connected with $connectedDeviceName" else if (selectedRole == UserRole.HOST) "Broadcasting app ID as '$riderName'..." else "Select a rider using RiderComms",
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

            // Audio & Mic Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute Microphone" else "Mute Microphone",
                            modifier = Modifier.size(48.dp),
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (isMuted) "Microphone Muted" else "Microphone Live",
                        style = MaterialTheme.typography.titleLarge
                    )

                    // Mic & Audio Hardware Testing Option
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    OutlinedButton(
                        onClick = { runAudioHardwareTest() },
                        enabled = !isAudioTesting && !isConnected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Text(if (isAudioTesting) audioTestStatus else "Test Mic & Speaker (3s Loop)")
                        }
                    }
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
                            Text(
                                text = if (isScanning) "Searching for RiderComms Apps..." else "Available RiderComms Devices",
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { startBleScanning() }) {
                                Icon(Icons.Default.Bluetooth, contentDescription = "Refresh")
                            }
                        }

                        if (discoveredRiders.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isScanning) "Scanning for nearby RiderComms riders..." else "No RiderComms riders found.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(discoveredRiders) { rider ->
                                    Card(
                                        onClick = { connectToRider(rider) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = rider.customName, style = MaterialTheme.typography.bodyLarge)
                                            Button(onClick = { connectToRider(rider) }) {
                                                Text("Connect Rider")
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

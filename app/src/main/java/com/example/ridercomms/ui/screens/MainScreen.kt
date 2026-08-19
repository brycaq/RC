package com.example.ridercomms.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var isConnected by remember { mutableStateOf(false) }
    var isMicMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var selectedMode by remember { mutableStateOf("Wi-Fi Hotspot") }

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
        targetValue = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935),
        label = "status_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rider Comms Intercom", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = statusColor
                        )
                        Text(
                            text = if (isConnected) "Intercom Channel Active" else "Ready to join group",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Pulsing Status Dot
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                            .then(
                                if (isConnected) Modifier.alpha(animatedAlpha) else Modifier
                            )
                    )
                }
            }

            // Central Mic Indicator & Toggle Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { if (isConnected) isMicMuted = !isMicMuted },
                    enabled = isConnected,
                    modifier = Modifier.size(130.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isMicMuted && isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = if (!isMicMuted && isConnected) "MIC\nON" else "MIC\nOFF",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (!isMicMuted && isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        !isConnected -> "Connect to enable Mic"
                        isMicMuted -> "Microphone MUTED"
                        else -> "Microphone TRANSMITTING"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (!isMicMuted && isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            // Connection Mode Selector, Volume & Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Connection Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = selectedMode == "Wi-Fi Hotspot",
                        onClick = { selectedMode = "Wi-Fi Hotspot" },
                        label = { Text("Wi-Fi Hotspot") }
                    )
                    FilterChip(
                        selected = selectedMode == "Bluetooth",
                        onClick = { selectedMode = "Bluetooth" },
                        label = { Text("Bluetooth") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Volume Controls
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Volume: ${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Connect / Disconnect Button
                Button(
                    onClick = { isConnected = !isConnected },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isConnected) "Disconnect Intercom" else "Connect via $selectedMode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

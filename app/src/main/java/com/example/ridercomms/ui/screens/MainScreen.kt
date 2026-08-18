package com.example.ridercomms.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridercomms.ui.RiderCommsViewModel
import com.example.ridercomms.ui.state.ConnectionRole
import com.example.ridercomms.ui.state.ConnectionStatus
import com.example.ridercomms.ui.state.RiderCommsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RiderCommsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ridercomms Intercom", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(uiState = uiState)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MicStatusIndicator(
                    isSpeaking = uiState.isSpeaking,
                    isConnected = isConnected,
                    isMuted = uiState.isMicMuted
                )

                Spacer(modifier = Modifier.height(36.dp))

                if (!isConnected && uiState.connectionStatus != ConnectionStatus.WAITING_FOR_PEER) {
                    Button(
                        onClick = { viewModel.openRoleSelection() },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Click connect to serve as host or connect to a host to start using the intercom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else if (uiState.connectionStatus == ConnectionStatus.WAITING_FOR_PEER) {
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text("Cancel Waiting", fontSize = 16.sp)
                    }
                } else {
                    Button(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            BottomAudioControls(
                uiState = uiState,
                onMuteToggle = { viewModel.toggleMicMute() },
                onMicVolumeChange = { viewModel.updateMicVolume(it) },
                onAudioVolumeChange = { viewModel.updateAudioVolume(it) }
            )
        }

        if (uiState.showRoleDialog) {
            RoleSelectionDialog(
                onDismiss = { viewModel.dismissRoleSelection() },
                onRoleSelected = { role -> viewModel.selectRoleAndConnect(role) }
            )
        }
    }
}

@Composable
fun StatusCard(uiState: RiderCommsUiState) {
    val statusText = when (uiState.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> "Disconnected"
        ConnectionStatus.WAITING_FOR_PEER -> if (uiState.role == ConnectionRole.HOST) {
            "Hosting: Waiting for child device..."
        } else {
            "Searching for Host..."
        }
        ConnectionStatus.CONNECTED -> "Connected with ${uiState.connectedPeerName ?: "Peer"}"
    }

    val cardColor = when (uiState.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
        ConnectionStatus.WAITING_FOR_PEER -> MaterialTheme.colorScheme.secondaryContainer
        ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MicStatusIndicator(
    isSpeaking: Boolean,
    isConnected: Boolean,
    isMuted: Boolean
) {
    val activeColor = if (isMuted) {
        MaterialTheme.colorScheme.error
    } else if (isSpeaking) {
        Color(0xFF4CAF50)
    } else if (isConnected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val animatedColor by animateColorAsState(targetValue = activeColor, label = "ColorAnimation")

    val pulseScale by if (isSpeaking && !isMuted) {
        val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseScale"
        )
    } else {
        rememberInfiniteTransition(label = "Static").animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = tween(0),
            label = "StaticScale"
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(pulseScale)
                .background(animatedColor.copy(alpha = 0.25f), shape = CircleShape)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .background(animatedColor, shape = CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Mic Status",
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }
    }
}

@Composable
fun BottomAudioControls(
    uiState: RiderCommsUiState,
    onMuteToggle: () -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onAudioVolumeChange: (Float) -> Unit
) {
    val enabled = uiState.connectionStatus == ConnectionStatus.CONNECTED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Controls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onMuteToggle,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute Toggle",
                        tint = if (uiState.isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Mic", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
                Slider(
                    value = uiState.micVolume,
                    onValueChange = onMicVolumeChange,
                    enabled = enabled && !uiState.isMicMuted,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Audio", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
                Slider(
                    value = uiState.audioVolume,
                    onValueChange = onAudioVolumeChange,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun RoleSelectionDialog(
    onDismiss: () -> Unit,
    onRoleSelected: (ConnectionRole) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select Intercom Mode") },
        text = {
            Text("Choose whether this device will create the network session (Host/Hotspot) or connect to an existing session (Child).")
        },
        confirmButton = {
            Button(onClick = { onRoleSelected(ConnectionRole.HOST) }) {
                Text("Host Session")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onRoleSelected(ConnectionRole.CHILD) }) {
                Text("Join Host")
            }
        }
    )
}

package com.example.ridercomms.ui.state

enum class ConnectionRole {
    NONE,
    HOST,
    CHILD
}

enum class ConnectionStatus {
    DISCONNECTED,
    WAITING_FOR_PEER,
    CONNECTED
}

data class RiderCommsUiState(
    val role: ConnectionRole = ConnectionRole.NONE,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val micVolume: Float = 0.8f,
    val audioVolume: Float = 0.8f,
    val connectedPeerName: String? = null,
    val showRoleDialog: Boolean = false
)

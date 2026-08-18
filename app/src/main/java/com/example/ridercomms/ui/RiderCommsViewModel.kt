package com.example.ridercomms.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ridercomms.ui.state.ConnectionRole
import com.example.ridercomms.ui.state.ConnectionStatus
import com.example.ridercomms.ui.state.RiderCommsUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RiderCommsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RiderCommsUiState())
    val uiState: StateFlow<RiderCommsUiState> = _uiState.asStateFlow()

    fun openRoleSelection() {
        _uiState.update { it.copy(showRoleDialog = true) }
    }

    fun dismissRoleSelection() {
        _uiState.update { it.copy(showRoleDialog = false) }
    }

    fun selectRoleAndConnect(role: ConnectionRole) {
        _uiState.update {
            it.copy(
                role = role,
                showRoleDialog = false,
                connectionStatus = ConnectionStatus.WAITING_FOR_PEER
            )
        }
        simulateConnectionProcess(role)
    }

    fun disconnect() {
        _uiState.update {
            it.copy(
                role = ConnectionRole.NONE,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                isSpeaking = false,
                connectedPeerName = null
            )
        }
    }

    fun toggleMicMute() {
        _uiState.update { currentState ->
            val newMuteState = !currentState.isMicMuted
            currentState.copy(
                isMicMuted = newMuteState,
                isSpeaking = if (newMuteState) false else currentState.isSpeaking
            )
        }
    }

    fun updateMicVolume(volume: Float) {
        _uiState.update { it.copy(micVolume = volume) }
    }

    fun updateAudioVolume(volume: Float) {
        _uiState.update { it.copy(audioVolume = volume) }
    }

    private fun simulateConnectionProcess(role: ConnectionRole) {
        viewModelScope.launch {
            delay(2500)
            if (_uiState.value.connectionStatus == ConnectionStatus.WAITING_FOR_PEER) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        connectedPeerName = if (role == ConnectionRole.HOST) "Rider B (Child)" else "Rider A (Host)"
                    )
                }
            }
        }
    }
}

package com.example.ridercomms.network

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioSource
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class AudioStreamManager(
    private val port: Int = 50005,
    private val onPeerDiscovered: (String) -> Unit
) {
    private val sampleRate = 16000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)

    private var socket: DatagramSocket? = null
    private var isRunning = false
    private var isMuted = true
    private var targetIpAddress: String? = null

    private var recordJob: Job? = null
    private var receiveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun start(targetIp: String? = null) {
        if (isRunning) stop()
        this.targetIpAddress = targetIp
        isRunning = true

        try {
            socket = DatagramSocket(port)
            socket?.reuseAddress = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Receiver Loop: Plays incoming UDP audio packets through speaker
        receiveJob = scope.launch {
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelOut,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            track.play()

            val receiveBuffer = ByteArray(bufferSize)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket?.receive(packet)

                    val senderIp = packet.address?.hostAddress
                    if (senderIp != null) {
                        onPeerDiscovered(senderIp)
                        // If host, automatically target the client IP back
                        if (targetIpAddress == null) {
                            targetIpAddress = senderIp
                        }
                    }

                    track.write(packet.data, 0, packet.length)
                } catch (e: Exception) {
                    break
                }
            }
            track.stop()
            track.release()
        }

        // Sender Loop: Captures mic audio and broadcasts over UDP
        recordJob = scope.launch {
            val recorder = AudioRecord(
                AudioSource.MIC,
                sampleRate,
                channelIn,
                audioFormat,
                bufferSize
            )

            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording()
                val sendBuffer = ByteArray(bufferSize)

                while (isRunning) {
                    val readBytes = recorder.read(sendBuffer, 0, sendBuffer.size)
                    if (readBytes > 0 && !isMuted && !targetIpAddress.isNull_or_Empty()) {
                        try {
                            val address = InetAddress.getByName(targetIpAddress)
                            val packet = DatagramPacket(sendBuffer, readBytes, address, port)
                            socket?.send(packet)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                recorder.stop()
                recorder.release()
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setTargetIp(ip: String) {
        this.targetIpAddress = ip
    }

    fun stop() {
        isRunning = false
        recordJob?.cancel()
        receiveJob?.cancel()
        socket?.close()
        socket = null
    }

    private fun String?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()
}

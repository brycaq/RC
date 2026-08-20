package com.example.ridercomms.network

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioSource
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class AudioStreamManager {

    private val sampleRate = 16000
    private val channelConfigRecord = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigPlay = AudioFormat.CHANNEL_OUT_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRecording = false
    private var isPlaying = false

    private var recordJob: Job? = null
    private var playJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun startRecording(targetIp: String, targetPort: Int) {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfigRecord,
            audioEncoding
        )

        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e("AudioStreamManager", "Invalid AudioRecord buffer size")
            return
        }

        audioRecord = AudioRecord(
            AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfigRecord,
            audioEncoding,
            minBufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioStreamManager", "AudioRecord failed to initialize")
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordJob = scope.launch {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(targetIp)
                val buffer = ByteArray(minBufferSize)

                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        val packet = DatagramPacket(buffer, readBytes, address, targetPort)
                        socket.send(packet)
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("AudioStreamManager", "Error recording/sending audio", e)
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioStreamManager", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    fun startPlaying(listenPort: Int) {
        if (isPlaying) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfigPlay,
            audioEncoding
        )

        audioTrack = AudioTrack.Builder()
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
                    .setChannelMask(channelConfigPlay)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .build()

        isPlaying = true
        audioTrack?.play()

        playJob = scope.launch {
            try {
                val socket = DatagramSocket(listenPort)
                val buffer = ByteArray(minBufferSize)

                while (isActive && isPlaying) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    audioTrack?.write(packet.data, 0, packet.length)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("AudioStreamManager", "Error playing audio", e)
            }
        }
    }

    fun stopPlaying() {
        isPlaying = false
        playJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("AudioStreamManager", "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }
}

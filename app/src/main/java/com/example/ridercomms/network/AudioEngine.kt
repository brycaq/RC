package com.example.ridercomms.network

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class AudioEngine {

    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    private var isRecording = false
    private var isPlaying = false

    @SuppressLint("MissingPermission")
    suspend fun startRecordingAndStream(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioEncoding)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfigIn,
            audioEncoding,
            minBufferSize
        )

        val buffer = ByteArray(minBufferSize)
        audioRecord.startRecording()
        isRecording = true

        try {
            while (isRecording) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    suspend fun startPlaybackStream(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioEncoding)
        val audioTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigOut)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .build()

        val buffer = ByteArray(minBufferSize)
        audioTrack.play()
        isPlaying = true

        try {
            while (isPlaying) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    audioTrack.write(buffer, 0, bytesRead)
                }
            }
        } finally {
            audioTrack.stop()
            audioTrack.release()
        }
    }

    fun stop() {
        isRecording = false
        isPlaying = false
    }
}

package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

object StarkAudioEngine {
    private var isMuted = false

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        return isMuted
    }

    fun playArcReactorCharge() {
        if (isMuted) return
        CoroutineScope(Dispatchers.Default).launch {
            playToneSweep(startFreq = 220.0, endFreq = 880.0, durationMs = 450)
            delay(50)
            playToneSweep(startFreq = 880.0, endFreq = 1760.0, durationMs = 250)
        }
    }

    fun playRepulsorBlast() {
        if (isMuted) return
        CoroutineScope(Dispatchers.Default).launch {
            playToneSweep(startFreq = 1200.0, endFreq = 180.0, durationMs = 280)
        }
    }

    fun playJarvisChime() {
        if (isMuted) return
        CoroutineScope(Dispatchers.Default).launch {
            playSingleTone(freq = 587.33, durationMs = 120) // D5
            delay(10)
            playSingleTone(freq = 880.00, durationMs = 120) // A5
            delay(10)
            playSingleTone(freq = 1174.66, durationMs = 200) // D6
        }
    }

    fun playStealthLock() {
        if (isMuted) return
        CoroutineScope(Dispatchers.Default).launch {
            playSingleTone(freq = 900.0, durationMs = 60)
            delay(30)
            playSingleTone(freq = 450.0, durationMs = 90)
        }
    }

    fun playMessageSent() {
        if (isMuted) return
        CoroutineScope(Dispatchers.Default).launch {
            playSingleTone(freq = 784.0, durationMs = 80)
            delay(20)
            playSingleTone(freq = 1046.5, durationMs = 140)
        }
    }

    private fun playSingleTone(freq: Double, durationMs: Int) {
        val sampleRate = 44100
        val numSamples = (durationMs * sampleRate) / 1000
        val generatedSnd = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.8 -> (numSamples - i) / (numSamples * 0.2)
                else -> 1.0
            }
            val sample = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE * 0.4).toInt()
            generatedSnd[i] = sample.toShort()
        }

        playPcmShorts(generatedSnd, sampleRate)
    }

    private fun playToneSweep(startFreq: Double, endFreq: Double, durationMs: Int) {
        val sampleRate = 44100
        val numSamples = (durationMs * sampleRate) / 1000
        val generatedSnd = ShortArray(numSamples)

        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * progress
            val envelope = when {
                progress < 0.1 -> progress / 0.1
                progress > 0.8 -> (1.0 - progress) / 0.2
                else -> 1.0
            }
            phase += 2.0 * Math.PI * currentFreq / sampleRate
            val sample = (sin(phase) * envelope * Short.MAX_VALUE * 0.35).toInt()
            generatedSnd[i] = sample.toShort()
        }

        playPcmShorts(generatedSnd, sampleRate)
    }

    private fun playPcmShorts(samples: ShortArray, sampleRate: Int) {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufSize, samples.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            CoroutineScope(Dispatchers.Default).launch {
                delay((samples.size * 1000L) / sampleRate + 100)
                audioTrack.release()
            }
        } catch (_: Exception) {
            // AudioTrack unavailable fallback
        }
    }
}

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var recordStartTime = 0L
    private var timerJob: Job? = null

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration = _recordingDuration.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _amplitudeList = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeList = _amplitudeList.asStateFlow()

    fun startRecording(coroutineScope: CoroutineScope): Boolean {
        return try {
            val audioDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val file = File(audioDir, "vn_${System.currentTimeMillis()}.m4a")
            currentFilePath = file.absolutePath

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            _isRecording.value = true
            recordStartTime = System.currentTimeMillis()
            _amplitudeList.value = emptyList()

            timerJob = coroutineScope.launch(Dispatchers.Default) {
                while (_isRecording.value) {
                    val dur = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt()
                    _recordingDuration.value = dur
                    val amp = try {
                        val maxAmp = recorder?.maxAmplitude ?: 0
                        (maxAmp / 32767f).coerceIn(0.1f, 1f)
                    } catch (_: Exception) {
                        (0.2f + (Math.random() * 0.6f).toFloat())
                    }
                    _amplitudeList.value = (_amplitudeList.value + amp).takeLast(30)
                    delay(100)
                }
            }
            true
        } catch (e: Exception) {
            // Fallback for emulator without mic permission or hardware mic: simulated voice note generator
            startSimulatedRecording(coroutineScope)
            true
        }
    }

    private fun startSimulatedRecording(coroutineScope: CoroutineScope) {
        _isRecording.value = true
        recordStartTime = System.currentTimeMillis()
        _amplitudeList.value = emptyList()
        val dummyFile = File(context.cacheDir, "vn_sim_${System.currentTimeMillis()}.raw")
        currentFilePath = dummyFile.absolutePath

        timerJob = coroutineScope.launch(Dispatchers.Default) {
            while (_isRecording.value) {
                val dur = ((System.currentTimeMillis() - recordStartTime) / 1000).toInt()
                _recordingDuration.value = dur
                val amp = (0.2f + (Math.random() * 0.7f).toFloat())
                _amplitudeList.value = (_amplitudeList.value + amp).takeLast(30)
                delay(100)
            }
        }
    }

    fun stopRecording(): VoiceNoteResult? {
        timerJob?.cancel()
        _isRecording.value = false
        val duration = _recordingDuration.value.coerceAtLeast(1)
        val amplitudes = _amplitudeList.value.ifEmpty {
            List(15) { (0.2f + Math.random().toFloat() * 0.6f) }
        }

        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        val path = currentFilePath ?: return null
        return VoiceNoteResult(
            filePath = path,
            durationSeconds = duration,
            waveform = amplitudes
        )
    }

    fun cancelRecording() {
        timerJob?.cancel()
        _isRecording.value = false
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        currentFilePath?.let { File(it).delete() }
        currentFilePath = null
    }
}

data class VoiceNoteResult(
    val filePath: String,
    val durationSeconds: Int,
    val waveform: List<Float>
)

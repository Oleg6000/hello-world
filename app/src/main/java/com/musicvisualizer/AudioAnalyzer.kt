package com.musicvisualizer

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AudioAnalyzer {

    private var visualizer: Visualizer? = null
    private var audioRecord: AudioRecord? = null
    private var isCapturing = false

    // Current audio data
    var waveform = ByteArray(1024)
        private set
    var fft = ByteArray(1024)
        private set
    var magnitude = FloatArray(512)
        private set
    var beatEnergy = 0f
        private set
    var bassEnergy = 0f
        private set
    var midEnergy = 0f
        private set
    var trebleEnergy = 0f
        private set

    private var previousEnergy = 0f
    private val energyHistory = FloatArray(43)
    private var energyIndex = 0

    // Callback invoked on each audio data update
    var onDataUpdate: (() -> Unit)? = null

    fun startWithAudioSession(audioSessionId: Int) {
        stopCapture()
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveformBytes: ByteArray, samplingRate: Int) {
                        waveform = waveformBytes
                        analyzeEnergy(waveformBytes)
                        onDataUpdate?.invoke()
                    }
                    override fun onFftDataCapture(v: Visualizer, fftBytes: ByteArray, samplingRate: Int) {
                        fft = fftBytes
                        computeMagnitude(fftBytes)
                        computeFrequencyBands()
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
            isCapturing = true
        } catch (e: Exception) {
            startMicCapture()
        }
    }

    fun startMicCapture() {
        stopCapture()
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).also { record ->
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    isCapturing = true
                    startMicThread(record, bufferSize)
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted; visualizer will use synthetic data
            startSyntheticMode()
        }
    }

    private fun startMicThread(record: AudioRecord, bufferSize: Int) {
        Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (isCapturing) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val bytes = ByteArray(minOf(read, 1024)) { i ->
                        (buffer[i % read] / 256).toByte()
                    }
                    waveform = bytes
                    analyzeEnergy(bytes)
                    computeMagnitudeFromShorts(buffer, read)
                    computeFrequencyBands()
                    onDataUpdate?.invoke()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun startSyntheticModePublic() = startSyntheticMode()

    private fun startSyntheticMode() {
        isCapturing = true
        Thread {
            var phase = 0.0
            while (isCapturing) {
                phase += 0.05
                // Generate synthetic pulsing waveform
                waveform = ByteArray(1024) { i ->
                    val t = i / 1024.0
                    (128 * Math.sin(2 * Math.PI * t * 4 + phase) *
                            (0.5 + 0.5 * Math.sin(phase * 0.3))).toInt().toByte()
                }
                val energy = (0.4f + 0.6f * abs(Math.sin(phase * 0.5).toFloat()))
                beatEnergy = energy
                bassEnergy = energy * 0.8f
                midEnergy = energy * 0.5f
                trebleEnergy = energy * 0.3f
                magnitude = FloatArray(512) { i ->
                    val freq = i / 512.0
                    (energy * Math.exp(-freq * 5) * (0.5 + 0.5 * Math.random())).toFloat()
                }
                onDataUpdate?.invoke()
                Thread.sleep(33) // ~30fps
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun analyzeEnergy(bytes: ByteArray) {
        var sum = 0.0
        for (b in bytes) {
            sum += b * b.toDouble()
        }
        val rms = sqrt(sum / bytes.size).toFloat()
        val energy = rms / 128f

        energyHistory[energyIndex % energyHistory.size] = energy
        energyIndex++

        val avg = energyHistory.average().toFloat()
        beatEnergy = if (energy > avg * 1.5f) energy else energy * 0.8f
        previousEnergy = energy
    }

    private fun computeMagnitude(fftBytes: ByteArray) {
        val n = minOf(fftBytes.size / 2, 512)
        magnitude = FloatArray(n) { i ->
            val re = fftBytes[2 * i].toFloat()
            val im = fftBytes[2 * i + 1].toFloat()
            val mag = sqrt(re * re + im * im)
            (20 * log10(mag.coerceAtLeast(1f) / 128f) + 60f).coerceAtLeast(0f) / 60f
        }
    }

    private fun computeMagnitudeFromShorts(buffer: ShortArray, count: Int) {
        val n = minOf(count / 2, 512)
        magnitude = FloatArray(n) { i ->
            val v = buffer[i * 2].toFloat() / 32768f
            abs(v)
        }
    }

    private fun computeFrequencyBands() {
        val n = magnitude.size
        bassEnergy = magnitude.take(n / 8).average().toFloat()
        midEnergy = magnitude.slice(n / 8 until n / 2).average().toFloat()
        trebleEnergy = magnitude.slice(n / 2 until n).average().toFloat()
    }

    fun stopCapture() {
        isCapturing = false
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }

    fun release() {
        stopCapture()
    }
}

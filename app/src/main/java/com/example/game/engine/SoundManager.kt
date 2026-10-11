package com.example.game.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*

class SoundManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)
    var isSoundEnabled: Boolean = true
    var isVibrationEnabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Pre-generated audio buffers
    private var eatBuffer: ShortArray? = null
    private var potionBuffer: ShortArray? = null
    private var crashBuffer: ShortArray? = null
    private var clickBuffer: ShortArray? = null
    private val sampleRate = 22050

    init {
        scope.launch {
            eatBuffer = generateEatSound()
            potionBuffer = generatePotionSound()
            crashBuffer = generateCrashSound()
            clickBuffer = generateClickSound()
        }
    }

    fun playEat() {
        if (!isSoundEnabled) return
        scope.launch {
            eatBuffer?.let { playBuffer(it) }
        }
    }

    fun playPotion() {
        if (!isSoundEnabled) return
        vibrate(40)
        scope.launch {
            potionBuffer?.let { playBuffer(it) }
        }
    }

    fun playCrash() {
        if (isVibrationEnabled) vibrate(120)
        if (!isSoundEnabled) return
        scope.launch {
            crashBuffer?.let { playBuffer(it) }
        }
    }

    fun playClick() {
        if (!isSoundEnabled) return
        vibrate(20)
        scope.launch {
            clickBuffer?.let { playBuffer(it) }
        }
    }

    private fun vibrate(durationMs: Long) {
        if (!isVibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun playBuffer(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            // Release after completion
            scope.launch {
                val durationMs = (buffer.size * 1000L) / sampleRate
                kotlinx.coroutines.delay(durationMs + 50)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun generateEatSound(): ShortArray {
        val numSamples = (sampleRate * 0.08f).toInt()
        val buffer = ShortArray(numSamples)
        val startFreq = 520.0
        val endFreq = 880.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val env = sin(progress * PI)
            val sample = sin(2 * PI * freq * (i.toDouble() / sampleRate)) * env
            buffer[i] = (sample * 16000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generatePotionSound(): ShortArray {
        val numSamples = (sampleRate * 0.25f).toInt()
        val buffer = ShortArray(numSamples)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        val samplesPerNote = numSamples / notes.size
        for (i in 0 until numSamples) {
            val noteIdx = min(i / samplesPerNote, notes.size - 1)
            val freq = notes[noteIdx]
            val noteProgress = (i % samplesPerNote).toDouble() / samplesPerNote
            val env = (1.0 - noteProgress).pow(1.5)
            val sample = sin(2 * PI * freq * (i.toDouble() / sampleRate)) * env
            buffer[i] = (sample * 18000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generateCrashSound(): ShortArray {
        val numSamples = (sampleRate * 0.35f).toInt()
        val buffer = ShortArray(numSamples)
        var lastRandom = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val noise = (Math.random() * 2.0 - 1.0)
            val filtered = (lastRandom * 0.75 + noise * 0.25)
            lastRandom = filtered
            val boomFreq = 90.0 * (1.0 - progress)
            val boom = sin(2 * PI * boomFreq * (i.toDouble() / sampleRate))
            val env = (1.0 - progress).pow(2.0)
            val sample = (filtered * 0.4 + boom * 0.6) * env
            buffer[i] = (sample * 24000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generateClickSound(): ShortArray {
        val numSamples = (sampleRate * 0.04f).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val env = 1.0 - progress
            val sample = sin(2 * PI * 1200.0 * (i.toDouble() / sampleRate)) * env
            buffer[i] = (sample * 14000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }
}

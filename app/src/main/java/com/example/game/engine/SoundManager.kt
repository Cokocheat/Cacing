package com.example.game.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    // Hardware SoundPool for low-latency, glitch-free audio mixing
    private val soundPool: SoundPool by lazy {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attrs)
            .build()
    }

    private var eatSoundId: Int = 0
    private var potionSoundId: Int = 0
    private var crashSoundId: Int = 0
    private var clickSoundId: Int = 0
    private var killSoundId: Int = 0

    private val sampleRate = 22050
    private var lastEatTime = 0L

    init {
        scope.launch {
            try {
                val cacheDir = context.cacheDir
                val eatFile = File(cacheDir, "sfx_eat_pop.wav")
                val potionFile = File(cacheDir, "sfx_potion_chime.wav")
                val crashFile = File(cacheDir, "sfx_crash_boom.wav")
                val clickFile = File(cacheDir, "sfx_ui_click.wav")
                val killFile = File(cacheDir, "sfx_kill_fanfare.wav")

                writeWav(eatFile, generateSmoothPopSound(), sampleRate)
                writeWav(potionFile, generatePotionSound(), sampleRate)
                writeWav(crashFile, generateCrashSound(), sampleRate)
                writeWav(clickFile, generateClickSound(), sampleRate)
                writeWav(killFile, generateKillSound(), sampleRate)

                eatSoundId = soundPool.load(eatFile.absolutePath, 1)
                potionSoundId = soundPool.load(potionFile.absolutePath, 1)
                crashSoundId = soundPool.load(crashFile.absolutePath, 1)
                clickSoundId = soundPool.load(clickFile.absolutePath, 1)
                killSoundId = soundPool.load(killFile.absolutePath, 1)
            } catch (_: Exception) {}
        }
    }

    // Silky smooth pop sound, rate-limited to avoid hardware crackle / distortion
    fun playEat() {
        if (!isSoundEnabled || eatSoundId == 0) return
        val now = System.currentTimeMillis()
        if (now - lastEatTime < 55) return // smooth 55ms throttle prevents audio pile-up
        lastEatTime = now

        try {
            // slight pitch variation makes eating lots of snacks very melodic and pleasant
            val pitch = 0.95f + (Math.random().toFloat() * 0.22f)
            soundPool.play(eatSoundId, 0.40f, 0.40f, 1, 0, pitch)
        } catch (_: Exception) {}
    }

    fun playPotion() {
        if (!isSoundEnabled || potionSoundId == 0) return
        vibrate(40)
        try {
            soundPool.play(potionSoundId, 0.70f, 0.70f, 2, 0, 1.0f)
        } catch (_: Exception) {}
    }

    fun playCrash() {
        if (isVibrationEnabled) vibrate(120)
        if (!isSoundEnabled || crashSoundId == 0) return
        try {
            soundPool.play(crashSoundId, 0.75f, 0.75f, 3, 0, 1.0f)
        } catch (_: Exception) {}
    }

    fun playClick() {
        if (!isSoundEnabled || clickSoundId == 0) return
        vibrate(20)
        try {
            soundPool.play(clickSoundId, 0.50f, 0.50f, 1, 0, 1.0f)
        } catch (_: Exception) {}
    }

    fun playKill() {
        if (!isSoundEnabled || killSoundId == 0) return
        vibrate(70)
        try {
            soundPool.play(killSoundId, 0.85f, 0.85f, 3, 0, 1.0f)
        } catch (_: Exception) {}
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

    // Write standard 16-bit PCM Mono WAV file
    private fun writeWav(file: File, pcmData: ShortArray, sampleRate: Int) {
        val totalAudioLen = pcmData.size * 2
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * 2 * channels

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16 // 16-bit
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val byteBuffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                byteBuffer.putShort(s)
            }
            fos.write(byteBuffer.array())
        }
    }

    // Soft bubble pop sound: warm sine envelope, no square distortion
    private fun generateSmoothPopSound(): ShortArray {
        val numSamples = (sampleRate * 0.055f).toInt()
        val buffer = ShortArray(numSamples)
        val startFreq = 620.0
        val endFreq = 380.0
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            // Gentle Hann-style bell envelope to prevent any popping/clicking
            val env = sin(progress * PI).pow(1.5)
            val sample = sin(2 * PI * freq * (i.toDouble() / sampleRate)) * env
            buffer[i] = (sample * 11000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    // Triumphant kill sound: bright 2-note fanfare (E6 to G6)
    private fun generateKillSound(): ShortArray {
        val numSamples = (sampleRate * 0.22f).toInt()
        val buffer = ShortArray(numSamples)
        val half = numSamples / 2
        for (i in 0 until numSamples) {
            val freq = if (i < half) 659.25 else 783.99 // E6 then G6
            val noteProgress = if (i < half) i.toDouble() / half else (i - half).toDouble() / (numSamples - half)
            val env = (1.0 - noteProgress).pow(1.2) * sin(noteProgress * PI).coerceAtLeast(0.1)
            val sample = (sin(2 * PI * freq * (i.toDouble() / sampleRate)) * 0.75 +
                    sin(4 * PI * freq * (i.toDouble() / sampleRate)) * 0.25) * env
            buffer[i] = (sample * 16000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generatePotionSound(): ShortArray {
        val numSamples = (sampleRate * 0.22f).toInt()
        val buffer = ShortArray(numSamples)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        val samplesPerNote = numSamples / notes.size
        for (i in 0 until numSamples) {
            val noteIdx = min(i / samplesPerNote, notes.size - 1)
            val freq = notes[noteIdx]
            val noteProgress = (i % samplesPerNote).toDouble() / samplesPerNote
            val env = (1.0 - noteProgress).pow(1.5)
            val sample = sin(2 * PI * freq * (i.toDouble() / sampleRate)) * env
            buffer[i] = (sample * 14000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generateCrashSound(): ShortArray {
        val numSamples = (sampleRate * 0.28f).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val boomFreq = 110.0 * (1.0 - progress)
            val boom = sin(2 * PI * boomFreq * (i.toDouble() / sampleRate))
            val env = (1.0 - progress).pow(2.2)
            val sample = boom * env
            buffer[i] = (sample * 16000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generateClickSound(): ShortArray {
        val numSamples = (sampleRate * 0.03f).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val env = 1.0 - progress
            val sample = sin(2 * PI * 1100.0 * (i.toDouble() / sampleRate)) * env
            buffer[i] = (sample * 9000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }
}

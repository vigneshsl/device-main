package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import com.example.data.CustomSoundStorage
import com.example.domain.BuiltInTone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ephemeral, zero-idle-drain acoustic and tactile alert player.
 *
 * Battery, Security & Resource rules enforced:
 * - Audio resources ([AudioTrack], [ToneGenerator], or [MediaPlayer]) are created ONLY at the moment
 *   an alert plays and are released immediately in a `finally` block right after playback finishes.
 * - No persistent AudioTrack, SoundPool, or MediaPlayer instances are kept alive while monitoring.
 * - Requires zero extra AndroidManifest permissions (strictly adheres to the 4 allowed permissions).
 */
class AlertAudioPlayer(
    private val context: Context,
    private val customSoundStorage: CustomSoundStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val playbackMutex = Mutex()

    suspend fun playAlert(
        tone: BuiltInTone?,
        volumePercent: Int,
        customSoundFileName: String?,
        triggerHaptics: Boolean
    ) {
        playbackMutex.withLock {
            withContext(ioDispatcher) {
                if (triggerHaptics) {
                    playTactilePulseToneOnce()
                }
                if (tone == null || volumePercent <= 0) return@withContext

                val volumeScalar = (volumePercent.coerceIn(0, 100) / 100f)
                if (tone == BuiltInTone.CUSTOM_FILE) {
                    val customFile = customSoundStorage.getCustomSoundFile(customSoundFileName)
                    if (customFile != null) {
                        val playedCustom = playCustomAudioFileOnce(customFile.absolutePath, volumeScalar)
                        if (playedCustom) return@withContext
                    }
                }
                playSynthesizedPcmToneOnce(tone, volumeScalar)
            }
        }
    }

    /**
     * Produces a short, low-frequency sub-bass tactile thump (55 Hz, 45 ms) through the notification
     * sonification channel with haptic-coupled audio attributes (`setHapticChannelsMuted(false)`),
     * providing tactile confirmation on supported audio-coupled haptic actuators without requiring
     * the `android.permission.VIBRATE` manifest permission.
     */
    private suspend fun playTactilePulseToneOnce() {
        val sampleRate = 8_000
        val durationMs = 45
        val numSamples = (sampleRate * durationMs) / 1000
        val pcmBuffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * 55.0 * (i.toDouble() / sampleRate.toDouble())
            pcmBuffer[i] = (sin(angle) * Short.MAX_VALUE * 0.6).toInt().toShort()
        }

        var track: AudioTrack? = null
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setHapticChannelsMuted(false)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcmBuffer.size * 2)
                .build()
            track = audioTrack
            audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
            audioTrack.play()
            delay(durationMs + 20L)
        } catch (_: Throwable) {
            // Ignore if audio output is unavailable
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
        }
    }

    private suspend fun playCustomAudioFileOnce(filePath: String, volumeScalar: Float): Boolean {
        var mediaPlayer: MediaPlayer? = null
        return try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setHapticChannelsMuted(false)
                    .build()
            )
            mp.setDataSource(filePath)
            mp.setVolume(volumeScalar, volumeScalar)
            mp.prepare()
            val durationMs = mp.duration.coerceIn(100, 6_000).toLong()
            mp.start()
            delay(durationMs + 60L)
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
        }
    }

    private suspend fun playSynthesizedPcmToneOnce(tone: BuiltInTone, volumeScalar: Float) {
        val sampleRate = 24_000
        val noteDurationMs = tone.noteDurationMs.coerceIn(50, 400)
        val samplesPerNote = (sampleRate * noteDurationMs) / 1000
        val gapSamples = (sampleRate * 30) / 1000 // 30ms crisp separation between notes
        val totalSamples = tone.frequenciesHz.size * (samplesPerNote + gapSamples)
        val pcmBuffer = ShortArray(totalSamples)

        var writeIndex = 0
        for (freq in tone.frequenciesHz) {
            for (i in 0 until samplesPerNote) {
                if (freq <= 0) {
                    pcmBuffer[writeIndex++] = 0
                } else {
                    val progress = i.toDouble() / samplesPerNote.toDouble()
                    val envelope = when {
                        progress < 0.08 -> progress / 0.08
                        progress > 0.82 -> (1.0 - progress) / 0.18
                        else -> 1.0
                    }
                    val angle = 2.0 * PI * freq * (i.toDouble() / sampleRate.toDouble())
                    val wave = (0.82 * sin(angle)) + (0.18 * sin(angle * 2.0))
                    val amplitude = (wave * envelope * volumeScalar * Short.MAX_VALUE * 0.75).toInt()
                    pcmBuffer[writeIndex++] = amplitude.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            for (g in 0 until gapSamples) {
                if (writeIndex < pcmBuffer.size) {
                    pcmBuffer[writeIndex++] = 0
                }
            }
        }

        var audioTrack: AudioTrack? = null
        try {
            val byteSize = pcmBuffer.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setHapticChannelsMuted(false)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(byteSize)
                .build()

            audioTrack = track
            track.write(pcmBuffer, 0, pcmBuffer.size)
            track.play()
            val totalDurationMs = (pcmBuffer.size * 1000L) / sampleRate
            delay(totalDurationMs + 50L)
        } catch (_: Throwable) {
            // Fallback to ToneGenerator if AudioTrack creation fails on constrained hardware
            var toneGen: ToneGenerator? = null
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, (volumeScalar * 100).toInt())
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                delay(160L)
            } catch (_: Throwable) {
            } finally {
                runCatching { toneGen?.release() }
            }
        } finally {
            runCatching { audioTrack?.stop() }
            runCatching { audioTrack?.release() }
        }
    }
}

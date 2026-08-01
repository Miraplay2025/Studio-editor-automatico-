package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.db.MediaItemEntity
import com.example.data.model.AudioSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class OfflineSpeechVadEngine(private val context: Context) {

    /**
     * Decodes and concatenates one or multiple audio URIs into a single WAV PCM file locally.
     */
    suspend fun concatenateAudioFiles(audioUris: List<Uri>): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "concatenated_narration.wav")
        if (outputFile.exists()) outputFile.delete()

        val pcmOutputStream = ByteArrayOutputStream()
        var sampleRate = 44100
        var channelCount = 1

        for (uri in audioUris) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                var audioTrackIndex = -1
                var format: MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        format = trackFormat
                        break
                    }
                }

                if (audioTrackIndex >= 0 && format != null) {
                    extractor.selectTrack(audioTrackIndex)
                    sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
                    channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    val codec = MediaCodec.createDecoderByType(mime)
                    codec.configure(format, null, null, 0)
                    codec.start()

                    val bufferInfo = MediaCodec.BufferInfo()
                    var isEOS = false

                    while (!isEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    isEOS = true
                                } else {
                                    val presentationTimeUs = extractor.sampleTime
                                    codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                                    extractor.advance()
                                }
                            }
                        }

                        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                        while (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val chunk = ByteArray(bufferInfo.size)
                                outputBuffer.get(chunk)
                                pcmOutputStream.write(chunk)
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                        }
                    }

                    codec.stop()
                    codec.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extractor.release()
            }
        }

        val pcmData = pcmOutputStream.toByteArray()
        val wavHeader = createWavHeader(pcmData.size, sampleRate, channelCount, 16)

        FileOutputStream(outputFile).use { fos ->
            fos.write(wavHeader)
            fos.write(pcmData)
        }

        outputFile
    }

    /**
     * Offline VAD (Voice Activity Detection) algorithm:
     * Analyzes PCM samples and isolates timestamps of spoken phrases vs pause boundaries.
     */
    suspend fun analyzeAudioPhrasesAndPauses(audioFile: File): List<AudioSegment> = withContext(Dispatchers.IO) {
        val segments = mutableListOf<AudioSegment>()
        if (!audioFile.exists() || audioFile.length() <= 44) {
            return@withContext segments
        }

        val pcmBytes = audioFile.readBytes()
        if (pcmBytes.size <= 44) return@withContext segments

        // Skip 44-byte WAV header
        val pcmBuffer = ByteBuffer.wrap(pcmBytes, 44, pcmBytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = pcmBuffer.asShortBuffer()
        val totalSamples = shortBuffer.remaining()

        val sampleRate = 44100
        val windowSizeSamples = sampleRate / 20 // 50ms window frames
        val numWindows = totalSamples / windowSizeSamples

        val windowEnergies = DoubleArray(numWindows)
        var maxEnergy = 0.0

        for (w in 0 until numWindows) {
            var sumSquare = 0.0
            for (s in 0 until windowSizeSamples) {
                val idx = w * windowSizeSamples + s
                if (idx < totalSamples) {
                    val sample = shortBuffer.get(idx).toDouble() / 32768.0
                    sumSquare += sample * sample
                }
            }
            val rms = sqrt(sumSquare / windowSizeSamples)
            windowEnergies[w] = rms
            if (rms > maxEnergy) maxEnergy = rms
        }

        val energyThreshold = max(0.015, maxEnergy * 0.18)

        // Find continuous voice regions and pause gaps
        var currentSpeechStart = -1
        var minSilenceFrames = 6 // ~300ms gap to trigger phrase boundary
        var silenceCount = 0
        var phraseId = 1

        for (w in 0 until numWindows) {
            val isVoice = windowEnergies[w] > energyThreshold
            if (isVoice) {
                if (currentSpeechStart < 0) {
                    currentSpeechStart = w
                }
                silenceCount = 0
            } else {
                if (currentSpeechStart >= 0) {
                    silenceCount++
                    if (silenceCount >= minSilenceFrames || w == numWindows - 1) {
                        val speechEnd = w - silenceCount
                        val startSec = (currentSpeechStart * windowSizeSamples).toDouble() / sampleRate
                        val endSec = max(startSec + 0.5, ((speechEnd + 1) * windowSizeSamples).toDouble() / sampleRate)
                        val duration = endSec - startSec

                        segments.add(
                            AudioSegment(
                                id = phraseId,
                                text = "Frase/Pausa #$phraseId (Segmento Falado)",
                                startTimeSeconds = startSec,
                                endTimeSeconds = endSec,
                                durationSeconds = duration,
                                isSilence = false
                            )
                        )
                        phraseId++
                        currentSpeechStart = -1
                        silenceCount = 0
                    }
                }
            }
        }

        // If no phrases detected or audio was continuous, fallback to default segments
        if (segments.isEmpty()) {
            val totalSec = totalSamples.toDouble() / sampleRate
            val step = max(2.5, totalSec / 4.0)
            var t = 0.0
            var id = 1
            while (t < totalSec) {
                val end = min(totalSec, t + step)
                segments.add(
                    AudioSegment(
                        id = id,
                        text = "Frase #$id (Transcrição Automática)",
                        startTimeSeconds = t,
                        endTimeSeconds = end,
                        durationSeconds = end - t,
                        isSilence = false
                    )
                )
                t = end
                id++
            }
        }

        segments
    }

    /**
     * Auto-syncs timeline items with detected voice/pause phrase timestamps.
     */
    fun syncTimelineWithAudio(
        items: List<MediaItemEntity>,
        audioSegments: List<AudioSegment>
    ): List<MediaItemEntity> {
        if (items.isEmpty() || audioSegments.isEmpty()) return items

        val syncedList = mutableListOf<MediaItemEntity>()

        for (i in items.indices) {
            val item = items[i]
            // Assign matching audio segment duration if available, else average duration
            val segmentDuration = if (i < audioSegments.size) {
                audioSegments[i].durationSeconds
            } else {
                audioSegments.last().durationSeconds
            }

            // Ensure minimum 1.5s display duration
            val newDuration = max(1.5, segmentDuration)
            syncedList.add(item.copy(durationSeconds = newDuration))
        }

        return syncedList
    }

    private fun createWavHeader(
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = pcmDataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16 // Subchunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // AudioFormat (1 for PCM)
        header[21] = 0

        header[22] = channels.toByte()
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()

        header[32] = (channels * bitsPerSample / 8).toByte() // BlockAlign
        header[33] = 0

        header[34] = bitsPerSample.toByte()
        header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = (pcmDataSize shr 8 and 0xff).toByte()
        header[42] = (pcmDataSize shr 16 and 0xff).toByte()
        header[43] = (pcmDataSize shr 24 and 0xff).toByte()

        return header
    }
}

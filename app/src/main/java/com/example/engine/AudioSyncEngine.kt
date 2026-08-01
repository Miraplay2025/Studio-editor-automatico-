package com.example.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.models.AudioTrack
import com.example.data.models.MediaItem
import java.nio.ByteBuffer

object AudioSyncEngine {

    suspend fun detectAudioPauses(context: Context, audioUri: String): List<Long> {
        val pauses = mutableListOf<Long>()
        try {
            val extractor = MediaExtractor()
            val uri = Uri.parse(audioUri)
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex >= 0) {
                extractor.selectTrack(trackIndex)
                val bufferSize = 1024 * 16
                val buffer = ByteBuffer.allocate(bufferSize)

                var lastSampleTimeUs = 0L
                var silenceStartUs = -1L
                val silenceThreshold = 1000 // PCM 16-bit amplitude threshold

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTimeUs = extractor.sampleTime
                    buffer.rewind()

                    var maxAmp = 0
                    var i = 0
                    while (i < sampleSize - 1) {
                        val sample = (buffer.get(i).toInt() and 0xFF) or (buffer.get(i + 1).toInt() shl 8)
                        val absSample = Math.abs(sample)
                        if (absSample > maxAmp) maxAmp = absSample
                        i += 2
                    }

                    if (maxAmp < silenceThreshold) {
                        if (silenceStartUs < 0) {
                            silenceStartUs = sampleTimeUs
                        }
                    } else {
                        if (silenceStartUs >= 0) {
                            val durationMs = (sampleTimeUs - silenceStartUs) / 1000
                            if (durationMs > 250) { // Pause longer than 250ms
                                val pauseTimestampMs = silenceStartUs / 1000
                                if (pauseTimestampMs > 1000) {
                                    pauses.add(pauseTimestampMs)
                                }
                            }
                            silenceStartUs = -1L
                        }
                    }

                    lastSampleTimeUs = sampleTimeUs
                    extractor.advance()
                }

                extractor.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (pauses.isEmpty()) {
            // Default 3-second fallback pauses if file parsing isn't PCM
            pauses.addAll(listOf(3000L, 6000L, 9000L, 12000L, 15000L))
        }

        return pauses.distinct().sorted()
    }

    fun syncMediaToAudioPauses(mediaItems: List<MediaItem>, pausesMs: List<Long>): List<MediaItem> {
        if (mediaItems.isEmpty() || pausesMs.isEmpty()) return mediaItems

        val updatedItems = mutableListOf<MediaItem>()
        var previousTimestampMs = 0L

        mediaItems.forEachIndexed { index, item ->
            val pauseTimeMs = if (index < pausesMs.size) {
                pausesMs[index]
            } else {
                previousTimestampMs + 3000L
            }

            val calculatedDurationMs = (pauseTimeMs - previousTimestampMs).coerceAtLeast(1000L)
            previousTimestampMs += calculatedDurationMs

            updatedItems.add(
                item.copy(
                    durationMs = calculatedDurationMs,
                    motionDurationMs = (calculatedDurationMs * 0.8f).toLong().coerceAtLeast(1000L)
                )
            )
        }

        return updatedItems
    }
}

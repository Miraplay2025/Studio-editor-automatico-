package com.example.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioSpeechAnalyzer(private val context: Context) {

    data class PauseMarker(
        val timestampSec: Float,
        val pauseDurationMs: Long
    )

    suspend fun analyzeAudioPauses(audioUri: Uri): List<PauseMarker> = withContext(Dispatchers.IO) {
        val markers = mutableListOf<PauseMarker>()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, audioUri, null)
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
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 10_000_000L
                val totalDurationSec = durationUs / 1_000_000f

                // Read sample buffers and analyze RMS energy
                val bufferSize = 8192
                val buffer = ByteBuffer.allocateDirect(bufferSize)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val frameDurSec = 0.05f // 50ms frames
                var silenceStartTime = -1f
                var currentSec = 0f

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val presentationTimeUs = extractor.sampleTime
                    currentSec = presentationTimeUs / 1_000_000f

                    // Calculate RMS amplitude
                    val shortBuffer = buffer.asShortBuffer()
                    var sumSquare = 0.0
                    var count = 0
                    while (shortBuffer.hasRemaining()) {
                        val sample = shortBuffer.get().toDouble()
                        sumSquare += sample * sample
                        count++
                    }

                    val rms = if (count > 0) Math.sqrt(sumSquare / count) else 0.0
                    val isSilence = rms < 800.0 // Threshold for silence / voice pause

                    if (isSilence) {
                        if (silenceStartTime < 0) {
                            silenceStartTime = currentSec
                        }
                    } else {
                        if (silenceStartTime >= 0) {
                            val pauseDuration = (currentSec - silenceStartTime) * 1000f
                            if (pauseDuration >= 250f) { // Significant sentence pause
                                val pauseMidPoint = (silenceStartTime + currentSec) / 2f
                                markers.add(PauseMarker(pauseMidPoint, pauseDuration.toLong()))
                            }
                            silenceStartTime = -1f
                        }
                    }

                    extractor.advance()
                    buffer.clear()
                }

                // Fallback markers if audio has continuous noise or no clear silent gaps
                if (markers.isEmpty() && totalDurationSec > 1f) {
                    val interval = (totalDurationSec / 4f).coerceAtLeast(2.5f)
                    var t = interval
                    while (t < totalDurationSec - 0.5f) {
                        markers.add(PauseMarker(t, 300L))
                        t += interval
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioSpeechAnalyzer", "Error analyzing audio speech pauses", e)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        markers
    }
}

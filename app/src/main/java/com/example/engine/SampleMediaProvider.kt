package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.models.AudioTrack
import com.example.data.models.CameraMotion
import com.example.data.models.MediaItem
import com.example.data.models.MediaType
import com.example.data.models.MotionAnimation
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object SampleMediaProvider {

    fun getOrGenerateSampleMedia(context: Context): List<MediaItem> {
        val mediaDir = File(context.cacheDir, "sample_media").apply { mkdirs() }
        val sampleItems = mutableListOf<MediaItem>()

        val presets = listOf(
            Triple("Montanha ao Pôr do Sol", Color.parseColor("#FF512F"), Color.parseColor("#DD2476")),
            Triple("Oceano Profundo", Color.parseColor("#00c6ff"), Color.parseColor("#0072ff")),
            Triple("Cidade Cyberpunk", Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0")),
            Triple("Floresta Mística", Color.parseColor("#11998e"), Color.parseColor("#38ef7d"))
        )

        presets.forEachIndexed { index, (title, color1, color2) ->
            val file = File(mediaDir, "sample_image_$index.jpg")
            if (!file.exists()) {
                val bitmap = createSampleBitmap(title, color1, color2)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            val uri = Uri.fromFile(file).toString()
            val motion = when (index % 4) {
                0 -> MotionAnimation.SWING
                1 -> MotionAnimation.SHRINK
                2 -> MotionAnimation.YOYO
                else -> MotionAnimation.BOUNCE
            }
            val camera = when (index % 4) {
                0 -> CameraMotion.ZOOM_IN
                1 -> CameraMotion.ZOOM_OUT
                2 -> CameraMotion.PAN_RIGHT
                else -> CameraMotion.PAN_LEFT
            }
            sampleItems.add(
                MediaItem(
                    uri = uri,
                    type = MediaType.IMAGE,
                    title = title,
                    durationMs = 3500L,
                    motionAnimation = motion,
                    motionDurationMs = 1500L,
                    cameraMotion = camera
                )
            )
        }
        return sampleItems
    }

    private fun createSampleBitmap(title: String, color1: Int, color2: Int): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), color1, color2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative Geometric Waves
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.FILL
        }

        val path = Path().apply {
            moveTo(0f, height * 0.6f)
            cubicTo(width * 0.3f, height * 0.5f, width * 0.7f, height * 0.7f, width.toFloat(), height * 0.55f)
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        canvas.drawPath(path, wavePaint)

        // Title Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(10f, 0f, 4f, Color.argb(150, 0, 0, 0))
        }
        canvas.drawText(title, width / 2f, height / 2f, textPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Video Motion Editor • 4K Sample Asset", width / 2f, height / 2f + 80f, subPaint)

        return bitmap
    }

    fun getOrGenerateSampleAudio(context: Context): AudioTrack {
        val audioDir = File(context.cacheDir, "sample_audio").apply { mkdirs() }
        val audioFile = File(audioDir, "sample_beat.wav")

        if (!audioFile.exists()) {
            createSampleWavFile(audioFile, durationSeconds = 12)
        }

        val uri = Uri.fromFile(audioFile).toString()
        return AudioTrack(
            uri = uri,
            name = "Trilha Sonora Demonstrativa",
            durationMs = 12000L,
            detectedPausesMs = listOf(3000L, 6000L, 9000L)
        )
    }

    private fun createSampleWavFile(file: File, durationSeconds: Int) {
        val sampleRate = 44100
        val numSamples = durationSeconds * sampleRate
        val pcmData = ShortArray(numSamples)

        // Generate synthesized beat pattern with rhythmic pauses
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val second = t % 3.0 // 3-second phrase
            if (second < 2.5) { // 2.5s sound, 0.5s pause
                val freq = 440.0 + (if ((t * 2).toInt() % 2 == 0) 110.0 else 0.0)
                val sample = Math.sin(2.0 * Math.PI * freq * t) * 0.5
                pcmData[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            } else {
                pcmData[i] = 0 // Silence pause
            }
        }

        val totalDataLen = pcmData.size * 2 + 36
        val byteRate = sampleRate * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(totalDataLen))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16)) // Subchunk1Size
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // AudioFormat = PCM
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // NumChannels = 1
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(byteRate))
            raf.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt()) // BlockAlign
            raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt()) // BitsPerSample
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(pcmData.size * 2))

            val byteBuffer = java.nio.ByteBuffer.allocate(pcmData.size * 2)
            byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                byteBuffer.putShort(s)
            }
            raf.write(byteBuffer.array())
        }
    }
}

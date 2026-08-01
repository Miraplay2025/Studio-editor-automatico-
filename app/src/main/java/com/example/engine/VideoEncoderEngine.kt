package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.view.Surface
import com.example.data.models.CameraMotion
import com.example.data.models.ExportConfig
import com.example.data.models.MediaItem
import com.example.data.models.MotionAnimation
import com.example.data.models.TransitionEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoEncoderEngine(
    private val context: Context,
    private val mediaItems: List<MediaItem>,
    private val selectedTransitions: List<String>,
    private val exportConfig: ExportConfig,
    private val onProgress: (percent: Int, logMessage: String) -> Unit
) {

    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    suspend fun encodeVideo(): File = withContext(Dispatchers.IO) {
        val (outWidth, outHeight) = exportConfig.resolution.getDimensions(exportConfig.aspectRatio)
        val fps = exportConfig.fps
        val bitRate = outWidth * outHeight * 4

        val outputDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "MotionEditor"
        ).apply { mkdirs() }

        val outputFile = File(outputDir, "Render_${System.currentTimeMillis()}.mp4")

        onProgress(0, "[INIT] Configurando codificador de vídeo H.264 AVC (${outWidth}x${outHeight} @ ${fps}FPS)")
        onProgress(2, "[INIT] Arquivo de saída: ${outputFile.absolutePath}")

        // Cache Bitmaps
        onProgress(5, "[ASSETS] Carregando e escalando ${mediaItems.size} mídias para resolução de renderização...")
        val bitmapCache = mutableMapOf<String, Bitmap>()
        mediaItems.forEachIndexed { idx, item ->
            if (isCancelled) throw IllegalStateException("Renderização cancelada pelo usuário")
            val bmp = loadAndScaleBitmap(context, item.uri, outWidth, outHeight)
            bitmapCache[item.id] = bmp
            onProgress(5 + ((idx + 1) * 10 / mediaItems.size), "[ASSETS] Mídia ${idx + 1}/${mediaItems.size} carregada com sucesso.")
        }

        val totalDurationMs = mediaItems.sumOf { it.durationMs }
        val totalFrames = ((totalDurationMs / 1000f) * fps).toInt().coerceAtLeast(fps)
        val frameDurationUs = 1_000_000L / fps

        onProgress(15, "[PIPELINE] Duração total: ${totalDurationMs / 1000f}s. Total de frames a renderizar: $totalFrames")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outWidth, outHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()

        try {
            var currentPresentationTimeUs = 0L

            for (frameIdx in 0 until totalFrames) {
                if (isCancelled) {
                    onProgress(0, "[CANCEL] Operação de renderização interrompida.")
                    throw IllegalStateException("Renderização cancelada")
                }

                val currentProgress = 15 + ((frameIdx.toFloat() / totalFrames) * 75).toInt()
                val currentTimeMs = (frameIdx * 1000L) / fps

                // Render Canvas Frame
                val canvas = inputSurface.lockCanvas(null)
                drawCompositionFrame(
                    canvas = canvas,
                    currentTimeMs = currentTimeMs,
                    width = outWidth,
                    height = outHeight,
                    mediaItems = mediaItems,
                    bitmapCache = bitmapCache,
                    selectedTransitions = selectedTransitions
                )
                inputSurface.unlockCanvasAndPost(canvas)

                // Drain Encoder
                drainEncoder(encoder, muxer, bufferInfo, false) { newTrack ->
                    trackIndex = newTrack
                    muxerStarted = true
                }

                currentPresentationTimeUs += frameDurationUs

                if (frameIdx % (fps / 2).coerceAtLeast(1) == 0 || frameIdx == totalFrames - 1) {
                    onProgress(
                        currentProgress,
                        "[ENCODER] Frame $frameIdx/$totalFrames (${currentTimeMs}ms) codificado..."
                    )
                }
            }

            // Drain remaining
            drainEncoder(encoder, muxer, bufferInfo, true) { newTrack ->
                trackIndex = newTrack
                muxerStarted = true
            }

            onProgress(95, "[FINALIZE] Finalizando contêiner MP4 e gravando cabeçalhos...")

        } finally {
            try {
                encoder.stop()
                encoder.release()
            } catch (e: Exception) { e.printStackTrace() }

            if (muxerStarted) {
                try {
                    muxer.stop()
                    muxer.release()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        onProgress(100, "[SUCCESS] Vídeo exportado com sucesso em 100%!")
        return@withContext outputFile
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (track: Int) -> Unit
    ) {
        val timeoutUs = 10_000L
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                val track = muxer.addTrack(newFormat)
                muxer.start()
                onMuxerStart(track)
            } else if (outputBufferIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(0, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }

    companion object {
        fun drawCompositionFrame(
            canvas: Canvas,
            currentTimeMs: Long,
            width: Int,
            height: Int,
            mediaItems: List<MediaItem>,
            bitmapCache: Map<String, Bitmap>,
            selectedTransitions: List<String>
        ) {
            canvas.drawColor(Color.BLACK)
            if (mediaItems.isEmpty()) return

            // Find active item
            var accumulatedMs = 0L
            var activeIndex = -1
            var timeInItemMs = 0L

            for (i in mediaItems.indices) {
                val item = mediaItems[i]
                if (currentTimeMs >= accumulatedMs && currentTimeMs < accumulatedMs + item.durationMs) {
                    activeIndex = i
                    timeInItemMs = currentTimeMs - accumulatedMs
                    break
                }
                accumulatedMs += item.durationMs
            }

            if (activeIndex < 0) {
                activeIndex = mediaItems.lastIndex
                timeInItemMs = mediaItems.last().durationMs
            }

            val currentItem = mediaItems[activeIndex]
            val currentBitmap = bitmapCache[currentItem.id] ?: return

            val transitionDurationMs = 800L
            val isNearEnd = timeInItemMs >= (currentItem.durationMs - transitionDurationMs) && activeIndex < mediaItems.lastIndex

            if (isNearEnd) {
                val nextItem = mediaItems[activeIndex + 1]
                val nextBitmap = bitmapCache[nextItem.id]
                val transitionProgress = (timeInItemMs - (currentItem.durationMs - transitionDurationMs)).toFloat() / transitionDurationMs

                val transitionEffectId = currentItem.transitionOverride
                    ?: selectedTransitions.getOrNull((activeIndex) % selectedTransitions.size.coerceAtLeast(1))
                    ?: "CROSSFADE"

                drawTransitionBetween(
                    canvas = canvas,
                    bmp1 = currentBitmap,
                    bmp2 = nextBitmap ?: currentBitmap,
                    progress = transitionProgress.coerceIn(0f, 1f),
                    effectId = transitionEffectId,
                    width = width,
                    height = height,
                    item1 = currentItem,
                    item2 = nextItem,
                    time1Ms = timeInItemMs,
                    time2Ms = timeInItemMs - (currentItem.durationMs - transitionDurationMs)
                )
            } else {
                drawSingleItemWithAnimations(
                    canvas = canvas,
                    bitmap = currentBitmap,
                    item = currentItem,
                    timeInItemMs = timeInItemMs,
                    width = width,
                    height = height,
                    alpha = 1f
                )
            }
        }

        private fun drawSingleItemWithAnimations(
            canvas: Canvas,
            bitmap: Bitmap,
            item: MediaItem,
            timeInItemMs: Long,
            width: Int,
            height: Int,
            alpha: Float
        ) {
            val normTime = (timeInItemMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
            val matrix = Matrix()

            // Base scale to fit container
            val scaleX = width.toFloat() / bitmap.width
            val scaleY = height.toFloat() / bitmap.height
            val baseScale = Math.max(scaleX, scaleY)

            val scaledW = bitmap.width * baseScale
            val scaledH = bitmap.height * baseScale
            val dx = (width - scaledW) / 2f
            val dy = (height - scaledH) / 2f

            matrix.postScale(baseScale, baseScale)
            matrix.postTranslate(dx, dy)

            // 1. Camera Pan & Zoom Transform (Ken Burns Effect)
            var cameraScale = 1.0f
            var cameraTransX = 0f
            var cameraTransY = 0f

            when (item.cameraMotion) {
                CameraMotion.ZOOM_IN -> cameraScale = 1.0f + (0.25f * normTime)
                CameraMotion.ZOOM_OUT -> cameraScale = 1.25f - (0.25f * normTime)
                CameraMotion.PAN_LEFT -> cameraTransX = -100f * normTime
                CameraMotion.PAN_RIGHT -> cameraTransX = 100f * normTime
                CameraMotion.PAN_UP -> cameraTransY = -100f * normTime
                CameraMotion.PAN_DOWN -> cameraTransY = 100f * normTime
                CameraMotion.NONE -> {}
            }

            if (cameraScale != 1.0f || cameraTransX != 0f || cameraTransY != 0f) {
                matrix.postScale(cameraScale, cameraScale, width / 2f, height / 2f)
                matrix.postTranslate(cameraTransX, cameraTransY)
            }

            // 2. Motion Animation Transform
            var motionScale = 1.0f
            var motionRotation = 0f
            var motionTransX = 0f
            var motionTransY = 0f
            var motionAlpha = alpha

            when (item.motionAnimation) {
                MotionAnimation.SWING -> {
                    motionRotation = Math.sin(normTime * Math.PI * 4).toFloat() * 8f
                }
                MotionAnimation.SHRINK -> {
                    motionScale = 1.15f - (0.15f * normTime)
                }
                MotionAnimation.YOYO -> {
                    motionScale = 1.0f + (Math.sin(normTime * Math.PI * 4).toFloat() * 0.12f)
                }
                MotionAnimation.BOUNCE -> {
                    motionTransY = Math.abs(Math.sin(normTime * Math.PI * 6)).toFloat() * -40f
                }
                MotionAnimation.PULSE -> {
                    motionScale = 1.0f + (Math.abs(Math.sin(normTime * Math.PI * 8)).toFloat() * 0.08f)
                }
                MotionAnimation.FADE_IN_OUT -> {
                    val fadeIn = (normTime / 0.2f).coerceIn(0f, 1f)
                    val fadeOut = ((1f - normTime) / 0.2f).coerceIn(0f, 1f)
                    motionAlpha *= Math.min(fadeIn, fadeOut)
                }
                MotionAnimation.SLIDE_IN -> {
                    if (normTime < 0.25f) {
                        val slideProgress = normTime / 0.25f
                        motionTransX = (1f - slideProgress) * -width.toFloat()
                    }
                }
                MotionAnimation.NONE -> {}
            }

            if (motionScale != 1.0f || motionRotation != 0f || motionTransX != 0f || motionTransY != 0f) {
                matrix.postScale(motionScale, motionScale, width / 2f, height / 2f)
                matrix.postRotate(motionRotation, width / 2f, height / 2f)
                matrix.postTranslate(motionTransX, motionTransY)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = (motionAlpha.coerceIn(0f, 1f) * 255).toInt()
            }

            canvas.drawBitmap(bitmap, matrix, paint)
        }

        private fun drawTransitionBetween(
            canvas: Canvas,
            bmp1: Bitmap,
            bmp2: Bitmap,
            progress: Float,
            effectId: String,
            width: Int,
            height: Int,
            item1: MediaItem,
            item2: MediaItem,
            time1Ms: Long,
            time2Ms: Long
        ) {
            when (effectId) {
                "CROSSFADE" -> {
                    drawSingleItemWithAnimations(canvas, bmp1, item1, time1Ms, width, height, 1f - progress)
                    drawSingleItemWithAnimations(canvas, bmp2, item2, time2Ms, width, height, progress)
                }
                "SLIDE_LEFT" -> {
                    canvas.save()
                    canvas.translate(-width * progress, 0f)
                    drawSingleItemWithAnimations(canvas, bmp1, item1, time1Ms, width, height, 1f)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(width * (1f - progress), 0f)
                    drawSingleItemWithAnimations(canvas, bmp2, item2, time2Ms, width, height, 1f)
                    canvas.restore()
                }
                "SLIDE_RIGHT" -> {
                    canvas.save()
                    canvas.translate(width * progress, 0f)
                    drawSingleItemWithAnimations(canvas, bmp1, item1, time1Ms, width, height, 1f)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(-width * (1f - progress), 0f)
                    drawSingleItemWithAnimations(canvas, bmp2, item2, time2Ms, width, height, 1f)
                    canvas.restore()
                }
                "CIRCLE_CROP" -> {
                    drawSingleItemWithAnimations(canvas, bmp1, item1, time1Ms, width, height, 1f)

                    val maxRadius = Math.hypot(width.toDouble(), height.toDouble()).toFloat() / 2f
                    val currentRadius = maxRadius * progress

                    val layerBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val layerCanvas = Canvas(layerBmp)
                    drawSingleItemWithAnimations(layerCanvas, bmp2, item2, time2Ms, width, height, 1f)

                    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    }
                    val maskPath = Path().apply {
                        addCircle(width / 2f, height / 2f, currentRadius, Path.Direction.CW)
                    }
                    layerCanvas.drawPath(maskPath, maskPaint)

                    canvas.drawBitmap(layerBmp, 0f, 0f, null)
                }
                "FADE_BLACK" -> {
                    if (progress < 0.5f) {
                        val p1 = 1f - (progress * 2f)
                        drawSingleItemWithAnimations(canvas, bmp1, item1, time1Ms, width, height, p1)
                    } else {
                        val p2 = (progress - 0.5f) * 2f
                        drawSingleItemWithAnimations(canvas, bmp2, item2, time2Ms, width, height, p2)
                    }
                }
                else -> {
                    // Default Crossfade
                    drawSingleItemWithAnimations(canvas, bmp1, item1, time1Ms, width, height, 1f - progress)
                    drawSingleItemWithAnimations(canvas, bmp2, item2, time2Ms, width, height, progress)
                }
            }
        }

        fun loadAndScaleBitmap(context: Context, uriStr: String, targetW: Int, targetH: Int): Bitmap {
            return try {
                val uri = Uri.parse(uriStr)
                val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
                val lower = uriStr.lowercase()
                val isVideo = mimeType.startsWith("video") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".3gp")

                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val frame = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frame != null) {
                            return Bitmap.createScaledBitmap(frame, targetW, targetH, true)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        try { retriever.release() } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                val sampleSize = Math.max(1, Math.min(options.outWidth / targetW, options.outHeight / targetH))

                val loadOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val realStream = context.contentResolver.openInputStream(uri)
                val decoded = BitmapFactory.decodeStream(realStream, null, loadOptions)
                realStream?.close()

                decoded ?: createFallbackBitmap(targetW, targetH)
            } catch (e: Exception) {
                createFallbackBitmap(targetW, targetH)
            }
        }

        private fun createFallbackBitmap(w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.DKGRAY)
            val p = Paint().apply {
                color = Color.WHITE
                textSize = 48f
                textAlign = Paint.Align.CENTER
            }
            c.drawText("Video Motion Media", w / 2f, h / 2f, p)
            return bmp
        }
    }
}

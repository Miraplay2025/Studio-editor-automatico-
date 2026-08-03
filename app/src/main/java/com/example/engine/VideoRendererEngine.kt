package com.example.engine

import android.content.ContentValues
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
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.AspectRatioOption
import com.example.data.CameraAnimation
import com.example.data.ExportFps
import com.example.data.ExportQuality
import com.example.data.MediaClip
import com.example.data.TransitionType
import com.example.data.VideoResolution
import com.example.data.ZoomAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

class VideoRendererEngine(private val context: Context) {

    data class RenderProgress(
        val percent: Float,
        val currentStep: String,
        val logMessage: String
    )

    suspend fun renderVideo(
        clips: List<MediaClip>,
        aspectRatio: AspectRatioOption,
        resolution: VideoResolution,
        quality: ExportQuality,
        fps: ExportFps,
        audioUriString: String?,
        onProgress: (RenderProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()

        fun emit(percent: Float, step: String, msg: String) {
            logs.add("[$step] $msg")
            onProgress(RenderProgress(percent, step, msg))
        }

        emit(0f, "Iniciando", "Preparando motor de renderização nativo...")

        if (clips.isEmpty()) {
            throw IllegalArgumentException("Nenhuma mídia selecionada para renderização.")
        }

        // Output Dimensions based on aspect ratio & resolution
        val targetHeight = resolution.heightPx
        val targetWidth = (targetHeight * aspectRatio.ratio).toInt() and 1.inv() // must be even

        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "video_editor_${System.currentTimeMillis()}.mp4")

        emit(5f, "Configurando", "Resolução: ${targetWidth}x${targetHeight} @ ${fps.fpsValue} FPS, Bitrate: ${quality.bitrateBps / 1000000} Mbps")

        // 1. Prepare bitmaps and scaled frames for each clip
        emit(10f, "Carregando Mídias", "Decodificando ${clips.size} clipes da timeline...")
        val clipBitmaps = mutableListOf<Bitmap>()

        for ((index, clip) in clips.withIndex()) {
            if (!coroutineContext.isActive) throw InterruptedException("Renderização cancelada")
            emit(10f + (index.toFloat() / clips.size) * 15f, "Carregando Mídias", "Carregando clipe ${index + 1} de ${clips.size}...")

            val bitmap = loadBitmapFromUri(clip.uriString, targetWidth, targetHeight)
            clipBitmaps.add(bitmap)
        }

        // Calculate frame counts
        val frameRate = fps.fpsValue
        val frameDurationMs = 1000L / frameRate
        val transitionDurationSec = 0.8f // 800ms transition time
        val transitionFrames = (transitionDurationSec * frameRate).toInt()

        val clipFrames = clips.map { (it.durationSec * frameRate).toInt() }
        val totalFrames = clipFrames.sum()

        emit(25f, "Inicializando Muxer", "Configurando codificador H.264 e contêiner MP4...")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        // Buffer info
        val bufferInfo = MediaCodec.BufferInfo()

        // Offscreen bitmap canvas to draw rendered frame
        val frameBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        emit(30f, "Renderizando Quadros", "Processando $totalFrames quadros com efeitos e transições...")

        var currentClipIndex = 0
        var currentClipFrame = 0
        var totalFrameCount = 0

        for (f in 0 until totalFrames) {
            if (!coroutineContext.isActive) {
                encoder.stop()
                encoder.release()
                outputFile.delete()
                throw InterruptedException("Renderização cancelada pelo usuário")
            }

            // Determine active clip and progress
            var accum = 0
            for (i in clips.indices) {
                if (f < accum + clipFrames[i]) {
                    currentClipIndex = i
                    currentClipFrame = f - accum
                    break
                }
                accum += clipFrames[i]
            }

            val activeClip = clips[currentClipIndex]
            val activeBitmap = clipBitmaps[currentClipIndex]
            val clipTotalFrames = clipFrames[currentClipIndex]
            val clipProgress = currentClipFrame.toFloat() / clipTotalFrames.coerceAtLeast(1)

            // Clear canvas
            canvas.drawColor(Color.BLACK)

            // Check if frame is in transition region with NEXT clip
            val isTransitioningOut = (clipTotalFrames - currentClipFrame) <= transitionFrames && currentClipIndex < clips.size - 1
            val transitionType = activeClip.assignedTransition

            if (isTransitioningOut && transitionType != null && currentClipIndex + 1 < clips.size) {
                val nextBitmap = clipBitmaps[currentClipIndex + 1]
                val nextClip = clips[currentClipIndex + 1]
                val tProgress = 1.0f - ((clipTotalFrames - currentClipFrame).toFloat() / transitionFrames)

                renderTransitionFrame(
                    canvas = canvas,
                    bitmap1 = activeBitmap,
                    clip1 = activeClip,
                    progress1 = clipProgress,
                    bitmap2 = nextBitmap,
                    clip2 = nextClip,
                    progress2 = 0f,
                    transition = transitionType,
                    tProgress = tProgress,
                    width = targetWidth,
                    height = targetHeight,
                    paint = paint
                )
            } else {
                // Single frame render with Camera & Zoom Animations
                renderSingleFrame(
                    canvas = canvas,
                    bitmap = activeBitmap,
                    clip = activeClip,
                    progress = clipProgress,
                    width = targetWidth,
                    height = targetHeight,
                    paint = paint
                )
            }

            // Draw frame bitmap to InputSurface
            drawBitmapToSurface(frameBitmap, inputSurface)

            // Drain encoder outputs
            drainEncoder(encoder, muxer, bufferInfo, false) { index, started ->
                trackIndex = index
                muxerStarted = started
            }

            totalFrameCount++
            if (f % 5 == 0 || f == totalFrames - 1) {
                val renderPercent = 30f + (f.toFloat() / totalFrames) * 60f
                emit(renderPercent, "Renderizando Quadros", "Renderizado quadro $f de $totalFrames (${renderPercent.toInt()}%)")
            }
        }

        // Finish encoding
        emit(90f, "Finalizando Vídeo", "Enviando sinal de fim de fluxo para o codificador...")
        drainEncoder(encoder, muxer, bufferInfo, true) { index, started ->
            trackIndex = index
            muxerStarted = started
        }

        encoder.stop()
        encoder.release()

        if (muxerStarted) {
            try {
                muxer.stop()
                muxer.release()
            } catch (e: Exception) {
                Log.e("VideoRendererEngine", "Error stopping muxer", e)
            }
        }

        // Save to public Gallery if requested
        saveToPublicGallery(outputFile)

        emit(100f, "Concluído", "Vídeo renderizado com sucesso em ${outputFile.name}!")

        outputFile
    }

    private fun renderSingleFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        clip: MediaClip,
        progress: Float,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        val matrix = Matrix()
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()

        // Base Scale Center-Crop
        val scale = Math.max(width.toFloat() / srcW, height.toFloat() / srcH)
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f

        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        // Apply Camera Animation (Translate displacement)
        val camShift = width * 0.12f * progress
        when (clip.cameraAnim) {
            CameraAnimation.MOVE_LEFT -> matrix.postTranslate(-camShift, 0f)
            CameraAnimation.MOVE_RIGHT -> matrix.postTranslate(camShift, 0f)
            CameraAnimation.MOVE_UP -> matrix.postTranslate(0f, -camShift)
            CameraAnimation.MOVE_DOWN -> matrix.postTranslate(0f, camShift)
            CameraAnimation.DIAG_UP_LEFT -> matrix.postTranslate(-camShift, -camShift)
            CameraAnimation.DIAG_UP_RIGHT -> matrix.postTranslate(camShift, -camShift)
            CameraAnimation.DIAG_DOWN_LEFT -> matrix.postTranslate(-camShift, camShift)
            CameraAnimation.DIAG_DOWN_RIGHT -> matrix.postTranslate(camShift, camShift)
            CameraAnimation.PAN_HORIZ -> matrix.postTranslate((progress - 0.5f) * width * 0.15f, 0f)
            CameraAnimation.PAN_VERT -> matrix.postTranslate(0f, (progress - 0.5f) * height * 0.15f)
            CameraAnimation.NONE -> {}
        }

        // Apply Zoom Animation (Scale relative to center)
        val zoomFactor = when (clip.zoomAnim) {
            ZoomAnimation.ZOOM_IN -> 1.0f + 0.20f * progress
            ZoomAnimation.ZOOM_OUT -> 1.20f - 0.20f * progress
            ZoomAnimation.PAN_LEFT -> 1.10f
            ZoomAnimation.PAN_RIGHT -> 1.10f
            ZoomAnimation.UP -> 1.10f
            ZoomAnimation.DOWN -> 1.10f
            ZoomAnimation.NONE -> 1.0f
        }

        if (zoomFactor != 1.0f) {
            matrix.postScale(zoomFactor, zoomFactor, width / 2f, height / 2f)
        }

        if (clip.zoomAnim == ZoomAnimation.PAN_LEFT) {
            matrix.postTranslate(-camShift * 0.5f, 0f)
        } else if (clip.zoomAnim == ZoomAnimation.PAN_RIGHT) {
            matrix.postTranslate(camShift * 0.5f, 0f)
        } else if (clip.zoomAnim == ZoomAnimation.UP) {
            matrix.postTranslate(0f, -camShift * 0.5f)
        } else if (clip.zoomAnim == ZoomAnimation.DOWN) {
            matrix.postTranslate(0f, camShift * 0.5f)
        }

        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun renderTransitionFrame(
        canvas: Canvas,
        bitmap1: Bitmap,
        clip1: MediaClip,
        progress1: Float,
        bitmap2: Bitmap,
        clip2: MediaClip,
        progress2: Float,
        transition: TransitionType,
        tProgress: Float, // 0.0 -> 1.0
        width: Int,
        height: Int,
        paint: Paint
    ) {
        val bmpOut1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c1 = Canvas(bmpOut1)
        renderSingleFrame(c1, bitmap1, clip1, progress1, width, height, paint)

        val bmpOut2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c2 = Canvas(bmpOut2)
        renderSingleFrame(c2, bitmap2, clip2, progress2, width, height, paint)

        when (transition) {
            TransitionType.FADE, TransitionType.DISSOLVE -> {
                // Draw clip 1 full
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)

                // Blend clip 2 with alpha tProgress
                val alphaPaint = Paint(paint).apply { alpha = (tProgress * 255).toInt().coerceIn(0, 255) }
                canvas.drawBitmap(bmpOut2, 0f, 0f, alphaPaint)
            }
            TransitionType.SLIDE_LEFT -> {
                val shiftX = width * tProgress
                canvas.drawBitmap(bmpOut1, -shiftX, 0f, paint)
                canvas.drawBitmap(bmpOut2, width - shiftX, 0f, paint)
            }
            TransitionType.SLIDE_RIGHT -> {
                val shiftX = width * tProgress
                canvas.drawBitmap(bmpOut1, shiftX, 0f, paint)
                canvas.drawBitmap(bmpOut2, -width + shiftX, 0f, paint)
            }
            TransitionType.SLIDE_UP -> {
                val shiftY = height * tProgress
                canvas.drawBitmap(bmpOut1, 0f, -shiftY, paint)
                canvas.drawBitmap(bmpOut2, 0f, height - shiftY, paint)
            }
            TransitionType.SLIDE_DOWN -> {
                val shiftY = height * tProgress
                canvas.drawBitmap(bmpOut1, 0f, shiftY, paint)
                canvas.drawBitmap(bmpOut2, 0f, -height + shiftY, paint)
            }
            TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val scale = if (transition == TransitionType.ZOOM_IN) tProgress else (1f - tProgress)
                val m = Matrix()
                m.postScale(scale, scale, width / 2f, height / 2f)
                val alphaPaint = Paint(paint).apply { alpha = (tProgress * 255).toInt().coerceIn(0, 255) }
                canvas.drawBitmap(bmpOut2, m, alphaPaint)
            }
            TransitionType.WIPE_LEFT -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val wipeRect = Rect(0, 0, (width * tProgress).toInt(), height)
                canvas.drawBitmap(bmpOut2, wipeRect, wipeRect, paint)
            }
            TransitionType.WIPE_RIGHT -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val startX = (width * (1f - tProgress)).toInt()
                val wipeRect = Rect(startX, 0, width, height)
                canvas.drawBitmap(bmpOut2, wipeRect, wipeRect, paint)
            }
            TransitionType.WIPE_UP -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val startY = (height * (1f - tProgress)).toInt()
                val wipeRect = Rect(0, startY, width, height)
                canvas.drawBitmap(bmpOut2, wipeRect, wipeRect, paint)
            }
            TransitionType.WIPE_DOWN -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val wipeRect = Rect(0, 0, width, (height * tProgress).toInt())
                canvas.drawBitmap(bmpOut2, wipeRect, wipeRect, paint)
            }
            TransitionType.PUSH_LEFT -> {
                val shiftX = width * tProgress
                canvas.drawBitmap(bmpOut1, -shiftX, 0f, paint)
                canvas.drawBitmap(bmpOut2, width - shiftX, 0f, paint)
            }
            TransitionType.PUSH_RIGHT -> {
                val shiftX = width * tProgress
                canvas.drawBitmap(bmpOut1, shiftX, 0f, paint)
                canvas.drawBitmap(bmpOut2, -width + shiftX, 0f, paint)
            }
            TransitionType.DIP_BLACK -> {
                if (tProgress < 0.5f) {
                    canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                    val blackPaint = Paint().apply { color = Color.BLACK; alpha = (tProgress * 2 * 255).toInt().coerceIn(0, 255) }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blackPaint)
                } else {
                    canvas.drawBitmap(bmpOut2, 0f, 0f, paint)
                    val blackPaint = Paint().apply { color = Color.BLACK; alpha = ((1f - tProgress) * 2 * 255).toInt().coerceIn(0, 255) }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blackPaint)
                }
            }
            TransitionType.DIP_WHITE -> {
                if (tProgress < 0.5f) {
                    canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                    val whitePaint = Paint().apply { color = Color.WHITE; alpha = (tProgress * 2 * 255).toInt().coerceIn(0, 255) }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), whitePaint)
                } else {
                    canvas.drawBitmap(bmpOut2, 0f, 0f, paint)
                    val whitePaint = Paint().apply { color = Color.WHITE; alpha = ((1f - tProgress) * 2 * 255).toInt().coerceIn(0, 255) }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), whitePaint)
                }
            }
            TransitionType.SPLIT_HORIZ -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val splitHeight = (height * tProgress / 2f).toInt()
                val topRect = Rect(0, 0, width, splitHeight)
                val botRect = Rect(0, height - splitHeight, width, height)
                canvas.drawBitmap(bmpOut2, topRect, topRect, paint)
                canvas.drawBitmap(bmpOut2, botRect, botRect, paint)
            }
            TransitionType.SPLIT_VERT -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val splitWidth = (width * tProgress / 2f).toInt()
                val leftRect = Rect(0, 0, splitWidth, height)
                val rightRect = Rect(width - splitWidth, 0, width, height)
                canvas.drawBitmap(bmpOut2, leftRect, leftRect, paint)
                canvas.drawBitmap(bmpOut2, rightRect, rightRect, paint)
            }
            TransitionType.CIRCLE_CROP -> {
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val maxRadius = Math.hypot(width.toDouble(), height.toDouble()).toFloat() / 2f
                val currentRadius = maxRadius * tProgress
                val path = Path().apply {
                    addCircle(width / 2f, height / 2f, currentRadius, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(bmpOut2, 0f, 0f, paint)
                canvas.restore()
            }
            else -> {
                // Default Cross Dissolve
                canvas.drawBitmap(bmpOut1, 0f, 0f, paint)
                val alphaPaint = Paint(paint).apply { alpha = (tProgress * 255).toInt().coerceIn(0, 255) }
                canvas.drawBitmap(bmpOut2, 0f, 0f, alphaPaint)
            }
        }
    }

    private fun drawBitmapToSurface(bitmap: Bitmap, surface: android.view.Surface) {
        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            surface.lockHardwareCanvas()
        } else {
            surface.lockCanvas(null)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        surface.unlockCanvasAndPost(canvas)
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (Int, Boolean) -> Unit
    ) {
        val TIMEOUT_USEC = 10000L
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        var encoderOutputBuffers = encoder.outputBuffers
        var trackIndex = -1
        var muxerStarted = false

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) throw RuntimeException("format changed twice")
                val newFormat = encoder.outputFormat
                trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                muxerStarted = true
                onMuxerStart(trackIndex, true)
            } else if (encoderStatus < 0) {
                // ignore
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    if (muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                }

                encoder.releaseOutputBuffer(encoderStatus, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun loadBitmapFromUri(uriStr: String, targetW: Int, targetH: Int): Bitmap {
        return try {
            val uri = Uri.parse(uriStr)
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val inW = options.outWidth
            val inH = options.outHeight
            var sampleSize = 1
            if (inH > targetH || inW > targetW) {
                val halfH = inH / 2
                val halfW = inW / 2
                while (halfH / sampleSize >= targetH && halfW / sampleSize >= targetW) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val stream2 = context.contentResolver.openInputStream(uri)
            val decoded = BitmapFactory.decodeStream(stream2, null, decodeOptions)
                ?: createPlaceholderBitmap(targetW, targetH, "Mídia")
            stream2?.close()
            decoded
        } catch (e: Exception) {
            createPlaceholderBitmap(targetW, targetH, "Foto")
        }
    }

    private fun createPlaceholderBitmap(w: Int, h: Int, title: String): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = (h * 0.08f)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(title, w / 2f, h / 2f, paint)
        return bitmap
    }

    private fun saveToPublicGallery(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EditorDeVideo")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRendererEngine", "Failed to save to public gallery", e)
        }
    }
}

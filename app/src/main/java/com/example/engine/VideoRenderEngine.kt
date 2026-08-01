package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.db.MediaItemEntity
import com.example.data.model.CameraAnimation
import com.example.data.model.ExportOptions
import com.example.data.model.TransitionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class VideoRenderEngine(private val context: Context) {

    suspend fun renderVideo(
        mediaItems: List<MediaItemEntity>,
        activeTransitions: List<TransitionType>,
        transitionDurationSec: Float,
        narrationAudioFile: File?,
        exportOptions: ExportOptions,
        onProgressUpdate: (progressPercent: Int, statusText: String, logMessage: String) -> Unit
    ): Pair<File, String> = withContext(Dispatchers.IO) {

        val width = exportOptions.resolution.width
        val height = exportOptions.resolution.height
        val fps = exportOptions.fps.fps
        val bitrate = exportOptions.quality.bitrate

        log(onProgressUpdate, 0, "Iniciando pipeline de renderização...", "Parâmetros: ${width}x${height} @ ${fps}fps (${exportOptions.quality.label})")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputRawVideoFile = File(context.cacheDir, "temp_video_$timestamp.mp4")
        if (outputRawVideoFile.exists()) outputRawVideoFile.delete()

        // 1. Preload Bitmaps for images and video frames
        log(onProgressUpdate, 10, "Carregando ${mediaItems.size} mídias...", "Decodificando imagens e quadros de vídeo")
        val loadedBitmaps = mutableListOf<Bitmap>()
        for ((idx, item) in mediaItems.withIndex()) {
            val bmp = loadScaledBitmap(item.uri, width, height)
            loadedBitmaps.add(bmp)
            val p = 10 + ((idx + 1) * 20 / max(1, mediaItems.size))
            log(onProgressUpdate, p, "Carregando mídia ${idx + 1}/${mediaItems.size}", "Carregado Uri: ${item.uri.takeLast(25)}")
        }

        // 2. Prepare Codec & Canvas
        log(onProgressUpdate, 35, "Configurando Codec H.264...", "Inicializando Surface e MediaFormat")

        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val outputMuxerFile = File(context.cacheDir, "CineCut_$timestamp.mp4")
        val muxer = MediaMuxer(outputMuxerFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var isMuxerStarted = false

        // Draw Surface Canvas setup
        val surfaceCanvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            inputSurface.lockHardwareCanvas()
        } else {
            inputSurface.lockCanvas(null)
        }
        val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Calculate total frames
        val totalClipFramesList = mediaItems.map { max(fps, (it.durationSeconds * fps).toInt()) }
        val totalFramesSum = totalClipFramesList.sum()
        var currentGlobalFrame = 0

        val activeTransitionPool = if (activeTransitions.isEmpty()) listOf(TransitionType.CROSS_DISSOLVE) else activeTransitions

        log(onProgressUpdate, 45, "Iniciando renderização de quadros...", "Total de quadros a processar: $totalFramesSum")

        val bufferInfo = MediaCodec.BufferInfo()

        for (clipIndex in mediaItems.indices) {
            val currentItem = mediaItems[clipIndex]
            val currentBmp = loadedBitmaps[clipIndex]
            val anim = CameraAnimation.fromId(currentItem.animationType)
            val clipTotalFrames = totalClipFramesList[clipIndex]

            val nextBmp = if (clipIndex < mediaItems.size - 1) loadedBitmaps[clipIndex + 1] else null
            val chosenTransition = activeTransitionPool[clipIndex % activeTransitionPool.size]
            val transitionFrames = max(0, (transitionDurationSec * fps).toInt())

            for (frameInClip in 0 until clipTotalFrames) {
                val progressInClip = frameInClip.toFloat() / max(1, clipTotalFrames - 1)

                // Clear canvas with deep black
                canvas.drawColor(Color.BLACK)

                // Render current clip frame with Pan/Zoom animation
                drawAnimatedBitmap(canvas, currentBmp, anim, progressInClip, width, height, paint)

                // Handle transition overlay if near end of current clip
                if (nextBmp != null && frameInClip >= (clipTotalFrames - transitionFrames) && transitionFrames > 0) {
                    val transitionProgress = (frameInClip - (clipTotalFrames - transitionFrames)).toFloat() / transitionFrames.toFloat()
                    val nextAnim = CameraAnimation.fromId(mediaItems[clipIndex + 1].animationType)

                    drawTransition(
                        canvas = canvas,
                        fromBitmap = currentBmp,
                        toBitmap = nextBmp,
                        fromAnim = anim,
                        toAnim = nextAnim,
                        clipProgress = progressInClip,
                        transitionProgress = transitionProgress,
                        transitionType = chosenTransition,
                        width = width,
                        height = height,
                        paint = paint
                    )
                }

                // Render Canvas onto Codec surface
                try {
                    val hwCanvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        inputSurface.lockHardwareCanvas()
                    } else {
                        inputSurface.lockCanvas(null)
                    }
                    hwCanvas.drawBitmap(canvasBitmap, 0f, 0f, paint)
                    inputSurface.unlockCanvasAndPost(hwCanvas)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Drain Codec Output
                var encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 0)
                while (encoderStatus >= 0) {
                    val encodedData = codec.getOutputBuffer(encoderStatus)
                    if (encodedData != null && bufferInfo.size > 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0) {
                            if (!isMuxerStarted) {
                                val newFormat = codec.outputFormat
                                videoTrackIndex = muxer.addTrack(newFormat)
                                muxer.start()
                                isMuxerStarted = true
                            }
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(encoderStatus, false)
                    encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                currentGlobalFrame++
                val renderPercent = 45 + (currentGlobalFrame * 45 / max(1, totalFramesSum))
                if (currentGlobalFrame % max(1, fps / 2) == 0) {
                    log(onProgressUpdate, renderPercent, "Renderizando clipe ${clipIndex + 1}/${mediaItems.size} (${anim.label})", "Quadro $currentGlobalFrame / $totalFramesSum")
                }
            }
        }

        // Signal End of Stream
        codec.signalEndOfInputStream()

        var isDone = false
        while (!isDone) {
            val status = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (status >= 0) {
                val encodedData = codec.getOutputBuffer(status)
                if (encodedData != null && bufferInfo.size > 0 && isMuxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                }
                codec.releaseOutputBuffer(status, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isDone = true
                }
            } else if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            }
        }

        codec.stop()
        codec.release()

        // 3. Audio Narration Multiplexing
        if (narrationAudioFile != null && narrationAudioFile.exists() && narrationAudioFile.length() > 44) {
            log(onProgressUpdate, 92, "Sincronizando áudio de narração...", "Adicionando trilha de áudio ao MP4")
            try {
                val audioExtractor = MediaExtractor()
                audioExtractor.setDataSource(narrationAudioFile.absolutePath)
                var audioTrackIdx = -1
                for (i in 0 until audioExtractor.trackCount) {
                    val format = audioExtractor.getTrackFormat(i)
                    if ((format.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                        audioTrackIdx = i
                        val muxerAudioTrack = muxer.addTrack(format)
                        audioExtractor.selectTrack(audioTrackIdx)

                        val audioBuf = ByteBuffer.allocate(256 * 1024)
                        val audioBufferInfo = MediaCodec.BufferInfo()

                        while (true) {
                            val sampleSize = audioExtractor.readSampleData(audioBuf, 0)
                            if (sampleSize < 0) break
                            audioBufferInfo.offset = 0
                            audioBufferInfo.size = sampleSize
                            audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                            audioBufferInfo.flags = audioExtractor.sampleFlags
                            muxer.writeSampleData(muxerAudioTrack, audioBuf, audioBufferInfo)
                            audioExtractor.advance()
                        }
                        break
                    }
                }
                audioExtractor.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isMuxerStarted) {
            try {
                muxer.stop()
                muxer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Recycle bitmaps
        loadedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        if (!canvasBitmap.isRecycled) canvasBitmap.recycle()

        // 4. Save to MediaStore (Public Gallery)
        log(onProgressUpdate, 98, "Salvando vídeo na galeria pública...", "Exportando para o armazenamento do dispositivo")
        val savedPublicUri = saveVideoToMediaStore(context, outputMuxerFile, timestamp)

        log(onProgressUpdate, 100, "Edição concluída com sucesso!", "Vídeo salvo: CineCut_$timestamp.mp4")

        Pair(outputMuxerFile, savedPublicUri)
    }

    private fun drawAnimatedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        anim: CameraAnimation,
        progress: Float,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        val matrix = Matrix()

        // Scale bitmap to fit/fill canvas area with extra margin for panning
        val scaleX = width.toFloat() / bitmap.width.toFloat()
        val scaleY = height.toFloat() / bitmap.height.toFloat()
        val baseScale = max(scaleX, scaleY) * 1.15f // 15% extra headroom for smooth pan/zoom

        val scaledW = bitmap.width * baseScale
        val scaledH = bitmap.height * baseScale

        val cx = width / 2f
        val cy = height / 2f

        var dx = (width - scaledW) / 2f
        var dy = (height - scaledH) / 2f
        var zoomFactor = 1.0f

        val panOffset = 50f * progress // 50px pan travel distance

        when (anim) {
            CameraAnimation.NONE -> {}
            CameraAnimation.PAN_RIGHT -> dx -= panOffset
            CameraAnimation.PAN_LEFT -> dx += panOffset
            CameraAnimation.PAN_UP -> dy += panOffset
            CameraAnimation.PAN_DOWN -> dy -= panOffset
            CameraAnimation.ZOOM_IN -> zoomFactor = 1.0f + (0.18f * progress)
            CameraAnimation.ZOOM_OUT -> zoomFactor = 1.18f - (0.18f * progress)
            CameraAnimation.DIAGONAL_TOP_LEFT -> {
                dx += panOffset
                dy += panOffset
            }
            CameraAnimation.DIAGONAL_TOP_RIGHT -> {
                dx -= panOffset
                dy += panOffset
            }
            CameraAnimation.DIAGONAL_BOTTOM_LEFT -> {
                dx += panOffset
                dy -= panOffset
            }
            CameraAnimation.DIAGONAL_BOTTOM_RIGHT -> {
                dx -= panOffset
                dy -= panOffset
            }
        }

        matrix.postScale(baseScale * zoomFactor, baseScale * zoomFactor)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawTransition(
        canvas: Canvas,
        fromBitmap: Bitmap,
        toBitmap: Bitmap,
        fromAnim: CameraAnimation,
        toAnim: CameraAnimation,
        clipProgress: Float,
        transitionProgress: Float,
        transitionType: TransitionType,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        val alpha = transitionProgress.coerceIn(0f, 1f)

        when (transitionType) {
            TransitionType.FADE, TransitionType.CROSS_DISSOLVE -> {
                paint.alpha = (alpha * 255).toInt()
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                paint.alpha = 255
            }
            TransitionType.SLIDE_LEFT -> {
                canvas.save()
                canvas.translate(-width * alpha, 0f)
                drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                canvas.restore()

                canvas.save()
                canvas.translate(width * (1f - alpha), 0f)
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                canvas.restore()
            }
            TransitionType.SLIDE_RIGHT -> {
                canvas.save()
                canvas.translate(width * alpha, 0f)
                drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                canvas.restore()

                canvas.save()
                canvas.translate(-width * (1f - alpha), 0f)
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                canvas.restore()
            }
            TransitionType.SLIDE_UP -> {
                canvas.save()
                canvas.translate(0f, -height * alpha)
                drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                canvas.restore()

                canvas.save()
                canvas.translate(0f, height * (1f - alpha))
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                canvas.restore()
            }
            TransitionType.SLIDE_DOWN -> {
                canvas.save()
                canvas.translate(0f, height * alpha)
                drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                canvas.restore()

                canvas.save()
                canvas.translate(0f, -height * (1f - alpha))
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                canvas.restore()
            }
            TransitionType.BLACK_FADE, TransitionType.DIP_BLACK -> {
                if (alpha < 0.5f) {
                    paint.alpha = ((1f - alpha * 2f) * 255).toInt()
                    drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                } else {
                    paint.alpha = (((alpha - 0.5f) * 2f) * 255).toInt()
                    drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                }
                paint.alpha = 255
            }
            TransitionType.WHITE_FADE -> {
                if (alpha < 0.5f) {
                    drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                    canvas.drawColor(Color.argb((alpha * 2f * 255).toInt(), 255, 255, 255))
                } else {
                    drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                    canvas.drawColor(Color.argb(((1f - (alpha - 0.5f) * 2f) * 255).toInt(), 255, 255, 255))
                }
            }
            TransitionType.CIRCLE_CROP -> {
                drawAnimatedBitmap(canvas, fromBitmap, fromAnim, clipProgress, width, height, paint)
                val maxRadius = Math.hypot(width.toDouble(), height.toDouble()).toFloat()
                val radius = maxRadius * alpha
                val path = Path().apply {
                    addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(path)
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                canvas.restore()
            }
            else -> {
                // Default smooth cross dissolve for other transition styles
                paint.alpha = (alpha * 255).toInt()
                drawAnimatedBitmap(canvas, toBitmap, toAnim, transitionProgress, width, height, paint)
                paint.alpha = 255
            }
        }
    }

    private fun loadScaledBitmap(uriString: String, targetW: Int, targetH: Int): Bitmap {
        return try {
            val uri = Uri.parse(uriString)
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true || uriString.endsWith(".mp4") || uriString.endsWith(".mkv")

            if (isVideo) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame?.let { scaleBitmap(it, targetW, targetH) } ?: createPlaceholderBitmap(targetW, targetH, "Vídeo Clipe")
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream)
                    scaleBitmap(original, targetW, targetH)
                } ?: createPlaceholderBitmap(targetW, targetH, "Imagem Clipe")
            }
        } catch (e: Exception) {
            createPlaceholderBitmap(targetW, targetH, "Mídia $targetW")
        }
    }

    private fun scaleBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        if (scaled != src) src.recycle()
        return scaled
    }

    private fun createPlaceholderBitmap(w: Int, h: Int, text: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, w / 2f, h / 2f, paint)
        return bmp
    }

    private fun saveVideoToMediaStore(context: Context, sourceFile: File, timestamp: String): String {
        val fileName = "CineCut_$timestamp.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CineCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri.toString()
        }
        return sourceFile.absolutePath
    }

    private fun log(
        onProgressUpdate: (Int, String, String) -> Unit,
        percent: Int,
        status: String,
        details: String
    ) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        onProgressUpdate(percent, status, "[$time] $status - $details")
    }
}

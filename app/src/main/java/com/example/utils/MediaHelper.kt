package com.example.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.data.models.CameraMotion
import com.example.data.models.MediaItem
import com.example.data.models.MediaType
import com.example.data.models.MotionAnimation
import java.io.File
import java.io.FileOutputStream

object MediaHelper {

    fun processFileUris(context: Context, uris: List<Uri>): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        uris.forEach { uri ->
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if URI doesn't support persistable permissions
                }

                val item = createMediaItemFromUri(context, uri)
                if (item != null) {
                    mediaItems.add(item)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return mediaItems
    }

    fun processFolderUri(context: Context, treeUri: Uri): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val rootFolder = DocumentFile.fromTreeUri(context, treeUri)
            if (rootFolder != null && rootFolder.exists() && rootFolder.isDirectory) {
                val files = rootFolder.listFiles()
                val sortedFiles = files.sortedBy { it.name ?: "" }
                for (doc in sortedFiles) {
                    if (doc.isFile) {
                        val type = doc.type ?: ""
                        val name = doc.name ?: ""
                        val isImage = type.startsWith("image/") || isImageExtension(name)
                        val isVideo = type.startsWith("video/") || isVideoExtension(name)

                        if (isImage || isVideo) {
                            val item = createMediaItemFromUri(context, doc.uri, doc.name)
                            if (item != null) {
                                mediaItems.add(item)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mediaItems
    }

    private fun createMediaItemFromUri(context: Context, uri: Uri, fallbackName: String? = null): MediaItem? {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(context, uri) ?: fallbackName ?: "Mídia"

        val isVideo = mimeType.startsWith("video/") || isVideoExtension(fileName)
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE
        var durationMs = 3000L
        var mediaUri = uri.toString()

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(1000L) ?: 5000L
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { retriever.release() } catch (e: Exception) { e.printStackTrace() }
            }
        }

        return MediaItem(
            uri = mediaUri,
            type = mediaType,
            title = fileName,
            durationMs = durationMs,
            motionAnimation = MotionAnimation.NONE,
            cameraMotion = CameraMotion.NONE,
            transitionOverride = null
        )
    }

    private fun isImageExtension(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif")
    }

    private fun isVideoExtension(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".3gp") || lower.endsWith(".avi")
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return it.getString(index)
                    }
                }
            }
        }
        return uri.lastPathSegment
    }
}

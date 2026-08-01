package com.example.data.local

import com.example.data.models.AudioTrack
import com.example.data.models.CameraMotion
import com.example.data.models.MediaItem
import com.example.data.models.MediaType
import com.example.data.models.MotionAnimation
import org.json.JSONArray
import org.json.JSONObject

object JsonUtil {

    fun serializeMediaItems(items: List<MediaItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("uri", item.uri)
                put("type", item.type.name)
                put("title", item.title)
                put("durationMs", item.durationMs)
                put("motionAnimation", item.motionAnimation.name)
                put("motionDurationMs", item.motionDurationMs)
                put("cameraMotion", item.cameraMotion.name)
                put("transitionOverride", item.transitionOverride ?: "")
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeMediaItems(jsonStr: String): List<MediaItem> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<MediaItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val transition = obj.optString("transitionOverride", "").ifEmpty { null }
                list.add(
                    MediaItem(
                        id = obj.optString("id"),
                        uri = obj.optString("uri"),
                        type = try { MediaType.valueOf(obj.optString("type", "IMAGE")) } catch (e: Exception) { MediaType.IMAGE },
                        title = obj.optString("title", "Mídia"),
                        durationMs = obj.optLong("durationMs", 3000L),
                        motionAnimation = try { MotionAnimation.valueOf(obj.optString("motionAnimation", "NONE")) } catch (e: Exception) { MotionAnimation.NONE },
                        motionDurationMs = obj.optLong("motionDurationMs", 1500L),
                        cameraMotion = try { CameraMotion.valueOf(obj.optString("cameraMotion", "NONE")) } catch (e: Exception) { CameraMotion.NONE },
                        transitionOverride = transition
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializeAudioTracks(tracks: List<AudioTrack>): String {
        val array = JSONArray()
        for (track in tracks) {
            val obj = JSONObject().apply {
                put("id", track.id)
                put("uri", track.uri)
                put("name", track.name)
                put("durationMs", track.durationMs)
                put("volume", track.volume.toDouble())
                val pauseArray = JSONArray()
                track.detectedPausesMs.forEach { pauseArray.put(it) }
                put("detectedPausesMs", pauseArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeAudioTracks(jsonStr: String): List<AudioTrack> {
        if (jsonStr.isBlank()) return emptyList()
        val list = mutableListOf<AudioTrack>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pauseArray = obj.optJSONArray("detectedPausesMs")
                val pauses = mutableListOf<Long>()
                if (pauseArray != null) {
                    for (j in 0 until pauseArray.length()) {
                        pauses.add(pauseArray.getLong(j))
                    }
                }
                list.add(
                    AudioTrack(
                        id = obj.optString("id"),
                        uri = obj.optString("uri"),
                        name = obj.optString("name", "Áudio"),
                        durationMs = obj.optLong("durationMs", 10000L),
                        volume = obj.optDouble("volume", 1.0).toFloat(),
                        detectedPausesMs = pauses
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun serializeTransitions(transitions: List<String>): String {
        val array = JSONArray()
        transitions.forEach { array.put(it) }
        return array.toString()
    }

    fun deserializeTransitions(jsonStr: String): List<String> {
        if (jsonStr.isBlank()) return listOf("CROSSFADE")
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            list.add("CROSSFADE")
        }
        return if (list.isEmpty()) listOf("CROSSFADE") else list
    }
}

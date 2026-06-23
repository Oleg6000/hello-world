package com.musicvisualizer

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

data class TrackInfo(
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean
)

class MediaSessionHelper(private val context: Context) {

    private val sessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    fun getCurrentTrack(): TrackInfo? {
        return try {
            val listenerComponent = ComponentName(context, NotificationListenerService::class.java)
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            val ytMusic = controllers.firstOrNull { it.packageName == "com.google.android.apps.youtube.music" }
                ?: controllers.firstOrNull()
            ytMusic?.let { controller ->
                val meta = controller.metadata
                val state = controller.playbackState
                TrackInfo(
                    title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
                    artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
                    isPlaying = state?.state == PlaybackState.STATE_PLAYING
                )
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun getActiveAudioSessionId(): Int {
        return try {
            val listenerComponent = ComponentName(context, NotificationListenerService::class.java)
            val controllers: List<MediaController> = sessionManager.getActiveSessions(listenerComponent)
            val ytMusic = controllers.firstOrNull { it.packageName == "com.google.android.apps.youtube.music" }
                ?: controllers.firstOrNull()
            ytMusic?.audioSessionId ?: 0
        } catch (e: Exception) {
            0
        }
    }
}

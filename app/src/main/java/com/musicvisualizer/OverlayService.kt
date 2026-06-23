package com.musicvisualizer

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val EXTRA_THEME = "theme"
        const val EXTRA_OPACITY = "opacity"
        const val ACTION_UPDATE_THEME = "com.musicvisualizer.UPDATE_THEME"
        const val ACTION_UPDATE_OPACITY = "com.musicvisualizer.UPDATE_OPACITY"
        const val CHANNEL_ID = "MusicVisualizerChannel"
        const val NOTIF_ID = 1
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var visualizerView: MusicVisualizerView
    private lateinit var audioAnalyzer: AudioAnalyzer
    private lateinit var mediaSessionHelper: MediaSessionHelper
    private var trackInfoText: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioAnalyzer = AudioAnalyzer()
        mediaSessionHelper = MediaSessionHelper(this)

        buildOverlay()
        startVisualization()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_UPDATE_THEME -> {
                    val themeName = it.getStringExtra(EXTRA_THEME) ?: return@let
                    val theme = VisualizationTheme.valueOf(themeName)
                    visualizerView.theme = theme
                }
                ACTION_UPDATE_OPACITY -> {
                    val opacity = it.getFloatExtra(EXTRA_OPACITY, 0.85f)
                    overlayView.alpha = opacity
                }
                else -> {
                    val themeName = it.getStringExtra(EXTRA_THEME)
                    if (themeName != null) {
                        visualizerView.theme = VisualizationTheme.valueOf(themeName)
                    }
                    val opacity = it.getFloatExtra(EXTRA_OPACITY, 0.85f)
                    overlayView.alpha = opacity
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_visualizer, null)
        visualizerView = overlayView.findViewById(R.id.visualizerView)
        trackInfoText = overlayView.findViewById(R.id.trackInfoText)

        val closeBtn = overlayView.findViewById<ImageButton>(R.id.btnClose)
        closeBtn.setOnClickListener { stopSelf() }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Allow touch on close button but pass through otherwise
        setupTouchPassthrough(overlayView, closeBtn)

        windowManager.addView(overlayView, params)
    }

    private fun setupTouchPassthrough(rootView: View, closeBtn: ImageButton) {
        rootView.setOnTouchListener { v, event ->
            // Only consume touch if on the close button area
            false
        }
    }

    private fun startVisualization() {
        val sessionId = mediaSessionHelper.getActiveAudioSessionId()
        if (sessionId != 0) {
            audioAnalyzer.startWithAudioSession(sessionId)
        } else {
            audioAnalyzer.startMicCapture()
        }

        audioAnalyzer.onDataUpdate = {
            visualizerView.post {
                visualizerView.waveform = audioAnalyzer.waveform
                visualizerView.magnitude = audioAnalyzer.magnitude
                visualizerView.beatEnergy = audioAnalyzer.beatEnergy
                visualizerView.bassEnergy = audioAnalyzer.bassEnergy
                visualizerView.midEnergy = audioAnalyzer.midEnergy
                visualizerView.trebleEnergy = audioAnalyzer.trebleEnergy
                updateTrackInfo()
            }
        }
    }

    private fun updateTrackInfo() {
        val track = mediaSessionHelper.getCurrentTrack()
        trackInfoText?.text = if (track != null && (track.title != null || track.artist != null)) {
            val playing = if (track.isPlaying) "▶ " else "⏸ "
            "$playing${track.title ?: "Unknown"} — ${track.artist ?: ""}"
        } else {
            ""
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Visualizer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Running music visualizer overlay"
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP" }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Visualizer Active")
            .setContentText("Tap to open settings")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        audioAnalyzer.release()
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
    }
}

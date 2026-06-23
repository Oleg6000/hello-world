package com.musicvisualizer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private lateinit var spinnerTheme: Spinner
    private lateinit var sliderOpacity: SeekBar
    private lateinit var tvOpacityValue: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnPermissions: Button
    private lateinit var statusIndicator: TextView
    private lateinit var previewVisualizer: MusicVisualizerView

    private var selectedTheme = VisualizationTheme.AUTO
    private var overlayOpacity = 0.85f

    private val previewAnalyzer = AudioAnalyzer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerTheme      = findViewById(R.id.spinnerTheme)
        sliderOpacity     = findViewById(R.id.sliderOpacity)
        tvOpacityValue    = findViewById(R.id.tvOpacityValue)
        btnToggle         = findViewById(R.id.btnToggleOverlay)
        btnPermissions    = findViewById(R.id.btnPermissions)
        statusIndicator   = findViewById(R.id.statusIndicator)
        previewVisualizer = findViewById(R.id.previewVisualizer)

        setupThemeSpinner()
        setupOpacitySlider()

        btnToggle.setOnClickListener {
            if (OverlayService.isRunning) stopOverlay() else checkPermissionsAndStart()
        }
        btnPermissions.setOnClickListener { showPermissionsDialog() }

        // Start synthetic audio for the preview
        previewAnalyzer.startSyntheticModePublic()
        previewAnalyzer.onDataUpdate = {
            previewVisualizer.post {
                previewVisualizer.waveform    = previewAnalyzer.waveform
                previewVisualizer.magnitude   = previewAnalyzer.magnitude
                previewVisualizer.beatEnergy  = previewAnalyzer.beatEnergy
                previewVisualizer.bassEnergy  = previewAnalyzer.bassEnergy
                previewVisualizer.midEnergy   = previewAnalyzer.midEnergy
                previewVisualizer.trebleEnergy = previewAnalyzer.trebleEnergy
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateToggleButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        previewAnalyzer.release()
    }

    private fun setupThemeSpinner() {
        val themes = VisualizationTheme.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTheme.adapter = adapter
        spinnerTheme.setSelection(VisualizationTheme.values().indexOf(VisualizationTheme.AUTO))

        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedTheme = VisualizationTheme.values()[pos]
                previewVisualizer.theme = selectedTheme
                if (OverlayService.isRunning) sendThemeUpdate()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun setupOpacitySlider() {
        sliderOpacity.max = 80 // 20..100 → 0..80 offset by 20
        sliderOpacity.progress = 65 // 85% - 20 = 65
        tvOpacityValue.text = "85%"

        sliderOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                overlayOpacity = (progress + 20) / 100f
                tvOpacityValue.text = "${progress + 20}%"
                if (OverlayService.isRunning) sendOpacityUpdate()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun updateToggleButton() {
        if (OverlayService.isRunning) {
            btnToggle.text = "Stop Visualizer"
            btnToggle.setBackgroundColor(0xFFB71C1C.toInt())
            statusIndicator.text = "● Running"
            statusIndicator.setTextColor(0xFF00E676.toInt())
        } else {
            btnToggle.text = "Start Visualizer"
            btnToggle.setBackgroundColor(0xFF2E7D32.toInt())
            statusIndicator.text = "● Stopped"
            statusIndicator.setTextColor(0xFF9E9E9E.toInt())
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("Grant 'Display over other apps' permission so the visualizer appears above YouTube Music.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), REQUEST_NOTIF)
            return
        }
        startOverlay()
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, results: IntArray) {
        if (Settings.canDrawOverlays(this)) startOverlay()
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_THEME, selectedTheme.name)
            putExtra(OverlayService.EXTRA_OPACITY, overlayOpacity)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateToggleButton()
        Toast.makeText(this, "Visualizer started — open YouTube Music!", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        updateToggleButton()
    }

    private fun sendThemeUpdate() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_THEME
            putExtra(OverlayService.EXTRA_THEME, selectedTheme.name)
        })
    }

    private fun sendOpacityUpdate() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_OPACITY
            putExtra(OverlayService.EXTRA_OPACITY, overlayOpacity)
        })
    }

    private fun showPermissionsDialog() {
        val overlay = Settings.canDrawOverlays(this)
        val audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val msg = buildString {
            append("Display over other apps: ${if (overlay) "✓ Granted" else "✗ REQUIRED"}\n")
            append("Microphone: ${if (audio) "✓ Granted" else "✗ Optional (demo mode)"}\n\n")
            append("For track info: Settings → Apps → Special app access → Notification access → enable Music Imagined")
        }
        AlertDialog.Builder(this)
            .setTitle("Permissions")
            .setMessage(msg)
            .setPositiveButton("App Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
            .setNeutralButton("Close", null)
            .show()
    }

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_NOTIF = 101
    }
}

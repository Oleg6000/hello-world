package com.musicvisualizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.musicvisualizer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var selectedTheme = VisualizationTheme.AUTO
    private var overlayOpacity = 0.85f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupThemeSpinner()
        setupOpacitySlider()
        setupButtons()
        updateToggleButton()
    }

    override fun onResume() {
        super.onResume()
        updateToggleButton()
    }

    private fun setupThemeSpinner() {
        val themes = VisualizationTheme.entries.map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = adapter
        binding.spinnerTheme.setSelection(VisualizationTheme.entries.indexOf(VisualizationTheme.AUTO))

        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                selectedTheme = VisualizationTheme.entries[position]
                if (OverlayService.isRunning) {
                    sendThemeUpdate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupOpacitySlider() {
        binding.sliderOpacity.value = overlayOpacity * 100f
        binding.tvOpacityValue.text = "${(overlayOpacity * 100).toInt()}%"

        binding.sliderOpacity.addOnChangeListener { _, value, _ ->
            overlayOpacity = value / 100f
            binding.tvOpacityValue.text = "${value.toInt()}%"
            if (OverlayService.isRunning) {
                sendOpacityUpdate()
            }
        }
    }

    private fun setupButtons() {
        binding.btnToggleOverlay.setOnClickListener {
            if (OverlayService.isRunning) {
                stopOverlay()
            } else {
                checkPermissionsAndStart()
            }
        }

        binding.btnPermissions.setOnClickListener {
            showPermissionsDialog()
        }
    }

    private fun updateToggleButton() {
        if (OverlayService.isRunning) {
            binding.btnToggleOverlay.text = "Stop Visualizer"
            binding.btnToggleOverlay.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            binding.statusIndicator.text = "Status: Running"
            binding.statusIndicator.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            )
        } else {
            binding.btnToggleOverlay.text = "Start Visualizer"
            binding.btnToggleOverlay.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            binding.statusIndicator.text = "Status: Stopped"
            binding.statusIndicator.setTextColor(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("Please grant 'Display over other apps' permission so the visualizer can appear above YouTube Music.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
            return
        }

        startOverlay()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO, REQUEST_NOTIFICATIONS -> {
                // Proceed regardless — audio will fall back to synthetic mode if denied
                if (Settings.canDrawOverlays(this)) {
                    startOverlay()
                }
            }
        }
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_THEME, selectedTheme.name)
            putExtra(OverlayService.EXTRA_OPACITY, overlayOpacity)
        }
        ContextCompat.startForegroundService(this, intent)
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
        val overlayGranted = Settings.canDrawOverlays(this)
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        val msg = buildString {
            append("Display over other apps: ${if (overlayGranted) "✓ Granted" else "✗ Required"}\n")
            append("Microphone (audio): ${if (audioGranted) "✓ Granted" else "✗ Optional (falls back to demo)"}\n")
            append("Notifications: ${if (notifGranted) "✓ Granted" else "✗ Recommended"}\n\n")
            append("Also enable Notification Access in Settings → Apps → Special app access → Notification access for track info display.")
        }

        AlertDialog.Builder(this)
            .setTitle("Permissions Status")
            .setMessage(msg)
            .setPositiveButton("Open App Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNeutralButton("Close", null)
            .show()
    }

    companion object {
        private const val REQUEST_AUDIO = 100
        private const val REQUEST_NOTIFICATIONS = 101
    }
}

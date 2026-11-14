package com.example.rtspcamera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var urlText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var resolution: Spinner
    private lateinit var fps: Spinner
    private lateinit var bitrate: Spinner
    private lateinit var camera: Spinner
    private lateinit var audio: SwitchMaterial
    private var streaming = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cameraGranted = grants[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) {
                if (audio.isChecked &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ) {
                    audio.isChecked = false
                    Toast.makeText(this, "Micrófono desactivado porque no se concedió el permiso.", Toast.LENGTH_LONG).show()
                }
                startStreaming()
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara para transmitir.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        urlText = findViewById(R.id.urlText)
        startButton = findViewById(R.id.startButton)
        resolution = findViewById(R.id.resolutionSpinner)
        fps = findViewById(R.id.fpsSpinner)
        bitrate = findViewById(R.id.bitrateSpinner)
        camera = findViewById(R.id.cameraSpinner)
        audio = findViewById(R.id.audioSwitch)

        setupSpinner(resolution, listOf("640x480", "854x480", "1280x720", "1920x1080"))
        setupSpinner(fps, listOf("10", "15", "20", "25", "30", "60"))
        setupSpinner(bitrate, listOf("500 kbps", "800 kbps", "1200 kbps", "2000 kbps", "3000 kbps", "5000 kbps"))
        setupSpinner(camera, listOf("Trasera", "Delantera"))

        startButton.setOnClickListener {
            if (streaming) stopStreaming() else requestPermissionsAndStart()
        }
        findViewById<MaterialButton>(R.id.copyButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Dirección RTSP", urlText.text))
            Toast.makeText(this, "Dirección copiada", Toast.LENGTH_SHORT).show()
        }
        refreshAddress()
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (audio.isChecked) permissions.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startStreaming() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startStreaming() {
        val dims = resolution.selectedItem.toString().split("x")
        val selectedFps = fps.selectedItem.toString().toInt()
        val bitrateBps = bitrate.selectedItem.toString().substringBefore(" ").toInt() * 1000
        val intent = Intent(this, CameraStreamService::class.java).apply {
            action = CameraStreamService.ACTION_START
            putExtra(CameraStreamService.EXTRA_WIDTH, dims[0].toInt())
            putExtra(CameraStreamService.EXTRA_HEIGHT, dims[1].toInt())
            putExtra(CameraStreamService.EXTRA_FPS, selectedFps)
            putExtra(CameraStreamService.EXTRA_BITRATE, bitrateBps)
            putExtra(CameraStreamService.EXTRA_FRONT, camera.selectedItemPosition == 1)
            putExtra(CameraStreamService.EXTRA_AUDIO, audio.isChecked)
        }
        ContextCompat.startForegroundService(this, intent)
        streaming = true
        statusText.text = "Estado: iniciando servidor RTSP…"
        startButton.text = "DETENER TRANSMISIÓN"
        refreshAddress()
    }

    private fun stopStreaming() {
        val intent = Intent(this, CameraStreamService::class.java).apply {
            action = CameraStreamService.ACTION_STOP
        }
        startService(intent)
        streaming = false
        statusText.text = "Estado: detenido"
        startButton.text = "INICIAR TRANSMISIÓN"
    }

    private fun refreshAddress() {
        val ip = getLocalIpAddress() ?: "IP-NO-DISPONIBLE"
        urlText.text = "rtsp://$ip:8554/live"
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) return address.hostAddress
                }
            }
        } catch (_: Exception) { }
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val raw = wifi.connectionInfo.ipAddress
            if (raw == 0) null else "${raw and 0xff}.${raw shr 8 and 0xff}.${raw shr 16 and 0xff}.${raw shr 24 and 0xff}"
        } catch (_: Exception) { null }
    }

    override fun onResume() {
        super.onResume()
        refreshAddress()
    }
}

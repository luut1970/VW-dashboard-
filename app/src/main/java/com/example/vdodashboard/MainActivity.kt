package com.example.vdodashboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var liveKph by mutableStateOf(0f)
    private var liveRpm by mutableStateOf(0f)
    private var liveFuel by mutableStateOf(0.5f)
    private var liveTemp by mutableStateOf(70f)
    private var liveBlinker by mutableStateOf(false)
    private var liveOel by mutableStateOf(false)
    private var liveLadung by mutableStateOf(false)
    private var liveFernlicht by mutableStateOf(false)
    private var totalKm by mutableStateOf(0f)

    private val prefs by lazy { getSharedPreferences("vdo_dashboard", MODE_PRIVATE) }

    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private val locationListener = LocationListener { location: Location ->
        // location.speed komt van de GPS-chip in m/s; alleen betrouwbaar buiten en in beweging
        liveKph = if (location.hasSpeed()) location.speed * 3.6f else liveKph

        val previous = lastLocation
        if (previous != null) {
            val deltaMeters = previous.distanceTo(location)
            // Ruisdrempel: alleen optellen als de verplaatsing groter is dan de GPS-nauwkeurigheid,
            // anders telt de kilometerteller door terwijl de auto stilstaat (GPS-drift).
            val accuracyThreshold = if (location.hasAccuracy()) location.accuracy else 10f
            if (deltaMeters > accuracyThreshold) {
                totalKm += deltaMeters / 1000f
                prefs.edit().putFloat("total_km", totalKm).apply()
            }
        }
        lastLocation = location
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.BLUETOOTH_CONNECT] == true || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startBluetoothConnection("VDO_Dashboard_ESP32")
        }
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startGpsUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupImmersiveMode()
        totalKm = prefs.getFloat("total_km", 0f)
        setContent {
            VDODashboardScreen(
                kph = liveKph,
                rpm = liveRpm,
                fuel = liveFuel,
                temp = liveTemp,
                totalKm = totalKm,
                blinker = liveBlinker,
                oel = liveOel,
                ladung = liveLadung,
                fernlicht = liveFernlicht
            )
        }
        requestPermissionsAndStart()
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Balken blijven verborgen, maar verschijnen tijdelijk als je vanaf de rand swipet/het scherm aanraakt
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
            needed += Manifest.permission.BLUETOOTH_SCAN
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startBluetoothConnection("VDO_Dashboard_ESP32")
            startGpsUpdates()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // minimaal elke 1s
                0f,    // geen minimale afstand, we willen ook snelheid=0 zien bij stilstand
                locationListener
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            // GPS_PROVIDER niet beschikbaar (bv. emulator zonder locatie ingesteld)
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothConnection(deviceName: String) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val pairedDevices = bluetoothAdapter.bondedDevices
        val device = pairedDevices.find { it.name == deviceName }

        if (device != null) {
            thread {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()
                    listenForEsp32Data(bluetoothSocket!!.inputStream)
                } catch (e: IOException) { e.printStackTrace() }
            }
        }
    }

    private fun listenForEsp32Data(inputStream: InputStream) {
        val buffer = ByteArray(1024)
        var bytes: Int
        val stringBuilder = StringBuilder()

        while (true) {
            try {
                bytes = inputStream.read(buffer)
                stringBuilder.append(String(buffer, 0, bytes))
                if (stringBuilder.contains("\n")) {
                    val rawLine = stringBuilder.toString().trim()
                    stringBuilder.clear()
                    val dataParts = rawLine.split(",")
                    // Protocol: kph,rpm,fuel,temp,blinker,oel,ladung,fernlicht
                    // kph komt uit GPS (niet hier). fuel: 0.0(leeg)..1.0(vol). temp: graden Celsius.
                    // blinker/oel/ladung/fernlicht: "1" = aan, "0" = uit.
                    if (dataParts.size >= 2) {
                        liveRpm = dataParts[1].toFloatOrNull() ?: liveRpm
                    }
                    if (dataParts.size >= 3) {
                        liveFuel = dataParts[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: liveFuel
                    }
                    if (dataParts.size >= 4) {
                        liveTemp = dataParts[3].toFloatOrNull() ?: liveTemp
                    }
                    if (dataParts.size >= 5) {
                        liveBlinker = dataParts[4].trim() == "1"
                    }
                    if (dataParts.size >= 6) {
                        liveOel = dataParts[5].trim() == "1"
                    }
                    if (dataParts.size >= 7) {
                        liveLadung = dataParts[6].trim() == "1"
                    }
                    if (dataParts.size >= 8) {
                        liveFernlicht = dataParts[7].trim() == "1"
                    }
                }
            } catch (e: IOException) { break }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { bluetoothSocket?.close() } catch (e: IOException) { e.printStackTrace() }
        try { locationManager?.removeUpdates(locationListener) } catch (e: SecurityException) { e.printStackTrace() }
    }
}

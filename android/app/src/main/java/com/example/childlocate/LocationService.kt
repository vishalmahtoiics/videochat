package com.example.childlocate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Foreground service that reports this device's location to the configured
 * server. It runs with a permanent, visible notification — Android requires
 * this for background location, and it also keeps the app honest: whoever uses
 * the phone can see that location sharing is on.
 */
class LocationService : Service() {

    private lateinit var locationManager: LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = report(location)
        // Deprecated no-op overrides kept for older platforms.
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        INTERVAL_MS,
                        MIN_DISTANCE_M,
                        listener
                    )
                    locationManager.getLastKnownLocation(provider)?.let(::report)
                }
            }
        } catch (e: SecurityException) {
            // Location permission not granted — stop quietly.
            stopSelf()
        }
    }

    private fun report(location: Location) {
        val server = Prefs.server(this).trimEnd('/')
        val key = Prefs.deviceKey(this)
        val name = Prefs.name(this)
        if (server.isEmpty() || key.isEmpty()) return

        thread {
            try {
                val body = buildString {
                    append("{")
                    append("\"key\":").append(jsonStr(key)).append(",")
                    append("\"name\":").append(jsonStr(name)).append(",")
                    append("\"lat\":").append(location.latitude).append(",")
                    append("\"lng\":").append(location.longitude).append(",")
                    append("\"accuracy\":").append(location.accuracy)
                    append("}")
                }
                val conn = (URL("$server/api/location").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { os: OutputStream ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
            } catch (_: Exception) {
                // Network hiccup — the next location update will try again.
            }
        }
    }

    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.append("\"").toString()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location sharing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while this device is sharing its location." }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location sharing is on")
            .setContentText("This phone is sharing its location with a family member.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun startForegroundNotification() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    override fun onDestroy() {
        try {
            locationManager.removeUpdates(listener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIF_ID = 1
        private const val INTERVAL_MS = 120_000L // ~2 minutes
        private const val MIN_DISTANCE_M = 0f

        fun start(ctx: Context) {
            val intent = Intent(ctx, LocationService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LocationService::class.java))
        }
    }
}

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Foreground service that answers on-demand location requests.
 *
 * It does NOT track continuously. Every POLL_MS it asks the server whether a
 * family member has requested a location ("Check location" on the dashboard).
 * Only when a request is pending does it turn on GPS, get a single fix, and
 * send it. A permanent, visible notification is shown the whole time — Android
 * requires it, and it keeps the app honest.
 */
class LocationService : Service() {

    private lateinit var locationManager: LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false
    private var pollThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        if (!running) {
            running = true
            pollThread = thread(name = "location-poll") { pollLoop() }
        }
        return START_STICKY
    }

    private fun pollLoop() {
        while (running) {
            try {
                if (checkPending()) fetchAndSendOnce()
            } catch (_: Exception) {
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    /** Returns true only while the parent has a pending request on the server. */
    private fun checkPending(): Boolean {
        val server = Prefs.server(this).trimEnd('/')
        val key = Prefs.deviceKey(this)
        if (server.isEmpty() || key.isEmpty()) return false
        return try {
            val url = URL("$server/api/request?key=" + URLEncoder.encode(key, "UTF-8"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()
            text.contains("\"pending\":true")
        } catch (_: Exception) {
            false
        }
    }

    /** Turn on GPS just long enough for one fresh location, then send it. */
    private fun fetchAndSendOnce() {
        try {
            // Fast answer: last known location, if we have one.
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(p)) {
                    locationManager.getLastKnownLocation(p)?.let(::report)
                }
            }

            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> return
            }

            val oneShot = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    report(location)
                    try {
                        locationManager.removeUpdates(this)
                    } catch (_: Exception) {
                    }
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Location updates must be requested on a thread with a Looper.
            mainHandler.post {
                try {
                    locationManager.requestLocationUpdates(provider, 0L, 0f, oneShot, Looper.getMainLooper())
                    // Stop listening after 30s even if no fix arrived.
                    mainHandler.postDelayed({
                        try {
                            locationManager.removeUpdates(oneShot)
                        } catch (_: Exception) {
                        }
                    }, 30_000)
                } catch (_: SecurityException) {
                }
            }
        } catch (_: SecurityException) {
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
                // Network hiccup — the next request will try again.
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
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location sharing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while this device can answer location requests." }
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
            .setContentText("Location is shared only when a family member requests it.")
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
        running = false
        pollThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIF_ID = 1
        private const val POLL_MS = 15_000L // check for a request every 15s

        fun start(ctx: Context) {
            val intent = Intent(ctx, LocationService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LocationService::class.java))
        }
    }
}

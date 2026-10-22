package com.example.childlocate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Setup screen. The parent (or the person who owns the phone) enters the
 * server address and a private key, then taps Start. Location sharing only
 * begins after the user grants permission, and a permanent notification shows
 * while it is active.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var serverField: EditText
    private lateinit var keyField: EditText
    private lateinit var nameField: EditText
    private lateinit var status: TextView

    private val foregroundPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fine) {
            requestBackgroundIfNeeded()
            beginSharing()
        } else {
            status.text = getString(R.string.need_location)
        }
    }

    private val backgroundPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Either way the foreground service already runs; "Allow all the time"
           just lets it keep reporting when the app isn't open. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverField = findViewById(R.id.server)
        keyField = findViewById(R.id.key)
        nameField = findViewById(R.id.name)
        status = findViewById(R.id.status)

        serverField.setText(Prefs.server(this))
        keyField.setText(Prefs.deviceKey(this))
        nameField.setText(Prefs.name(this))

        findViewById<Button>(R.id.start).setOnClickListener { onStart() }
        findViewById<Button>(R.id.stop).setOnClickListener { onStop() }

        refreshStatus()
    }

    private fun onStart() {
        val server = serverField.text.toString().trim()
        val key = keyField.text.toString().trim()
        val name = nameField.text.toString().trim()
        if (server.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            Toast.makeText(this, R.string.bad_url, Toast.LENGTH_LONG).show()
            return
        }
        Prefs.save(this, server, key, name)

        // Ask for notification permission on Android 13+ so the ongoing
        // notification is visible.
        val toRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (hasFineLocation()) {
            requestBackgroundIfNeeded()
            beginSharing()
        } else {
            foregroundPerms.launch(toRequest.toTypedArray())
        }
    }

    private fun onStop() {
        Prefs.setEnabled(this, false)
        LocationService.stop(this)
        refreshStatus()
    }

    private fun beginSharing() {
        Prefs.setEnabled(this, true)
        LocationService.start(this)
        refreshStatus()
    }

    private fun requestBackgroundIfNeeded() {
        // Background location must be requested on its own, after fine location.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            status.text = getString(R.string.pick_all_the_time)
            backgroundPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun hasFineLocation() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        status.text = if (Prefs.isEnabled(this))
            getString(R.string.sharing_on)
        else
            getString(R.string.sharing_off)
    }
}

package com.example.childlocate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Setup screen. The app stays visible and always shows its status, and the
 * foreground service keeps an ongoing notification — so whoever uses this
 * phone knows location sharing is on. The setup fields (server, key, start/
 * stop) sit behind a settings password so only the parent can change them.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var serverField: EditText
    private lateinit var keyField: EditText
    private lateinit var nameField: EditText
    private lateinit var consentBox: CheckBox
    private lateinit var status: TextView
    private lateinit var settingsPanel: View

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
        consentBox = findViewById(R.id.consent)
        status = findViewById(R.id.status)
        settingsPanel = findViewById(R.id.settings_panel)

        // Pre-fill the server address (editable), defaulting to our domain.
        val savedServer = Prefs.server(this)
        serverField.setText(if (savedServer.isEmpty()) getString(R.string.default_server) else savedServer)
        keyField.setText(Prefs.deviceKey(this))
        nameField.setText(Prefs.name(this))

        findViewById<Button>(R.id.chat).setOnClickListener {
            startActivity(android.content.Intent(this, ChatActivity::class.java))
        }
        findViewById<Button>(R.id.settings).setOnClickListener { promptForSettings() }
        findViewById<Button>(R.id.start).setOnClickListener { onStartClicked() }
        findViewById<Button>(R.id.stop).setOnClickListener { onStopClicked() }

        refreshStatus()
    }

    /** Ask for the settings password before revealing the setup fields. */
    private fun promptForSettings() {
        if (settingsPanel.visibility == View.VISIBLE) {
            settingsPanel.visibility = View.GONE
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.enter_password)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.enter_password)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString() == SETTINGS_PASSWORD) {
                    settingsPanel.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onStartClicked() {
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
        // Require explicit consent before any location is shared.
        if (!consentBox.isChecked) {
            Toast.makeText(this, R.string.need_consent, Toast.LENGTH_LONG).show()
            return
        }
        Prefs.save(this, server, key, name)

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

    private fun onStopClicked() {
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

    companion object {
        // Settings password — only someone who knows it can change the setup.
        private const val SETTINGS_PASSWORD = "8826950436"
    }
}

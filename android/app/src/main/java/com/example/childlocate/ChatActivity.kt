package com.example.childlocate

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * A plain, visible chat between this phone and the family member on the
 * dashboard. Both sides see every message — nothing hidden.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var server: String
    private lateinit var key: String
    private lateinit var me: String
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView

    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastTs = 0L
    @Volatile private var running = false

    private val poller = object : Runnable {
        override fun run() {
            poll()
            if (running) handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        server = Prefs.server(this).trimEnd('/')
        key = Prefs.deviceKey(this)
        me = Prefs.name(this).ifEmpty { "Phone" }
        messages = findViewById(R.id.messages)
        scroll = findViewById(R.id.scroll)

        if (server.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<Button>(R.id.send).setOnClickListener {
            val field = findViewById<EditText>(R.id.msg)
            val text = field.text.toString().trim()
            if (text.isNotEmpty()) {
                field.setText("")
                send(text)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        running = true
        handler.post(poller)
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(poller)
        super.onPause()
    }

    private fun addMessage(from: String, text: String) {
        val tv = TextView(this)
        tv.text = "$from: $text"
        tv.setPadding(0, 8, 0, 8)
        messages.addView(tv)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun poll() {
        thread {
            try {
                val url = URL(
                    "$server/api/chat?key=" + URLEncoder.encode(key, "UTF-8") + "&since=$lastTs"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                conn.disconnect()

                val arr = JSONObject(body).optJSONArray("messages") ?: JSONArray()
                val items = ArrayList<Pair<String, String>>()
                var maxTs = lastTs
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    items.add(Pair(m.optString("from"), m.optString("text")))
                    val ts = m.optLong("ts")
                    if (ts > maxTs) maxTs = ts
                }
                handler.post {
                    for (p in items) addMessage(p.first, p.second)
                    lastTs = maxTs
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun send(text: String) {
        thread {
            try {
                val payload = JSONObject()
                    .put("key", key)
                    .put("from", me)
                    .put("text", text)
                    .toString()
                val conn = (URL("$server/api/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { os: OutputStream ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }
                conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                handler.post { poll() } // show it right away
            } catch (_: Exception) {
            }
        }
    }
}

package com.example.childlocate

import android.content.Context

/** Simple persistent settings shared between the activity, service and boot receiver. */
object Prefs {
    private const val FILE = "childlocate"
    private const val KEY_SERVER = "server"
    private const val KEY_DEVICE = "device_key"
    private const val KEY_NAME = "device_name"
    private const val KEY_ENABLED = "enabled"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(ctx: Context, server: String, deviceKey: String, name: String) {
        sp(ctx).edit()
            .putString(KEY_SERVER, server)
            .putString(KEY_DEVICE, deviceKey)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun server(ctx: Context) = sp(ctx).getString(KEY_SERVER, "") ?: ""
    fun deviceKey(ctx: Context) = sp(ctx).getString(KEY_DEVICE, "") ?: ""
    fun name(ctx: Context) = sp(ctx).getString(KEY_NAME, "") ?: ""
}

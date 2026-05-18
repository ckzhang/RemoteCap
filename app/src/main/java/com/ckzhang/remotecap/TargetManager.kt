package com.ckzhang.remotecap

import android.content.Context
import android.content.SharedPreferences

object TargetManager {
    private const val PREFS_NAME = "RemoteCapPrefs"
    private const val KEY_X = "TARGET_X"
    private const val KEY_Y = "TARGET_Y"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var targetX: Float
        get() = prefs?.getFloat(KEY_X, 0f) ?: 0f
        set(value) {
            prefs?.edit()?.putFloat(KEY_X, value)?.apply()
        }

    var targetY: Float
        get() = prefs?.getFloat(KEY_Y, 0f) ?: 0f
        set(value) {
            prefs?.edit()?.putFloat(KEY_Y, value)?.apply()
        }
}

package com.ckzhang.remotecap

import android.content.Context
import android.content.SharedPreferences

object TargetManager {
    private const val PREFS_NAME = "AnyCamPrefs"
    private const val KEY_X = "TARGET_X"
    private const val KEY_Y = "TARGET_Y"
    private const val KEY_COUNTDOWN = "COUNTDOWN_SEC"
    private const val KEY_TEST_PRO = "TEST_PRO_STATE"

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
        
    var countdownSec: Int
        get() = prefs?.getInt(KEY_COUNTDOWN, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_COUNTDOWN, value)?.apply()
        }
        
    var testProState: Boolean
        get() = prefs?.getBoolean(KEY_TEST_PRO, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_TEST_PRO, value)?.apply()
        }
}

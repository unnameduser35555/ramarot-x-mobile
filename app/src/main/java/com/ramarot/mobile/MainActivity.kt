package com.ramarot.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.statusText)
        val openAccessibility = findViewById<Button>(R.id.openAccessibility)
        val start = findViewById<Button>(R.id.startButton)
        val stop = findViewById<Button>(R.id.stopButton)

        openAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        start.setOnClickListener {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            status.text = "自動化: ON（Fortniteを前面にしてプレイ画面に移動）"
        }

        stop.setOnClickListener {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            status.text = "自動化: OFF"
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        findViewById<TextView>(R.id.statusText).text = if (enabled) {
            "自動化: ON"
        } else {
            "自動化: OFF"
        }
    }

    companion object {
        const val PREF_NAME = "ramarot_auto"
        const val KEY_ENABLED = "enabled"
    }
}

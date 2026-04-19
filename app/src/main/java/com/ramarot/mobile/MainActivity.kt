package com.ramarot.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            findViewById<TextView>(R.id.statusText).text =
                "自動化: ON（Fortniteを前面にしてプレイ画面に移動）"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.statusText)
        val openAccessibility = findViewById<Button>(R.id.openAccessibility)
        val start = findViewById<Button>(R.id.startButton)
        val stop = findViewById<Button>(R.id.stopButton)

        // ① アクセシビリティ設定を開く
        openAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // ② 自動化開始 → オーバーレイ権限 → 画面録画許可 → サービス起動
        start.setOnClickListener {
    if (!Settings.canDrawOverlays(this)) {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
        status.text = "オーバーレイを許可してからもう一度押してください"
        return@setOnClickListener
    }
    status.text = "画面共有ダイアログを開いています..."
    val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    projectionLauncher.launch(mgr.createScreenCaptureIntent())
}

        // ③ 自動化停止
        stop.setOnClickListener {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            stopService(Intent(this, ScreenCaptureService::class.java))
            status.text = "自動化: OFF"
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        findViewById<TextView>(R.id.statusText).text =
            if (enabled) "自動化: ON" else "自動化: OFF"
    }

    companion object {
        const val PREF_NAME = "ramarot_auto"
        const val KEY_ENABLED = "enabled"
    }
}

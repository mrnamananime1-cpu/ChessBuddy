package com.chessbuddy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "ChessBuddy ♟️"
            textSize = 28f
        })

        root.addView(Button(this).apply {
            text = "♟️ Play vs Stockfish bot"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GameActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = "Overlay: grant permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    Toast.makeText(this@MainActivity, "Already granted ✅", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Overlay: start floating button"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
                } else {
                    startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        })

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }
}

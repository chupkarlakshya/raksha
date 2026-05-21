package com.safepath.indore.wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class DangerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full red screen
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.RED)
            setPadding(32, 32, 32, 32)
        }
        
        // DANGER ZONE text
        val mainText = TextView(this).apply {
            text = "⚠️ DANGER ZONE"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        // Check app text
        val subText = TextView(this).apply {
            text = "Check the phone app\nfor further info"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        
        layout.addView(mainText)
        layout.addView(subText)
        setContentView(layout)

        // Auto-close after 5 seconds or with a tap
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5000)
        layout.setOnClickListener { finish() }
    }
}

package com.safepath.indore.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.safepath.indore.R
import com.safepath.indore.data.EmergencyGuideData
import com.safepath.indore.data.EmergencyGuideItem

class EmergencyGuideActivity : AppCompatActivity() {

    private lateinit var adapter: EmergencyGuideAdapter
    private var allItems: List<EmergencyGuideItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_guide)

        val btnBack = findViewById<ImageView>(R.id.btnBack)

        // Setup Back Button
        btnBack?.setOnClickListener {
            finish()
        }
    }

    private fun filterList(query: String) {
        // No-op in heatmap view
    }
}

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
        val searchInput = findViewById<EditText>(R.id.searchQueryInput)
        val recyclerView = findViewById<RecyclerView>(R.id.guideRecyclerView)

        // Load data (deep copy so search filtering doesn't mess up expand states globally if needed, though simple reference is fine)
        allItems = EmergencyGuideData.items.map { it.copy() }

        // Setup RecyclerView
        adapter = EmergencyGuideAdapter(allItems)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Setup Back Button
        btnBack.setOnClickListener {
            finish()
        }

        // Setup Search Filter
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterList(query: String) {
        val lowerCaseQuery = query.lowercase().trim()
        val filteredList = if (lowerCaseQuery.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                it.question.lowercase().contains(lowerCaseQuery) ||
                it.answer.lowercase().contains(lowerCaseQuery)
            }
        }
        adapter.updateData(filteredList)
    }
}

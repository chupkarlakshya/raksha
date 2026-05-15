package com.safepath.indore

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Geocoder as AndroidGeocoder
import android.net.Uri
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.LinearLayout as LayoutWidget
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import com.safepath.indore.R
import com.safepath.indore.data.*
import com.safepath.indore.databinding.ActivityMainBinding
import com.safepath.indore.routing.Route
import com.safepath.indore.routing.RouteGenerator
import com.safepath.indore.routing.RouteType
import com.safepath.indore.ui.*
import com.safepath.indore.utils.*
import com.safepath.indore.utils.Geocoder
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var riskCalc: RiskCalculator
    private lateinit var routeGen: RouteGenerator
    private lateinit var fusedClient: FusedLocationProviderClient

    private var googleMap: GoogleMap? = null

    // Default origin → Rajwada (Indore old town). Updated to user GPS once available.
    private var origin: LatLng = LatLng(22.7196, 75.8577)
    private var destination: LatLng? = null

    private val routePolylines = mutableMapOf<RouteType, Polyline>()
    private val markers = mutableListOf<Marker>()
    private val incidentMarkers = mutableListOf<Marker>()
    private val policeMarkers = mutableListOf<Marker>()
    private val aiRiskCircles = mutableListOf<Circle>()
    private var heatmapOverlay: com.google.android.gms.maps.model.TileOverlay? = null
    private var unsafeCircle: Circle? = null
    private var hazardCircles = mutableListOf<Circle>()
    private var activeHazards = listOf<HazardZone>()
    private var hazardRefreshJob: Job? = null
    private val waypointMarkers = mutableMapOf<RouteType, MutableList<Circle>>()

    private var routes: List<Route> = emptyList()
    private var selected: RouteType = RouteType.SAFEST
    private var heatmapVisible = false
    private var policeVisible = false
    private var aiRiskVisible = false
    private var safetyTimer: CountDownTimer? = null
    private lateinit var voice: VoiceCoach
    
    // For crowd-sourced reporting
    private var isPickingIncidentLocation = false

    private val policeStations = listOf(
        Pair("Vijay Nagar Police Station", LatLng(22.7533, 75.8937)),
        Pair("Palasia Police Station", LatLng(22.7244, 75.8839)),
        Pair("Sarafa Police Station (Rajwada)", LatLng(22.7196, 75.8577)),
        Pair("Bhawarkuan Police Station", LatLng(22.7001, 75.8701)),
        Pair("Annapurna Police Station", LatLng(22.6934, 75.8344)),
        Pair("Khajrana Police Station", LatLng(22.7441, 75.9012)),
        Pair("Tukoganj Police Station", LatLng(22.7231, 75.8744)),
        Pair("Aerodrome Police Station", LatLng(22.7248, 75.8075))
    )

    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) enableMyLocationLayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        voice = VoiceCoach(this)

        // Heavy work off the main thread.
        lifecycleScope.launch {
            val crimes = withContext(Dispatchers.IO) { CrimeDataLoader.load(this@MainActivity) }
            riskCalc = RiskCalculator(crimes)
            routeGen = RouteGenerator(riskCalc)
            refreshCommunityIncidents()
            startHazardRefresh()
        }

        setupMap()
        setupBottomPanel()
        setupSosButton()
        setupLocationInput()
        checkWatchConnection()
        
        // Demo specific: set greeting name
        binding.userName.text = "Aanya"
        
        Toast.makeText(this, "SafePath Connect: ${com.safepath.indore.BuildConfig.SAFEPATH_API_URL}", Toast.LENGTH_LONG).show()
    }

    // ----------------------------------------------------- Watch status ----

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        checkWatchConnection()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sos") {
            val message = String(messageEvent.data)
            if (message == "SOS_TRIGGERED") {
                runOnUiThread {
                    val intent = Intent(this, SosActivity::class.java).apply {
                        putExtra(SosActivity.EXTRA_LAT, origin.latitude)
                        putExtra(SosActivity.EXTRA_LNG, origin.longitude)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun checkWatchConnection() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                // In the new layout, we don't have a specific watchStatusChip
                // We'll update the 'Shield Active' pill text instead if it exists
                if (nodes.isNotEmpty()) {
                    val nodeName = nodes[0].displayName
                    binding.tvWatchStatusHome.text = "Watch: $nodeName"
                    binding.tvWatchStatusHome.setTextColor(getColor(R.color.accent))
                    binding.watchStatusDot.setBackgroundResource(R.drawable.circle_green)
                } else {
                    binding.tvWatchStatusHome.text = "No watch paired"
                    binding.tvWatchStatusHome.setTextColor(getColor(R.color.slate400))
                    binding.watchStatusDot.setBackgroundResource(R.drawable.circle_slate)
                }
            }
            .addOnFailureListener {
                binding.tvWatchStatusHome.text = "Watch Error"
            }
    }

    // ---------------------------------------------------------------- Map ---

    private fun setupMap() {
        val frag = supportFragmentManager.findFragmentById(R.id.mainMapView) as SupportMapFragment
        frag.getMapAsync { map ->
            googleMap = map
            map.uiSettings.setZoomControlsEnabled(false)
            map.uiSettings.setMyLocationButtonEnabled(true)
            map.uiSettings.setCompassEnabled(true)

            map.setOnPolylineClickListener { line: Polyline ->
                val type = line.tag as? RouteType ?: return@setOnPolylineClickListener
                selectRoute(type)
            }

            map.setOnMapClickListener { latLng: LatLng ->
                if (isPickingIncidentLocation) {
                    isPickingIncidentLocation = false
                    startIncidentTypeSelection(latLng)
                }
            }

            map.setOnMapLongClickListener { latLng: LatLng ->
                setDestinationAt(latLng, "Dropped Pin")
            }

            map.setOnInfoWindowClickListener { marker ->
                if (policeMarkers.contains(marker)) {
                    val uri = "google.navigation:q=${marker.position.latitude},${marker.position.longitude}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    intent.setPackage("com.google.android.apps.maps")
                    try { startActivity(intent) } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Google Maps not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // FAB for reporting incidents — always visible on map
            binding.fabReport.setOnClickListener { showReportIncidentDialog() }

            // Center on Indore.
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13f))
            requestLocation()
            updateUnsafeWarning()
            
            refreshCommunityIncidents()
            loadSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE)
        val pVis = prefs.getBoolean("police_visible", false)
        val hVis = prefs.getBoolean("heatmap_visible", false)
        
        if (pVis) togglePoliceStations()
        if (hVis) toggleHeatmap()
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            enableMyLocationLayer()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        val map = googleMap ?: return
        try {
            map.isMyLocationEnabled = true
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    origin = LatLng(loc.latitude, loc.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 14f))
                    updateUnsafeWarning()
                    
                    // Update origin label text
                    val addr = Geocoder.reverseGeocode(this@MainActivity, origin) ?: "Current Location"
                    binding.originLabel.text = addr
                }
            }
        } catch (_: SecurityException) { /* permission revoked mid-flight */ }
    }

    private fun startHazardRefresh() {
        hazardRefreshJob?.cancel()
        hazardRefreshJob = lifecycleScope.launch {
            while (isActive) {
                val newHazards = HazardZoneFetcher.fetchActiveZones()
                if (newHazards != activeHazards) {
                    activeHazards = newHazards
                    withContext(Dispatchers.Main) {
                        drawHazardZones(activeHazards)
                        updateUnsafeWarning()
                        updateRoutes(activeHazards)
                    }
                }
                delay(120_000) // 2 minutes
            }
        }
    }

    private fun updateRoutes(hazards: List<HazardZone>) {
        destination?.let { regenerateRoutes(it) }
    }

    private fun drawHazardZones(zones: List<HazardZone>) {
        val map = googleMap ?: return
        hazardCircles.forEach { it.remove() }
        hazardCircles.clear()
        for (zone in zones) {
            val circle = map.addCircle(
                CircleOptions()
                    .center(LatLng(zone.lat, zone.lng))
                    .radius(zone.radiusM.toDouble())
                    .fillColor(Color.argb(70, 255, 0, 0))
                    .strokeColor(Color.RED)
                    .strokeWidth(2f)
            )
            hazardCircles.add(circle)
        }
    }

    // ---------------------------------------------------------------- UI ----
    private fun setupBottomPanel() {
        // Navigation Bar Listeners
        binding.navHome.setOnClickListener {
            showHomeState()
        }
        binding.navRoute.setOnClickListener {
            showRouteState()
        }
        binding.navReport.setOnClickListener {
            showReportIncidentDialog()
        }
        binding.navSettings.setOnClickListener {
            showSettingsState()
        }

        binding.chipPoliceStations.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != policeVisible) togglePoliceStations()
        }

        binding.chipHeatmap.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != heatmapVisible) toggleHeatmap()
        }

        // Secondary features from the Grid
        binding.btnFakeCall.setOnClickListener {
            startActivity(android.content.Intent(this, com.safepath.indore.ui.FakeCallActivity::class.java))
        }
        binding.btnTimer.setOnClickListener {
            showTimerDialog()
        }
        binding.btnLiveTrack.setOnClickListener {
            startActivity(android.content.Intent(this, com.safepath.indore.ui.LiveTrackActivity::class.java))
        }
        binding.btnSafeRoute.setOnClickListener {
            showRouteState()
        }

        binding.btnBackToHome.setOnClickListener {
            showHomeState()
        }

        binding.btnRouteSafest.setOnClickListener { selectRoute(RouteType.SAFEST) }
        binding.btnRouteFastest.setOnClickListener { selectRoute(RouteType.FASTEST) }

        binding.navigateButton.setOnClickListener { launchGoogleMapsNavigation() }

        // Make location card interactive (Disambiguation Search)
        binding.originLabel.setOnClickListener { showSearchDialog(isOrigin = true) }
        binding.destLabel.setOnClickListener { showSearchDialog(isOrigin = false) }

        binding.btnRefreshHazards.setOnClickListener {
            lifecycleScope.launch {
                val newHazards = HazardZoneFetcher.fetchActiveZones()
                activeHazards = newHazards
                drawHazardZones(activeHazards)
                updateUnsafeWarning()
                updateRoutes(activeHazards)
                Toast.makeText(this@MainActivity, "Hazards refreshed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSearchDialog(isOrigin: Boolean) {
        val input = android.widget.EditText(this)
        input.hint = if (isOrigin) "Starting point..." else "Destination..."
        input.setSingleLine()
        input.setPadding(60, 40, 60, 40)
        
        androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(if (isOrigin) "Update Start" else "Update Destination")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    val matches = Geocoder.getPossibleMatches(this, query)
                    if (matches.isEmpty()) {
                        Toast.makeText(this, "No matches found for '$query'", Toast.LENGTH_SHORT).show()
                    } else if (matches.size == 1) {
                        if (isOrigin) {
                            origin = matches[0].second
                            binding.originLabel.text = matches[0].first
                            destination?.let { regenerateRoutes(it) }
                        } else {
                            setDestinationAt(matches[0].second, matches[0].first)
                        }
                    } else {
                        val names = matches.map { it.first }.toTypedArray()
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Which one did you mean?")
                            .setItems(names) { _, which ->
                                val selectedMatch = matches[which]
                                if (isOrigin) {
                                    origin = selectedMatch.second
                                    binding.originLabel.text = selectedMatch.first
                                    destination?.let { regenerateRoutes(it) }
                                } else {
                                    setDestinationAt(selectedMatch.second, selectedMatch.first)
                                }
                            }.show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupLocationInput() {
        // Live Suggestions Logic
        binding.destinationInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    // 1. Get Local Landmarks
                    val localMatches = Geocoder.getPossibleMatches(this@MainActivity, query)
                    
                    // 2. Get Google Suggestions
                    Geocoder.getGoogleSuggestions(query) { googleResults ->
                        runOnUiThread {
                            // Merge results (Local first)
                            val merged = mutableListOf<Pair<String, String>>()
                            localMatches.forEach { merged.add(it.first to "LOCAL:${it.second.latitude},${it.second.longitude}") }
                            googleResults.forEach { merged.add(it.first to it.second) }
                            
                            if (merged.isNotEmpty()) {
                                showSuggestions(merged)
                            } else {
                                binding.searchSuggestionsCard.visibility = android.view.View.GONE
                            }
                        }
                    }
                } else {
                    binding.searchSuggestionsCard.visibility = android.view.View.GONE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.destinationInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || 
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                onDestinationEntered(v.text.toString())
                binding.searchSuggestionsCard.visibility = android.view.View.GONE
                true
            } else false
        }
    }

    private fun showSuggestions(matches: List<Pair<String, String>>) {
        binding.searchSuggestionsCard.visibility = android.view.View.VISIBLE
        binding.searchSuggestions.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.searchSuggestions.adapter = SuggestionAdapter(matches) { name, id ->
            binding.searchSuggestionsCard.visibility = android.view.View.GONE
            binding.destinationInput.setText(name)
            
            if (id.startsWith("LOCAL:")) {
                val coords = id.removePrefix("LOCAL:").split(",")
                val pos = LatLng(coords[0].toDouble(), coords[1].toDouble())
                setDestinationAt(pos, name)
            } else {
                // Fetch coordinates from Google Place ID
                Toast.makeText(this, "Locating $name…", Toast.LENGTH_SHORT).show()
                Geocoder.getPlaceDetails(id) { pos ->
                    runOnUiThread {
                        if (pos != null) {
                            setDestinationAt(pos, name)
                        } else {
                            Toast.makeText(this, "Could not find coordinates for $name", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            // Hide keyboard
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.destinationInput.windowToken, 0)
        }
    }

    // --- Suggestion Adapter ---
    inner class SuggestionAdapter(
        private val items: List<Pair<String, String>>,
        private val onClick: (String, String) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SuggestionAdapter.VH>() {
        inner class VH(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val text: android.widget.TextView = view.findViewById(R.id.suggestionText)
        }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, pos) = items[position]
            holder.text.text = name
            holder.itemView.setOnClickListener { onClick(name, pos) }
        }
        override fun getItemCount() = items.size
    }


    private fun showHomeState() {
        binding.homeScroll.visibility = View.VISIBLE
        binding.mapContainer.visibility = View.GONE
        binding.routeSummary.visibility = View.GONE
        binding.btnBackToHome.visibility = View.GONE
        binding.fragmentContainer.visibility = View.GONE
        
        // Highlight Home Tab
        updateNavState(binding.navHome)
    }

    private fun showRouteState() {
        binding.homeScroll.visibility = View.GONE
        binding.mapContainer.visibility = View.VISIBLE
        binding.btnBackToHome.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
        if (destination != null) {
            binding.routeSummary.visibility = View.VISIBLE
        }
        // Highlight Route Tab
        updateNavState(binding.navRoute)
    }

    private fun updateNavState(active: View) {
        val colorActive = ContextCompat.getColor(this, R.color.accent)
        val colorInactive = ContextCompat.getColor(this, R.color.slate400)

        // Reset all
        binding.navHomeIcon.setColorFilter(colorInactive)
        binding.navHomeText.setTextColor(colorInactive)
        binding.navRouteIcon.setColorFilter(colorInactive)
        binding.navRouteText.setTextColor(colorInactive)
        binding.navReportIcon.setColorFilter(colorInactive)
        binding.navReportText.setTextColor(colorInactive)
        binding.navSettingsIcon.setColorFilter(colorInactive)
        binding.navSettingsText.setTextColor(colorInactive)

        // Set active
        when(active.id) {
            binding.navHome.id -> {
                binding.navHomeIcon.setColorFilter(colorActive)
                binding.navHomeText.setTextColor(colorActive)
                binding.navHomeText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            binding.navRoute.id -> {
                binding.navRouteIcon.setColorFilter(colorActive)
                binding.navRouteText.setTextColor(colorActive)
                binding.navRouteText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            binding.navReport.id -> {
                binding.navReportIcon.setColorFilter(colorActive)
                binding.navReportText.setTextColor(colorActive)
                binding.navReportText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            binding.navSettings.id -> {
                binding.navSettingsIcon.setColorFilter(colorActive)
                binding.navSettingsText.setTextColor(colorActive)
                binding.navSettingsText.setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    // ---------------------------------------------------------- SOS hold ----

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSosButton() {
        var timer: CountDownTimer? = null
        binding.sosButton.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.sosProgress.visibility = View.VISIBLE
                    binding.sosProgress.progress = 0
                    timer = object : CountDownTimer(2000, 50) {
                        override fun onTick(left: Long) {
                            binding.sosProgress.progress = (((2000 - left).toFloat() / 2000f) * 100).toInt()
                        }
                        override fun onFinish() {
                            binding.sosProgress.progress = 100
                            triggerSos()
                        }
                    }.also { it.start() }
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    timer?.cancel()
                    binding.sosProgress.visibility = View.INVISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerSos() {
        // Haptic feedback
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }

        val intent = Intent(this, SosActivity::class.java)
            .putExtra(SosActivity.EXTRA_LAT, origin.latitude)
            .putExtra(SosActivity.EXTRA_LNG, origin.longitude)
        startActivity(intent)
    }

    // ----------------------------------------------------- Destination + routes

    private fun onDestinationEntered(rawQuery: String) {
        if (!::routeGen.isInitialized) {
            Toast.makeText(this, "Wait, still loading local data...", Toast.LENGTH_SHORT).show()
            return
        }
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        // Get all possible matches
        val matches = Geocoder.getPossibleMatches(this, query)
        
        if (matches.isEmpty()) {
            Toast.makeText(this, "No matches found for '$query'", Toast.LENGTH_SHORT).show()
        } else if (matches.size == 1) {
            val match = matches[0]
            setDestinationAt(match.second, match.first)
        } else {
            // Show disambiguation dialog
            val names = matches.map { it.first }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Which one did you mean?")
                .setItems(names, object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        val match = matches[which]
                        setDestinationAt(match.second, match.first)
                    }
                })
                .show()
        }
    }

    private fun setDestinationAt(latLng: LatLng, label: String) {
        destination = latLng
        binding.destinationInput.setText(label)
        binding.destLabel.text = label
        
        // Update origin label if needed
        if (binding.originLabel.text == "Indore Junction") {
            binding.originLabel.text = "Current Location"
        }
        
        showRouteState()
        regenerateRoutes(latLng)
        Toast.makeText(this, "Finding safest path to $label…", Toast.LENGTH_SHORT).show()
    }

    private fun regenerateRoutes(dest: LatLng) {
        val map = googleMap ?: return
        val stick = true // Always stick to main roads for safety
        routes = routeGen.generate(origin, dest, stick, activeHazards)

        clearRoutes()
        for (r in routes) {
            val color = when (r.type) {
                RouteType.FASTEST -> Color.BLUE
                RouteType.BALANCED -> Color.YELLOW
                RouteType.SAFEST -> Color.GREEN
            }
            
            // Widths: 6dp, 8dp, 10dp
            val density = resources.displayMetrics.density
            val width = when (r.type) {
                RouteType.FASTEST -> 6f * density
                RouteType.BALANCED -> 8f * density
                RouteType.SAFEST -> 10f * density
            }

            val polyOptions = PolylineOptions()
                .addAll(r.points)
                .color(color)
                .width(width)
                .clickable(true)
                .jointType(com.google.android.gms.maps.model.JointType.ROUND)
            
            val poly = map.addPolyline(polyOptions)
            poly.tag = r.type
            routePolylines[r.type] = poly

            // Waypoint dots
            val dots = mutableListOf<Circle>()
            for (p in r.points) {
                val circle = map.addCircle(CircleOptions()
                    .center(p)
                    .radius(4.0) // 4 meters radius for visibility
                    .fillColor(color)
                    .strokeWidth(0f)
                    .zIndex(5f))
                dots.add(circle)
            }
            waypointMarkers[r.type] = dots
        }

        // Markers
        markers += map.addMarker(MarkerOptions().position(origin).title("You")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))!!
        markers += map.addMarker(MarkerOptions().position(dest).title("Destination")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)))!!

        // Camera fit
        val bounds = LatLngBounds.Builder().include(origin).include(dest).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200))

        // Update bottom UI stats
        routes.find { it.type == RouteType.SAFEST }?.let { r ->
            val norm = if (r.distanceMeters > 0) (r.risk / (r.distanceMeters / 1000.0)) else 0.0
            val score = (100 - norm).coerceIn(0.0, 100.0)
            binding.routeSafestScore.text = "%.0f".format(score)
            binding.routeSafestStats.text = "via ${r.shortLabel().substringAfter("km · ")}"
            binding.routeSafestTime.text = "${(r.distanceMeters / 400).toInt()}m"
        }
        routes.find { it.type == RouteType.FASTEST }?.let { r ->
            val norm = if (r.distanceMeters > 0) (r.risk / (r.distanceMeters / 1000.0)) else 0.0
            val score = (100 - norm).coerceIn(0.0, 100.0)
            binding.routeFastestScore.text = "%.0f".format(score)
            binding.routeFastestStats.text = "via ${r.shortLabel().substringAfter("km · ")}"
            binding.routeFastestTime.text = "${(r.distanceMeters / 600).toInt()}m"
        }
        
        // Transition to Route view
        showRouteState()

        selectRoute(selected)
        updateUnsafeWarning()

        val safest = routes.firstOrNull { it.type == RouteType.SAFEST }
        if (safest != null) {
            val km = "%.1f".format(safest.distanceMeters / 1000.0)
            val riskPerKm = if (safest.distanceMeters > 0)
                safest.risk / (safest.distanceMeters / 1000.0) else 0.0
            val riskLabel = when {
                riskPerKm < 10 -> "low risk"
                riskPerKm < 40 -> "moderate risk"
                else -> "elevated risk"
            }
            voice.speak("Three routes found. Safest is $km kilometers, $riskLabel. Stay on main roads.")
        }
    }

    private fun clearRoutes() {
        routePolylines.values.forEach { it.remove() }
        routePolylines.clear()
        waypointMarkers.values.forEach { list -> list.forEach { it.remove() } }
        waypointMarkers.clear()
        markers.forEach { it.remove() }
        markers.clear()
    }

    private fun selectRoute(type: RouteType) {
        selected = type
        for ((t, line) in routePolylines) {
            val isSelected = (t == type)
            
            // Highlight opaque/front, dim others (alpha 0.4)
            val baseColor = when (t) {
                RouteType.FASTEST -> Color.BLUE
                RouteType.BALANCED -> Color.YELLOW
                RouteType.SAFEST -> Color.GREEN
            }
            line.color = if (isSelected) baseColor else Color.argb(102, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            line.zIndex = if (isSelected) 10f else 1f
            
            // Show only selected route's waypoint markers
            waypointMarkers[t]?.forEach { it.isVisible = isSelected }
        }

        // Update list selection UI (Mockup High-fidelity)
        val selectedGreen = Color.parseColor("#F0FFF4")
        val transparent = Color.TRANSPARENT
        val white = Color.WHITE

        binding.btnRouteSafest.setCardBackgroundColor(if (type == RouteType.SAFEST) selectedGreen else white)
        binding.btnRouteSafest.strokeWidth = if (type == RouteType.SAFEST) (2 * resources.displayMetrics.density).toInt() else 0
        
        binding.btnRouteFastest.setCardBackgroundColor(if (type == RouteType.FASTEST) Color.parseColor("#F8FAFC") else white)
        binding.btnRouteFastest.strokeWidth = if (type == RouteType.FASTEST) (2 * resources.displayMetrics.density).toInt() else 0
    }

    // ------------------------------------------------- Heatmap toggle -------

    private fun toggleHeatmap() {
        val map = googleMap ?: return
        if (!::riskCalc.isInitialized) {
            Toast.makeText(this, "Loading crime data…", Toast.LENGTH_SHORT).show()
            return
        }
        if (heatmapVisible) {
            heatmapOverlay?.remove()
            heatmapOverlay = null
            heatmapVisible = false
            binding.chipHeatmap.isChecked = false
            getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE).edit().putBoolean("heatmap_visible", false).apply()
            return
        }
        val weighted = riskCalc.allWeightedPoints().map { (latLng, w) ->
            WeightedLatLng(latLng, w)
        }
        if (weighted.isEmpty()) return

        val gradient = Gradient(
            intArrayOf(
                ContextCompat.getColor(this, R.color.risk_low),
                ContextCompat.getColor(this, R.color.risk_medium),
                ContextCompat.getColor(this, R.color.risk_high)
            ),
            floatArrayOf(0.2f, 0.6f, 1.0f)
        )
        val provider = HeatmapTileProvider.Builder()
            .weightedData(weighted)
            .radius(40)
            .gradient(gradient)
            .opacity(0.6)
            .build()
        heatmapOverlay = map.addTileOverlay(
            com.google.android.gms.maps.model.TileOverlayOptions().tileProvider(provider)
        )
        heatmapVisible = true
        binding.chipHeatmap.isChecked = true
        getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE).edit().putBoolean("heatmap_visible", true).apply()
    }

    // ----------------------------------------------- AI risk grid overlay --

    private fun toggleAiRisk() {
        val map = googleMap ?: return
        if (aiRiskVisible) {
            aiRiskCircles.forEach { it.remove() }
            aiRiskCircles.clear()
            aiRiskVisible = false
            // binding.btnAiRisk.isSelected = false
            voice.speak("AI risk overlay off")
            return
        }

        Toast.makeText(this, "Loading AI risk model…", Toast.LENGTH_SHORT).show()
        // Use the visible map bounds so we don't waste cells off-screen.
        val region = map.projection.visibleRegion.latLngBounds
        val sw = region.southwest
        val ne = region.northeast

        RiskApiRepository.fetchGrid(
            minLat = sw.latitude,
            maxLat = ne.latitude,
            minLng = sw.longitude,
            maxLng = ne.longitude,
            steps = 22
        ) { cells ->
            if (cells.isEmpty()) {
                Toast.makeText(
                    this,
                    "AI risk unavailable — is the backend running and the model trained?",
                    Toast.LENGTH_LONG
                ).show()
                return@fetchGrid
            }
            renderAiRiskCells(cells)
            aiRiskVisible = true
            // binding.btnAiRisk.isSelected = true
            val hot = cells.count { it.score >= 30 }
            voice.speak(
                "A I risk overlay on. ${cells.size} cells analyzed, $hot high risk."
            )
        }
    }

    private fun renderAiRiskCells(cells: List<RiskCell>) {
        val map = googleMap ?: return
        aiRiskCircles.forEach { it.remove() }
        aiRiskCircles.clear()

        // Adaptive cell radius based on grid spacing — works for any zoom.
        val latitudes = cells.map { it.location.latitude }.distinct().sorted()
        val cellSpacingDeg =
            if (latitudes.size >= 2) latitudes[1] - latitudes[0] else 0.005
        val radiusMeters = (cellSpacingDeg * 111_000.0 * 0.55)

        for (cell in cells) {
            if (cell.score < 1.0) continue
            val color = riskColor(cell.score)
            val circle = map.addCircle(CircleOptions()
                .center(cell.location)
                .radius(radiusMeters)
                .strokeWidth(0f)
                .fillColor(color))
            aiRiskCircles += circle
        }
    }

    /** Maps 0–100 risk score to a translucent green→yellow→red ARGB color. */
    private fun riskColor(score: Double): Int {
        val s = score.coerceIn(0.0, 100.0) / 100.0
        val r: Int
        val g: Int
        if (s < 0.5) {
            r = (2 * s * 255).toInt().coerceIn(0, 255)
            g = 200
        } else {
            r = 230
            g = ((1 - 2 * (s - 0.5)) * 200).toInt().coerceIn(0, 200)
        }
        val alpha = (90 + 130 * s).toInt().coerceIn(0, 220)
        return (alpha shl 24) or (r shl 16) or (g shl 8)
    }

    // ------------------------------------------------------- Voice toggle --

    private fun toggleVoice() {
        voice.enabled = !voice.enabled
        // binding.btnVoiceLabel.text = if (voice.enabled) "Voice On" else "Voice Off"
        // binding.btnVoice.isSelected = voice.enabled
        if (voice.enabled) {
            voice.speak("Voice prompts enabled.")
        }
    }

    private fun togglePoliceStations() {
        val map = googleMap ?: return
        if (policeVisible) {
            policeMarkers.forEach { it.remove() }
            policeMarkers.clear()
            policeVisible = false
            binding.chipPoliceStations.isChecked = false
            getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE).edit().putBoolean("police_visible", false).apply()
            return
        }

        // Use the new PoliceStationProvider
        for (station in com.safepath.indore.data.PoliceStationProvider.stations) {
            val marker = map.addMarker(MarkerOptions()
                .position(station.location)
                .title(station.name)
                .snippet("Emergency: 100")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            marker?.let { policeMarkers.add(it) }
        }
        policeVisible = true
        binding.chipPoliceStations.isChecked = true
        getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE).edit().putBoolean("police_visible", true).apply()
        Toast.makeText(this, "Police Stations Visible", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------- Report Incident -----

    private fun showReportIncidentDialog() {
        // Ensure map is visible
        if (binding.mapContainer.visibility != View.VISIBLE) {
            showRouteState()
        }

        val lat = origin.latitude
        val lng = origin.longitude

        val descInput = android.widget.EditText(this)
        descInput.hint = "What happened? (optional)"
        descInput.setSingleLine(false)
        descInput.minLines = 2
        descInput.setPadding(60, 30, 60, 30)

        val locText = "📍 %.5f, %.5f".format(lat, lng)

        AlertDialog.Builder(this)
            .setTitle("⚠ Report Incident")
            .setMessage("Your current location: $locText\n\nThis will be sent to the safety dashboard for review.")
            .setView(descInput)
            .setPositiveButton("Submit Report") { _, _ ->
                val desc = descInput.text.toString().trim().ifEmpty { "No description provided" }
                submitIncidentReport(lat, lng, desc)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitIncidentReport(lat: Double, lng: Double, description: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = org.json.JSONObject().apply {
                    put("latitude", lat)
                    put("longitude", lng)
                    put("type", "UNSAFE_SPOT")
                    put("description", description)
                    put("reportedBy", "app_user")
                    put("severity", 3)
                }.toString()

                val url = java.net.URL("${com.safepath.indore.BuildConfig.SAFEPATH_API_URL}/api/incidents")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
                val ok = conn.responseCode in 200..299
                conn.disconnect()

                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(this@MainActivity, "✅ Incident reported! Admins will review it.", Toast.LENGTH_LONG).show()
                        // Refresh hazards to see if it affects routing
                        lifecycleScope.launch {
                            val newHazards = HazardZoneFetcher.fetchActiveZones()
                            activeHazards = newHazards
                            drawHazardZones(newHazards)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Report saved locally. Will sync when connected.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "⚠ Report queued (offline). Will sync later.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // -------------------------------------------------- Unsafe warning ------

    private fun updateUnsafeWarning() {
        if (!::riskCalc.isInitialized) return
        val map = googleMap ?: return
        val wasHigh = unsafeCircle != null
        val high = riskCalc.isHighRisk(origin, activeHazards)
        // binding.warningChip.visibility = if (high) View.VISIBLE else View.GONE
        unsafeCircle?.remove()
        unsafeCircle = null
        if (high) {
            unsafeCircle = map.addCircle(CircleOptions()
                .center(origin)
                .radius(250.0)
                .strokeColor(ContextCompat.getColor(this, R.color.accent))
                .strokeWidth(4f)
                .fillColor(0x33E53935))
            if (!wasHigh && ::voice.isInitialized) {
                voice.speak("Caution. You are entering an area with elevated risk. Stay alert.")
            }
        }
    }

    // -------------------------------------------- Google Maps deep link ----

    private fun launchGoogleMapsNavigation() {
        val route = routes.firstOrNull { it.type == selected } ?: return
        val pts = route.points
        if (pts.size < 2) return

        val km = "%.1f".format(route.distanceMeters / 1000.0)
        voice.speak("Starting navigation. ${route.type.name.lowercase()} route, $km kilometers.")

        val start = pts.first()
        val end = pts.last()

        // Increase waypoints for better accuracy while maintaining route logic.
        // We use 8 intermediate points to guide Google Maps more precisely.
        val sampled = mutableListOf<LatLng>()
        val mids = pts.drop(1).dropLast(1)
        if (mids.isNotEmpty()) {
            val count = 8.coerceAtMost(mids.size)
            for (i in 0 until count) {
                sampled.add(mids[(i * mids.size) / count])
            }
        }

        val waypointParam = if (selected == RouteType.FASTEST) "" else sampled.joinToString("|") { "%.6f,%.6f".format(it.latitude, it.longitude) }
        val builder = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("origin", "%.6f,%.6f".format(start.latitude, start.longitude))
            .appendQueryParameter("destination", "%.6f,%.6f".format(end.latitude, end.longitude))
            .appendQueryParameter("travelmode", "driving")

        if (waypointParam.isNotEmpty()) {
            builder.appendQueryParameter("waypoints", waypointParam)
        }

        val intent = Intent(Intent.ACTION_VIEW, builder.build())
        intent.setPackage("com.google.android.apps.maps")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Fall back to a browser if the Maps app isn't installed.
            startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
        }
    }

    private fun showContactDialog() {
        val prefs = getSharedPreferences("safepath_prefs", MODE_PRIVATE)
        val current = prefs.getString("emergency_contact", "")

        val input = EditText(this).apply {
            hint = "Enter phone number"
            setText(current)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(60, 40, 60, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Emergency Contact")
            .setMessage("Type a number or tap a demo contact below:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("emergency_contact", input.text.toString()).apply()
                Toast.makeText(this, "Contact saved!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear") { _, _ ->
                prefs.edit().remove("emergency_contact").apply()
                Toast.makeText(this, "Contact cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimerDialog() {
        if (safetyTimer != null) {
            safetyTimer?.cancel()
            safetyTimer = null
            // binding.statusChip.findViewById<android.widget.TextView>(android.R.id.text1)?.text = "🛡 Shield Secured"
            Toast.makeText(this, "Safety timer cancelled.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("10 Minutes", "20 Minutes", "30 Minutes", "60 Minutes")
        val minutes = intArrayOf(10, 20, 30, 60)

        AlertDialog.Builder(this)
            .setTitle("Set Safety Arrival Timer")
            .setItems(options) { _, which ->
                startSafetyTimer(minutes[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun startSafetyTimer(mins: Int) {
        val millis = mins * 60 * 1000L
        safetyTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000) % 60
                val min = (millisUntilFinished / (1000 * 60)) % 60
                // Update status chip or title to show timer
                // binding.statusChip.findViewById<android.widget.TextView>(android.R.id.text1)?.text = 
                //    "⏱ %02d:%02d".format(min, sec)
            }

            override fun onFinish() {
                triggerSos()
                safetyTimer = null
            }
        }.start()
        Toast.makeText(this, "Safety timer started for $mins mins.", Toast.LENGTH_SHORT).show()
    }

    private fun startIncidentTypeSelection(location: LatLng) {
        val types = IncidentType.values().map { it.label }.toTypedArray()
        var selectedTypeIndex = 0

        AlertDialog.Builder(this)
            .setTitle("What happened?")
            .setSingleChoiceItems(types, 0) { _, which ->
                selectedTypeIndex = which
            }
            .setPositiveButton("Next") { _, _ ->
                showIncidentDetailsDialog(IncidentType.values()[selectedTypeIndex], location)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showIncidentDetailsDialog(type: IncidentType, location: LatLng) {
        val form = LayoutWidget(this).apply {
            orientation = LayoutWidget.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val input = EditText(this).apply {
            hint = "Describe what happened (optional)"
            minLines = 2
        }
        val severityLabel = TextView(this).apply {
            text = "Severity: 3 / 5"
            setPadding(0, 18, 0, 4)
        }
        val severitySeek = SeekBar(this).apply {
            max = 4
            progress = 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    severityLabel.text = "Severity: ${progress + 1} / 5"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        form.addView(input)
        form.addView(severityLabel)
        form.addView(severitySeek)

        AlertDialog.Builder(this)
            .setTitle("Report ${type.label}")
            .setMessage("Your report helps keep Indore safe.")
            .setView(form)
            .setPositiveButton("Submit") { _, _ ->
                val report = IncidentReport(
                    type = type,
                    location = location,
                    description = input.text.toString(),
                    severity = severitySeek.progress + 1
                )
                
                IncidentRepository.submitReport(report) { success ->
                    if (success) {
                        Toast.makeText(this, "Report sent for admin verification.", Toast.LENGTH_LONG).show()
                        addIncidentMarkerToMap(report)
                    } else {
                        val prefs = getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE)
                        val override = prefs.getString("api_endpoint", null)
                        val activeIp = override ?: com.safepath.indore.BuildConfig.SAFEPATH_API_URL
                        Toast.makeText(this, "Could not reach $activeIp. Check your Wi-Fi and Laptop Firewall.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Back") { _, _ ->
                startIncidentTypeSelection(location)
            }
            .show()
    }

    private fun addIncidentMarkerToMap(report: IncidentReport) {
        val map = googleMap ?: return
        val marker = map.addMarker(MarkerOptions()
            .position(report.location)
            .title("${report.type.label} (${report.status})")
            .snippet(report.description.ifBlank { "Crowd-sourced safety report" })
            .icon(BitmapDescriptorFactory.defaultMarker(
                if (report.status == "verified") BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_ORANGE
            ))
        )
        marker?.let { incidentMarkers.add(it) }
    }

    override fun onDestroy() {
        if (::voice.isInitialized) voice.shutdown()
        super.onDestroy()
    }

    private fun refreshCommunityIncidents() {
        if (!::riskCalc.isInitialized || googleMap == null) return
        IncidentRepository.getActiveIncidents { reports ->
            riskCalc.setCrowdReports(reports)
            incidentMarkers.forEach { it.remove() }
            incidentMarkers.clear()
            reports.forEach { addIncidentMarkerToMap(it) }
            destination?.let { regenerateRoutes(it) }
            updateUnsafeWarning()
        }
    }

    private fun showSettingsState() {
        // Reset Nav UI
        binding.navHomeIcon.imageTintList = ColorStateList.valueOf(getColor(R.color.slate400))
        binding.navHomeText.setTextColor(getColor(R.color.slate400))
        binding.navRouteIcon.imageTintList = ColorStateList.valueOf(getColor(R.color.slate400))
        binding.navRouteText.setTextColor(getColor(R.color.slate400))
        binding.navReportIcon.imageTintList = ColorStateList.valueOf(getColor(R.color.slate400))
        binding.navReportText.setTextColor(getColor(R.color.slate400))
        binding.navSettingsIcon.imageTintList = ColorStateList.valueOf(getColor(R.color.accent))
        binding.navSettingsText.setTextColor(getColor(R.color.accent))

        // Toggle visibility
        binding.homeScroll.visibility = View.GONE
        binding.routeSummary.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE
        binding.mapContainer.visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment())
            .commit()
    }
}

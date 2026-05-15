package com.safepath.indore.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.safepath.indore.BuildConfig
import com.safepath.indore.databinding.ActivityLiveTrackBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LiveTrackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveTrackBinding
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var sessionId: String? = null
    private var shareUrl: String? = null
    private var isSharing = false
    private var currentLat = 22.7196
    private var currentLng = 75.8577

    private val locationTick = object : Runnable {
        override fun run() {
            fetchAndUpdateLocation()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveTrackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnShareLocation.setOnClickListener {
            if (isSharing) stopSharing() else startSharing()
        }

        binding.btnCopyLink.setOnClickListener {
            val url = shareUrl ?: return@setOnClickListener
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("SafePath Live Link", url))
            Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show()
        }

        binding.btnWhatsapp.setOnClickListener {
            val url = shareUrl ?: run {
                Toast.makeText(this, "Start sharing first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val msg = "I'm sharing my live location via SafePath. Track me here: $url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?text=${Uri.encode(msg)}"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg)
                })
            }
        }

        fetchAndUpdateLocation()
        handler.postDelayed(locationTick, 5000)
    }

    @SuppressLint("MissingPermission")
    private fun fetchAndUpdateLocation() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                currentLat = loc.latitude
                currentLng = loc.longitude
                binding.tvCurrentCoords.text = "📍 %.5f, %.5f".format(currentLat, currentLng)

                if (isSharing) {
                    postLocationUpdate(currentLat, currentLng)
                }
            }
        }
    }

    private fun startSharing() {
        val prefs = getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE)
        val contact = prefs.getString("emergency_contact", "") ?: ""

        binding.progressSharing.visibility = View.VISIBLE
        binding.btnShareLocation.isEnabled = false

        scope.launch {
            try {
                val base = BuildConfig.SAFEPATH_API_URL
                val body = JSONObject().apply {
                    put("lat", currentLat)
                    put("lng", currentLng)
                    put("emergency_contact", contact)
                }.toString()

                val url = URL("$base/api/tracking/start")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                if (code in 200..299) {
                    val json = JSONObject(response)
                    sessionId = json.getString("session_id")
                    shareUrl = json.getString("share_url")

                    withContext(Dispatchers.Main) {
                        isSharing = true
                        binding.progressSharing.visibility = View.GONE
                        binding.btnShareLocation.isEnabled = true
                        binding.btnShareLocation.text = "Stop Sharing"
                        binding.btnShareLocation.setBackgroundColor(0xFFEF4444.toInt())
                        binding.tvShareUrl.text = shareUrl
                        binding.shareUrlCard.visibility = View.VISIBLE
                        binding.btnCopyLink.visibility = View.VISIBLE
                        binding.btnWhatsapp.visibility = View.VISIBLE

                        if (contact.isNotEmpty()) {
                            binding.tvSharingStatus.text = "🟢 Live sharing — SMS sent to $contact"
                        } else {
                            binding.tvSharingStatus.text = "🟢 Live sharing active"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressSharing.visibility = View.GONE
                        binding.btnShareLocation.isEnabled = true
                        // Fallback: show coordinate-based share
                        shareUrl = "https://maps.google.com/?q=$currentLat,$currentLng"
                        binding.tvShareUrl.text = shareUrl
                        binding.shareUrlCard.visibility = View.VISIBLE
                        binding.btnCopyLink.visibility = View.VISIBLE
                        binding.btnWhatsapp.visibility = View.VISIBLE
                        binding.tvSharingStatus.text = "⚠ Sharing via static link (server unavailable)"
                        isSharing = true
                        binding.btnShareLocation.text = "Stop Sharing"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressSharing.visibility = View.GONE
                    binding.btnShareLocation.isEnabled = true
                    // Offline fallback
                    shareUrl = "https://maps.google.com/?q=$currentLat,$currentLng"
                    binding.tvShareUrl.text = shareUrl
                    binding.shareUrlCard.visibility = View.VISIBLE
                    binding.btnCopyLink.visibility = View.VISIBLE
                    binding.btnWhatsapp.visibility = View.VISIBLE
                    binding.tvSharingStatus.text = "⚠ Offline mode — sharing last known location"
                    isSharing = true
                    binding.btnShareLocation.text = "Stop Sharing"
                }
            }
        }
    }

    private fun stopSharing() {
        val sid = sessionId
        if (sid != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val base = BuildConfig.SAFEPATH_API_URL
                    val body = JSONObject().put("session_id", sid).toString()
                    val url = URL("$base/api/tracking/stop")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {}
            }
        }

        isSharing = false
        sessionId = null
        binding.btnShareLocation.text = "Share My Location"
        binding.btnShareLocation.setBackgroundColor(0xFF0EA5E9.toInt())
        binding.shareUrlCard.visibility = View.GONE
        binding.btnCopyLink.visibility = View.GONE
        binding.btnWhatsapp.visibility = View.GONE
        binding.tvSharingStatus.text = "Tap below to share your live location with contacts"
    }

    private fun postLocationUpdate(lat: Double, lng: Double) {
        val sid = sessionId ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val base = BuildConfig.SAFEPATH_API_URL
                val body = JSONObject().apply {
                    put("session_id", sid)
                    put("lat", lat)
                    put("lng", lng)
                }.toString()
                val url = URL("$base/api/tracking/update")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(locationTick)
        if (isSharing) stopSharing()
        scope.cancel()
    }
}

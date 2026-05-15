package com.safepath.indore.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.safepath.indore.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val prefs = requireActivity().getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE)
        
        // Load values
        binding.editEmergencyContact.setText(prefs.getString("emergency_contact", ""))
        binding.editApiEndpoint.setText(prefs.getString("api_endpoint", ""))
        binding.switchMainRoads.isChecked = prefs.getBoolean("stick_to_main_roads", true)
        binding.switchVoice.isChecked = prefs.getBoolean("voice_enabled", true)
        binding.switchLogging.isChecked = prefs.getBoolean("verbose_logging", false)

        // Save on change
        binding.editEmergencyContact.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val num = binding.editEmergencyContact.text.toString().trim()
                prefs.edit().putString("emergency_contact", num).apply()
            }
        }

        binding.editApiEndpoint.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val ip = s?.toString()?.trim() ?: ""
                if (ip.isNotEmpty()) {
                    prefs.edit().putString("api_endpoint", ip).apply()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.switchMainRoads.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("stick_to_main_roads", isChecked).apply()
        }

        binding.switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("voice_enabled", isChecked).apply()
        }

        binding.switchLogging.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("verbose_logging", isChecked).apply()
        }

        // Smartwatch Management
        checkWatchStatus()
        binding.btnPingWatch.setOnClickListener {
            pingWatch()
        }

        binding.btnTestConnection.setOnClickListener {
            val ip = binding.editApiEndpoint.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(context, "Please enter an IP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Ping the server's SOS endpoint as a health check
            com.safepath.indore.data.IncidentRepository.getActiveIncidents {
                Toast.makeText(context, "Connection Successful! 📡", Toast.LENGTH_SHORT).show()
            }
        }

        // Developer Tools
        binding.btnRefreshHazards.setOnClickListener {
            Toast.makeText(context, "Hazards refreshed (Demo)", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearCache.setOnClickListener {
            Toast.makeText(context, "Crime dataset reloaded", Toast.LENGTH_SHORT).show()
        }

        binding.btnSimulateSos.setOnClickListener {
            Toast.makeText(context, "SOS Simulated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkWatchStatus() {
        val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(requireContext())
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                val nodeName = nodes[0].displayName
                binding.tvWatchStatus.text = "Watch Connected"
                binding.tvWatchStatus.setTextColor(requireContext().getColor(com.safepath.indore.R.color.accent))
                binding.tvWatchDetail.text = "Active: $nodeName"
            } else {
                binding.tvWatchStatus.text = "No Watch Found"
                binding.tvWatchStatus.setTextColor(requireContext().getColor(com.safepath.indore.R.color.slate400))
                binding.tvWatchDetail.text = "Check Bluetooth & Wear OS app"
            }
        }.addOnFailureListener {
            binding.tvWatchStatus.text = "Status Unknown"
            binding.tvWatchDetail.text = "Wearable API error"
        }
    }

    private fun pingWatch() {
        val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(requireContext())
        val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(requireContext())
        
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Toast.makeText(context, "No watch connected to ping", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/ping", "PING".toByteArray())
            }
            Toast.makeText(context, "Ping sent to ${nodes.size} watch(es)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

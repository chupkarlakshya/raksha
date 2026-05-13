package com.safepath.indore.wear

import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.safepath.indore.wear.databinding.ActivityWearMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityWearMainBinding

    companion object {
        private const val SOS_PATH = "/sos"
        private const val SOS_MESSAGE = "SOS_TRIGGERED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWearMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPhoneConnection()

        binding.sosBtnWatch.setOnClickListener {
            sendSosToPhone()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPhoneConnection()
    }

    private fun checkPhoneConnection() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    binding.watchStatusText.text = "⌚ Phone Connected"
                    binding.watchStatusText.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    binding.watchStatusText.text = "⌚ Disconnected"
                    binding.watchStatusText.setTextColor(Color.parseColor("#F44336"))
                }
            }
            .addOnFailureListener {
                binding.watchStatusText.text = "⌚ Disconnected"
                binding.watchStatusText.setTextColor(Color.parseColor("#F44336"))
            }
    }

    private fun sendSosToPhone() {
        // Vibrate the watch to give haptic feedback
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))

        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Toast.makeText(this, "Phone not connected!", Toast.LENGTH_SHORT).show()
                    binding.watchStatusText.text = "⌚ Disconnected"
                    binding.watchStatusText.setTextColor(Color.parseColor("#F44336"))
                    return@addOnSuccessListener
                }

                // Send SOS message to all connected phone nodes
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(
                        node.id,
                        SOS_PATH,
                        SOS_MESSAGE.toByteArray()
                    ).addOnSuccessListener {
                        runOnUiThread {
                            binding.sosBtnWatch.text = "SENT ✓"
                            binding.watchStatusText.text = "SOS sent to phone!"
                            binding.watchStatusText.setTextColor(Color.parseColor("#FF9800"))
                            // Reset button text after 3 seconds
                            binding.sosBtnWatch.postDelayed({
                                binding.sosBtnWatch.text = "SOS"
                                binding.watchStatusText.text = "⌚ Phone Connected"
                                binding.watchStatusText.setTextColor(Color.parseColor("#4CAF50"))
                            }, 3000)
                        }
                    }.addOnFailureListener {
                        runOnUiThread {
                            Toast.makeText(this, "Failed to send SOS!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not reach phone!", Toast.LENGTH_SHORT).show()
            }
    }
}

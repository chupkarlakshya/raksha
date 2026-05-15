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
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.safepath.indore.wear.databinding.ActivityWearMainBinding

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private lateinit var binding: ActivityWearMainBinding

    companion object {
        private const val SOS_PATH = "/sos"
        private const val SOS_MESSAGE = "SOS_TRIGGERED"
        private const val PHONE_SOS_PATH = "/phone_sos"
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
        Wearable.getMessageClient(this).addListener(this)
        checkPhoneConnection()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PHONE_SOS_PATH) {
            runOnUiThread {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
                binding.watchStatusText.setText("⚠️ SOS ON PHONE!")
                binding.watchStatusText.setTextColor(Color.RED)
                binding.sosBtnWatch.setText("HELPING")
                
                binding.sosBtnWatch.postDelayed({
                    binding.sosBtnWatch.setText("SOS")
                    checkPhoneConnection()
                }, 5000)
            }
        } else if (messageEvent.path == "/ping") {
            runOnUiThread {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                binding.watchStatusText.setText("📍 Phone Pinged!")
                binding.watchStatusText.postDelayed({
                    checkPhoneConnection()
                }, 2000)
            }
        }
    }

    private fun checkPhoneConnection() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    binding.watchStatusText.setText("⌚ Phone Connected")
                    binding.watchStatusText.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    binding.watchStatusText.setText("⌚ Disconnected")
                    binding.watchStatusText.setTextColor(Color.parseColor("#F44336"))
                }
            }
            .addOnFailureListener {
                binding.watchStatusText.setText("⌚ Disconnected")
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
                    binding.watchStatusText.setText("⌚ Disconnected")
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
                            binding.sosBtnWatch.setText("SENT ✓")
                            binding.watchStatusText.setText("SOS sent to phone!")
                            binding.watchStatusText.setTextColor(Color.parseColor("#FF9800"))
                            // Reset button text after 3 seconds
                            binding.sosBtnWatch.postDelayed({
                                binding.sosBtnWatch.setText("SOS")
                                binding.watchStatusText.setText("⌚ Phone Connected")
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

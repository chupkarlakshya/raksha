package com.safepath.indore.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable

object WearConnectionManager {
    private const val PHONE_SOS_PATH = "/phone_sos"

    fun checkWatchStatus(context: Context, callback: (Boolean) -> Unit) {
        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            callback(nodes.isNotEmpty())
        }
    }

    fun sendSosToWatch(context: Context) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)
        
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, PHONE_SOS_PATH, "SOS_TRIGGERED".toByteArray())
                    .addOnFailureListener { e ->
                        Log.e("WearConnection", "Error sending SOS to node ${node.id}", e)
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("WearConnection", "Error fetching connected nodes", e)
        }
    }
}

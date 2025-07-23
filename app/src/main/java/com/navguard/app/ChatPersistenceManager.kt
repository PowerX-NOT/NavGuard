package com.navguard.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class ChatPersistenceManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "emergency_chat_prefs"
        private const val KEY_MESSAGES_PREFIX = "messages_"
        private const val TAG = "ChatPersistence"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    data class StoredMessage(
        val content: String,
        val type: String,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val isSent: Boolean
    )
    
    fun saveMessages(deviceAddress: String, messages: List<MessageDisplay>) {
        try {
            val storedMessages = messages.map { messageDisplay ->
                StoredMessage(
                    content = messageDisplay.message.content,
                    type = messageDisplay.message.type.name,
                    latitude = messageDisplay.message.latitude,
                    longitude = messageDisplay.message.longitude,
                    timestamp = messageDisplay.message.timestamp,
                    isSent = messageDisplay.isSent
                )
            }
            
            val json = gson.toJson(storedMessages)
            prefs.edit().putString(getKeyForDevice(deviceAddress), json).apply()
            Log.d(TAG, "Saved ${messages.size} messages for device $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages for device $deviceAddress", e)
        }
    }
    
    fun loadMessages(deviceAddress: String): List<MessageDisplay> {
        return try {
            val json = prefs.getString(getKeyForDevice(deviceAddress), null)
            if (json != null) {
                val type: Type = object : TypeToken<List<StoredMessage>>() {}.type
                val storedMessages: List<StoredMessage> = gson.fromJson(json, type)
                
                val messages = storedMessages.map { stored ->
                    val emergencyMessage = EmergencyMessage(
                        content = stored.content,
                        type = EmergencyMessage.MessageType.valueOf(stored.type),
                        latitude = stored.latitude,
                        longitude = stored.longitude,
                        timestamp = stored.timestamp
                    )
                    MessageDisplay(emergencyMessage, stored.isSent)
                }
                
                Log.d(TAG, "Loaded ${messages.size} messages for device $deviceAddress")
                messages
            } else {
                Log.d(TAG, "No saved messages found for device $deviceAddress")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages for device $deviceAddress", e)
            emptyList()
        }
    }
    
    fun clearMessages(deviceAddress: String) {
        try {
            prefs.edit().remove(getKeyForDevice(deviceAddress)).apply()
            Log.d(TAG, "Cleared messages for device $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing messages for device $deviceAddress", e)
        }
    }
    
    fun hasMessages(deviceAddress: String): Boolean {
        return prefs.contains(getKeyForDevice(deviceAddress))
    }
    
    private fun getKeyForDevice(deviceAddress: String): String {
        return KEY_MESSAGES_PREFIX + deviceAddress.replace(":", "_")
    }
}

data class MessageDisplay(
    val message: EmergencyMessage,
    val isSent: Boolean
)
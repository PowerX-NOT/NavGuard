package com.navguard.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class PersistenceManager(private val context: Context) {

    companion object {
        private const val CHAT_PREFS_NAME = "emergency_chat_prefs"
        private const val OFFLINE_MAP_PREFS_NAME = "offline_map_prefs"
        private const val KEY_MESSAGES_PREFIX = "messages_"
        private const val KEY_OFFLINE_MAP_URI = "offline_map_uri"
        private const val TAG = "PersistenceManager"
    }

    private val chatPrefs: SharedPreferences = context.getSharedPreferences(CHAT_PREFS_NAME, Context.MODE_PRIVATE)
    private val offlineMapPrefs: SharedPreferences = context.getSharedPreferences(OFFLINE_MAP_PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ---- Chat persistence (compatible with previous ChatPersistenceManager) ----
    data class StoredMessage(
        val content: String,
        val type: String,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val isSent: Boolean,
        val status: String = EmergencyMessage.MessageStatus.SENDING.name,
        val messageId: String = ""
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
                    isSent = messageDisplay.isSent,
                    status = messageDisplay.message.status.name,
                    messageId = messageDisplay.message.messageId
                )
            }

            val json = gson.toJson(storedMessages)
            chatPrefs.edit().putString(getKeyForDevice(deviceAddress), json).apply()
            Log.d(TAG, "Saved ${messages.size} messages for device $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages for device $deviceAddress", e)
        }
    }

    fun loadMessages(deviceAddress: String): List<MessageDisplay> {
        return try {
            val json = chatPrefs.getString(getKeyForDevice(deviceAddress), null)
            if (json != null) {
                val type: Type = object : TypeToken<List<StoredMessage>>() {}.type
                val storedMessages: List<StoredMessage> = gson.fromJson(json, type)

                val messages = storedMessages.map { stored ->
                    val emergencyMessage = EmergencyMessage(
                        messageId = stored.messageId.ifEmpty { MessageIdGenerator.generate() },
                        content = stored.content,
                        type = EmergencyMessage.MessageType.valueOf(stored.type),
                        latitude = stored.latitude,
                        longitude = stored.longitude,
                        timestamp = stored.timestamp,
                        status = try {
                            EmergencyMessage.MessageStatus.valueOf(stored.status)
                        } catch (e: Exception) {
                            EmergencyMessage.MessageStatus.SENDING
                        }
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
            chatPrefs.edit().remove(getKeyForDevice(deviceAddress)).apply()
            Log.d(TAG, "Cleared messages for device $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing messages for device $deviceAddress", e)
        }
    }

    fun hasMessages(deviceAddress: String): Boolean {
        return chatPrefs.contains(getKeyForDevice(deviceAddress))
    }

    private fun getKeyForDevice(deviceAddress: String): String {
        return KEY_MESSAGES_PREFIX + deviceAddress.replace(":", "_")
    }

    // ---- Offline map persistence ----
    fun setOfflineMapUri(uriString: String) {
        try {
            offlineMapPrefs.edit().putString(KEY_OFFLINE_MAP_URI, uriString).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving offline map URI", e)
        }
    }

    fun getOfflineMapUri(): String? {
        return try {
            offlineMapPrefs.getString(KEY_OFFLINE_MAP_URI, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading offline map URI", e)
            null
        }
    }

    fun clearOfflineMapUri() {
        try {
            offlineMapPrefs.edit().remove(KEY_OFFLINE_MAP_URI).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing offline map URI", e)
        }
    }
}

data class MessageDisplay(
    val message: EmergencyMessage,
    val isSent: Boolean
)



package com.navguard.app

import java.text.SimpleDateFormat
import java.util.*

data class EmergencyMessage(
    val messageId: String = UUID.randomUUID().toString(),
    var senderId: String = "",
    var recipientId: String = "",
    val content: String = "",
    val type: MessageType = MessageType.REGULAR,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    var hopCount: Int = 0,
    var relayPath: Array<String> = emptyArray()
) {
    enum class MessageType {
        REGULAR,
        EMERGENCY,
        SOS,
        RELAY
    }
    
    constructor(content: String, type: MessageType) : this(
        content = content,
        type = type
    )
    
    constructor(content: String, type: MessageType, latitude: Double, longitude: Double) : this(
        content = content,
        type = type,
        latitude = latitude,
        longitude = longitude
    )
    
    fun hasLocation(): Boolean = latitude != 0.0 && longitude != 0.0
    
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getLocationString(): String {
        return if (hasLocation()) {
            String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude)
        } else {
            "No location"
        }
    }
    
    fun incrementHopCount() {
        hopCount++
    }
    
    fun isEmergency(): Boolean = type == MessageType.EMERGENCY || type == MessageType.SOS
    
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[${type.name}] ")
        if (isEmergency()) {
            sb.append("🚨 ")
        }
        sb.append(content)
        if (hasLocation()) {
            sb.append(" 📍 ${getLocationString()}")
        }
        sb.append(" (${getFormattedTimestamp()})")
        if (hopCount > 0) {
            sb.append(" [Relayed ${hopCount}x]")
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmergencyMessage

        if (messageId != other.messageId) return false
        if (senderId != other.senderId) return false
        if (recipientId != other.recipientId) return false
        if (content != other.content) return false
        if (type != other.type) return false
        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (timestamp != other.timestamp) return false
        if (hopCount != other.hopCount) return false
        if (!relayPath.contentEquals(other.relayPath)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + hopCount
        result = 31 * result + relayPath.contentHashCode()
        return result
    }
}
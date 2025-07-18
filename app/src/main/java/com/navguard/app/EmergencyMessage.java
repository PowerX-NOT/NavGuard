package com.navguard.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class EmergencyMessage {
    public enum MessageType {
        REGULAR,
        EMERGENCY,
        SOS,
        RELAY
    }
    
    private String messageId;
    private String senderId;
    private String recipientId;
    private String content;
    private MessageType type;
    private double latitude;
    private double longitude;
    private long timestamp;
    private int hopCount;
    private String[] relayPath;
    
    public EmergencyMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.hopCount = 0;
        this.type = MessageType.REGULAR;
    }
    
    public EmergencyMessage(String content, MessageType type) {
        this();
        this.content = content;
        this.type = type;
    }
    
    public EmergencyMessage(String content, MessageType type, double latitude, double longitude) {
        this(content, type);
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }
    
    public String[] getRelayPath() { return relayPath; }
    public void setRelayPath(String[] relayPath) { this.relayPath = relayPath; }
    
    public boolean hasLocation() {
        return latitude != 0.0 && longitude != 0.0;
    }
    
    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    public String getLocationString() {
        if (hasLocation()) {
            return String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude);
        }
        return "No location";
    }
    
    public void incrementHopCount() {
        this.hopCount++;
    }
    
    public boolean isEmergency() {
        return type == MessageType.EMERGENCY || type == MessageType.SOS;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type.name()).append("] ");
        if (isEmergency()) {
            sb.append("🚨 ");
        }
        sb.append(content);
        if (hasLocation()) {
            sb.append(" 📍 ").append(getLocationString());
        }
        sb.append(" (").append(getFormattedTimestamp()).append(")");
        if (hopCount > 0) {
            sb.append(" [Relayed ").append(hopCount).append("x]");
        }
        return sb.toString();
    }
}
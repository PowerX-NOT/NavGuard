#include "BluetoothSerial.h"
#include <HardwareSerial.h>

BluetoothSerial BT;
HardwareSerial LoRaSerial(2);  // UART2 for RYLR998

#define LORA_RXD    16   // RYLR998 TX → ESP32 RX
#define LORA_TXD    17   // RYLR998 RX ← ESP32 TX
#define BUZZER_PIN  26   // Buzzer signal pin
#define BLUE_LED    2    // Built-in blue LED on ESP32

// ⚙️ Device Config — change per device
String myAddress = "1";         // A = "1", B = "2"
String targetAddress = "2";     // A sends to B, vice versa

void setup() {
  Serial.begin(115200);
  BT.begin("LoRa_Node_" + myAddress);
  LoRaSerial.begin(115200, SERIAL_8N1, LORA_RXD, LORA_TXD);

  // Initialize buzzer and LED
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  pinMode(BLUE_LED, OUTPUT);
  digitalWrite(BLUE_LED, LOW);

  delay(500);
  Serial.println("🛠️ Configuring RYLR998...");

  sendAT("AT+ADDRESS=" + myAddress);
  sendAT("AT+NETWORKID=5");
  sendAT("AT+IPR=115200");
  sendAT("AT+BAND=868000000");

  Serial.println("✅ Ready. Chat via Bluetooth...");
}

void loop() {
  // 🔁 Bluetooth → LoRa
  if (BT.available()) {
    String msg = BT.readStringUntil('\n');
    msg.trim();
    if (msg.length() > 0) {
      Serial.println("📤 BT → LoRa: " + msg);
      
      // Check if this is an acknowledgment message
      if (msg.startsWith("ACK|")) {
        // Forward acknowledgment message to LoRa
        String cmd = "AT+SEND=" + targetAddress + "," + String(msg.length()) + "," + msg + "\r\n";
        LoRaSerial.print(cmd);
        Serial.println("📤 Forwarding ACK to LoRa");
      } else {
        // Regular message - compact format without timestamp:
        // TYPE|CONTENT|LAT|LON|ID|STATUS
        // If incoming has legacy timestamp (7 fields), strip 5th field
        int pipeCount = 0;
        for (int i = 0; i < msg.length(); i++) {
          if (msg.charAt(i) == '|') pipeCount++;
        }
        String out = msg;
        if (pipeCount == 6) { // 7 parts → legacy with timestamp
          int p1 = msg.indexOf('|');
          int p2 = msg.indexOf('|', p1 + 1);
          int p3 = msg.indexOf('|', p2 + 1);
          int p4 = msg.indexOf('|', p3 + 1);
          int p5 = msg.indexOf('|', p4 + 1);
          int p6 = msg.indexOf('|', p5 + 1);
          int p7 = msg.indexOf('|', p6 + 1);
          if (p7 == -1) {
            // Build compact: TYPE|CONTENT|LAT|LON|ID|STATUS (remove TS at parts[4])
            out = msg.substring(0, p4) + String("|") + msg.substring(p5 + 1);
          }
        }
        String cmd = "AT+SEND=" + targetAddress + "," + String(out.length()) + "," + out + "\r\n";
        LoRaSerial.print(cmd);
      }
    }
  }

  // 📡 LoRa → Bluetooth or Buzzer + Blink LED
  if (LoRaSerial.available()) {
    String raw = "";
    while (LoRaSerial.available()) {
      char c = LoRaSerial.read();
      raw += c;
      delay(2);
    }

    raw.trim();
    if (raw.startsWith("+RCV=")) {
      // Parse: +RCV=2,5,Hello,-45,45
      int firstComma = raw.indexOf(',');
      int secondComma = raw.indexOf(',', firstComma + 1);
      int thirdComma = raw.indexOf(',', secondComma + 1);

      if (thirdComma > 0) {
        String message = raw.substring(secondComma + 1, thirdComma);
        Serial.println("📥 Message: " + message);

        blinkLED();  // Blink blue LED once

        // Check if this is an acknowledgment message
        if (message.startsWith("ACK|")) {
          // Forward acknowledgment to Bluetooth
          if (BT.hasClient()) {
            BT.print(message);
            BT.print("\r\n");
            Serial.println("📲 Sent ACK to Bluetooth");
          }
        } else {
          // Regular message - ensure compact format before forwarding to Bluetooth
          String out = message;
          int pc = 0;
          for (int i = 0; i < message.length(); i++) if (message.charAt(i) == '|') pc++;
          if (pc == 6) {
            int p1 = message.indexOf('|');
            int p2 = message.indexOf('|', p1 + 1);
            int p3 = message.indexOf('|', p2 + 1);
            int p4 = message.indexOf('|', p3 + 1);
            int p5 = message.indexOf('|', p4 + 1);
            int p6 = message.indexOf('|', p5 + 1);
            int p7 = message.indexOf('|', p6 + 1);
            if (p7 == -1) {
              out = message.substring(0, p4) + String("|") + message.substring(p5 + 1);
            }
          }
          if (BT.hasClient()) {
            BT.print(out);
            BT.print("\r\n");
            Serial.println("📲 Sent message to Bluetooth");
            
            // Send acknowledgment for received message
            sendAcknowledgment(out);
          } else {
            ringBuzzer();
          }
        }
      }
    }
  }
}

// 🔧 Send AT command and print response
void sendAT(String cmd) {
  LoRaSerial.print(cmd + "\r\n");
  delay(200);
  while (LoRaSerial.available()) {
    String res = LoRaSerial.readStringUntil('\n');
    res.trim();
    if (res.length() > 0 && !res.startsWith("+")) {
      Serial.println("🔧 " + res);
    }
  }
}

// 📨 Send acknowledgment for received message
void sendAcknowledgment(String message) {
  // Extract message ID from format:
  // TYPE|CONTENT|LAT|LON|TIMESTAMP|ID|STATUS
  // We need the substring between 5th and 6th pipes (0-based fields → field 5)
  int pipe1 = message.indexOf('|');
  if (pipe1 < 0) return;
  int pipe2 = message.indexOf('|', pipe1 + 1);
  if (pipe2 < 0) return;
  int pipe3 = message.indexOf('|', pipe2 + 1);
  if (pipe3 < 0) return;
  int pipe4 = message.indexOf('|', pipe3 + 1);
  if (pipe4 < 0) return;
  int pipe5 = message.indexOf('|', pipe4 + 1);
  if (pipe5 < 0) return;
  int pipe6 = message.indexOf('|', pipe5 + 1);
  if (pipe6 < 0) return;

  String messageId = message.substring(pipe5 + 1, pipe6);

  // Trim long IDs to reduce LoRa payload size (match by prefix)
  String shortId = messageId;
  if (shortId.length() > 6) {
    shortId = shortId.substring(0, 6);
  }

  // Send acknowledgment with status code 2 (DELIVERED)
  String ackMessage = String("ACK|") + shortId + "|2";
  String cmd = String("AT+SEND=") + targetAddress + "," + String(ackMessage.length()) + "," + ackMessage + "\r\n";
  LoRaSerial.print(cmd);
  Serial.println("📨 Sent ACK: " + ackMessage);
}

// 🔔 Buzzer alert if BT not connected
void ringBuzzer() {
  Serial.println("🚨 BT not connected. Ringing buzzer!");
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(200);
    digitalWrite(BUZZER_PIN, LOW);
    delay(200);
  }
}

// 💡 Blink blue LED once
void blinkLED() {
  for (int i = 0; i < 2; i++) {
    digitalWrite(BLUE_LED, HIGH);
    delay(150);
    digitalWrite(BLUE_LED, LOW);
    delay(150);
  }
}

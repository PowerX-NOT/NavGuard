#include "BluetoothSerial.h"
#include <HardwareSerial.h>
#include <TinyGPS++.h>

BluetoothSerial BT;
HardwareSerial LoRaSerial(2);  // UART2 for RYLR998
// GPS (NavIC) on UART1
HardwareSerial GPSSerial(1);   // UART1 for NavIC GPS
TinyGPSPlus gps;

#define LORA_RXD    16   // RYLR998 TX → ESP32 RX
#define LORA_TXD    17   // RYLR998 RX ← ESP32 TX
#define BUZZER_PIN  26   // Buzzer signal pin
#define BLUE_LED    2    // Built-in blue LED on ESP32
// NavIC GPS UART1 pins (match navic-gps.ino wiring)
#define GPS_RX_PIN  21   // GPS TX → ESP32 RX (Blue wire)
#define GPS_TX_PIN  22   // GPS RX ← ESP32 TX (Green wire)
// Button input (active low, uses internal pull-up) — wired to GPIO27 -> GND
#define BUTTON_PIN  27
// Flash button (GPIO0) for signal sending
#define FLASH_BUTTON_PIN  0
//tx=green

// Live location state
bool liveEnabled = false;
unsigned long lastLiveSendMs = 0;
const unsigned long liveIntervalMs = 2000;

// Signal sending state
bool signalEnabled = false;
unsigned long lastSignalSendMs = 0;
const unsigned long signalIntervalMs = 1000; // Send signal every 1 second

// Button handling state
bool btnPrev = HIGH;             // Button state tracking
unsigned long pressStartMs = 0;
const unsigned long longPressMs = 3000;
bool longPressFired = false;
int tapCount = 0;
unsigned long lastReleaseMs = 0;
const unsigned long doubleTapWindowMs = 500;
bool singleTapPending = false;

// Flash button state tracking
bool flashBtnPrev = HIGH;
bool flashPressed = false;

// ⚙️ Device Config — change per device
String myAddress = "2";         // A = "1", B = "2"
String targetAddress = "1";     // A sends to B, vice versa

void setup() {
  Serial.begin(115200);
  BT.begin("LoRa_Node_" + myAddress);
  LoRaSerial.begin(115200, SERIAL_8N1, LORA_RXD, LORA_TXD);
  // GPS UART1
  GPSSerial.begin(115200, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  // Initialize buzzer and LED
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  pinMode(BLUE_LED, OUTPUT);
  digitalWrite(BLUE_LED, LOW);

  // Set up button pin with internal pull-up
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  // Set up flash button pin with internal pull-up
  pinMode(FLASH_BUTTON_PIN, INPUT_PULLUP);

  delay(500);
  Serial.println("🛠️ Configuring RYLR998...");

  sendAT("AT+ADDRESS=" + myAddress);
  sendAT("AT+NETWORKID=5");
  sendAT("AT+IPR=115200");
  sendAT("AT+BAND=868000000");

  Serial.println("✅ Ready. Chat via Bluetooth...");
}

void loop() {
  // ---- Feed GPS parser ----
  while (GPSSerial.available() > 0) {
    gps.encode(GPSSerial.read());
  }

  // ---- Flash button handling for signal sending ----
  bool flashBtnNow = digitalRead(FLASH_BUTTON_PIN);
  unsigned long nowMs = millis();
  
  // Flash button press detection (active low)
  if (flashBtnPrev == HIGH && flashBtnNow == LOW && !flashPressed) {
    // Flash button pressed → toggle signal sending
    signalEnabled = !signalEnabled;
    flashPressed = true;
    if (signalEnabled) {
      Serial.println("📡 Signal sending started (Flash button)");
      longBeep(); // long feedback beep
      blinkLED(); // LED feedback
    } else {
      Serial.println("🛑 Signal sending stopped (Flash button)");
      miniBeep(); // short feedback beep
    }
  } else if (flashBtnPrev == LOW && flashBtnNow == HIGH) {
    // Flash button released
    flashPressed = false;
  }
  flashBtnPrev = flashBtnNow;
  
  // ---- Main button handling (active low) ----
  bool btnNow = digitalRead(BUTTON_PIN);
  // Rising/falling edge detection
  if (btnPrev == HIGH && btnNow == LOW) {
    // Press started
    pressStartMs = nowMs;
    longPressFired = false;
  } else if (btnPrev == LOW && btnNow == HIGH) {
    // Released
    unsigned long pressDur = nowMs - pressStartMs;
    if (pressDur < longPressMs) {
      // Consider a tap
      tapCount++;
      if (tapCount == 1) {
        lastReleaseMs = nowMs;
        singleTapPending = true;
      } else if (tapCount == 2) {
        // Double press → stop live/signal
        liveEnabled = false;
        signalEnabled = false;
        tapCount = 0;
        singleTapPending = false;
        doubleBeep(); // 2 beeps feedback
        Serial.println("🛑 Live location and signal sending stopped");
      }
    }
  } else {
    // While held, check long-press
    if (btnNow == LOW && !longPressFired && (nowMs - pressStartMs >= longPressMs)) {
      // Long press → start live location
      liveEnabled = true;
      longPressFired = true;
      tapCount = 0;
      singleTapPending = false;
      // Print NavIC banner when starting live
      Serial.println("\n\n\n**********************************************************************************");
      Serial.println("  Bharat Pi NavIC Shield Test Program");
      Serial.println("  Connected via UART2: GPIO21 (RX), GPIO22 (TX)");
      Serial.println("  Please wait while the NavIC module latches to satellites.");
      Serial.println("  Red LED (PPS) will blink once it latches.");
      Serial.println("  Ensure clear sky visibility.");
      Serial.println("**********************************************************************************\n");
      Serial.println("  Awaiting for NavIC satellite signal latching...\n\n");
      longBeep(); // long feedback beep
      blinkLED(); // LED feedback
    }
    // Handle tap timeout window
    if (tapCount == 1 && (nowMs - lastReleaseMs > doubleTapWindowMs) && !longPressFired) {
      // Single tap timeout passed
      tapCount = 0;
      singleTapPending = false;
    }
  }
  btnPrev = btnNow;

  // ---- Live GPS sending ----
  if (liveEnabled && gps.location.isValid()) {
    if (nowMs - lastLiveSendMs >= liveIntervalMs) {
      lastLiveSendMs = nowMs;
      float lat = gps.location.lat();
      float lon = gps.location.lng();
      String payload = String("LOC|") + String(lat, 7) + "|" + String(lon, 7);
      String cmd = String("AT+SEND=") + targetAddress + "," + String(payload.length()) + "," + payload + "\r\n";
      LoRaSerial.print(cmd);
      Serial.println("📡 Sent LIVE: " + payload);
      miniBeep(); // small tick on each send
    }
  }
  
  // ---- Signal sending ----
  if (signalEnabled) {
    if (nowMs - lastSignalSendMs >= signalIntervalMs) {
      lastSignalSendMs = nowMs;
      String signalPayload = String("SOS from ") + myAddress;
      String cmd = String("AT+SEND=") + targetAddress + "," + String(signalPayload.length()) + "," + signalPayload + "\r\n";
      LoRaSerial.print(cmd);
      Serial.println("🚨 Sent SIGNAL: " + signalPayload);
      miniBeep(); // small tick on each send
    }
  }

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
        String rssi = "";
        String snr = "";
        
        // Extract RSSI and SNR from the raw response
        int fourthComma = raw.indexOf(',', thirdComma + 1);
        if (fourthComma > 0) {
          rssi = raw.substring(thirdComma + 1, fourthComma);
          int fifthComma = raw.indexOf(',', fourthComma + 1);
          if (fifthComma > 0) {
            snr = raw.substring(fourthComma + 1, fifthComma);
          } else {
            snr = raw.substring(fourthComma + 1);
          }
        }
        
        Serial.println("📥 Message: " + message + " | RSSI=" + rssi + " | SNR=" + snr);

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
          // Check if this is a signal message and format with RSSI/SNR
          String out = message;
          if (message.startsWith("SOS from") && rssi != "" && snr != "") {
            // Format: SIGNAL|SOS from X|RSSI|SNR
            out = String("SIGNAL|") + message + "|" + rssi + "|" + snr;
          } else {
            // Regular message - ensure compact format before forwarding to Bluetooth
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
          }
          
          if (BT.hasClient()) {
            BT.print(out);
            BT.print("\r\n");
            Serial.println("📲 Sent message to Bluetooth");
            
            // Send acknowledgment for received message (only for non-signal messages)
            if (!message.startsWith("SOS from")) {
              sendAcknowledgment(out);
            }
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

// 🔊 Long press feedback: single long beep (~600ms)
void longBeep() {
  digitalWrite(BUZZER_PIN, HIGH);
  delay(600);
  digitalWrite(BUZZER_PIN, LOW);
}

// 🔊 Double press feedback: two short beeps
void doubleBeep() {
  for (int i = 0; i < 2; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(150);
    digitalWrite(BUZZER_PIN, LOW);
    delay(150);
  }
}

// 🔊 Mini tick while live is sending
void miniBeep() {
  digitalWrite(BUZZER_PIN, HIGH);
  delay(30);
  digitalWrite(BUZZER_PIN, LOW);
}

#include "BluetoothSerial.h"
#include <HardwareSerial.h>

BluetoothSerial BT;
HardwareSerial LoRaSerial(2);  // UART2 for RYLR998

#define LORA_RXD    16   // RYLR998 TX → ESP32 RX
#define LORA_TXD    17   // RYLR998 RX ← ESP32 TX
#define BUZZER_PIN  26   // Buzzer signal pin
#define BLUE_LED    2    // Built-in blue LED on ESP32

// ⚙️ Device Config — change per device
String myAddress = "2";         // A = "1", B = "2"
String targetAddress = "1";     // A sends to B, vice versa

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
      String cmd = "AT+SEND=" + targetAddress + "," + String(msg.length()) + "," + msg + "\r\n";
      LoRaSerial.print(cmd);
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

        if (BT.hasClient()) {
          BT.print(message);
          BT.print("\r\n");
          Serial.println("📲 Sent to Bluetooth");
        } else {
          ringBuzzer();
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

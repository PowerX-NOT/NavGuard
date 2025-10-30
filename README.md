# NavGuard

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/a3d8a40d7133497caa11051eaac6f1a2)](https://www.codacy.com/manual/kai-morich/SimpleBluetoothTerminal?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=kai-morich/SimpleBluetoothTerminal&amp;utm_campaign=Badge_Grade)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.txt)

**NavGuard** is an offline emergency communication system designed for areas without cellular network or internet connectivity. It enables long-range communication using LoRa radio technology, combines GPS/NavIC positioning, and provides offline mapping capabilities for rescue operations and emergency response.

---

## Overview

NavGuard creates a mesh communication network for emergency situations using:
- **LoRa (Long Range) Radio**: 10-15km direct range, mesh relay capability
- **Bluetooth**: Local connectivity between Android app and hardware device
- **GPS/NavIC**: Satellite positioning for location tracking
- **Offline Maps**: Pre-loaded map files for navigation without internet

### Use Cases
- Mountain rescue operations
- Disaster relief communications
- Remote area expeditions
- Search and rescue missions
- Military and defense applications
- Areas with destroyed telecommunications infrastructure

---

## System Architecture

### Components

#### 1. Android Application
- **Technology**: Kotlin, Jetpack Compose, Material Design 3
- **Features**:
  - Bluetooth connectivity with LoRa devices
  - Real-time GPS location tracking (NavIC/GPS)
  - Chat interface with message status (Sending → Sent → Delivered → Read)
  - Live location sharing with continuous updates
  - Offline map viewer with Mapsforge library
  - Message persistence and chat history
  - Signal strength monitoring (RSSI/SNR)
  - Precision finding mode for signal tracking

#### 2. Hardware Device (ESP32-based)
- **Microcontroller**: ESP32 (dual-core, Bluetooth + WiFi)
- **LoRa Module**: RYLR998 (Reyax) operating at 868MHz
- **GPS Module**: NavIC/GPS compatible module (UART1)
- **Interface**:
  - Bluetooth Serial (115200 baud)
  - Hardware button (GPIO27) for live location control
  - Flash button (GPIO0) for signal sending
  - Buzzer (GPIO26) for audio alerts
  - Blue LED (GPIO2) for visual feedback

#### 3. Communication Protocol
```
Message Format: TYPE|CONTENT|LAT|LON|MESSAGE_ID|STATUS
- TYPE: REGULAR, EMERGENCY, SOS, RELAY
- CONTENT: Message text or "LOC" for live updates
- LAT/LON: Geographic coordinates (0.0 if unavailable)
- MESSAGE_ID: 6-character unique identifier (base36)
- STATUS: 0=Sending, 1=Sent, 2=Delivered, 3=Read

Acknowledgment Format: ACK|MESSAGE_ID|STATUS_CODE
Signal Format: SIGNAL|SOS from X|RSSI|SNR
Control Format: CTRL|LOC_STOP (stops live location)
```

---

## Features

### Emergency Messaging
- **Regular Messages**: Text communication with delivery confirmation
- **Emergency Messages**: Priority messages with GPS location
- **SOS Alerts**: Critical emergency signals with automatic location
- **Message Status Tracking**: Real-time delivery and read receipts
- **Message Persistence**: Chat history saved per device

### Live Location Sharing
- **Continuous Transmission**: GPS coordinates sent every 2 seconds
- **Two Modes**:
  - **App-initiated**: Share from Android app
  - **Hardware-initiated**: Long-press button (3s) on ESP32 device
- **NavIC/GPS Support**: High-accuracy positioning with Indian satellite system
- **Visual Indicators**: Animated status banner and map markers
- **Stop Controls**: Double-press button or tap "Stop" in app

### Signal Tracking
- **Direction Finding**: Send periodic signals for rescue location
- **Signal Strength**: Real-time RSSI (Received Signal Strength Indicator)
- **Signal Quality**: SNR (Signal-to-Noise Ratio) monitoring
- **Precision Finding Mode**: Dedicated screen for signal tracking and triangulation

### Offline Mapping
- **Map Format**: Mapsforge .map files
- **Map Sources**:
  - Bundled world.map (low detail, global coverage)
  - Downloadable regional maps via in-app browser
- **Features**:
  - Real-time location marker (updates every second)
  - Dual marker support (your location + sender's location)
  - Distance calculation between markers
  - Smooth zoom and pan (levels 5-20)
  - Map scale bar and zoom controls
- **Map Downloads**: Integrated download manager for OpenAndroMaps

### Mesh Network Capability
- **Message Relay**: Automatic forwarding through intermediate devices
- **Extended Range**: Coverage beyond direct line-of-sight
- **Hop Count Tracking**: Monitor relay chain length
- **Relay Path Recording**: Track message routing (future feature)

### Device Management
- **Auto-connection**: Automatically connects to paired devices
- **Device Discovery**: Scan and pair new Bluetooth devices
- **Connection Status**: Real-time indicator (green = connected)
- **Multiple Device Support**: Chat history per paired device
- **Background Service**: Maintains connection when app is backgrounded

---

## Hardware Requirements

### Core Components
| Component | Specification | Purpose |
|-----------|--------------|---------|
| ESP32 | Dual-core, 240MHz, 520KB RAM | Main controller |
| RYLR998 | LoRa transceiver, 868MHz, 115200 baud | Long-range radio |
| GPS Module | NavIC/GPS compatible, UART 115200 | Positioning |
| Buzzer | Active 5V | Audio alerts |
| LED | Built-in blue LED (GPIO2) | Visual feedback |
| Button | Momentary push button | User controls |
| Power Supply | 3.3V-5V, 500mA+ | Device power |

### Wiring Diagram
```
ESP32 Pin Assignments:
├── UART2 (LoRa - RYLR998)
│   ├── GPIO16 (RX) ← LoRa TX
│   └── GPIO17 (TX) → LoRa RX
├── UART1 (GPS/NavIC)
│   ├── GPIO21 (RX) ← GPS TX (Blue)
│   └── GPIO22 (TX) → GPS RX (Green)
├── GPIO27 → Button (Pull-up, Active-low)
├── GPIO0  → Flash Button (Pull-up, Active-low)
├── GPIO26 → Buzzer Signal
└── GPIO2  → Blue LED
```

### LoRa Configuration (RYLR998)
```c
AT+ADDRESS=2        // Device address (1 or 2)
AT+NETWORKID=5      // Network ID (match all devices)
AT+IPR=115200       // UART baud rate
AT+BAND=868000000   // Frequency: 868MHz (Europe)
```

### Alternative Frequency Bands
- **433MHz**: Asia/Africa
- **915MHz**: Americas/Australia
- **868MHz**: Europe (default)

---

## Software Requirements

### Development Environment
- **Android Studio**: 2023.1.1 (Hedgehog) or newer
- **Android SDK**: API 21 (minimum) to API 34 (target)
- **Kotlin**: 1.9.20
- **Gradle**: 8.1.4
- **JDK**: 8 or higher

### Key Dependencies
```gradle
// UI Framework
Jetpack Compose 2024.02.00
Material Design 3
Navigation Compose 2.7.6

// Bluetooth & Location
Core KTX 1.12.0
Lifecycle Runtime KTX 2.7.0

// Offline Maps
Mapsforge 0.25.0 (core, map, map-reader, themes)
AndroidSVG 1.4
Jsoup 1.16.1 (map downloads)

// Data Persistence
Gson 2.10.1
```

### Permissions Required
```xml
<!-- Bluetooth (Classic) -->
android.permission.BLUETOOTH (API ≤32)
android.permission.BLUETOOTH_ADMIN (API ≤30)
android.permission.BLUETOOTH_CONNECT (API ≥31)
android.permission.BLUETOOTH_SCAN (API ≥31)

<!-- Location (GPS) -->
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_COARSE_LOCATION

<!-- Services -->
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_LOCATION
android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING
android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE

<!-- Other -->
android.permission.VIBRATE
android.permission.POST_NOTIFICATIONS
android.permission.INTERNET (map downloads only)
```

---

## Installation & Setup

### Building the Android App

#### Prerequisites
1. Install [Android Studio](https://developer.android.com/studio)
2. Clone or download this repository
3. Ensure Android SDK API levels 21-34 are installed

#### Build Steps
```bash
# Navigate to project directory
cd NavGuard

# Sync Gradle (automatic in Android Studio)
# Or via command line:
./gradlew --refresh-dependencies

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing key)
./gradlew assembleRelease
```

#### Output Locations
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

#### Install on Device
```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio "Run" button
# Or transfer APK to device and install manually
```

### Programming the Hardware

#### Arduino/ESP32 Setup
1. Install [Arduino IDE](https://www.arduino.cc/en/software) 2.0+
2. Add ESP32 board support:
   - File → Preferences → Additional Board Manager URLs
   - Add: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. Install ESP32 board package (Tools → Board → Boards Manager)
4. Install libraries:
   - TinyGPS++ (for GPS parsing)
   - HardwareSerial (built-in)

#### Upload Process
1. Open `navguard/navguard.ino` in Arduino IDE
2. Configure device address:
   ```c
   String myAddress = "2";      // Change to "1" or "2"
   String targetAddress = "1";  // Opposite of myAddress
   ```
3. Select board: "ESP32 Dev Module"
4. Select port: Your ESP32's USB port
5. Click "Upload"

#### Device Configuration
- **Device A**: Set `myAddress = "1"`, `targetAddress = "2"`
- **Device B**: Set `myAddress = "2"`, `targetAddress = "1"`
- Multiple devices can form mesh network (relay capability)

---

## Usage Guide

### First-Time Setup

1. **Power on ESP32 device**
   - Blue LED should blink during startup
   - Buzzer confirms initialization

2. **Enable Bluetooth on Android phone**
   - Settings → Bluetooth → On

3. **Launch NavGuard app**
   - Grant all requested permissions
   - Bluetooth, Location, Notifications

4. **Pair Device**
   - Tap "SCAN" to discover devices
   - Select "LoRa_Node_X" from available devices
   - Tap to pair (may require pairing confirmation)

5. **Connect**
   - After pairing, tap device to connect
   - Status bar shows "Connected to device" (green icon)

### Sending Messages

#### Regular Message
1. Type message in text field
2. Tap send button (▶)
3. Message shows status: ⏳ Sending → ✔ Sent → ✔✔ Delivered

#### Emergency Message
1. Compose message describing emergency
2. Long-press send button
3. App automatically attaches GPS coordinates
4. Message type shown as "🚨 EMERGENCY"

#### SOS Alert (Hardware)
1. Press and hold hardware button for 3+ seconds
2. Buzzer gives long beep
3. Device sends "SOS from [ADDRESS]" every second
4. Press flash button to stop

### Live Location Sharing

#### From Android App
1. Connect to device
2. Tap location icon (📍) in message input area
3. Icon turns green, status banner shows "Live location sharing"
4. GPS coordinates sent every 2 seconds
5. Tap "Stop" in banner or press icon again to stop

#### From Hardware Device
1. Press and hold button for 3+ seconds
2. Long beep confirms activation
3. NavIC/GPS coordinates sent every 2 seconds as "LOC|lat|lon"
4. Double-press button to stop

#### Receiving Live Location
- Automatically activates when peer starts sharing
- Status banner shows "Live location receiving"
- Coordinates update in real-time
- Tap "Map" to view on offline map
- Two markers: Red (your location), Blue (sender's location)

### Viewing Offline Maps

1. **Open Map**
   - Tap map icon (🗺️) in top bar
   - Or tap "Map" button in live location banner

2. **Initial Map**
   - Bundled world.map loads (low detail)
   - Your location shown with red pin marker
   - Auto-centers on your GPS position

3. **Download Detailed Maps**
   - Tap download icon (⬇️) in map screen
   - Opens web browser to OpenAndroMaps
   - Download .map file for your region
   - Return to app

4. **Load Downloaded Map**
   - Tap folder icon (📁) in map screen
   - Select downloaded map from list
   - Map reloads with high detail
   - Position markers remain

5. **Map Controls**
   - Pinch to zoom (levels 5-20)
   - Pan by dragging
   - Tap location icon (📍) to re-center on your position
   - Scale bar shows distance

### Signal Tracking (Direction Finding)

1. **Sender**: Press flash button on ESP32
   - Sends "SOS from [ADDRESS]" signal every second
   - Continues until flash button pressed again

2. **Receiver**: Tap "Track" in signal banner
   - Opens Precision Finding screen
   - Shows real-time RSSI and SNR values
   - Move to maximize signal strength
   - Guides you toward sender

### Hardware Button Controls

| Action | Duration | Function |
|--------|----------|----------|
| Long Press | 3+ seconds | Start live location (NavIC/GPS) |
| Double Press | < 500ms between taps | Stop live location |
| Flash Button | Single press | Toggle signal sending (SOS) |

**Feedback Indicators:**
- **Long beep**: Live location started
- **Two short beeps**: Live location stopped
- **Mini tick**: Location/signal sent successfully
- **Three beeps**: Incoming message (no Bluetooth connected)
- **LED blink**: Message received

---

## Technical Details

### Message Flow

#### Outbound (App → LoRa)
```
User Input → Android App → Bluetooth Serial → ESP32
→ LoRa Module → Radio Transmission → Remote Device
```

#### Inbound (LoRa → App)
```
Radio Reception → LoRa Module → ESP32 → Parse +RCV
→ Bluetooth Serial → Android App → Display + ACK
```

### Acknowledgment System
1. Sender transmits message with status=0 (Sending)
2. Sender updates to status=1 (Sent) after transmission
3. Receiver sends ACK with status=2 (Delivered)
4. Sender updates message status to Delivered
5. User views message, sender receives ACK with status=3 (Read)

### Live Location Mechanism
- **Android App Mode**: Foreground service (LocationService) sends every 2s
- **Hardware Mode**: ESP32 reads NavIC/GPS, formats "LOC|lat|lon", transmits via LoRa
- **Receiver**: Parses LOC message, displays in status banner, updates map marker
- **Efficiency**: Compact format reduces radio airtime (critical for LoRa)

### GPS/NavIC Integration
- **NavIC**: Indian Regional Navigation Satellite System (7 satellites)
- **Compatibility**: Dual GPS/NavIC modules supported
- **Accuracy**: Typically 3-5 meters in open sky
- **Update Rate**: 1-10Hz depending on module
- **Latching Time**: 30-120 seconds for first fix

### LoRa Characteristics
- **Range**: 10-15km line-of-sight, 2-5km urban
- **Data Rate**: 300bps - 50kbps (configurable)
- **Power**: ~100mW transmit, ~10mA receive
- **Modulation**: Chirp Spread Spectrum (CSS)
- **Advantages**: Long range, low power, penetrates obstacles
- **Limitations**: Low data rate, duty cycle restrictions (EU: 1% for 868MHz)

### Offline Map Format
- **Format**: Mapsforge .map (binary vector maps)
- **Sources**:
  - [OpenAndroMaps](https://www.openandromaps.org/) - Detailed regional maps
  - [Mapsforge Downloads](https://download.mapsforge.org/) - Official repository
- **Features**: Multi-language, elevation contours, hiking trails
- **Size**: 50MB-500MB per country, ~2GB for world.map (low detail)
- **Rendering**: On-device vector rendering (smooth zoom, small file size)

---

## File Structure

```
NavGuard/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # App permissions & components
│   │   ├── assets/world.map             # Bundled world map (low detail)
│   │   ├── java/com/navguard/app/
│   │   │   ├── MainActivity.kt          # Main entry point, navigation
│   │   │   ├── EmergencyMessage.kt      # Message data model & logic
│   │   │   ├── LocationManager.kt       # GPS location handling
│   │   │   ├── LocationService.kt       # Background location service
│   │   │   ├── SerialService.kt         # Bluetooth serial communication
│   │   │   ├── SerialSocket.kt          # Bluetooth socket wrapper
│   │   │   ├── SerialListener.kt        # Serial event callbacks
│   │   │   ├── SerialBus.kt             # App-wide message event bus
│   │   │   ├── PersistenceManager.kt    # Chat & settings storage
│   │   │   ├── MapDownloadActivity.kt   # In-app map browser
│   │   │   ├── BluetoothUtil.kt         # Bluetooth helpers
│   │   │   ├── TextUtil.kt              # Text formatting utilities
│   │   │   ├── Constants.kt             # App constants
│   │   │   └── ui/
│   │   │       ├── screens/
│   │   │       │   ├── DevicesScreen.kt         # Device list & pairing
│   │   │       │   ├── EmergencyTerminalScreen.kt # Chat interface
│   │   │       │   ├── OfflineMapScreen.kt      # Map viewer
│   │   │       │   └── PrecisionFindingScreen.kt # Signal tracking
│   │   │       ├── components/
│   │   │       │   └── StatusBar.kt             # Reusable status bar
│   │   │       └── theme/
│   │   │           ├── Color.kt                 # Color palette
│   │   │           ├── Theme.kt                 # Material theme
│   │   │           └── Type.kt                  # Typography
│   │   └── res/
│   │       ├── drawable/                 # Icons & images
│   │       ├── mipmap/                   # App launcher icons
│   │       ├── values/
│   │       │   ├── strings.xml           # Text resources
│   │       │   ├── colors.xml            # Color definitions
│   │       │   ├── styles.xml            # UI styles
│   │       │   └── arrays.xml            # String arrays
│   │       └── ...
│   └── build.gradle                      # App dependencies & config
├── navguard/
│   └── navguard.ino                      # Arduino ESP32 firmware
├── gradle/                               # Gradle wrapper files
├── build.gradle                          # Project-level build config
├── settings.gradle                       # Project settings
├── gradlew / gradlew.bat                # Gradle wrapper scripts
├── LICENSE.txt                           # MIT License
└── README.md                             # This file
```

---

## Troubleshooting

### Android App Issues

#### Bluetooth Connection Fails
- **Check**: Bluetooth enabled on phone
- **Check**: Device paired (appears in "Paired Devices" list)
- **Try**: Unpair and re-pair device
- **Try**: Restart Bluetooth: Settings → Bluetooth → Toggle off/on
- **Check**: Device not connected to another phone

#### GPS Not Working
- **Check**: Location permission granted (Fine Location)
- **Check**: Location services enabled: Settings → Location → On
- **Try**: Go outdoors for clear sky view
- **Wait**: First GPS fix can take 30-120 seconds
- **Check**: GPS icon in status bar (should show coordinates)

#### Messages Not Sending
- **Check**: Bluetooth icon green (connected)
- **Check**: Device within LoRa range (10-15km line-of-sight)
- **Check**: Message not too long (LoRa payload limit ~240 bytes)
- **Try**: Restart app and reconnect

#### App Crashes on Startup
- **Try**: Clear app data: Settings → Apps → NavGuard → Storage → Clear Data
- **Try**: Reinstall app
- **Check**: Android version (minimum API 21 / Android 5.0)
- **Check**: Logcat for error messages

#### Maps Not Loading
- **Check**: Storage permission granted
- **Check**: Map file has .map extension
- **Check**: Map file not corrupted (re-download)
- **Try**: Use bundled world.map (folder icon → select world.map)

### Hardware Issues

#### ESP32 Not Responding
- **Check**: Power supply adequate (5V, 500mA+)
- **Check**: USB cable data capable (not charge-only)
- **Try**: Press RESET button on ESP32
- **Check**: Blue LED blinks on startup
- **Check**: Serial monitor shows initialization messages

#### LoRa Not Transmitting
- **Check**: RYLR998 wired correctly (RX/TX not swapped)
- **Check**: LoRa antenna connected (module can be damaged without antenna)
- **Check**: Device address configured (AT+ADDRESS)
- **Check**: Network ID matches (AT+NETWORKID=5)
- **Try**: Test with AT commands via Serial Monitor

#### GPS Not Getting Fix
- **Check**: GPS module powered (LED blinking)
- **Check**: GPS antenna has clear sky view (no metal/concrete above)
- **Wait**: NavIC cold start can take 5-10 minutes
- **Check**: GPS TX/RX wires not swapped (TX→RX, RX→TX)
- **Try**: Different location (away from buildings)

#### Buzzer Always On / Not Working
- **Check**: Buzzer wiring (GPIO26 → Buzzer+, GND → Buzzer-)
- **Check**: Active buzzer used (not passive piezo)
- **Try**: Swap buzzer polarity
- **Check**: GPIO26 not shorted to ground

### Communication Issues

#### Short Range (< 1km)
- **Check**: LoRa frequency correct for region (868MHz EU, 915MHz US)
- **Check**: Antenna properly connected (SMA connector tight)
- **Try**: Elevate devices (higher = better range)
- **Check**: Line-of-sight path (obstacles reduce range)
- **Check**: Other LoRa devices causing interference

#### Messages Delayed
- **Normal**: LoRa duty cycle limits (1% for 868MHz in EU)
- **Check**: Too many devices transmitting simultaneously
- **Try**: Reduce live location frequency (increase interval in code)

#### Garbled Messages
- **Check**: UART baud rate matches (115200)
- **Check**: LoRa network ID matches all devices
- **Check**: Message format correct (TYPE|CONTENT|LAT|LON|ID|STATUS)
- **Try**: Power cycle both devices

---

## Advanced Configuration

### Modify LoRa Parameters

Edit `navguard.ino`:
```c
// Change frequency band (match your region)
sendAT("AT+BAND=868000000");  // 868MHz (EU)
// sendAT("AT+BAND=915000000");  // 915MHz (US/AU)
// sendAT("AT+BAND=433000000");  // 433MHz (Asia)

// Adjust transmit power (5-20dBm)
sendAT("AT+POWER=20");  // Maximum power

// Change spreading factor (5-11, higher=longer range, slower)
sendAT("AT+PARAMETER=12,7,1,7");  // BW=125kHz, SF=7, CR=1, Preamble=7
```

### Modify Live Location Frequency

Edit `navguard.ino`:
```c
const unsigned long liveIntervalMs = 2000;  // Default: 2 seconds
// Increase to 5000 for 5-second intervals (reduces radio usage)
```

Edit `LocationService.kt`:
```kotlin
private const val LOCATION_UPDATE_INTERVAL = 5000L  // 5 seconds
```

### Add More Devices to Network
1. Flash firmware to additional ESP32 units
2. Set unique address for each: `myAddress = "3"`, `"4"`, etc.
3. Set `targetAddress` to recipient (or broadcast address in future)
4. All devices on same `NETWORKID` can communicate
5. Messages automatically relay through intermediate devices

### Customize Message Types

Edit `EmergencyMessage.kt`:
```kotlin
enum class MessageType {
    REGULAR,
    EMERGENCY,
    SOS,
    RELAY,
    // Add custom types:
    WEATHER,
    STATUS,
    PHOTO  // Future: image transmission support
}
```

### Enable Mesh Relay (Future)
Currently, relay capability exists but requires manual configuration. To enable automatic mesh relay:
1. Modify `navguard.ino` to track received message IDs
2. Check if message already seen (avoid loops)
3. Re-transmit message with incremented hop count
4. Add relay path to message format

---

## Development Roadmap

### Planned Features
- [ ] Automatic mesh routing algorithm
- [ ] End-to-end encryption (AES-128)
- [ ] Group messaging (broadcast to multiple devices)
- [ ] Voice message recording and playback
- [ ] Image compression and transmission
- [ ] Battery level monitoring
- [ ] Solar charging integration
- [ ] Web-based configuration interface
- [ ] Desktop companion app (Windows/Linux/Mac)
- [ ] Integration with emergency services (API)

### Known Limitations
- LoRa duty cycle restrictions in regulated bands
- No end-to-end encryption (messages visible to relays)
- Single-hop relay only (no multi-hop routing)
- Limited message size (~240 bytes)
- No message queue (one message at a time)
- No image/file transfer capability

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/YourFeature`)
3. **Commit** your changes (`git commit -m 'Add YourFeature'`)
4. **Push** to the branch (`git push origin feature/YourFeature`)
5. **Open** a Pull Request

### Coding Standards
- **Kotlin**: Follow official [Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- **Arduino**: Use consistent indentation (2 spaces)
- **Comments**: Document complex logic and hardware interactions
- **Testing**: Test on physical hardware before submitting

---

## Credits & Attribution

### Original Project
This project is based on **SimpleBluetoothTerminal** by Kai Morich:
- Repository: [kai-morich/SimpleBluetoothTerminal](https://github.com/kai-morich/SimpleBluetoothTerminal)
- License: MIT

### Libraries & Tools
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI framework
- [Mapsforge](https://github.com/mapsforge/mapsforge) - Offline map rendering
- [TinyGPS++](https://github.com/mikalhart/TinyGPSPlus) - GPS parsing library
- [RYLR998](https://reyax.com/products/rylr998/) - LoRa transceiver module
- [OpenAndroMaps](https://www.openandromaps.org/) - Offline map data
- [Material Design](https://m3.material.io/) - UI design system

### Hardware Inspiration
- **NavIC**: Indian Regional Navigation Satellite System by ISRO
- **Bharat Pi**: NavIC shield for ESP32 by [Bharat Pi](https://bharatpi.net/)

---

## License

This project is licensed under the **MIT License**. See [LICENSE.txt](LICENSE.txt) for full details.

```
MIT License

Copyright (c) 2019 Kai Morich (original SimpleBluetoothTerminal)
Copyright (c) 2025 NavGuard Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Support & Contact

### Issue Reporting
- **GitHub Issues**: Report bugs and feature requests on GitHub
- **Logs**: Include Android logcat output when reporting crashes
- **Hardware**: Specify ESP32 model and component versions

### Resources
- [Android Developer Documentation](https://developer.android.com/)
- [ESP32 Documentation](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/)
- [LoRaWAN Specification](https://lora-alliance.org/resource_hub/lorawan-specification-v1-0-3/)
- [Mapsforge Map Format](https://github.com/mapsforge/mapsforge/blob/master/docs/Specification-Binary-Map-File.md)

---

## Disclaimer

This software is provided for **educational and emergency preparedness purposes**. Users are responsible for:
- Complying with local radio transmission regulations
- Obtaining necessary licenses for LoRa operation
- Ensuring proper usage in emergency situations
- Not relying solely on this system for life-safety applications

**Radio Regulations**: LoRa operates in ISM (Industrial, Scientific, Medical) bands. Check your country's regulations:
- **Europe**: 868MHz (ETSI EN300.220, 1% duty cycle)
- **USA**: 915MHz (FCC Part 15, unlicensed)
- **Asia**: 433MHz (varies by country, license may be required)

**Emergency Use**: This system should complement, not replace, professional emergency communication systems. Always contact official emergency services (911, 112, etc.) when possible.

---

**Version**: 1.0
**Last Updated**: January 2025
**Status**: Active Development

Made with ❤️ for emergency preparedness and rescue operations.

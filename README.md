[![Codacy Badge](https://api.codacy.com/project/badge/Grade/a3d8a40d7133497caa11051eaac6f1a2)](https://www.codacy.com/manual/kai-morich/SimpleBluetoothTerminal?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=kai-morich/SimpleBluetoothTerminal&amp;utm_campaign=Badge_Grade)

# NavGuard

This Android app provides an emergency communication interface for IoT devices using LoRa, Bluetooth, and Arduino microcontrollers to enable offline communication in areas without mobile network or internet connectivity.

## Features

- **Emergency Messaging**: Send emergency messages with GPS coordinates via LoRa
- **SOS Alerts**: Hold SOS button for 5 seconds to send automatic emergency alert with location
- **Mesh Network**: Support for message relaying through intermediate devices for long-distance communication
- **Offline Communication**: Works without mobile network or internet connectivity
- **GPS Integration**: NavIC/GPS module integration for location services
- **Bluetooth Connectivity**: Connects to Arduino-based LoRa devices via Bluetooth
- **Audio Alerts**: Buzzer notifications for unpaired devices receiving messages

## System Architecture

1. **Mobile App** (Android) - User interface for messaging and emergency alerts
2. **Arduino Device** - LoRa transceiver with Bluetooth, GPS, and buzzer
3. **LoRa Network** - 10-15km range direct communication or mesh networking
4. **Emergency Services** - Integration with rescue teams and base stations

## Hardware Requirements

- Arduino microcontroller (ESP32 recommended)
- LoRa module (SX1276/SX1278)
- Bluetooth module
- NavIC/GPS module
- Buzzer for audio alerts
- SOS button
- Power management system

## Message Types

- **REGULAR**: Standard text messages
- **EMERGENCY**: Emergency messages with optional GPS coordinates
- **SOS**: Critical emergency alerts with automatic GPS location
- **RELAY**: Messages forwarded through mesh network

## Usage

1. Pair your Android device with the NavGuard emergency device via Bluetooth
2. Use the app to send regular messages or emergency alerts
3. Hold the SOS button for 5 seconds to trigger automatic emergency alert with GPS
4. Messages are transmitted via LoRa to other devices in range (10-15km)
5. For longer distances, messages are relayed through mesh network
6. Unpaired devices will sound buzzer alerts for incoming messages

## Permissions Required

- Bluetooth and Bluetooth Admin
- Location (Fine and Coarse) for GPS functionality
- Vibration for emergency alerts
- Foreground service for background operation

## Based on SimpleBluetoothTerminal

This project is adapted from the SimpleBluetoothTerminal by Kai Morich, extending it for emergency communication use cases.

## Compilation and Setup Guide

### Prerequisites

1. **Android Studio**: Download and install the latest version of Android Studio from [developer.android.com](https://developer.android.com/studio)
2. **Android SDK**: Ensure you have Android SDK API level 18 (minimum) to 34 (target) installed
3. **Java Development Kit (JDK)**: JDK 8 or higher
4. **Git**: For cloning the repository (optional)

### Step-by-Step Compilation

#### 1. Clone or Download the Project
```bash
git clone <repository-url>
cd NavGuard
```
Or download the ZIP file and extract it to your desired location.

#### 2. Open Project in Android Studio
1. Launch Android Studio
2. Click "Open an existing Android Studio project"
3. Navigate to the project folder and select it
4. Wait for Android Studio to sync the project and download dependencies

#### 3. Configure SDK and Build Tools
1. Go to **File → Project Structure** (or press Ctrl+Alt+Shift+S)
2. Under **Project Settings → Project**, ensure:
   - **Android Gradle Plugin Version**: 8.2.1
   - **Gradle Version**: 8.2
3. Under **Modules → app**, verify:
   - **Compile SDK Version**: 34
   - **Build Tools Version**: Latest available
   - **Source Compatibility**: Java 8
   - **Target Compatibility**: Java 8

#### 4. Sync Project with Gradle Files
1. Click **File → Sync Project with Gradle Files**
2. Wait for the sync to complete
3. Resolve any dependency issues if they appear

#### 5. Build the Project

**Option A: Using Android Studio GUI**
1. Click **Build → Make Project** (or press Ctrl+F9)
2. Wait for the build to complete
3. Check the **Build** tab at the bottom for any errors

**Option B: Using Command Line**
```bash
# Navigate to project root directory
cd /path/to/NavGuard

# For Windows
gradlew.bat build

# For Linux/Mac
./gradlew build
```

#### 6. Generate APK

**Debug APK (for testing):**
```bash
# Command line
./gradlew assembleDebug

# Or in Android Studio: Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Release APK (for distribution):**
```bash
./gradlew assembleRelease
```

The APK files will be generated in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Installation and Testing

#### 1. Install on Device

**Via Android Studio:**
1. Connect your Android device via USB
2. Enable **Developer Options** and **USB Debugging** on your device
3. Click the **Run** button (green triangle) in Android Studio
4. Select your device from the list

**Via ADB (Android Debug Bridge):**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Manual Installation:**
1. Transfer the APK file to your Android device
2. Enable **Install from Unknown Sources** in device settings
3. Open the APK file and install

#### 2. Grant Permissions
After installation, the app will request the following permissions:
- **Bluetooth**: For connecting to LoRa devices
- **Location**: For GPS functionality
- **Vibration**: For emergency alerts
- **Notifications**: For background service alerts

### Troubleshooting Common Issues

#### Build Errors

**"SDK location not found":**
1. Create `local.properties` file in project root
2. Add: `sdk.dir=/path/to/your/Android/Sdk`

**"Failed to resolve dependencies":**
1. Check internet connection
2. Try **File → Invalidate Caches and Restart**
3. Update Android Studio and SDK tools

**"Minimum supported Gradle version":**
1. Update `gradle/wrapper/gradle-wrapper.properties`
2. Ensure Gradle version matches Android Gradle Plugin requirements

#### Runtime Issues

**Bluetooth permissions denied:**
- Ensure all Bluetooth permissions are granted in app settings
- For Android 12+, specifically grant "Nearby devices" permission

**GPS not working:**
- Enable location services on device
- Grant precise location permission to the app
- Test outdoors for better GPS signal

**App crashes on startup:**
- Check device logs: `adb logcat | grep NavGuard`
- Ensure minimum Android version (API 18 / Android 4.3)

### Development Environment Setup

#### For Contributors

1. **Code Style**: Follow Android coding conventions
2. **Testing**: Test on multiple Android versions (minimum API 18)
3. **Debugging**: Use Android Studio debugger and logcat
4. **Version Control**: Use meaningful commit messages

#### Recommended Testing Devices
- **Minimum**: Android 4.3 (API 18) device
- **Target**: Android 14 (API 34) device
- **Hardware**: Device with Bluetooth 2.0+ support
- **GPS**: Device with GPS/GNSS capability

### Building for Production

#### 1. Create Signing Key
```bash
keytool -genkey -v -keystore emergency-release-key.keystore -alias emergency_key -keyalg RSA -keysize 2048 -validity 10000
```

#### 2. Configure Signing in `app/build.gradle`
```gradle
android {
    signingConfigs {
        release {
            storeFile file('emergency-release-key.keystore')
            storePassword 'your_store_password'
            keyAlias 'emergency_key'
            keyPassword 'your_key_password'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}
```

#### 3. Build Signed APK
```bash
./gradlew assembleRelease
```

### Hardware Integration Notes

The app is designed to work with Arduino-based LoRa devices. Ensure your hardware:

1. **Bluetooth Module**: HC-05, HC-06, or ESP32 Bluetooth
2. **LoRa Module**: SX1276, SX1278, or compatible
3. **GPS Module**: NEO-6M, NEO-8M, or NavIC-compatible
4. **Communication Protocol**: 9600 baud rate (default)
5. **Message Format**: `TYPE|CONTENT|LAT|LON|TIMESTAMP`

For detailed hardware setup and Arduino code examples, refer to the hardware documentation in the `/hardware` directory (to be added).

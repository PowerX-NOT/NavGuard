[![Codacy Badge](https://api.codacy.com/project/badge/Grade/a3d8a40d7133497caa11051eaac6f1a2)](https://www.codacy.com/manual/kai-morich/SimpleBluetoothTerminal?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=kai-morich/SimpleBluetoothTerminal&amp;utm_campaign=Badge_Grade)

# LoRa Emergency Communication System

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

1. Pair your Android device with the LoRa emergency device via Bluetooth
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

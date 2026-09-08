BLE MIDI for Android
====================
[![Build Status](https://jitpack.io/v/kshoji/BLE-MIDI-for-Android.svg)](https://jitpack.io/#kshoji/BLE-MIDI-for-Android)

MIDI over Bluetooth LE library for Android `API Level 18`(4.3, JellyBean) or later

- Protocol compatible with [Apple Bluetooth Low Energy MIDI Specification](https://developer.apple.com/bluetooth/Apple-Bluetooth-Low-Energy-MIDI-Specification.pdf).
    - The app can be connected with iOS 8 / OS X Yosemite MIDI apps, and BLE MIDI devices.
- BLE Central function
    - `Central` means `BLE MIDI Device's client`.
- BLE Peripheral function
    - `Peripheral` means `BLE MIDI Device`.

Requirements
------------

- BLE Central function needs:
    - Bluetooth LE(4.0) support
    - `API Level 18`(4.3, JellyBean) or above
        - Bluetooth Pairing function needs `API Level 19`(4.4, KitKat) or above
- BLE Peripheral function needs:
    - Bluetooth LE(4.0) support
    - Bluetooth LE Peripheral support(Nexus 5 with custom ROM, Nexus 6, Nexus 9, etc.)
    - `API Level 21`(5.0, Lollipop) or above

Repository Overview
-------------------

- Library Project: `library`
- Sample Project: `sample`
    - Includes `BleMidiCentralActivity`, and `BleMidiPeripheralActivity` examples.

Usage of the library
--------------------

For the detail, see the [wiki](https://github.com/kshoji/BLE-MIDI-for-Android/wiki).

High-density MIDI transfer
--------------------------

When the send queue is congested, packing every pending message into one GATT write tends to drop packets. This library therefore:

- Packs at most **6 MIDI messages** per BLE packet (and never exceeds the negotiated MTU / `getBufferSize()`)
- Sends **SysEx in its own packets** (never mixed with channel messages)
- Retries Android 13+ writes that fail with `BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY` (201)

The tradeoff is a small increase in latency under load, in exchange for fewer lost notes / CCs. Tune with `MidiOutputDevice.setMaxMessagesPerPacket(int)` (smaller = more reliable, more latency).

RPN / NRPN value width
----------------------

`onRPNMessage` / `onNRPNMessage` expose a combined `value` that may be 7-bit or 14-bit. The parser also delivers every RPN/NRPN controller through `onMidiControlChange` (`CC 101/100/6/38` or `CC 99/98/6/38`). To rebuild MIDI bytes, emit those CCs as-is. To tell 7-bit from 14-bit Data Entry, feed each `onMidiControlChange` into `RpnNrpnValueWidthTracker` and read `getValueWidth(channel)` when the controller is CC 6 or CC 38.

LICENSE
=======
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

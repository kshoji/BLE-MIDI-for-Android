BLE MIDI for Android
====================
[![Build Status](https://travis-ci.org/kshoji/BLE-MIDI-for-Android.svg?branch=master)](https://travis-ci.org/kshoji/BLE-MIDI-for-Android)

MIDI over Bluetooth LE driver for Android 4.4 (API Level 19) or later

- Protocol compatible with [Apple Bluetooth Low Energy MIDI Specification](https://developer.apple.com/bluetooth/Apple-Bluetooth-Low-Energy-MIDI-Specification.pdf).
    - The app can be connected with iOS devices.
- BLE Central function
    - `Central` means `BLE MIDI Device's client`.
    - The specification needs `Bluetooth Bonding(Pairing)` feature that is implemented at Android KitKat(API Level 19), so this library also needs `API Level 19`.
- BLE Peripheral function
    - `Peripheral` means `BLE MIDI Device`.
    - The Peripheral function is introduced at `API Level 21`. 
    - Currently, the bonding feature is not implemented.

Requirements
------------

- BLE Central function needs:
    - Bluetooth LE(4.0) support
    - API Level 19 or above
- BLE Peripheral function needs:
    - Bluetooth LE(4.0) support
    - Bluetooth LE Peripheral support(Nexus 5 with custom ROM, Nexus 6, Nexus 9, etc.)
    - API Level 21 or above

Repository Overview
-------------------

- Library Project: `library`
- Sample Project: `sample`
    - Includes `BleMidiCentralActivity`, and `BleMidiPeripheralActivity` examples.

Pre-compiled sample app is available on [Google Play Market](https://play.google.com/store/apps/details?id=jp.kshoji.blemidi.sample).
<br />![QR Code](https://dl.dropboxusercontent.com/u/3968074/qr_ble_midi_sample.png)

Usage of the library
--------------------

For the detail, see the [wiki](https://github.com/kshoji/BLE-MIDI-for-Android/wiki).

LICENSE
=======
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

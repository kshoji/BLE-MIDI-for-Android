BLE MIDI for Android
====================
[![Build Status](https://travis-ci.org/kshoji/BLE-MIDI-for-Android.svg?branch=develop)](https://travis-ci.org/kshoji/BLE-MIDI-for-Android)

MIDI over Bluetooth LE driver for Android 4.4 (API Level 19) or later

﻿Status
------

**Work in the progress**

- BLE Central function provider is mostly done. Now refactoring and cleaning up the code.
    - [Apple Bluetooth Low Energy MIDI Specification](https://developer.apple.com/bluetooth/Apple-Bluetooth-Low-Energy-MIDI-Specification.pdf) has been revealed, so I'm trying to fit the implementation.
    - The specification needs `BLE bonding` feature that is implemented at Android KitKat(API Level 19), so this library also needs `API Level 19`.
- BLE Peripheral function provider is not finished. The function needs a Nexus 5 or newer Nexus series with Android Lollipop, but I don't have it.

Usage of the library
--------------------

At first, create `BleMidiCentralProvider` instance with `Context`
```java
import jp.kshoji.blemidi;

public class SampleActivity extends Activity {

BleMidiCentralProvider bleMidiCentralProvider;

@Override
public void onCreate(final Bundle savedInstanceState) {
    bleMidiCentralProvider = new BleMidiCentralProvider(this);
}

...
```

And then, setup the event listeners.
**Note:** These event listeners' `onMidi...` method will be called from a `Thread` not on the `UI thread`.
When you want to change the UI objects(update ListView or something), send a message with a `Handler`.

```java
bleMidiCentralProvider.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
    @Override
    public void onMidiInputDeviceAttached(MidiInputDevice midiInputDevice) {
        // TODO process event
    }

    @Override
    public void onMidiOutputDeviceAttached(MidiOutputDevice midiOutputDevice) {
        // TODO process event
    }
});

OnMidiInputEventListener onMidiInputEventListener = new OnMidiInputEventListener() {
    @Override
    public void onMidiNoteOn(MidiInputDevice sender, int channel, int note, int velocity) {
        // TODO process note on event
    }

    // TODO implement other method
    ...
}
```

Now, call `startScanDevice` method to scan the BLE MIDI devices.

```java
bleMidiCentralProvider.startScanDevice(30000); // scan device 30 seconds
```

For more details, see the [wiki](https://github.com/kshoji/BLE-MIDI-for-Android/wiki).

LICENSE
=======
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

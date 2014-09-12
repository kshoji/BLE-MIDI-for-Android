package jp.kshoji.blemidi.device;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiDeviceUtils;
import jp.kshoji.blemidi.util.Constants;
import jp.kshoji.blemidi.util.ReusableByteArrayOutputStream;

/**
 * Represents BLE MIDI Input Device
 *
 * @author K.Shoji
 */
public class MidiInputDevice {

    private BluetoothGattCharacteristic midiInputCharacteristic;
    private final BluetoothGatt bluetoothGatt;

    private OnMidiInputEventListener midiInputEventListener;

    /**
     * Obtains MidiInputDevice instance if available from specified BluetoothGatt
     *
     * @param context
     * @param bluetoothGatt
     * @return null if the device doesn't contain BLE MIDI service
     */
    public static MidiInputDevice getInstance(Context context, final BluetoothGatt bluetoothGatt) {
        // create instance if available
        try {
            return new MidiInputDevice(context, bluetoothGatt);
        } catch (IllegalArgumentException e) {
            Log.i(Constants.TAG, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Constructor
     *
     * @param context
     * @param bluetoothGatt
     * @throws IllegalArgumentException if specified gatt doesn't contain BLE MIDI service
     */
    private MidiInputDevice(Context context, final BluetoothGatt bluetoothGatt) throws IllegalArgumentException {
        this.bluetoothGatt = bluetoothGatt;

        BluetoothGattService midiService = BleMidiDeviceUtils.getMidiService(context, bluetoothGatt);
        if (midiService == null) {
            throw new IllegalArgumentException("MIDI GattService not found.");
        }

        midiInputCharacteristic = BleMidiDeviceUtils.getMidiInputCharacteristic(context, midiService);
        if (midiInputCharacteristic == null) {
            throw new IllegalArgumentException("MIDI GattCharacteristic not found.");
        }

        midiState = MIDI_STATE_WAIT;
        midiEventKind = 0;
        midiEventNote = 0;
        midiEventVelocity = 0;

        List<BluetoothGattDescriptor> descriptors = midiInputCharacteristic.getDescriptors();

        bluetoothGatt.setCharacteristicNotification(midiInputCharacteristic, true);
        for (BluetoothGattDescriptor descriptor : descriptors) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public void setOnMidiInputEventListener(OnMidiInputEventListener midiInputEventListener) {
        this.midiInputEventListener = midiInputEventListener;
    }

    @Override
    public void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            // ignore it
        }

        midiInputEventListener = null;

        bluetoothGatt.disconnect();
        bluetoothGatt.close();
    }

    /**
     * Obtains the device name
     *
     * @return device name + ".input"
     */
    public String getDeviceName() {
        return bluetoothGatt.getDevice().getName() + ".input";
    }

    @Override
    public String toString() {
        return getDeviceName();
    }

    private int midiState = MIDI_STATE_WAIT;
    private int midiEventKind = 0;
    private int midiEventNote = 0;
    private int midiEventVelocity = 0;

    // for RPN/NRPN messages
    private static final int PARAMETER_MODE_NONE = 0;
    private static final int PARAMETER_MODE_RPN = 1;
    private static final int PARAMETER_MODE_NRPN = 2;
    private int parameterMode = PARAMETER_MODE_NONE;
    private int parameterNumber = 0x3fff;
    private int parameterValue = 0x3fff;

    private final ReusableByteArrayOutputStream systemExclusiveStream = new ReusableByteArrayOutputStream();

    private static final int MIDI_STATE_WAIT = 0;
    private static final int MIDI_STATE_SIGNAL_2BYTES_2 = 21;
    private static final int MIDI_STATE_SIGNAL_3BYTES_2 = 31;
    private static final int MIDI_STATE_SIGNAL_3BYTES_3 = 32;
    private static final int MIDI_STATE_SIGNAL_SYSEX = 41;

    public void parseMidiEvent(byte event) {
        int midiEvent = event & 0xff;
        if (midiState == MIDI_STATE_WAIT) {
            switch (midiEvent & 0xf0) {
                case 0xf0: {
                    switch (midiEvent) {
                        case 0xf0:
                            synchronized (systemExclusiveStream) {
                                systemExclusiveStream.reset();
                                systemExclusiveStream.write(midiEvent);
                                midiState = MIDI_STATE_SIGNAL_SYSEX;
                            }
                            break;

                        case 0xf1:
                        case 0xf3:
                            // 0xf1 MIDI Time Code Quarter Frame. : 2bytes
                            // 0xf3 Song Select. : 2bytes
                            midiEventKind = midiEvent;
                            midiState = MIDI_STATE_SIGNAL_2BYTES_2;
                            break;

                        case 0xf2:
                            // 0xf2 Song Position Pointer. : 3bytes
                            midiEventKind = midiEvent;
                            midiState = MIDI_STATE_SIGNAL_3BYTES_2;
                            break;

                        case 0xf6:
                            // 0xf6 Tune Request : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiTuneRequest(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xf8:
                            // 0xf8 Timing Clock : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiTimingClock(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xfa:
                            // 0xfa Start : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiStart(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xfb:
                            // 0xfb Continue : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiContinue(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xfc:
                            // 0xfc Stop : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiStop(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xfe:
                            // 0xfe Active Sensing : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiActiveSensing(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xff:
                            // 0xff Reset : 1byte
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiReset(this);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;

                        default:
                            break;
                    }
                }
                break;
                case 0x80:
                case 0x90:
                case 0xa0:
                case 0xb0:
                case 0xe0:
                    // 3bytes pattern
                    midiEventKind = midiEvent;
                    midiState = MIDI_STATE_SIGNAL_3BYTES_2;
                    break;
                case 0xc0: // program change
                case 0xd0: // channel after-touch
                    // 2bytes pattern
                    midiEventKind = midiEvent;
                    midiState = MIDI_STATE_SIGNAL_2BYTES_2;
                    break;
                default:
                    // 0x00 - 0x70: running status
                    if ((midiEventKind & 0xf0) != 0xf0) {
                        // previous event kind is multi-bytes pattern
                        midiEventNote = midiEvent;
                        midiState = MIDI_STATE_SIGNAL_3BYTES_3;
                    }
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_2BYTES_2) {
            switch (midiEventKind & 0xf0) {
                // 2bytes pattern
                case 0xc0: // program change
                    midiEventNote = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiProgramChange(this, midiEventKind & 0xf, midiEventNote);
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0xd0: // channel after-touch
                    midiEventNote = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiChannelAftertouch(this, midiEventKind & 0xf, midiEventNote);
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0xf0: {
                    switch (midiEventKind) {
                        case 0xf1:
                            // 0xf1 MIDI Time Code Quarter Frame. : 2bytes
                            midiEventNote = midiEvent;
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiTimeCodeQuarterFrame(this, midiEventNote);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        case 0xf3:
                            // 0xf3 Song Select. : 2bytes
                            midiEventNote = midiEvent;
                            if (midiInputEventListener != null) {
                                midiInputEventListener.onMidiSongSelect(this, midiEventNote);
                            }
                            midiState = MIDI_STATE_WAIT;
                            break;
                        default:
                            // illegal state
                            midiState = MIDI_STATE_WAIT;
                            break;
                    }
                }
                default:
                    // illegal state
                    midiState = MIDI_STATE_WAIT;
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_3BYTES_2) {
            switch (midiEventKind & 0xf0) {
                case 0x80:
                case 0x90:
                case 0xa0:
                case 0xb0:
                case 0xe0:
                case 0xf0:
                    // 3bytes pattern
                    midiEventNote = midiEvent;
                    midiState = MIDI_STATE_SIGNAL_3BYTES_3;
                    break;
                default:
                    // illegal state
                    midiState = MIDI_STATE_WAIT;
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_3BYTES_3) {
            switch (midiEventKind & 0xf0) {
                // 3bytes pattern
                case 0x80: // note off
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiNoteOff(this, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0x90: // note on
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiNoteOn(this, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0xa0: // control polyphonic key pressure
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiPolyphonicAftertouch(this, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0xb0: // control change
                    midiEventVelocity = midiEvent;
                    switch (midiEventNote & 0x7f) {
                        case 98:
                            // NRPN LSB
                            parameterNumber &= 0x3f80;
                            parameterNumber |= midiEventVelocity & 0x7f;
                            parameterMode = PARAMETER_MODE_NRPN;
                            break;
                        case 99:
                            // NRPN MSB
                            parameterNumber &= 0x007f;
                            parameterNumber |= (midiEventVelocity & 0x7f) << 7;
                            parameterMode = PARAMETER_MODE_NRPN;
                            break;
                        case 100:
                            // RPN LSB
                            parameterNumber &= 0x3f80;
                            parameterNumber |= midiEventVelocity & 0x7f;
                            parameterMode = PARAMETER_MODE_RPN;
                            break;
                        case 101:
                            // RPN MSB
                            parameterNumber &= 0x007f;
                            parameterNumber |= (midiEventVelocity & 0x7f) << 7;
                            parameterMode = PARAMETER_MODE_RPN;
                            break;
                        case 38:
                            // data LSB
                            parameterValue &= 0x3f80;
                            parameterValue |= midiEventVelocity & 0x7f;

                            if (parameterNumber != 0x3fff) {
                                if (parameterMode == PARAMETER_MODE_RPN) {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onRPNMessage(this, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                    }
                                } else if (parameterMode == PARAMETER_MODE_NRPN) {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onNRPNMessage(this, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                    }
                                }
                            }
                            break;
                        case 6:
                            // data MSB
                            parameterValue &= 0x007f;
                            parameterValue |= (midiEventVelocity & 0x7f) << 7;

                            if (parameterNumber != 0x3fff) {
                                if (parameterMode == PARAMETER_MODE_RPN) {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onRPNMessage(this, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                    }
                                } else if (parameterMode == PARAMETER_MODE_NRPN) {
                                    if (midiInputEventListener != null) {
                                        midiInputEventListener.onNRPNMessage(this, midiEventKind & 0xf, parameterNumber & 0x3fff, parameterValue & 0x3fff);
                                    }
                                }
                            }
                            break;
                    }

                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiControlChange(this, midiEventKind & 0xf, midiEventNote, midiEventVelocity);
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0xe0: // pitch bend
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiPitchWheel(this, midiEventKind & 0xf, (midiEventNote & 0x7f) | ((midiEventVelocity & 0x7f) << 7));
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                case 0xf0: // Song Position Pointer.
                    midiEventVelocity = midiEvent;
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiSongPositionPointer(this, (midiEventNote & 0x7f) | ((midiEventVelocity & 0x7f) << 7));
                    }
                    midiState = MIDI_STATE_WAIT;
                    break;
                default:
                    // illegal state
                    midiState = MIDI_STATE_WAIT;
                    break;
            }
        } else if (midiState == MIDI_STATE_SIGNAL_SYSEX) {
            if (midiEvent == 0xf7) {
                // the end of message
                synchronized (systemExclusiveStream) {
                    systemExclusiveStream.write(midiEvent);
                    if (midiInputEventListener != null) {
                        midiInputEventListener.onMidiSystemExclusive(this, systemExclusiveStream.toByteArray());
                    }
                }
                midiState = MIDI_STATE_WAIT;
            } else {
                synchronized (systemExclusiveStream) {
                    systemExclusiveStream.write(midiEvent);
                }
            }
        }
    }

    public void incomingData(byte[] data) {
        Log.i(Constants.TAG, "data: " + Arrays.toString(data));
        for (byte dat : data) {
            parseMidiEvent(dat);
        }
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return midiInputCharacteristic;
    }
}

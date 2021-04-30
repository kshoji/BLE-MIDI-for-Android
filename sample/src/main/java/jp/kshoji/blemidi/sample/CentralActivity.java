package jp.kshoji.blemidi.sample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.Midi2InputDevice;
import jp.kshoji.blemidi.device.Midi2OutputDevice;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidi2InputEventListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.listener.OnMidiScanStatusListener;
import jp.kshoji.blemidi.sample.util.SoundMaker;
import jp.kshoji.blemidi.sample.util.Tone;
import jp.kshoji.blemidi.util.BleUtils;

/**
 * Activity for BLE MIDI Central Application
 *
 * @author K.Shoji
 */
public class CentralActivity extends Activity {
    BleMidiCentralProvider bleMidiCentralProvider;

    MenuItem toggleScanMenu;

    boolean isScanning = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.central, menu);
        toggleScanMenu = menu.getItem(0);

        if (isScanning) {
            toggleScanMenu.setTitle(R.string.stop_scan);
        } else {
            toggleScanMenu.setTitle(R.string.start_scan);
        }

        return true;
    }

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private int scanDuration;
    private void startScanDeviceWithRequestingPermission(int duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    scanDuration = duration;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                    return;
                }
            }
        }

        // already has permission
        bleMidiCentralProvider.startScanDevice(duration);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_scan) {
            if (isScanning) {
                bleMidiCentralProvider.stopScanDevice();
            } else {
                startScanDeviceWithRequestingPermission(scanDuration);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    bleMidiCentralProvider.startScanDevice(0);
                }
            }
        }
    }

    // User interface
    final Handler midiInputEventHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (midiInputEventAdapter != null) {
                midiInputEventAdapter.add((String)msg.obj);
            }
            // message handled successfully
            return true;
        }
    });

    final Handler midiOutputEventHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (midiOutputEventAdapter != null) {
                midiOutputEventAdapter.add((String)msg.obj);
            }
            // message handled successfully
            return true;
        }
    });

    final Handler midiOutputConnectionChangedHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.obj instanceof MidiOutputDevice) {
                MidiOutputDevice midiOutputDevice = (MidiOutputDevice) msg.obj;
                connectedMidiOutputDevicesAdapter.remove(midiOutputDevice);
                if (msg.arg1 == 0) {
                    // attached
                    connectedMidiOutputDevicesAdapter.add(midiOutputDevice);
                }
                connectedMidiOutputDevicesAdapter.notifyDataSetChanged();
            }

            // message handled successfully
            return true;
        }
    });

    ArrayAdapter<String> midiInputEventAdapter;
    ArrayAdapter<String> midiOutputEventAdapter;
    private ToggleButton thruToggleButton;
    Spinner deviceSpinner;
    Spinner deviceSpinner2;

    ArrayAdapter<MidiOutputDevice> connectedMidiOutputDevicesAdapter;
    ArrayAdapter<Midi2OutputDevice> connectedMidi2OutputDevicesAdapter;

    // Play sounds
    AudioTrack audioTrack;
    Timer timer;
    TimerTask timerTask;
    SoundMaker soundMaker;
    final Set<Tone> tones = new HashSet<>();
    int currentProgram = 0;

    /**
     * Choose device from spinner
     *
     * @return chosen {@link jp.kshoji.blemidi.device.MidiOutputDevice}
     */
    MidiOutputDevice getBleMidiOutputDeviceFromSpinner() {
        if (deviceSpinner != null && deviceSpinner.getSelectedItemPosition() >= 0 && connectedMidiOutputDevicesAdapter != null && !connectedMidiOutputDevicesAdapter.isEmpty()) {
            MidiOutputDevice device = connectedMidiOutputDevicesAdapter.getItem(deviceSpinner.getSelectedItemPosition());
            if (device != null) {
                Set<MidiOutputDevice> midiOutputDevices = bleMidiCentralProvider.getMidiOutputDevices();

                if (midiOutputDevices.size() > 0) {
                    // returns the first one.
                    return (MidiOutputDevice) midiOutputDevices.toArray()[0];
                }
            }
        }
        return null;
    }

    /**
     * Choose device from spinner
     *
     * @return chosen {@link jp.kshoji.blemidi.device.Midi2OutputDevice}
     */
    Midi2OutputDevice getBleMidi2OutputDeviceFromSpinner() {
        if (deviceSpinner != null && deviceSpinner.getSelectedItemPosition() >= 0 && connectedMidi2OutputDevicesAdapter != null && !connectedMidi2OutputDevicesAdapter.isEmpty()) {
            Midi2OutputDevice device = connectedMidi2OutputDevicesAdapter.getItem(deviceSpinner.getSelectedItemPosition());
            if (device != null) {
                Set<MidiOutputDevice> midiOutputDevices = bleMidiCentralProvider.getMidiOutputDevices();

                if (midiOutputDevices.size() > 0) {
                    // returns the first one.
                    return (Midi2OutputDevice) midiOutputDevices.toArray()[0];
                }
            }
        }
        return null;
    }

    final OnMidiInputEventListener onMidiInputEventListener = new OnMidiInputEventListener() {
        @Override
        public void onMidiSystemExclusive(@NonNull MidiInputDevice sender, @NonNull byte[] systemExclusive) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "SystemExclusive from: " + sender.getDeviceName() + ", data:" + Arrays.toString(systemExclusive)));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiSystemExclusive(systemExclusive);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "SystemExclusive from: " + sender.getDeviceName() + ", data:" + Arrays.toString(systemExclusive)));
            }
        }

        @Override
        public void onMidiNoteOff(@NonNull MidiInputDevice sender, int channel, int note, int velocity) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NoteOff from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiNoteOff(channel, note, velocity);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "NoteOff from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));
            }

            synchronized (tones) {
                Iterator<Tone> it = tones.iterator();
                while (it.hasNext()) {
                    Tone tone = it.next();
                    if (tone.getNote() == note) {
                        it.remove();
                    }
                }
            }
        }

        @Override
        public void onMidiNoteOn(@NonNull MidiInputDevice sender, int channel, int note, int velocity) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NoteOn from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiNoteOn(channel, note, velocity);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "NoteOn from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));
            }

            synchronized (tones) {
                if (velocity == 0) {
                    Iterator<Tone> it = tones.iterator();
                    while (it.hasNext()) {
                        Tone tone = it.next();
                        if (tone.getNote() == note) {
                            it.remove();
                        }
                    }
                } else {
                    tones.add(new Tone(note, velocity / 127.0, currentProgram));
                }
            }
        }

        @Override
        public void onMidiPolyphonicAftertouch(@NonNull MidiInputDevice sender, int channel, int note, int pressure) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "PolyphonicAftertouch  from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", pressure: " + pressure));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiPolyphonicAftertouch(channel, note, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "PolyphonicAftertouch from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", pressure: " + pressure));
            }
        }

        @Override
        public void onMidiControlChange(@NonNull MidiInputDevice sender, int channel, int function, int value) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ControlChange from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiControlChange(channel, function, value);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ControlChange from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));
            }
        }

        @Override
        public void onMidiProgramChange(@NonNull MidiInputDevice sender, int channel, int program) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ProgramChange from: " + sender.getDeviceName() + ", channel: " + channel + ", program: " + program));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiProgramChange(channel, program);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ProgramChange from: " + sender.getDeviceName() + ", channel: " + channel + ", program: " + program));
            }

            currentProgram = program % Tone.FORM_MAX;
            synchronized (tones) {
                for (Tone tone : tones) {
                    tone.setForm(currentProgram);
                }
            }
        }

        @Override
        public void onMidiChannelAftertouch(@NonNull MidiInputDevice sender, int channel, int pressure) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ChannelAftertouch from: " + sender.getDeviceName() + ", channel: " + channel + ", pressure: " + pressure));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiChannelAftertouch(channel, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ChannelAftertouch from: " + sender.getDeviceName() + ", channel: " + channel + ", pressure: " + pressure));
            }
        }

        @Override
        public void onMidiPitchWheel(@NonNull MidiInputDevice sender, int channel, int amount) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "PitchWheel from: " + sender.getDeviceName() + ", channel: " + channel + ", amount: " + amount));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiPitchWheel(channel, amount);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "PitchWheel from: " + sender.getDeviceName() + ", channel: " + channel + ", amount: " + amount));
            }
        }

        @Override
        public void onMidiTimeCodeQuarterFrame(@NonNull MidiInputDevice sender, int timing) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "TimeCodeQuarterFrame from: " + sender.getDeviceName() + ", timing: " + timing));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiTimeCodeQuarterFrame(timing);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "TimeCodeQuarterFrame from: " + sender.getDeviceName() + ", timing: " + timing));
            }
        }

        @Override
        public void onMidiSongSelect(@NonNull MidiInputDevice sender, int song) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "SongSelect from: " + sender.getDeviceName() + ", song: " + song));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiSongSelect(song);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "SongSelect from: " + sender.getDeviceName() + ", song: " + song));
            }
        }

        @Override
        public void onMidiSongPositionPointer(@NonNull MidiInputDevice sender, int position) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "SongPositionPointer from: " + sender.getDeviceName() + ", position: " + position));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiSongPositionPointer(position);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "SongPositionPointer from: " + sender.getDeviceName() + ", position: " + position));
            }
        }

        @Override
        public void onMidiTuneRequest(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "TuneRequest from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiTuneRequest();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "TuneRequest from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiTimingClock(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "TimingClock from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiTimingClock();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "TimingClock from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiStart(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Start from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiStart();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Start from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiContinue(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Continue from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiContinue();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Continue from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiStop(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Stop from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiStop();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Stop from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiActiveSensing(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ActiveSensing from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiActiveSensing();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ActiveSensing from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiReset(@NonNull MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Reset from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiReset();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Reset from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "RPN message from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));
        }

        @Override
        public void onNRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NRPN message from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));
        }
    };

    final OnMidi2InputEventListener onMidi2InputEventListener = new OnMidi2InputEventListener() {
        // region MIDI 2.0
        @Override
        public void onMidiNoop(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Noop group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiNoop(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiJitterReductionClock(Midi2InputDevice sender, int group, int senderClockTime) {
            String message = String.format(Locale.ROOT, "Jitter Reduction Clock group: %d, senderClockTime: %d", group, senderClockTime);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiJitterReductionClock(group, senderClockTime);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiJitterReductionTimestamp(Midi2InputDevice sender, int group, int senderClockTimestamp) {
            String message = String.format(Locale.ROOT, "Jitter Reduction Timestamp group: %d, senderClockTimestamp: %d", group, senderClockTimestamp);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiJitterReductionTimestamp(group, senderClockTimestamp);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiTimeCodeQuarterFrame(Midi2InputDevice sender, int group, int timing) {
            String message = String.format(Locale.ROOT, "Time Code Quarter Frame group: %d, timing: %d", group, timing);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiTimeCodeQuarterFrame(group, timing);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiSongSelect(Midi2InputDevice sender, int group, int song) {
            String message = String.format(Locale.ROOT, "Song Select group: %d, song: %d", group, song);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiSongSelect(group, song);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiSongPositionPointer(Midi2InputDevice sender, int group, int position) {
            String message = String.format(Locale.ROOT, "Song Position Pointer group: %d, position: %d", group, position);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiSongPositionPointer(group, position);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiTuneRequest(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Tune Request group: %d,", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiTuneRequest(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiTimingClock(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Timing Clock group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiTimingClock(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiStart(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Start group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiStart(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiContinue(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Continue group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiContinue(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiStop(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Stop group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiStop(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiActiveSensing(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Active Sensing group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiActiveSensing(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiReset(Midi2InputDevice sender, int group) {
            String message = String.format(Locale.ROOT, "Reset group: %d", group);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiReset(group);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1NoteOff(Midi2InputDevice sender, int group, int channel, int note, int velocity) {
            String message = String.format(Locale.ROOT, "Note Off group: %d, channel: %d, note: %d, velocity: %d", group, channel, note, velocity);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1NoteOff(group, channel, note, velocity);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1NoteOn(Midi2InputDevice sender, int group, int channel, int note, int velocity) {
            String message = String.format(Locale.ROOT, "Note On group: %d, channel: %d, note: %d, velocity: %d", group, channel, note, velocity);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1NoteOn(group, channel, note, velocity);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1PolyphonicAftertouch(Midi2InputDevice sender, int group, int channel, int note, int pressure) {
            String message = String.format(Locale.ROOT, "Polyphonic Aftertouch group: %d, channel: %d, note: %d, pressure: %d", group, channel, note, pressure);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1PolyphonicAftertouch(group, channel, note, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1ControlChange(Midi2InputDevice sender, int group, int channel, int function, int value) {
            String message = String.format(Locale.ROOT, "Control Change group: %d, channel: %d, function: %d, value: %d", group, channel, function, value);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1ControlChange(group, channel, function, value);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1ProgramChange(Midi2InputDevice sender, int group, int channel, int program) {
            String message = String.format(Locale.ROOT, "Program Change group: %d, channel: %d, program: %d", group, channel, program);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1ProgramChange(group, channel, program);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1ChannelAftertouch(Midi2InputDevice sender, int group, int channel, int pressure) {
            String message = String.format(Locale.ROOT, "Channel Aftertouch group: %d, channel: %d, pressure: %d", group, channel, pressure);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1ChannelAftertouch(group, channel, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1PitchWheel(Midi2InputDevice sender, int group, int channel, int amount) {
            String message = String.format(Locale.ROOT, "Pitch Wheel group: %d, channel: %d, amount: %d", group, channel, amount);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1PitchWheel(group, channel, amount);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi1SystemExclusive(Midi2InputDevice sender, int group, @NonNull byte[] systemExclusive) {
            String message = String.format(Locale.ROOT, "System Exclusive group: %d, data: %s", group, Arrays.toString(systemExclusive));
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi1SystemExclusive(group, systemExclusive);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2NoteOff(Midi2InputDevice sender, int group, int channel, int note, int velocity, int attributeType, int attributeData) {
            String message = String.format(Locale.ROOT, "Note Off group: %d, channel: %d, note: %d, velocity: %d, attributeType: %d, attributeData: %d", group, channel, note, velocity, attributeType, attributeData);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2NoteOff(group, channel, note, velocity, attributeType, attributeData);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2NoteOn(Midi2InputDevice sender, int group, int channel, int note, int velocity, int attributeType, int attributeData) {
            String message = String.format(Locale.ROOT, "Note On group: %d, channel: %d, note: %d, velocity: %d, attributeType: %d, attributeData: %d", group, channel, note, velocity, attributeType, attributeData);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2NoteOn(group, channel, note, velocity, attributeType, attributeData);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2PolyphonicAftertouch(Midi2InputDevice sender, int group, int channel, int note, long pressure) {
            String message = String.format(Locale.ROOT, "Polyphonic Aftertouch group: %d, channel: %d, note: %d, pressure: %d", group, channel, note, pressure);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2PolyphonicAftertouch(group, channel, note, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2ControlChange(Midi2InputDevice sender, int group, int channel, int index, long value) {
            String message = String.format(Locale.ROOT, "Control Change group: %d, channel: %d, index: %d, value: %d", group, channel, index, value);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2ControlChange(group, channel, index, value);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2ProgramChange(Midi2InputDevice sender, int group, int channel, int optionFlags, int program, int bank) {
            String message = String.format(Locale.ROOT, "Noop group: %d, channel: %d, optionFlags: %d, program: %d, bank: %d", group, channel, optionFlags, program, bank);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2ProgramChange(group, channel, optionFlags, program, bank);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2ChannelAftertouch(Midi2InputDevice sender, int group, int channel, long pressure) {
            String message = String.format(Locale.ROOT, "Channel Aftertouch group: %d, channel: %d, pressure: %d", group, channel, pressure);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2ChannelAftertouch(group, channel, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2PitchWheel(Midi2InputDevice sender, int group, int channel, long amount) {
            String message = String.format(Locale.ROOT, "Pitch Wheel group: %d, channel: %d, amount: %d", group, channel, amount);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2PitchWheel(group, channel, amount);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiPerNotePitchWheel(Midi2InputDevice sender, int group, int channel, int note, long amount) {
            String message = String.format(Locale.ROOT, "Per Note Pitch Wheel group: %d, channel: %d, note: %d, amount: %d", group, channel, note, amount);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiPerNotePitchWheel(group, channel, note, amount);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiPerNoteManagement(Midi2InputDevice sender, int group, int channel, int note, int optionFlags) {
            String message = String.format(Locale.ROOT, "Per Note Management group: %d, channel: %d, note: %d, optionFlags: %d", group, channel, note, optionFlags);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiPerNoteManagement(group, channel, note, optionFlags);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiRegisteredPerNoteController(Midi2InputDevice sender, int group, int channel, int note, int index, long data) {
            String message = String.format(Locale.ROOT, "Registered Per Note Controller group: %d, channel: %d, note: %d, data: %d", group, channel, note, data);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiRegisteredPerNoteController(group, channel, note, index, data);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiAssignablePerNoteController(Midi2InputDevice sender, int group, int channel, int note, int index, long data) {
            String message = String.format(Locale.ROOT, "Assignable Per Note Controller group: %d, channel: %d, note: %d, data: %d", group, channel, note, data);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiAssignablePerNoteController(group, channel, note, index, data);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiRegisteredController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {
            String message = String.format(Locale.ROOT, "Registered Controller group: %d, channel: %d, bank: %d, index: %d, data: %d", group, channel, bank, index, data);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiRegisteredController(group, channel, bank, index, data);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiAssignableController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {
            String message = String.format(Locale.ROOT, "Assignable Controller group: %d, channel: %d, bank: %d, index: %d, data: %d", group, channel, bank, index, data);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiAssignableController(group, channel, bank, index, data);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiRelativeRegisteredController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {
            String message = String.format(Locale.ROOT, "Relative Registered Controller group: %d, channel: %d, bank: %d, index: %d, data: %d", group, channel, bank, index, data);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiRelativeRegisteredController(group, channel, bank, index, data);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiRelativeAssignableController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {
            String message = String.format(Locale.ROOT, "Relative Assignable Controller group: %d, channel: %d, bank: %d, index: %d, data: %d", group, channel, bank, index, data);
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiRelativeAssignableController(group, channel, bank, index, data);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidi2SystemExclusive(Midi2InputDevice sender, int group, int streamId, @NonNull byte[] systemExclusive) {
            String message = String.format(Locale.ROOT, "System Exclusive group: %d, streamId: %d, data: %s", group, streamId, Arrays.toString(systemExclusive));
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidi2SystemExclusive(group, streamId, systemExclusive);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiMixedDataSetHeader(Midi2InputDevice sender, int group, int mdsId, @NonNull byte[] headers) {
            String message = String.format(Locale.ROOT, "Mixed Data Set Header group: %d, mdsId: %d, headers: %s", group, mdsId, Arrays.toString(headers));
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiMixedDataSetHeader(group, mdsId, headers);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }

        @Override
        public void onMidiMixedDataSetPayload(Midi2InputDevice sender, int group, int mdsId, @NonNull byte[] payloads) {
            String message = String.format(Locale.ROOT, "Mixed Data Set Payload group: %d, mdsId: %d, payloads: %s", group, mdsId, Arrays.toString(payloads));
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, message));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidi2OutputDeviceFromSpinner() != null) {
                getBleMidi2OutputDeviceFromSpinner().sendMidiMixedDataSetPayload(group, mdsId, payloads);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, message));
            }
        }
        // endregion
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView midiInputEventListView = findViewById(R.id.midiInputEventListView);
        midiInputEventAdapter = new ArrayAdapter<>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiInputEventAdapter = new ArrayAdapter<>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiInputEventListView.setAdapter(midiInputEventAdapter);

        ListView midiOutputEventListView = findViewById(R.id.midiOutputEventListView);
        midiOutputEventAdapter = new ArrayAdapter<>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiOutputEventListView.setAdapter(midiOutputEventAdapter);

        thruToggleButton = findViewById(R.id.toggleButtonThru);

        deviceSpinner = findViewById(R.id.deviceNameSpinner);
        connectedMidiOutputDevicesAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.simple_spinner_dropdown_item, android.R.id.text1, new ArrayList<MidiOutputDevice>());
        deviceSpinner.setAdapter(connectedMidiOutputDevicesAdapter);

        deviceSpinner2 = findViewById(R.id.deviceNameSpinner2);
        connectedMidi2OutputDevicesAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.simple_spinner_dropdown_item, android.R.id.text1, new ArrayList<Midi2OutputDevice>());
        deviceSpinner2.setAdapter(connectedMidi2OutputDevicesAdapter);

        View.OnTouchListener onToneButtonTouchListener = new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                MidiOutputDevice midiOutputDevice = getBleMidiOutputDeviceFromSpinner();
                if (midiOutputDevice == null) {
                    return false;
                }

                int note = 60 + Integer.parseInt((String) v.getTag());
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        midiOutputDevice.sendMidiNoteOn(0, note, 127);
                        midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "NoteOn to: " + midiOutputDevice.getDeviceName() + ", note: " + note + ", velocity: 127"));
                        break;
                    case MotionEvent.ACTION_UP:
                        midiOutputDevice.sendMidiNoteOff(0, note, 127);
                        midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "NoteOff to: " + midiOutputDevice.getDeviceName() + ", note: " + note + ", velocity: 127"));
                        v.performClick();
                        break;
                    default:
                        // do nothing.
                        break;
                }
                return false;
            }
        };
        findViewById(R.id.buttonC).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonCis).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonD).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonDis).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonE).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonF).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonFis).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonG).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonGis).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonA).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonAis).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonB).setOnTouchListener(onToneButtonTouchListener);
        findViewById(R.id.buttonC2).setOnTouchListener(onToneButtonTouchListener);

        int whiteKeyColor = 0xFFFFFFFF;
        int blackKeyColor = 0xFF808080;
        findViewById(R.id.buttonC).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonCis).getBackground().setColorFilter(blackKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonD).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonDis).getBackground().setColorFilter(blackKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonE).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonF).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonFis).getBackground().setColorFilter(blackKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonG).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonGis).getBackground().setColorFilter(blackKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonA).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonAis).getBackground().setColorFilter(blackKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonB).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);
        findViewById(R.id.buttonC2).getBackground().setColorFilter(whiteKeyColor, PorterDuff.Mode.MULTIPLY);

        soundMaker = SoundMaker.getInstance();
        final int bufferSize = AudioTrack.getMinBufferSize(soundMaker.getSamplingRate(), AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int timerRate = bufferSize * 1000 / soundMaker.getSamplingRate() / 2;
        final short[] wav = new short[bufferSize / 2];

        audioTrack = prepareAudioTrack(soundMaker.getSamplingRate());
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (soundMaker != null) {
                    synchronized (tones) {
                        for (int i = 0; i < wav.length; i++) {
                            wav[i] = (short) (soundMaker.makeWaveStream(tones) * 1024);
                        }
                    }
                    try {
                        if (audioTrack != null) {
                            audioTrack.write(wav, 0, wav.length);
                        }
                    } catch (IllegalStateException | NullPointerException e) {
                        // do nothing
                    }
                }
            }
        };
        timer.scheduleAtFixedRate(timerTask, 10, timerRate);

        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MidiOutputDevice bleMidiOutputDeviceFromSpinner = getBleMidiOutputDeviceFromSpinner();
                if (bleMidiOutputDeviceFromSpinner != null) {
                    bleMidiCentralProvider.disconnectDevice(bleMidiOutputDeviceFromSpinner);
                }
            }
        });

        if (!BleUtils.isBluetoothEnabled(this)) {
            BleUtils.enableBluetooth(this);
            return;
        }

        if (!BleUtils.isBleSupported(this)) {
            // display alert and exit
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Not supported");
            alertDialog.setMessage("Bluetooth LE is not supported on this device. The app will exit.");
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                }
            });
            alertDialog.show();
        } else {
            setupCentralProvider();
        }
    }

    /**
     * Configure BleMidiCentralProvider instance
     */
    private void setupCentralProvider() {
        bleMidiCentralProvider = new BleMidiCentralProvider(this);

        bleMidiCentralProvider.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(onMidiInputEventListener);
            }

            @Override
            public void onMidiOutputDeviceAttached(@NonNull MidiOutputDevice midiOutputDevice) {
                Message message = new Message();
                message.arg1 = 0;
                message.obj = midiOutputDevice;
                midiOutputConnectionChangedHandler.sendMessage(message);
            }

            @Override
            public void onMidi2InputDeviceAttached(@NonNull Midi2InputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(onMidi2InputEventListener);
            }

            @Override
            public void onMidi2OutputDeviceAttached(@NonNull Midi2OutputDevice midiOutputDevice) {
                Message message = new Message();
                message.arg1 = 0;
                message.obj = midiOutputDevice;
                midiOutputConnectionChangedHandler.sendMessage(message);
            }
        });

        bleMidiCentralProvider.setOnMidiDeviceDetachedListener(new OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {
                // do nothing
            }

            @Override
            public void onMidiOutputDeviceDetached(@NonNull MidiOutputDevice midiOutputDevice) {
                Message message = new Message();
                message.arg1 = 1;
                message.obj = midiOutputDevice;
                midiOutputConnectionChangedHandler.sendMessage(message);
            }

            @Override
            public void onMidi2InputDeviceDetached(@NonNull Midi2InputDevice midiInputDevice) {
                // do nothing
            }

            @Override
            public void onMidi2OutputDeviceDetached(@NonNull Midi2OutputDevice midiOutputDevice) {
                Message message = new Message();
                message.arg1 = 1;
                message.obj = midiOutputDevice;
                midiOutputConnectionChangedHandler.sendMessage(message);
            }
        });

        bleMidiCentralProvider.setOnMidiScanStatusListener(new OnMidiScanStatusListener() {
            @Override
            public void onMidiScanStatusChanged(boolean isScanning) {
                CentralActivity.this.isScanning = isScanning;
                if (toggleScanMenu != null) {
                    if (isScanning) {
                        toggleScanMenu.setTitle(R.string.stop_scan);
                    } else {
                        toggleScanMenu.setTitle(R.string.start_scan);
                    }
                }
            }
        });

        // scan devices for 30 seconds
        startScanDeviceWithRequestingPermission(30000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == BleUtils.REQUEST_CODE_BLUETOOTH_ENABLE) {
            if (!BleUtils.isBluetoothEnabled(this)) {
                // User selected NOT to use Bluetooth.
                // do nothing
                return;
            }

            if (!BleUtils.isBleSupported(this)) {
                // display alert and exit
                AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle("Not supported");
                alertDialog.setMessage("Bluetooth LE is not supported on this device. The app will exit.");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                });
                alertDialog.show();
            } else {
                setupCentralProvider();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (bleMidiCentralProvider != null) {
            bleMidiCentralProvider.terminate();
        }

        if (timer != null) {
            try {
                timer.cancel();
                timer.purge();
            } catch (Throwable t) {
                // do nothing
            } finally {
                timer = null;
            }
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.flush();
                audioTrack.release();
            } catch (Throwable t) {
                // do nothing
            } finally {
                audioTrack = null;
            }
        }

        super.onDestroy();
    }

    /**
     * @param samplingRate sampling rate for playing
     * @return configured {@link android.media.AudioTrack} instance
     */
    private static AudioTrack prepareAudioTrack(int samplingRate) {
        AudioTrack result = new AudioTrack(AudioManager.STREAM_MUSIC, samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, AudioTrack.getMinBufferSize(samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioTrack.MODE_STREAM);
        result.setStereoVolume(1f, 1f);
        result.play();
        return result;
    }
}

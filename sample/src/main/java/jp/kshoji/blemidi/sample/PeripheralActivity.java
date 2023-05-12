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
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;
import jp.kshoji.blemidi.sample.util.SoundMaker;
import jp.kshoji.blemidi.sample.util.Tone;
import jp.kshoji.blemidi.util.BleUtils;

/**
 * Activity for BLE MIDI Peripheral Application
 *
 * @author K.Shoji
 */
public class PeripheralActivity extends Activity {
    BleMidiPeripheralProvider bleMidiPeripheralProvider;

    MenuItem toggleAdvertiseMenu;

    boolean isAdvertising = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.peripheral, menu);
        toggleAdvertiseMenu = menu.getItem(0);

        if (isAdvertising) {
            toggleAdvertiseMenu.setTitle(R.string.stop_advertise);
        } else {
            toggleAdvertiseMenu.setTitle(R.string.start_advertise);
        }

        return true;
    }

    private static final int PERMISSION_REQUEST_ADVERTISE = 2;

    private void startAdvertisingWithRequestingPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_ADVERTISE) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_ADVERTISE);
                }
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH)) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, PERMISSION_REQUEST_ADVERTISE);
                    return;
                }
            }
        }

        // already has permission
        bleMidiPeripheralProvider.startAdvertising();
        isAdvertising = true;
        toggleAdvertiseMenu.setTitle(R.string.stop_advertise);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requestCode == PERMISSION_REQUEST_ADVERTISE) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bleMidiPeripheralProvider.startAdvertising();
                    isAdvertising = true;
                    toggleAdvertiseMenu.setTitle(R.string.stop_advertise);
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_advertise) {
            if (isAdvertising) {
                bleMidiPeripheralProvider.stopAdvertising();
                isAdvertising = false;
                toggleAdvertiseMenu.setTitle(R.string.start_advertise);
            } else {
                startAdvertisingWithRequestingPermission();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // User interface
    final Handler midiInputEventHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (midiInputEventAdapter != null) {
                midiInputEventAdapter.add((String)msg.obj);
            }
            // message handled successfully
            return true;
        }
    });

    final Handler midiOutputEventHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (midiOutputEventAdapter != null) {
                midiOutputEventAdapter.add((String)msg.obj);
            }
            // message handled successfully
            return true;
        }
    });

    final Handler midiOutputConnectionChangedHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.obj instanceof MidiOutputDevice) {
                MidiOutputDevice midiOutputDevice = (MidiOutputDevice) msg.obj;
                if (msg.arg1 == 0) {
                    connectedOutputDevicesAdapter.remove(midiOutputDevice);
                    connectedOutputDevicesAdapter.add(midiOutputDevice);
                    connectedOutputDevicesAdapter.notifyDataSetChanged();
                } else {
                    connectedOutputDevicesAdapter.remove(midiOutputDevice);
                    connectedOutputDevicesAdapter.notifyDataSetChanged();
                }
            }

            // message handled successfully
            return true;
        }
    });

    ArrayAdapter<String> midiInputEventAdapter;
    ArrayAdapter<String> midiOutputEventAdapter;
    private ToggleButton thruToggleButton;
    Spinner deviceSpinner;

    ArrayAdapter<MidiOutputDevice> connectedOutputDevicesAdapter;

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
        if (deviceSpinner != null && deviceSpinner.getSelectedItemPosition() >= 0 && connectedOutputDevicesAdapter != null && !connectedOutputDevicesAdapter.isEmpty()) {
            return connectedOutputDevicesAdapter.getItem(deviceSpinner.getSelectedItemPosition());
        }
        return null;
    }

    OnMidiInputEventListener onMidiInputEventListener = new OnMidiInputEventListener() {
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
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NoteOn  from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));

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

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView midiInputEventListView = (ListView) findViewById(R.id.midiInputEventListView);
        midiInputEventAdapter = new ArrayAdapter<>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiInputEventAdapter = new ArrayAdapter<>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiInputEventListView.setAdapter(midiInputEventAdapter);

        ListView midiOutputEventListView = (ListView) findViewById(R.id.midiOutputEventListView);
        midiOutputEventAdapter = new ArrayAdapter<>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiOutputEventListView.setAdapter(midiOutputEventAdapter);

        thruToggleButton = (ToggleButton) findViewById(R.id.toggleButtonThru);

        deviceSpinner = (Spinner) findViewById(R.id.deviceNameSpinner);
        connectedOutputDevicesAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.simple_spinner_dropdown_item, android.R.id.text1, new ArrayList<MidiOutputDevice>());
        deviceSpinner.setAdapter(connectedOutputDevicesAdapter);

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

        Button disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MidiOutputDevice bleMidiOutputDeviceFromSpinner = getBleMidiOutputDeviceFromSpinner();
                if (bleMidiOutputDeviceFromSpinner != null) {
                    bleMidiPeripheralProvider.disconnectDevice(bleMidiOutputDeviceFromSpinner);
                }
            }
        });

        if (!BleUtils.isBluetoothEnabled(this)) {
            BleUtils.enableBluetooth(this);
            return;
        }

        if (!BleUtils.isBleSupported(this) || !BleUtils.isBlePeripheralSupported(this)) {
            // display alert and exit
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Not supported");
            alertDialog.setMessage("Bluetooth LE Peripheral is not supported on this device. The app will exit.");
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
            setupBlePeripheralProvider();
        }
    }

    /**
     * Configure BleMidiPeripheralProvider instance
     */
    private void setupBlePeripheralProvider() {
        bleMidiPeripheralProvider = new BleMidiPeripheralProvider(this);

        bleMidiPeripheralProvider.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
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
        });

        bleMidiPeripheralProvider.setOnMidiDeviceDetachedListener(new OnMidiDeviceDetachedListener() {
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
        });
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

            if (!BleUtils.isBleSupported(this) || !BleUtils.isBlePeripheralSupported(this)) {
                // display alert and exit
                AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle("Not supported");
                alertDialog.setMessage("Bluetooth LE Peripheral is not supported on this device. The app will exit.");
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
                setupBlePeripheralProvider();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (bleMidiPeripheralProvider != null) {
            bleMidiPeripheralProvider.terminate();
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

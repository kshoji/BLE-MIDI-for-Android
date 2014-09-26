package jp.kshoji.blemidi.sample;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Arrays;

import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider;

/**
 * Activity for Sample Application
 *
 * @author K.Shoji
 */
public class PeripheralActivity extends Activity {
    BleMidiPeripheralProvider bluetoothMidiManager;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_start_scan:
                return true;
            case R.id.action_stop_scan:
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
//    AudioTrack audioTrack;
//    Timer timer;
//    TimerTask timerTask;
//    SoundMaker soundMaker;
//    final Set<Tone> tones = new HashSet<Tone>();
//    int currentProgram = 0;

    /**
     * Choose device from spinner
     *
     * @return chosen {@link jp.kshoji.blemidi.device.MidiOutputDevice}
     */
    MidiOutputDevice getBleMidiOutputDeviceFromSpinner() {
        if (deviceSpinner != null && deviceSpinner.getSelectedItemPosition() >= 0 && connectedOutputDevicesAdapter != null && !connectedOutputDevicesAdapter.isEmpty()) {
            MidiOutputDevice device = connectedOutputDevicesAdapter.getItem(deviceSpinner.getSelectedItemPosition());
            if (device != null) {
                return bluetoothMidiManager.getMidiOutputDevice();
            }
        }
        return null;
    }

    OnMidiInputEventListener onMidiInputEventListener = new OnMidiInputEventListener() {
        @Override
        public void onMidiSystemExclusive(MidiInputDevice sender, byte[] systemExclusive) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "SystemExclusive from: " + sender.getDeviceName() + ", data:" + Arrays.toString(systemExclusive)));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiSystemExclusive(systemExclusive);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "SystemExclusive from: " + sender.getDeviceName() + ", data:" + Arrays.toString(systemExclusive)));
            }
        }

        @Override
        public void onMidiNoteOff(MidiInputDevice sender, int channel, int note, int velocity) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NoteOff from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiNoteOff(channel, note, velocity);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "NoteOff from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));
            }

//            synchronized (tones) {
//                Iterator<Tone> it = tones.iterator();
//                while (it.hasNext()) {
//                    Tone tone = it.next();
//                    if (tone.getNote() == note) {
//                        it.remove();
//                    }
//                }
//            }
        }

        @Override
        public void onMidiNoteOn(MidiInputDevice sender, int channel, int note, int velocity) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NoteOn  from: " + sender.getDeviceName() + "channel: " + channel + ", note: " + note + ", velocity: " + velocity));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiNoteOn(channel, note, velocity);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "NoteOn from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", velocity: " + velocity));
            }

//            synchronized (tones) {
//                if (velocity == 0) {
//                    Iterator<Tone> it = tones.iterator();
//                    while (it.hasNext()) {
//                        Tone tone = it.next();
//                        if (tone.getNote() == note) {
//                            it.remove();
//                        }
//                    }
//                } else {
//                    tones.add(new Tone(note, velocity / 127.0, currentProgram));
//                }
//            }
        }

        @Override
        public void onMidiPolyphonicAftertouch(MidiInputDevice sender, int channel, int note, int pressure) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "PolyphonicAftertouch  from: " + sender.getDeviceName() + "channel: " + channel + ", note: " + note + ", pressure: " + pressure));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiPolyphonicAftertouch(channel, note, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "PolyphonicAftertouch from: " + sender.getDeviceName() + " channel: " + channel + ", note: " + note + ", pressure: " + pressure));
            }
        }

        @Override
        public void onMidiControlChange(MidiInputDevice sender, int channel, int function, int value) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ControlChange from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiControlChange(channel, function, value);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ControlChange from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));
            }
        }

        @Override
        public void onMidiProgramChange(MidiInputDevice sender, int channel, int program) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ProgramChange from: " + sender.getDeviceName() + ", channel: " + channel + ", program: " + program));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiProgramChange(channel, program);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ProgramChange from: " + sender.getDeviceName() + ", channel: " + channel + ", program: " + program));
            }

//            currentProgram = program % Tone.FORM_MAX;
//            synchronized (tones) {
//                for (Tone tone : tones) {
//                    tone.setForm(currentProgram);
//                }
//            }
        }

        @Override
        public void onMidiChannelAftertouch(MidiInputDevice sender, int channel, int pressure) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ChannelAftertouch from: " + sender.getDeviceName() + ", channel: " + channel + ", pressure: " + pressure));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiChannelAftertouch(channel, pressure);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ChannelAftertouch from: " + sender.getDeviceName() + ", channel: " + channel + ", pressure: " + pressure));
            }
        }

        @Override
        public void onMidiPitchWheel(MidiInputDevice sender, int channel, int amount) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "PitchWheel from: " + sender.getDeviceName() + ", channel: " + channel + ", amount: " + amount));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiPitchWheel(channel, amount);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "PitchWheel from: " + sender.getDeviceName() + ", channel: " + channel + ", amount: " + amount));
            }
        }

        @Override
        public void onMidiTimeCodeQuarterFrame(MidiInputDevice sender, int timing) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "TimeCodeQuarterFrame from: " + sender.getDeviceName() + ", timing: " + timing));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiTimeCodeQuarterFrame(timing);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "TimeCodeQuarterFrame from: " + sender.getDeviceName() + ", timing: " + timing));
            }
        }

        @Override
        public void onMidiSongSelect(MidiInputDevice sender, int song) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "SongSelect from: " + sender.getDeviceName() + ", song: " + song));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiSongSelect(song);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "SongSelect from: " + sender.getDeviceName() + ", song: " + song));
            }
        }

        @Override
        public void onMidiSongPositionPointer(MidiInputDevice sender, int position) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "SongPositionPointer from: " + sender.getDeviceName() + ", position: " + position));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiSongPositionPointer(position);
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "SongPositionPointer from: " + sender.getDeviceName() + ", position: " + position));
            }
        }

        @Override
        public void onMidiTuneRequest(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "TuneRequest from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiTuneRequest();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "TuneRequest from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiTimingClock(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "TimingClock from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiTimingClock();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "TimingClock from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiStart(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Start from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiStart();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Start from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiContinue(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Continue from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiContinue();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Continue from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiStop(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Stop from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiStop();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Stop from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiActiveSensing(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "ActiveSensing from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiActiveSensing();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "ActiveSensing from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onMidiReset(MidiInputDevice sender) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "Reset from: " + sender.getDeviceName()));

            if (thruToggleButton != null && thruToggleButton.isChecked() && getBleMidiOutputDeviceFromSpinner() != null) {
                getBleMidiOutputDeviceFromSpinner().sendMidiReset();
                midiOutputEventHandler.sendMessage(Message.obtain(midiOutputEventHandler, 0, "Reset from: " + sender.getDeviceName()));
            }
        }

        @Override
        public void onRPNMessage(MidiInputDevice sender, int channel, int function, int value) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "RPN message from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));
        }

        @Override
        public void onNRPNMessage(MidiInputDevice sender, int channel, int function, int value) {
            midiInputEventHandler.sendMessage(Message.obtain(midiInputEventHandler, 0, "NRPN message from: " + sender.getDeviceName() + ", channel: " + channel + ", function: " + function + ", value: " + value));
        }
    };

    /*
     * (non-Javadoc)
     * @see jp.kshoji.driver.midi.activity.AbstractMultipleMidiActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothMidiManager = new BleMidiPeripheralProvider(this);

        bluetoothMidiManager.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(MidiInputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(onMidiInputEventListener);
            }

            @Override
            public void onMidiOutputDeviceAttached(MidiOutputDevice midiOutputDevice) {
                Message message = new Message();
                message.arg1 = 0;
                message.obj = midiOutputDevice;
                midiOutputConnectionChangedHandler.sendMessage(message);
            }
        });

        bluetoothMidiManager.setOnMidiDeviceDetachedListener(new OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(MidiInputDevice midiInputDevice) {
                // do nothing
            }

            @Override
            public void onMidiOutputDeviceDetached(MidiOutputDevice midiOutputDevice) {
                Message message = new Message();
                message.arg1 = 1;
                message.obj = midiOutputDevice;
                midiOutputConnectionChangedHandler.sendMessage(message);
            }
        });

        ListView midiInputEventListView = (ListView) findViewById(R.id.midiInputEventListView);
        midiInputEventAdapter = new ArrayAdapter<String>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiInputEventAdapter = new ArrayAdapter<String>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiInputEventListView.setAdapter(midiInputEventAdapter);

        ListView midiOutputEventListView = (ListView) findViewById(R.id.midiOutputEventListView);
        midiOutputEventAdapter = new ArrayAdapter<String>(this, R.layout.midi_event, R.id.midiEventDescriptionTextView);
        midiOutputEventListView.setAdapter(midiOutputEventAdapter);

        thruToggleButton = (ToggleButton) findViewById(R.id.toggleButtonThru);

        deviceSpinner = (Spinner) findViewById(R.id.deviceNameSpinner);
        connectedOutputDevicesAdapter = new ArrayAdapter<MidiOutputDevice>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, android.R.id.text1, new ArrayList<MidiOutputDevice>());
        deviceSpinner.setAdapter(connectedOutputDevicesAdapter);

        View.OnTouchListener onToneButtonTouchListener = new View.OnTouchListener() {

            /*
             * (non-Javadoc)
             * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
             */
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

//        soundMaker = SoundMaker.getInstance();
//        final int bufferSize = AudioTrack.getMinBufferSize(soundMaker.getSamplingRate(), AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        int timerRate = bufferSize * 1000 / soundMaker.getSamplingRate() / 2;
//        final short[] wav = new short[bufferSize / 2];
//
//        audioTrack = prepareAudioTrack(soundMaker.getSamplingRate());
//        timer = new Timer();
//        timerTask = new TimerTask() {
//            /*
//             * (non-Javadoc)
//             * @see java.util.TimerTask#run()
//             */
//            @Override
//            public void run() {
//                if (soundMaker != null) {
//                    synchronized (tones) {
//                        for (int i = 0; i < wav.length; i++) {
//                            wav[i] = (short) (soundMaker.makeWaveStream(tones) * 1024);
//                        }
//                    }
//                    try {
//                        if (audioTrack != null) {
//                            audioTrack.write(wav, 0, wav.length);
//                        }
//                    } catch (IllegalStateException e) {
//                        // do nothing
//                    } catch (NullPointerException e) {
//                        // do nothing
//                    }
//                }
//            }
//        };
//        timer.scheduleAtFixedRate(timerTask, 10, timerRate);
    }

    /*
     * (non-Javadoc)
     * @see jp.kshoji.driver.midi.activity.AbstractMultipleMidiActivity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

//        if (timer != null) {
//            try {
//                timer.cancel();
//                timer.purge();
//            } catch (Throwable t) {
//                // do nothing
//            } finally {
//                timer = null;
//            }
//        }
//        if (audioTrack != null) {
//            try {
//                audioTrack.stop();
//                audioTrack.flush();
//                audioTrack.release();
//            } catch (Throwable t) {
//                // do nothing
//            } finally {
//                audioTrack = null;
//            }
//        }
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

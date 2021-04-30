package jp.kshoji.blemidi.sample;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import java.util.HashSet;
import java.util.Iterator;
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
import jp.kshoji.blemidi.sample.util.SoundMaker;
import jp.kshoji.blemidi.sample.util.Tone;

public class SynthesizerActivity extends FragmentActivity implements OnMidiInputEventListener, OnMidi2InputEventListener, AmbientModeSupport.AmbientCallbackProvider {

    private TextView textView;
    private TextView noteView;
    private TextView titleView;
    private View background;

    // Play sounds
    AudioTrack audioTrack;
    Timer timer;
    TimerTask timerTask;
    SoundMaker soundMaker;
    final Set<Tone> tones = new HashSet<>();
    int currentProgram = 0;

    BleMidiCentralProvider bleMidiCentralProvider;
    private String[] notenames;

    AmbientCallback ambientCallback;

    /**
     * @param samplingRate sampling rate for playing
     * @return configured {@link AudioTrack} instance
     */
    private static AudioTrack prepareAudioTrack(int samplingRate) {
        AudioTrack result = new AudioTrack(AudioManager.STREAM_MUSIC, samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, AudioTrack.getMinBufferSize(samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioTrack.MODE_STREAM);
        result.setVolume(1f);
        result.play();
        return result;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        bleMidiCentralProvider = null;

        textView = null;
        titleView = null;
        background = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synthesizer);

        textView = findViewById(R.id.text);
        titleView = findViewById(R.id.title);
        background = findViewById(R.id.background);
        noteView = findViewById(R.id.note);

        bleMidiCentralProvider = new BleMidiCentralProvider(this);

        notenames = getResources().getStringArray(R.array.notenames);

        ambientCallback = new AmbientCallback();
        AmbientModeSupport.attach(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        bleMidiCentralProvider.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(SynthesizerActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (textView != null) {
                            textView.setText(getString(R.string.connected));
                        }
                    }
                });
            }

            @Override
            public void onMidiOutputDeviceAttached(@NonNull final MidiOutputDevice midiOutputDevice) {
                // do nothing
            }

            @Override
            public void onMidi2InputDeviceAttached(@NonNull Midi2InputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(SynthesizerActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (textView != null) {
                            textView.setText(getString(R.string.connected));
                        }
                    }
                });
            }

            @Override
            public void onMidi2OutputDeviceAttached(@NonNull final Midi2OutputDevice midiOutputDevice) {
                // do nothing
            }
        });

        bleMidiCentralProvider.setOnMidiDeviceDetachedListener(new OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(null);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bleMidiCentralProvider == null) {
                            return;
                        }

                        if (bleMidiCentralProvider.getMidiInputDevices().size() <= 1) {
                            if (textView != null) {
                                textView.setText(getString(R.string.no_devices_found));
                            }
                        }

                        noteView.setText("");
                    }
                });
            }

            @Override
            public void onMidiOutputDeviceDetached(@NonNull final MidiOutputDevice midiOutputDevice) {
                // do nothing
            }

            @Override
            public void onMidi2InputDeviceDetached(@NonNull Midi2InputDevice midiInputDevice) {
                midiInputDevice.setOnMidiInputEventListener(null);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bleMidiCentralProvider == null) {
                            return;
                        }

                        if (bleMidiCentralProvider.getMidiInputDevices().size() <= 1) {
                            if (textView != null) {
                                textView.setText(getString(R.string.no_devices_found));
                            }
                        }

                        noteView.setText("");
                    }
                });
            }

            @Override
            public void onMidi2OutputDeviceDetached(@NonNull final Midi2OutputDevice midiOutputDevice) {
                // do nothing
            }
        });

        bleMidiCentralProvider.startScanDevice(-1);

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
    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return ambientCallback;
    }

    // region MIDI 2.0
    @Override
    public void onMidiNoop(Midi2InputDevice sender, int group) {

    }

    @Override
    protected void onPause() {
        super.onPause();

        bleMidiCentralProvider.stopScanDevice();

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
    }

    @Override
    public void onMidiSystemExclusive(@NonNull MidiInputDevice midiInputDevice, @NonNull byte[] bytes) {

    }

    @Override
    public void onMidiNoteOff(@NonNull MidiInputDevice midiInputDevice, int channel, int note, int velocity) {
        synchronized (tones) {
            Iterator<Tone> it = tones.iterator();
            while (it.hasNext()) {
                Tone tone = it.next();
                if (tone.getNote() == note) {
                    it.remove();
                }
            }

            if (tones.size() < 1) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noteView.setText("");
                    }
                });
            }
        }
    }

    @Override
    public void onMidiNoteOn(@NonNull MidiInputDevice midiInputDevice, int channel, final int note, int velocity) {
        synchronized (tones) {
            if (velocity == 0) {
                Iterator<Tone> it = tones.iterator();
                while (it.hasNext()) {
                    Tone tone = it.next();
                    if (tone.getNote() == note) {
                        it.remove();
                    }
                }

                if (tones.size() < 1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            noteView.setText("");
                        }
                    });
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noteView.setText(notenames[note % 12]);
                    }
                });
                tones.add(new Tone(note, velocity / 127.0, currentProgram));
            }
        }
    }

    @Override
    public void onMidiPolyphonicAftertouch(@NonNull MidiInputDevice midiInputDevice, int i, int i1, int i2) {

    }

    @Override
    public void onMidiControlChange(@NonNull MidiInputDevice midiInputDevice, int i, int i1, int i2) {

    }

    @Override
    public void onMidiProgramChange(@NonNull MidiInputDevice midiInputDevice, int channel, int program) {
        currentProgram = program % Tone.FORM_MAX;
        synchronized (tones) {
            for (Tone tone : tones) {
                tone.setForm(currentProgram);
            }
        }
    }

    @Override
    public void onMidiChannelAftertouch(@NonNull MidiInputDevice midiInputDevice, int i, int i1) {

    }

    @Override
    public void onMidiPitchWheel(@NonNull MidiInputDevice midiInputDevice, int i, int i1) {

    }

    @Override
    public void onMidiTimeCodeQuarterFrame(@NonNull MidiInputDevice midiInputDevice, int i) {

    }

    @Override
    public void onMidiSongSelect(@NonNull MidiInputDevice midiInputDevice, int i) {

    }

    @Override
    public void onMidiSongPositionPointer(@NonNull MidiInputDevice midiInputDevice, int i) {

    }

    @Override
    public void onMidiTuneRequest(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onMidiTimingClock(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onMidiStart(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onMidiContinue(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onMidiStop(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onMidiActiveSensing(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onMidiReset(@NonNull MidiInputDevice midiInputDevice) {

    }

    @Override
    public void onRPNMessage(@NonNull MidiInputDevice midiInputDevice, int i, int i1, int i2) {

    }

    @Override
    public void onNRPNMessage(@NonNull MidiInputDevice midiInputDevice, int i, int i1, int i2) {

    }

    @Override
    public void onMidiJitterReductionClock(Midi2InputDevice sender, int group, int senderClockTime) {

    }

    @Override
    public void onMidiJitterReductionTimestamp(Midi2InputDevice sender, int group, int senderClockTimestamp) {

    }

    @Override
    public void onMidiTimeCodeQuarterFrame(Midi2InputDevice sender, int group, int timing) {

    }

    @Override
    public void onMidiSongSelect(Midi2InputDevice sender, int group, int song) {

    }

    @Override
    public void onMidiSongPositionPointer(Midi2InputDevice sender, int group, int position) {

    }

    @Override
    public void onMidiTuneRequest(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidiTimingClock(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidiStart(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidiContinue(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidiStop(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidiActiveSensing(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidiReset(Midi2InputDevice sender, int group) {

    }

    @Override
    public void onMidi1NoteOff(Midi2InputDevice sender, int group, int channel, int note, int velocity) {
        synchronized (tones) {
            Iterator<Tone> it = tones.iterator();
            while (it.hasNext()) {
                Tone tone = it.next();
                if (tone.getNote() == note) {
                    it.remove();
                }
            }

            if (tones.size() < 1) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noteView.setText("");
                    }
                });
            }
        }
    }

    @Override
    public void onMidi1NoteOn(Midi2InputDevice sender, int group, int channel, final int note, int velocity) {
        synchronized (tones) {
            if (velocity == 0) {
                Iterator<Tone> it = tones.iterator();
                while (it.hasNext()) {
                    Tone tone = it.next();
                    if (tone.getNote() == note) {
                        it.remove();
                    }
                }

                if (tones.size() < 1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            noteView.setText("");
                        }
                    });
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noteView.setText(notenames[note % 12]);
                    }
                });
                tones.add(new Tone(note, velocity / 127.0, currentProgram));
            }
        }
    }

    @Override
    public void onMidi1PolyphonicAftertouch(Midi2InputDevice sender, int group, int channel, int note, int pressure) {

    }

    @Override
    public void onMidi1ControlChange(Midi2InputDevice sender, int group, int channel, int function, int value) {

    }

    @Override
    public void onMidi1ProgramChange(Midi2InputDevice sender, int group, int channel, int program) {
        currentProgram = program % Tone.FORM_MAX;
        synchronized (tones) {
            for (Tone tone : tones) {
                tone.setForm(currentProgram);
            }
        }
    }

    @Override
    public void onMidi1ChannelAftertouch(Midi2InputDevice sender, int group, int channel, int pressure) {

    }

    @Override
    public void onMidi1PitchWheel(Midi2InputDevice sender, int group, int channel, int amount) {

    }

    @Override
    public void onMidi1SystemExclusive(Midi2InputDevice sender, int group, @NonNull byte[] systemExclusive) {

    }

    @Override
    public void onMidi2NoteOff(Midi2InputDevice sender, int group, int channel, int note, int velocity, int attributeType, int attributeData) {
        synchronized (tones) {
            Iterator<Tone> it = tones.iterator();
            while (it.hasNext()) {
                Tone tone = it.next();
                if (tone.getNote() == note) {
                    it.remove();
                }
            }

            if (tones.size() < 1) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noteView.setText("");
                    }
                });
            }
        }
    }

    @Override
    public void onMidi2NoteOn(Midi2InputDevice sender, int group, int channel, final int note, int velocity, int attributeType, int attributeData) {
        synchronized (tones) {
            if (velocity == 0) {
                Iterator<Tone> it = tones.iterator();
                while (it.hasNext()) {
                    Tone tone = it.next();
                    if (tone.getNote() == note) {
                        it.remove();
                    }
                }

                if (tones.size() < 1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            noteView.setText("");
                        }
                    });
                }
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noteView.setText(notenames[note % 12]);
                    }
                });
                tones.add(new Tone(note, velocity / 65535.0, currentProgram));
            }
        }
    }

    @Override
    public void onMidi2PolyphonicAftertouch(Midi2InputDevice sender, int group, int channel, int note, long pressure) {

    }

    @Override
    public void onMidi2ControlChange(Midi2InputDevice sender, int group, int channel, int index, long value) {

    }

    @Override
    public void onMidi2ProgramChange(Midi2InputDevice sender, int group, int channel, int optionFlags, int program, int bank) {
        currentProgram = program % Tone.FORM_MAX;
        synchronized (tones) {
            for (Tone tone : tones) {
                tone.setForm(currentProgram);
            }
        }
    }

    @Override
    public void onMidi2ChannelAftertouch(Midi2InputDevice sender, int group, int channel, long pressure) {

    }

    @Override
    public void onMidi2PitchWheel(Midi2InputDevice sender, int group, int channel, long amount) {

    }

    @Override
    public void onMidiPerNotePitchWheel(Midi2InputDevice sender, int group, int channel, int note, long amount) {

    }

    @Override
    public void onMidiPerNoteManagement(Midi2InputDevice sender, int group, int channel, int note, int optionFlags) {

    }

    @Override
    public void onMidiRegisteredPerNoteController(Midi2InputDevice sender, int group, int channel, int note, int index, long data) {

    }

    @Override
    public void onMidiAssignablePerNoteController(Midi2InputDevice sender, int group, int channel, int note, int index, long data) {

    }

    @Override
    public void onMidiRegisteredController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {

    }

    @Override
    public void onMidiAssignableController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {

    }

    @Override
    public void onMidiRelativeRegisteredController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {

    }

    @Override
    public void onMidiRelativeAssignableController(Midi2InputDevice sender, int group, int channel, int bank, int index, long data) {

    }

    @Override
    public void onMidi2SystemExclusive(Midi2InputDevice sender, int group, int streamId, @NonNull byte[] systemExclusive) {

    }

    @Override
    public void onMidiMixedDataSetHeader(Midi2InputDevice sender, int group, int mdsId, @NonNull byte[] headers) {

    }

    @Override
    public void onMidiMixedDataSetPayload(Midi2InputDevice sender, int group, int mdsId, @NonNull byte[] payloads) {

    }

    class AmbientCallback extends AmbientModeSupport.AmbientCallback {
        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            super.onEnterAmbient(ambientDetails);

            textView.getPaint().setAntiAlias(false);
            titleView.getPaint().setAntiAlias(false);
            noteView.getPaint().setAntiAlias(false);
            background.setBackgroundColor(0);
        }

        @Override
        public void onExitAmbient() {
            super.onExitAmbient();

            textView.getPaint().setAntiAlias(true);
            titleView.getPaint().setAntiAlias(true);
            noteView.getPaint().setAntiAlias(true);
            background.setBackgroundColor(0xff80a0f0);
        }
    }
    // endRegion
}

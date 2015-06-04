package jp.kshoji.blemidi.sample;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;
import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.sample.util.SoundMaker;
import jp.kshoji.blemidi.sample.util.Tone;

public class SynthesizerActivity extends WearableActivity implements OnMidiInputEventListener {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synthesizer);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                textView = (TextView) stub.findViewById(R.id.text);
                titleView = (TextView) stub.findViewById(R.id.title);
                background = stub.findViewById(R.id.background);
                noteView = (TextView) stub.findViewById(R.id.note);
            }
        });

        bleMidiCentralProvider = new BleMidiCentralProvider(this);

        notenames = getResources().getStringArray(R.array.notenames);

        setAmbientEnabled();
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
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);

        if (textView != null) {
            textView.getPaint().setAntiAlias(false);
        }
        if (titleView != null) {
            titleView.getPaint().setAntiAlias(false);
        }
        if (noteView != null) {
            noteView.getPaint().setAntiAlias(false);
        }
        if (background != null) {
            background.setBackgroundColor(0);
        }
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();

        if (textView != null) {
            textView.getPaint().setAntiAlias(true);
        }
        if (titleView != null) {
            titleView.getPaint().setAntiAlias(true);
        }
        if (noteView != null) {
            noteView.getPaint().setAntiAlias(true);
        }
        if (background != null) {
            background.setBackgroundColor(0xff80a0f0);
        }
    }

    /**
     * @param samplingRate sampling rate for playing
     * @return configured {@link AudioTrack} instance
     */
    private static AudioTrack prepareAudioTrack(int samplingRate) {
        AudioTrack result = new AudioTrack(AudioManager.STREAM_MUSIC, samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, AudioTrack.getMinBufferSize(samplingRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), AudioTrack.MODE_STREAM);
        result.setStereoVolume(1f, 1f);
        result.play();
        return result;
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
                noteView.setText("");
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
                    noteView.setText("");
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
}

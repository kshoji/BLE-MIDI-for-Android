package jp.kshoji.blemidi.listener;

import android.support.annotation.NonNull;

import jp.kshoji.blemidi.device.MidiInputDevice;

/**
 * MIDI events Listener without behaviour
 *
 * @author K.Shoji
 */
public class NullOnMidiInputEventListener implements OnMidiInputEventListener {
    @Override
    public void onMidiSystemExclusive(@NonNull MidiInputDevice sender, @NonNull byte[] systemExclusive) {

    }

    @Override
    public void onMidiNoteOff(@NonNull MidiInputDevice sender, int channel, int note, int velocity) {

    }

    @Override
    public void onMidiNoteOn(@NonNull MidiInputDevice sender, int channel, int note, int velocity) {

    }

    @Override
    public void onMidiPolyphonicAftertouch(@NonNull MidiInputDevice sender, int channel, int note, int pressure) {

    }

    @Override
    public void onMidiControlChange(@NonNull MidiInputDevice sender, int channel, int function, int value) {

    }

    @Override
    public void onMidiProgramChange(@NonNull MidiInputDevice sender, int channel, int program) {

    }

    @Override
    public void onMidiChannelAftertouch(@NonNull MidiInputDevice sender, int channel, int pressure) {

    }

    @Override
    public void onMidiPitchWheel(@NonNull MidiInputDevice sender, int channel, int amount) {

    }

    @Override
    public void onMidiTimeCodeQuarterFrame(@NonNull MidiInputDevice sender, int timing) {

    }

    @Override
    public void onMidiSongSelect(@NonNull MidiInputDevice sender, int song) {

    }

    @Override
    public void onMidiSongPositionPointer(@NonNull MidiInputDevice sender, int position) {

    }

    @Override
    public void onMidiTuneRequest(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onMidiTimingClock(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onMidiStart(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onMidiContinue(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onMidiStop(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onMidiActiveSensing(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onMidiReset(@NonNull MidiInputDevice sender) {

    }

    @Override
    public void onRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {

    }

    @Override
    public void onNRPNMessage(@NonNull MidiInputDevice sender, int channel, int function, int value) {

    }
}

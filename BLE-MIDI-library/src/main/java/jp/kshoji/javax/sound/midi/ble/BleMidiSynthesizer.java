package jp.kshoji.javax.sound.midi.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import jp.kshoji.javax.sound.midi.Instrument;
import jp.kshoji.javax.sound.midi.MidiChannel;
import jp.kshoji.javax.sound.midi.MidiUnavailableException;
import jp.kshoji.javax.sound.midi.Patch;
import jp.kshoji.javax.sound.midi.Receiver;
import jp.kshoji.javax.sound.midi.Soundbank;
import jp.kshoji.javax.sound.midi.Synthesizer;
import jp.kshoji.javax.sound.midi.Transmitter;
import jp.kshoji.javax.sound.midi.VoiceStatus;
import jp.kshoji.javax.sound.midi.impl.MidiChannelImpl;

/**
 * {@link jp.kshoji.javax.sound.midi.Synthesizer} implementation
 *
 * @author K.Shoji
 */
public class BleMidiSynthesizer implements Synthesizer {
    private final BleMidiDevice bleMidiDevice;
    private MidiChannel[] channels;
    private VoiceStatus[] voiceStatuses;

    /**
     * Constructor
     *
     * @param bleMidiDevice the device
     */
    public BleMidiSynthesizer(final BleMidiDevice bleMidiDevice) {
        this.bleMidiDevice = bleMidiDevice;

        Receiver receiver = null;
        try {
            receiver = this.bleMidiDevice.getReceiver();
        } catch (final MidiUnavailableException ignored) {
        }

        if (receiver == null) {
            // empty
            channels = new MidiChannel[0];
            voiceStatuses = new VoiceStatus[0];
        } else {
            // 16 channels
            voiceStatuses = new VoiceStatus[16];
            channels = new MidiChannel[16];
            for (int channel = 0; channel < 16; channel++) {
                voiceStatuses[channel] = new VoiceStatus();
                channels[channel] = new MidiChannelImpl(channel, receiver, voiceStatuses[channel]);
            }
        }
    }

    @NonNull
    @Override
    public MidiChannel[] getChannels() {
        return channels;
    }

    @Override
    public long getLatency() {
        return 0;
    }

    @Override
    public int getMaxPolyphony() {
        return 127;
    }

    @NonNull
    @Override
    public VoiceStatus[] getVoiceStatus() {
        return voiceStatuses;
    }

    @Nullable
    @Override
    public Soundbank getDefaultSoundbank() {
        return null;
    }

    @Override
    public boolean isSoundbankSupported(@NonNull final Soundbank soundbank) {
        return false;
    }

    @NonNull
    @Override
    public Instrument[] getAvailableInstruments() {
        return new Instrument[0];
    }

    @NonNull
    @Override
    public Instrument[] getLoadedInstruments() {
        return new Instrument[0];
    }

    @Override
    public boolean remapInstrument(@NonNull final Instrument from, @NonNull final Instrument to) {
        return false;
    }

    @Override
    public boolean loadAllInstruments(@NonNull final Soundbank soundbank) {
        return false;
    }

    @Override
    public void unloadAllInstruments(@NonNull final Soundbank soundbank) {

    }

    @Override
    public boolean loadInstrument(@NonNull final Instrument instrument) {
        return false;
    }

    @Override
    public void unloadInstrument(@NonNull final Instrument instrument) {

    }

    @Override
    public boolean loadInstruments(@NonNull final Soundbank soundbank, @NonNull final Patch[] patchList) {
        return false;
    }

    @Override
    public void unloadInstruments(@NonNull final Soundbank soundbank, @NonNull final Patch[] patchList) {

    }

    @NonNull
    @Override
    public Info getDeviceInfo() {
        return bleMidiDevice.getDeviceInfo();
    }

    @Override
    public void open() throws MidiUnavailableException {
        bleMidiDevice.open();
    }

    @Override
    public void close() {
        bleMidiDevice.close();
    }

    @Override
    public boolean isOpen() {
        return bleMidiDevice.isOpen();
    }

    @Override
    public long getMicrosecondPosition() {
        return -1;
    }

    @Override
    public int getMaxReceivers() {
        return bleMidiDevice.getMaxReceivers();
    }

    @Override
    public int getMaxTransmitters() {
        return bleMidiDevice.getMaxTransmitters();
    }

    @NonNull
    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        return bleMidiDevice.getReceiver();
    }

    @NonNull
    @Override
    public List<Receiver> getReceivers() {
        return bleMidiDevice.getReceivers();
    }

    @NonNull
    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        return bleMidiDevice.getTransmitter();
    }

    @NonNull
    @Override
    public List<Transmitter> getTransmitters() {
        return bleMidiDevice.getTransmitters();
    }

    public void setReceiver(final Receiver receiver) {
        // 16 channels
        voiceStatuses = new VoiceStatus[16];
        channels = new MidiChannel[16];
        for (int channel = 0; channel < 16; channel++) {
            voiceStatuses[channel] = new VoiceStatus();
            channels[channel] = new MidiChannelImpl(channel, receiver, voiceStatuses[channel]);
        }
    }
}

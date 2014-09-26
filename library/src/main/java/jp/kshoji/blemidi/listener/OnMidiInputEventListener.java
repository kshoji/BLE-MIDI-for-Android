package jp.kshoji.blemidi.listener;

import jp.kshoji.blemidi.device.MidiInputDevice;

/**
 * Listener for MIDI events
 *
 * @author K.Shoji
 */
public interface OnMidiInputEventListener {

	/**
	 * SysEx
	 *
     * @param sender the device sent this message
	 * @param systemExclusive received message
	 */
	void onMidiSystemExclusive(MidiInputDevice sender, byte[] systemExclusive);
	
	/**
	 * Note-off
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param note 0-127
	 * @param velocity 0-127
	 */
	void onMidiNoteOff(MidiInputDevice sender, int channel, int note, int velocity);
	
	/**
	 * Note-on
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param note 0-127
	 * @param velocity 0-127
	 */
	void onMidiNoteOn(MidiInputDevice sender, int channel, int note, int velocity);
	
	/**
	 * Poly-KeyPress
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param note 0-127
	 * @param pressure 0-127
	 */
	void onMidiPolyphonicAftertouch(MidiInputDevice sender, int channel, int note, int pressure);
	
	/**
	 * Control Change
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param function 0-127
	 * @param value 0-127
	 */
	void onMidiControlChange(MidiInputDevice sender, int channel, int function, int value);
	
	/**
	 * Program Change
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param program 0-127
	 */
	void onMidiProgramChange(MidiInputDevice sender, int channel, int program);
	
	/**
	 * Channel Pressure
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param pressure 0-127
	 */
	void onMidiChannelAftertouch(MidiInputDevice sender, int channel, int pressure);
	
	/**
	 * PitchBend Change
	 *
     * @param sender the device sent this message
	 * @param channel 0-15
	 * @param amount 0(low)-8192(center)-16383(high)
	 */
	void onMidiPitchWheel(MidiInputDevice sender, int channel, int amount);

    /**
     * MIDI Time Code(MTC) Quarter Frame
     *
     * @param sender the device sent this message
     * @param timing 0-127
     */
    void onMidiTimeCodeQuarterFrame(MidiInputDevice sender, int timing);

    /**
     * Song Select
     *
     * @param sender the device sent this message
     * @param song 0-127
     */
    void onMidiSongSelect(MidiInputDevice sender, int song);

    /**
     * Song Position Pointer
     *
     * @param sender the device sent this message
     * @param position 0-16383
     */
    void onMidiSongPositionPointer(MidiInputDevice sender, int position);

    /**
     * Tune Request
     *
     * @param sender the device sent this message
     */
    void onMidiTuneRequest(MidiInputDevice sender);

    /**
     * Timing Clock
     *
     * @param sender the device sent this message
     */
    void onMidiTimingClock(MidiInputDevice sender);

    /**
     * Start Playing
     *
     * @param sender the device sent this message
     */
    void onMidiStart(MidiInputDevice sender);

    /**
     * Continue Playing
     *
     * @param sender the device sent this message
     */
    void onMidiContinue(MidiInputDevice sender);

    /**
     * Stop Playing
     *
     * @param sender the device sent this message
     */
    void onMidiStop(MidiInputDevice sender);

    /**
     * Active Sensing
     *
     * @param sender the device sent this message
     */
    void onMidiActiveSensing(MidiInputDevice sender);

    /**
     * Reset Device
     *
     * @param sender the device sent this message
     */
    void onMidiReset(MidiInputDevice sender);

    /**
     * RPN message<br />
     * invoked when value's MSB or LSB changed
     *
     * @param sender the device sent this message
     * @param channel 0-15
     * @param function 14bits
     * @param value 7 bits or 14 bits
     */
    void onRPNMessage(MidiInputDevice sender, int channel, int function, int value);

    /**
     * NRPN message<br />
     * invoked when value's MSB or LSB changed
     *
     * @param sender the device sent this message
     * @param channel 0-15
     * @param function 14bits
     * @param value 7 bits or 14 bits
     */
    void onNRPNMessage(MidiInputDevice sender, int channel, int function, int value);
}

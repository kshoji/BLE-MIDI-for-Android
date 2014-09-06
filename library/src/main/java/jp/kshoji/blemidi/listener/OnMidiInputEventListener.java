package jp.kshoji.blemidi.listener;

/**
 * Listener for MIDI events
 *
 * @author K.Shoji
 */
public interface OnMidiInputEventListener {

	/**
	 * SysEx
	 *
	 * @param systemExclusive
	 */
	void onMidiSystemExclusive(byte[] systemExclusive);
	
	/**
	 * Note-off
	 *
	 * @param channel 0-15
	 * @param note 0-127
	 * @param velocity 0-127
	 */
	void onMidiNoteOff(int channel, int note, int velocity);
	
	/**
	 * Note-on
	 *
	 * @param channel 0-15
	 * @param note 0-127
	 * @param velocity 0-127
	 */
	void onMidiNoteOn(int channel, int note, int velocity);
	
	/**
	 * Poly-KeyPress
	 *
	 * @param channel 0-15
	 * @param note 0-127
	 * @param pressure 0-127
	 */
	void onMidiPolyphonicAftertouch(int channel, int note, int pressure);
	
	/**
	 * Control Change
	 *
	 * @param channel 0-15
	 * @param function 0-127
	 * @param value 0-127
	 */
	void onMidiControlChange(int channel, int function, int value);
	
	/**
	 * Program Change
	 *
	 * @param channel 0-15
	 * @param program 0-127
	 */
	void onMidiProgramChange(int channel, int program);
	
	/**
	 * Channel Pressure
	 *
	 * @param channel 0-15
	 * @param pressure 0-127
	 */
	void onMidiChannelAftertouch(int channel, int pressure);
	
	/**
	 * PitchBend Change
	 *
	 * @param channel 0-15
	 * @param amount 0(low)-8192(center)-16383(high)
	 */
	void onMidiPitchWheel(int channel, int amount);

    /**
     * MIDI Time Code(MTC) Quarter Frame
     * @param timing 0-127
     */
    void onMidiTimeCodeQuarterFrame(int timing);

    /**
     * Song Select
     *
     * @param song 0-127
     */
    void onMidiSongSelect(int song);

    /**
     * Song Position Pointer
     *
     * @param position 0-16383
     */
    void onMidiSongPositionPointer(int position);

    /**
     * Tune Request
     */
    void onMidiTuneRequest();

    /**
     * Timing Clock
     */
    void onMidiTimingClock();

    /**
     * Start Playing
     */
    void onMidiStart();

    /**
     * Continue Playing
     */
    void onMidiContinue();

    /**
     * Stop Playing
     */
    void onMidiStop();

    /**
     * Active Sensing
     */
    void onMidiActiveSensing();

    /**
     * Reset Device
     */
    void onMidiReset();

    /**
     * RPN message<br />
     * invoked when value's MSB or LSB changed
     *
     * @param channel 0-15
     * @param function 14bits
     * @param value 7 bits or 14 bits
     */
    void onRPNMessage(int channel, int function, int value);

    /**
     * NRPN message<br />
     * invoked when value's MSB or LSB changed
     *
     * @param channel 0-15
     * @param function 14bits
     * @param value 7 bits or 14 bits
     */
    void onNRPNMessage(int channel, int function, int value);
}

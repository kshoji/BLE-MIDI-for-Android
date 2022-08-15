package jp.kshoji.unity.midi;

/**
 * Listener for MIDI events
 *
 * @author K.Shoji
 */
public interface OnBleMidiInputEventListener {

	/**
	 * SysEx
	 *
     * @param deviceId the device sent this message
	 * @param systemExclusive received message
	 */
	void onMidiSystemExclusive(String deviceId, byte[] systemExclusive);
	
	/**
	 * Note-off
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param note 0-127
	 * @param velocity 0-127
	 */
	void onMidiNoteOff(String deviceId, int channel, int note, int velocity);
	
	/**
	 * Note-on
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param note 0-127
	 * @param velocity 0-127
	 */
	void onMidiNoteOn(String deviceId, int channel, int note, int velocity);
	
	/**
	 * Poly-KeyPress
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param note 0-127
	 * @param pressure 0-127
	 */
	void onMidiPolyphonicAftertouch(String deviceId, int channel, int note, int pressure);
	
	/**
	 * Control Change
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param function 0-127
	 * @param value 0-127
	 */
	void onMidiControlChange(String deviceId, int channel, int function, int value);
	
	/**
	 * Program Change
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param program 0-127
	 */
	void onMidiProgramChange(String deviceId, int channel, int program);
	
	/**
	 * Channel Pressure
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param pressure 0-127
	 */
	void onMidiChannelAftertouch(String deviceId, int channel, int pressure);
	
	/**
	 * PitchBend Change
	 *
     * @param deviceId the device sent this message
	 * @param channel 0-15
	 * @param amount 0(low)-8192(center)-16383(high)
	 */
	void onMidiPitchWheel(String deviceId, int channel, int amount);

    /**
     * MIDI Time Code(MTC) Quarter Frame
     *
     * @param deviceId the device sent this message
     * @param timing 0-16383
     */
    void onMidiTimeCodeQuarterFrame(String deviceId, int timing);

    /**
     * Song Select
     *
     * @param deviceId the device sent this message
     * @param song 0-127
     */
    void onMidiSongSelect(String deviceId, int song);

    /**
     * Song Position Pointer
     *
     * @param deviceId the device sent this message
     * @param position 0-16383
     */
    void onMidiSongPositionPointer(String deviceId, int position);

    /**
     * Tune Request
     *
     * @param deviceId the device sent this message
     */
    void onMidiTuneRequest(String deviceId);

    /**
     * Timing Clock
     *
     * @param deviceId the device sent this message
     */
    void onMidiTimingClock(String deviceId);

    /**
     * Start Playing
     *
     * @param deviceId the device sent this message
     */
    void onMidiStart(String deviceId);

    /**
     * Continue Playing
     *
     * @param deviceId the device sent this message
     */
    void onMidiContinue(String deviceId);

    /**
     * Stop Playing
     *
     * @param deviceId the device sent this message
     */
    void onMidiStop(String deviceId);

    /**
     * Active Sensing
     *
     * @param deviceId the device sent this message
     */
    void onMidiActiveSensing(String deviceId);

    /**
     * Reset Device
     *
     * @param deviceId the device sent this message
     */
    void onMidiReset(String deviceId);
}

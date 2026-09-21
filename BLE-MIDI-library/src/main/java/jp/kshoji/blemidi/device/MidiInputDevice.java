package jp.kshoji.blemidi.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import jp.kshoji.blemidi.listener.OnMidiInputEventListener;
import jp.kshoji.blemidi.util.BleMidiTimestampCoordinator;

/**
 * Represents BLE MIDI Input Device
 *
 * @author K.Shoji
 */
public abstract class MidiInputDevice {

    /**
     * Attaches {@link jp.kshoji.blemidi.listener.OnMidiInputEventListener}
     *
     * @param midiInputEventListener the listener
     */
    public abstract void setOnMidiInputEventListener(@Nullable OnMidiInputEventListener midiInputEventListener);

    /**
     * Sets how BLE MIDI timestamps affect input callback delivery.
     * <p>
     * Default is {@link BleMidiTimestampCoordinator.SchedulingMode#SCHEDULED}.
     * Unity uses {@link BleMidiTimestampCoordinator.SchedulingMode#LOW_LATENCY} by default.
     *
     * @param schedulingMode the scheduling mode
     */
    public abstract void setTimestampSchedulingMode(@NonNull BleMidiTimestampCoordinator.SchedulingMode schedulingMode);

    /**
     * @return the current timestamp scheduling mode
     */
    @NonNull
    public abstract BleMidiTimestampCoordinator.SchedulingMode getTimestampSchedulingMode();

    /**
     * Sets the schedule-ahead ceiling for
     * {@link BleMidiTimestampCoordinator.SchedulingMode#LOW_LATENCY}.
     * Default is {@link BleMidiTimestampCoordinator#DEFAULT_MAX_SCHEDULE_AHEAD_MS} (40).
     *
     * @param maxScheduleAheadMs milliseconds (typical 20–50)
     */
    public abstract void setMaxScheduleAheadMs(int maxScheduleAheadMs);

    /**
     * @return the LOW_LATENCY schedule-ahead ceiling in milliseconds
     */
    public abstract int getMaxScheduleAheadMs();

    /**
     * Starts using the device
     */
    public abstract void start();

    /**
     * Stops using the device
     */
    public abstract void stop();

    /**
     * Terminates the device instance
     */
    public abstract void terminate();

    /**
     * Obtains the device name
     *
     * @return device name
     */
    @NonNull
    public abstract String getDeviceName();

    /**
     * Obtains the manufacturer name
     *
     * @return manufacturer name
     */
    @NonNull
    public abstract String getManufacturer();

    /**
     * Obtains the model name
     *
     * @return model name
     */
    @NonNull
    public abstract String getModel();

    /**
     * Obtains the device address
     *
     * @return device address
     */
    @NonNull
    public abstract String getDeviceAddress();

    @NonNull
    @Override
    public final String toString() {
        return getDeviceName();
    }
}

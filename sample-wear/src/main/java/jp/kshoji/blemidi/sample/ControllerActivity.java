package jp.kshoji.blemidi.sample;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

import jp.kshoji.blemidi.central.BleMidiCentralProvider;
import jp.kshoji.blemidi.device.MidiInputDevice;
import jp.kshoji.blemidi.device.MidiOutputDevice;
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener;
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener;

public class ControllerActivity extends Activity implements SensorEventListener {

    private TextView textView;
    private Spinner deviceSpinner;
    private ArrayAdapter<MidiOutputDevice> connectedDevicesAdapter;
    private SensorManager sensorManager;

    BleMidiCentralProvider bleMidiCentralProvider;

    MidiOutputDevice getBleMidiOutputDeviceFromSpinner() {
        if (deviceSpinner != null && deviceSpinner.getSelectedItemPosition() >= 0 && connectedDevicesAdapter != null && !connectedDevicesAdapter.isEmpty()) {
            MidiOutputDevice device = connectedDevicesAdapter.getItem(deviceSpinner.getSelectedItemPosition());
            if (device != null) {
                Set<MidiOutputDevice> midiOutputDevices = bleMidiCentralProvider.getMidiOutputDevices();

                if (midiOutputDevices.size() > 0) {
                    // returns the first one.
                    return (MidiOutputDevice) midiOutputDevices.toArray()[0];
                }
            }
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                textView = (TextView) stub.findViewById(R.id.text);
                deviceSpinner = (Spinner) stub.findViewById(R.id.deviceSpinner);
                connectedDevicesAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, android.R.id.text1, new ArrayList<MidiOutputDevice>());
                deviceSpinner.setAdapter(connectedDevicesAdapter);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        textView = null;
        connectedDevicesAdapter.clear();
        connectedDevicesAdapter = null;
        deviceSpinner = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);

        bleMidiCentralProvider = new BleMidiCentralProvider(this);

        bleMidiCentralProvider.setOnMidiDeviceAttachedListener(new OnMidiDeviceAttachedListener() {
            @Override
            public void onMidiInputDeviceAttached(@NonNull MidiInputDevice midiInputDevice) {
                // do nothing
            }

            @Override
            public void onMidiOutputDeviceAttached(@NonNull final MidiOutputDevice midiOutputDevice) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectedDevicesAdapter.add(midiOutputDevice);
                        textView.setVisibility(View.GONE);
                        deviceSpinner.setVisibility(View.VISIBLE);
                    }
                });
            }
        });

        bleMidiCentralProvider.setOnMidiDeviceDetachedListener(new OnMidiDeviceDetachedListener() {
            @Override
            public void onMidiInputDeviceDetached(@NonNull MidiInputDevice midiInputDevice) {

            }

            @Override
            public void onMidiOutputDeviceDetached(@NonNull final MidiOutputDevice midiOutputDevice) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectedDevicesAdapter.remove(midiOutputDevice);
                        if (connectedDevicesAdapter.isEmpty()) {
                            textView.setVisibility(View.VISIBLE);
                            deviceSpinner.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });

        bleMidiCentralProvider.startScanDevice(-1);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("BLE MIDI", "onPause");

        bleMidiCentralProvider.stopScanDevice();

        bleMidiCentralProvider = null;

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        MidiOutputDevice midiOutputDevice = getBleMidiOutputDeviceFromSpinner();
        if (midiOutputDevice != null) {
            //Log.i("Sensor", "sensor value: " + sensorEvent.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}

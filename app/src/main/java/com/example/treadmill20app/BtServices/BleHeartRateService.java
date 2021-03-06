package com.example.treadmill20app.BtServices;
/*
This is an Android Service component communicating with the the Ble Gatt server on a HR device
Based on: https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
 */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.example.treadmill20app.utils.HeartRateUtils;

import java.util.List;

import static com.example.treadmill20app.BtServices.GattActions.*;
import static com.example.treadmill20app.utils.HeartRateServiceUUIDs.*;

public class BleHeartRateService extends Service {

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattService mHeartRateService = null;

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection
     * result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(...)} callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device - try to reconnect
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            boolean result = mBluetoothGatt.connect();
            return result;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources
     * are released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /*
    Implementation of callback methods for GATT events that the app cares about.
    For example connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(
                BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");

                broadcastUpdate(Event.GATT_CONNECTED);
                // attempt to discover services
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                broadcastUpdate(Event.GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                broadcastUpdate(Event.GATT_SERVICES_DISCOVERED);
                logServices(gatt); // debug

                // get the heart rate service
                mHeartRateService = gatt.getService(HEART_RATE_SERVICE);

                if (mHeartRateService != null) {
                    broadcastUpdate(Event.HEART_RATE_SERVICE_DISCOVERED);
                    logCharacteristics(mHeartRateService); // debug

                    // enable notifications on heart rate measurement
                    BluetoothGattCharacteristic heartRateMeasurement =
                            mHeartRateService.getCharacteristic(HEART_RATE_MEASUREMENT);
                    boolean result = setCharacteristicNotification(
                            heartRateMeasurement, true);
                    Log.i(TAG, "setCharacteristicNotification: " + result);
                } else {
                    broadcastUpdate(Event.HEART_RATE_SERVICE_NOT_AVAILABLE);
                    Log.i(TAG, "heart rate service not available");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if(HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
                int heartRate = HeartRateUtils.calculateHeartRateValue(characteristic);
                Log.d(TAG, String.format("heart rate: %d", heartRate));
                broadcastHeartRateUpdate(heartRate);
            }
        }
    };

    /**
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public boolean setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        // first: call setCharacteristicNotification (client side)
        boolean result = mBluetoothGatt.setCharacteristicNotification(
                characteristic, enabled);

        // second: set enable notification server side (sensor)
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        return result;
    }

    /*
    Broadcast methods for events and data
     */
    private void broadcastUpdate(final Event event) {
        final Intent intent = new Intent(ACTION_GATT_HEART_RATE_EVENTS);
        intent.putExtra(EVENT, event);
        sendBroadcast(intent);
        Log.i(TAG, "event: " + EVENT);
    }

    private void broadcastHeartRateUpdate(final int heartRate) {
        final Intent intent = new Intent(ACTION_GATT_HEART_RATE_EVENTS);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(HEART_RATE_DATA, heartRate);
        sendBroadcast(intent);
    }


    /*
    Android Service specific code for binding and unbinding to this Android service
     */
    public class LocalBinder extends Binder {
        public BleHeartRateService getService() {

            return BleHeartRateService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close()
        // is called such that resources are cleaned up properly.  In this particular
        // example, close() is invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();


    /*
    logging and debugging
     */
    private final static String TAG = BleHeartRateService.class.getSimpleName();

    private void logServices(BluetoothGatt gatt) {
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services) {
            String uuid = service.getUuid().toString();
            Log.i(TAG, "service: " + uuid);
        }
    }

    private void logCharacteristics(BluetoothGattService gattService) {
        List<BluetoothGattCharacteristic> characteristics =
                gattService.getCharacteristics();
        for (BluetoothGattCharacteristic chara : characteristics) {
            String uuid = chara.getUuid().toString();
            Log.i(TAG, "characteristic: " + uuid);
        }
    }
}

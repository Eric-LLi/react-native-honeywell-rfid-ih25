// HoneywellRfidIh25Module.java

package com.honeywellrfidih25;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.honeywell.rfidservice.EventListener;
import com.honeywell.rfidservice.RfidManager;
import com.honeywell.rfidservice.TriggerMode;
import com.honeywell.rfidservice.rfid.AntennaPower;
import com.honeywell.rfidservice.rfid.Gen2;
import com.honeywell.rfidservice.rfid.OnTagReadListener;
import com.honeywell.rfidservice.rfid.Region;
import com.honeywell.rfidservice.rfid.RfidReader;
import com.honeywell.rfidservice.rfid.RfidReaderException;
import com.honeywell.rfidservice.rfid.TagAdditionData;
import com.honeywell.rfidservice.rfid.TagReadData;
import com.honeywell.rfidservice.rfid.TagReadOption;

import java.util.ArrayList;

public class HoneywellRfidIh25Module extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

    private static final String LOG = "[IH25]";
    private final String READER_STATUS = "READER_STATUS";
    private final String TRIGGER_STATUS = "TRIGGER_STATUS";
    private final String WRITE_TAG_STATUS = "WRITE_TAG_STATUS";
    private final String BATTERY_STATUS = "BATTERY_STATUS";
    private final String TAG = "TAG";
    private static RfidManager mRfidMgr;
    private static RfidReader mRfidReader;
    private static BluetoothDevice mDevice;
    private static ArrayList<String> cacheTags = new ArrayList<>();
    private static boolean isSingleRead = false;
    private static final ArrayList<BluetoothDevice> mDevices = new ArrayList<>();

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public HoneywellRfidIh25Module(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return "HoneywellRfidIh25";
    }

    @Override
    public void onHostResume() {
        //
    }

    @Override
    public void onHostPause() {
        //
    }

    @Override
    public void onHostDestroy() {
        doDisconnect();
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendEvent(String eventName, String msg) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, msg);
    }

    @ReactMethod
    public void isConnected(Promise promise) {
        if (mRfidMgr != null) {
            promise.resolve(mRfidMgr.isConnected());
        } else {
            promise.resolve(false);
        }
    }

    @ReactMethod
    public void connect(final String name, final Promise promise) {
        try {
            mDevices.clear();

            final BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanner.startScan(scanCallback);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanner.stopScan(scanCallback);

                    for (BluetoothDevice d : mDevices) {
                        if (d.getName().equals(name)) {
                            mDevice = d;
                        }
                    }

                    if (mDevice != null) {
                        doConnect();
                    } else {
                        promise.reject(LOG, "No reader found");
                    }

                }
            }, 5 * 1000);

            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void reconnect() {
        doConnect();
    }

    @ReactMethod
    public void disconnect(Promise promise) {
        try {
            doDisconnect();
            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void clear() {
        cacheTags = new ArrayList<>();
    }

    @ReactMethod
    public void setSingleRead(boolean enable) {
        isSingleRead = enable;
    }

    @ReactMethod
    public void getDevices(final Promise promise) {
        try {
            mDevices.clear();

            final BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanner.startScan(scanCallback);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanner.stopScan(scanCallback);

                    WritableArray deviceList = Arguments.createArray();
                    for (BluetoothDevice device : mDevices) {
                        WritableMap map = Arguments.createMap();
                        map.putString("name", device.getName());
                        map.putString("mac", device.getAddress());
                        deviceList.pushMap(map);
                    }

                    promise.resolve(deviceList);
                }
            }, 3 * 1000);

        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void getDeviceDetails(Promise promise) {
        try {
            if (mRfidReader != null) {
                AntennaPower[] antennas = mRfidReader.getAntennaPower();

                WritableMap map = Arguments.createMap();
                map.putString("name", mDevice.getName());
                map.putString("mac", mDevice.getAddress());
                map.putString("power", mRfidMgr.getBatteryLevel());
                map.putInt("antennaLevel", antennas[0].getReadPower() / 100);

                promise.resolve(map);
            }

            promise.reject(LOG, "Reader is not connected");
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void setAntennaLevel(int antennaLevel, Promise promise) {
        try {
            if (mRfidReader != null) {
                AntennaPower[] antennas = mRfidReader.getAntennaPower();
                antennas[0].setReadPower(antennaLevel * 100);

                mRfidReader.setAntennaPower(antennas);
                promise.resolve(true);
            }
            promise.reject(LOG, "Reader is not connected");
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void programTag(String oldTag, String newTag, Promise promise) {
        try {
            if (mRfidReader != null) {
                mRfidReader.writeTagData(oldTag, 1, 2, "00000000", newTag);

                promise.resolve(true);

                WritableMap map = Arguments.createMap();
                map.putBoolean("status", true);
                sendEvent(WRITE_TAG_STATUS, map);
            }
            promise.reject(LOG, "Reader is not connected");
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void setEnabled(boolean enable, Promise promise) {
        try {
            if (mRfidMgr != null) {
                mRfidMgr.setTriggerMode(enable ? TriggerMode.RFID : TriggerMode.BARCODE_SCAN);

                promise.resolve(true);
            }

            promise.reject("Reader is not connected");
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    private void init() {
        if (mRfidMgr == null) {
            mRfidMgr = RfidManager.getInstance(this.reactContext);
        }
    }

    private void doConnect() {
        if (mRfidMgr != null && mRfidMgr.isConnected()) {
            doDisconnect();
        }

        if (mRfidMgr == null) {
            init();
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mRfidMgr.addEventListener(mEventListener);
                mRfidMgr.connect(mDevice.getAddress());
            }
        }, 1000);
    }

    private void doDisconnect() {
        if (mRfidMgr != null) {
            mRfidMgr.removeEventListener(mEventListener);
            mRfidMgr.disconnect();

            mRfidMgr = null;
        }

        if (mRfidReader != null) {
            mRfidReader.removeOnTagReadListener(dataListener);

            mRfidReader = null;
        }
    }

    private void read() {
        if (mRfidReader != null && mRfidReader.available()) {
            mRfidReader.read(TagAdditionData.get("None"), new TagReadOption());
        }
    }

    private void cancel() {
        if (mRfidReader != null && mRfidReader.available()) {
            mRfidReader.stopRead();
        }
    }

    private boolean addTagToList(String strEPC) {
        if (strEPC != null) {
            if (!cacheTags.contains(strEPC)) {
                cacheTags.add(strEPC);
                return true;
            }
        }
        return false;
    }

    private final EventListener mEventListener = new EventListener() {
        @Override
        public void onDeviceConnected(Object o) {
            mRfidMgr.createReader();
        }

        @Override
        public void onDeviceDisconnected(Object o) {
            WritableMap map = Arguments.createMap();
            map.putBoolean("status", false);
            sendEvent(READER_STATUS, map);
        }

        @Override
        public void onReaderCreated(boolean b, RfidReader rfidReader) {
            mRfidReader = rfidReader;

            mRfidReader.setOnTagReadListener(dataListener);
            mRfidMgr.setBeeper(true, 0, 10);
            mRfidMgr.setBeeper(false, 0, 10);
            mRfidMgr.setAutoReconnect(false);
            try {
                mRfidReader.setSession(Gen2.Session.Session0);
            } catch (RfidReaderException e) {
                e.printStackTrace();
            }
            WritableMap map = Arguments.createMap();
            map.putBoolean("status", true);
            sendEvent(READER_STATUS, map);
        }

        @Override
        public void onRfidTriggered(boolean b) {
            if (b) {
                read();
            } else {
                cancel();
            }

            WritableMap map = Arguments.createMap();
            map.putBoolean("status", b);
            sendEvent(TRIGGER_STATUS, map);
        }

        @Override
        public void onTriggerModeSwitched(TriggerMode triggerMode) {
            //
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();

            if (device.getName() != null && !device.getName().isEmpty()) {
                boolean newDevice = true;

                for (BluetoothDevice info : mDevices) {
                    if (device.getAddress().equals(info.getAddress())) {
                        newDevice = false;
                    }
                }

                if (newDevice) {
                    mDevices.add(device);
                }
            }
        }
    };

    private final OnTagReadListener dataListener = new OnTagReadListener() {
        @Override
        public void onTagRead(final TagReadData[] t) {
            mRfidMgr.setBeeper(true, 0, 10);
            mRfidMgr.setBeeper(false, 0, 10);

            for (TagReadData trd : t) {
                String epc = trd.getEpcHexStr();
                int rssi = trd.getRssi();

                if (isSingleRead) {
                    if (addTagToList(epc) && cacheTags.size() == 1) {
                        sendEvent(TAG, epc);
                        return;
                    }

//                    if (rssi < -50) {
//                        sendEvent(TAG, epc);
//                    }
                } else {
                    if (addTagToList(epc)) {
                        sendEvent(TAG, epc);
                    }
                }
            }
        }
    };

//    private boolean requestPermissions() {
//        if (Build.VERSION.SDK_INT >= 23) {
//            for (int i = 0; i < mPermissions.length; i++) {
//                if (ContextCompat.checkSelfPermission(this.reactContext, mPermissions[i]) != PackageManager.PERMISSION_GRANTED) {
//                    mRequestPermissions.add(mPermissions[i]);
//                }
//            }
//
//            if (mRequestPermissions.size() > 0) {
////                ActivityCompat.requestPermissions(this.reactContext, mPermissions, PERMISSION_REQUEST_CODE);
//                return false;
//            }
//        }
//
//        return true;
//    }
}

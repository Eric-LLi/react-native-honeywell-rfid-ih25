// HoneywellRfidIh25Module.java

package com.honeywellrfidih25;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.honeywell.rfidservice.EventListener;
import com.honeywell.rfidservice.RfidManager;
import com.honeywell.rfidservice.TriggerMode;
import com.honeywell.rfidservice.rfid.AntennaPower;
import com.honeywell.rfidservice.rfid.OnTagReadListener;
import com.honeywell.rfidservice.rfid.RfidReader;
import com.honeywell.rfidservice.rfid.TagAdditionData;
import com.honeywell.rfidservice.rfid.TagReadData;
import com.honeywell.rfidservice.rfid.TagReadOption;

import java.util.ArrayList;

public class HoneywellRfidIh25Module extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

    private final String LOG = "[IH25]";
    private final String READER_STATUS = "READER_STATUS";
    private final String TRIGGER_STATUS = "TRIGGER_STATUS";
    private final String WRITE_TAG_STATUS = "WRITE_TAG_STATUS";
    private final String BATTERY_STATUS = "BATTERY_STATUS";
    private final String TAG = "TAG";
    private static RfidManager mRfidMgr;
    private static RfidReader mRfidReader;
    private static ArrayList<String> cacheTags = new ArrayList<>();
    private static boolean isSingleRead = false;
    private static String mac;

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
    public void connect(String name, Promise promise) {
        try {
            mac = name;

            doConnect();

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
    public void setSingleRead(boolean isSingleRead) {
        isSingleRead = isSingleRead;
    }

    @ReactMethod
    public void getDeviceDetails(Promise promise) {
        try {
            if (mRfidReader != null) {
                AntennaPower antenna = mRfidReader.getAntennaPower()[0];

                WritableMap map = Arguments.createMap();
                map.putString("name", mRfidReader.getHardwareVersion());
                map.putString("mac", mRfidReader.getHardwareVersion());
                map.putString("power", mRfidMgr.getBatteryLevel());
                map.putInt("antennaLevel", antenna.getReadPower());

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
                AntennaPower antenna = mRfidReader.getAntennaPower()[0];
                antenna.setReadPower(antennaLevel);

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
            }

            promise.reject(LOG, "Reader is not connected");
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

        mRfidMgr.addEventListener(mEventListener);
        mRfidMgr.connect(mac);
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
            WritableMap map = Arguments.createMap();
            map.putBoolean("status", true);
            sendEvent(READER_STATUS, map);
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
            mRfidMgr.setBeeper(true, 10, 10);
        }

        @Override
        public void onRfidTriggered(boolean b) {
            WritableMap map = Arguments.createMap();
            map.putBoolean("status", b);
            sendEvent(TRIGGER_STATUS, map);

            if (b) {
                read();
            } else {
                cancel();
            }
        }

        @Override
        public void onTriggerModeSwitched(TriggerMode triggerMode) {
            //
        }
    };

    private final OnTagReadListener dataListener = new OnTagReadListener() {
        @Override
        public void onTagRead(final TagReadData[] t) {
            for (TagReadData trd : t) {
                String epc = trd.getEpcHexStr();
                int rssi = trd.getRssi();

                if (isSingleRead) {
                    if (rssi < -40) {
                        sendEvent(TAG, epc);
                    }
                } else {
                    if (addTagToList(epc)) {
                        sendEvent(TAG, epc);
                    }
                }
            }
        }
    };
}

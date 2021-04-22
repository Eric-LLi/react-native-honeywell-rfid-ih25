// HoneywellRfidIh25Module.java

package com.honeywellrfidih25;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import androidx.annotation.Nullable;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;

public class HoneywellRfidIh25Module extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

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
        //
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
}

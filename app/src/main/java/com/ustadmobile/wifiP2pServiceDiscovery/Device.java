package com.ustadmobile.wifiP2pServiceDiscovery;

import java.util.HashMap;

/**
 * Created by kileha3 on 29/01/2017.
 */

public class Device {

    private String deviceId;

    private HashMap<String, String> attrs;

    public Device(String deviceId) {
        this.deviceId = deviceId;
        this.attrs = new HashMap<>();

    }

    public HashMap<String, String> getAttrs() {
        return attrs;
    }

    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Device) {
            return deviceId.equals(((Device) obj).deviceId);
        }

        return false;
    }
}

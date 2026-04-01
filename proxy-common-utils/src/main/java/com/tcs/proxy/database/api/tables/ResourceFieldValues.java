package com.tcs.proxy.database.api.tables;

import java.io.Serializable;

/**
 * Carries the field values of a resource (camera) record from the proxy database.
 *
 * getName()       → camera display name
 * getKey3()       → RTSP URL stored in the resource key-3 field
 * getIpAddress()  → direct IP address of the camera device (may be null)
 */
public class ResourceFieldValues implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final String key3;
    /** Direct camera IP address from the resource record; null when not stored. */
    private final String ipAddress;

    /** Legacy constructor – ipAddress defaults to null. */
    public ResourceFieldValues(String name, String key3) {
        this(name, key3, null);
    }

    public ResourceFieldValues(String name, String key3, String ipAddress) {
        this.name = name;
        this.key3 = key3;
        this.ipAddress = ipAddress;
    }

    /** Camera display name. */
    public String getName() { return name; }

    /** RTSP URL (key-3 field of the resource record). */
    public String getKey3() { return key3; }

    /**
     * Direct IP address of the camera device as stored in the resource record.
     * Returns null when the proxy did not populate this field; callers should
     * fall back to extracting the IP from the RTSP URL in that case.
     */
    public String getIpAddress() { return ipAddress; }
}

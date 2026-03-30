package com.tcs.proxy.database.api.tables;

import java.io.Serializable;

/**
 * Carries the field values of a resource (camera) record from the proxy database.
 *
 * getName()  → camera display name
 * getKey3()  → RTSP URL stored in the resource key-3 field
 */
public class ResourceFieldValues implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final String key3;

    public ResourceFieldValues(String name, String key3) {
        this.name = name;
        this.key3 = key3;
    }

    /** Camera display name. */
    public String getName() { return name; }

    /** RTSP URL (key-3 field of the resource record). */
    public String getKey3() { return key3; }
}

package org.telegram.messenger.futoon;

import java.util.HashMap;
import java.util.Map;

/** JVM-only store boundary; no Android storage or user data is accessed. */
public final class FutoonStore {
    private String address;
    public int addressReads;
    public String afterNextAddressRead;
    public final Map<String, String> sessions = new HashMap<>();
    public FutoonStore(String address) { this.address = address; }
    public String address() {
        addressReads++;
        String value = address;
        if (afterNextAddressRead != null) {
            address = afterNextAddressRead;
            afterNextAddressRead = null;
        }
        return value;
    }
    public void address(String value) { address = value; }
    public FutoonApi.SessionPersistence session(String snapshot) {
        return new FutoonApi.SessionPersistence() {
            public String load() { return sessions.getOrDefault(snapshot, ""); }
            public void save(String value) { sessions.put(snapshot, value); }
            public void clear() { sessions.remove(snapshot); }
        };
    }
}

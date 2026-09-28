package com.game.server.store.memory;

import com.game.server.domain.Device;
import com.game.server.store.DeviceStore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDeviceStore implements DeviceStore {
    private final ConcurrentHashMap<String, Device> values = new ConcurrentHashMap<>();
    public Optional<Device> find(String id) { return Optional.ofNullable(values.get(id)); }
    public void save(Device device) { values.put(device.id(), device); }
    public int size() { return values.size(); }
}

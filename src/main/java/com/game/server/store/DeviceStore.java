package com.game.server.store;

import com.game.server.domain.Device;
import java.util.Optional;

public interface DeviceStore {
    Optional<Device> find(String id);
    void save(Device device);
    int size();
}

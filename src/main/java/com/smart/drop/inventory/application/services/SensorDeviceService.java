
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.SensorDevice;
import com.smart.drop.inventory.domain.model.repositories.SensorDeviceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;

    public SensorDeviceService(
            SensorDeviceRepository sensorDeviceRepository) {

        this.sensorDeviceRepository = sensorDeviceRepository;
    }

    // Create a new sensor device.
    public SensorDevice createDevice(
            Integer userId,
            String name,
            String location,
            String flow,
            String daily,
            Integer battery,
            String status) {

        SensorDevice device = SensorDevice.create(
                userId,
                name,
                location,
                flow,
                daily,
                battery,
                status
        );

        return sensorDeviceRepository.save(device);
    }

    // Legacy internal lookup.
    // Do not expose directly through HTTP.
    @Transactional(readOnly = true)
    public Optional<SensorDevice> getById(Integer deviceId) {
        return sensorDeviceRepository.findById(deviceId);
    }

    // SECURE: Read only devices owned by the authenticated user.
    @Transactional(readOnly = true)
    public Optional<SensorDevice> getByIdForUser(
            Integer deviceId,
            Integer authenticatedUserId) {

        validateAuthenticatedUser(authenticatedUserId);

        return sensorDeviceRepository.findById(deviceId)
                .filter(device ->
                        authenticatedUserId.equals(
                                device.userId()
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<SensorDevice> getByUserId(Integer userId) {
        return sensorDeviceRepository.findByUserId(userId);
    }

    // Legacy internal listing.
    // Do not expose directly through HTTP.
    @Transactional(readOnly = true)
    public List<SensorDevice> getAll() {
        return sensorDeviceRepository.findAll();
    }


@Transactional
public Optional<SensorDevice> updateDeviceForUser(
        Integer deviceId,
        Integer authenticatedUserId,
        String name,
        String location,
        String flow,
        String daily,
        Integer battery,
        String status) {

    validateAuthenticatedUser(authenticatedUserId);

    if (deviceId == null || deviceId <= 0) {
        return Optional.empty();
    }

    return sensorDeviceRepository.findById(deviceId)
            .filter(existing ->
                    authenticatedUserId.equals(
                            existing.userId()
                    )
            )
            .map(existing -> {

                SensorDevice updated = new SensorDevice(
                        existing.deviceId(),
                        existing.userId(),
                        name,
                        location,
                        flow,
                        daily,
                        battery,
                        status
                );

                return sensorDeviceRepository.save(updated);
            });
}

    // Legacy update. Route remains blocked.
    public SensorDevice updateDevice(
            Integer deviceId,
            String name,
            String location,
            String flow,
            String daily,
            Integer battery,
            String status) {

        SensorDevice current = sensorDeviceRepository
                .findById(deviceId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "SensorDevice not found: " + deviceId
                        )
                );

        SensorDevice updated = new SensorDevice(
                current.deviceId(),
                current.userId(),
                name,
                location,
                flow,
                daily,
                battery,
                status
        );

        return sensorDeviceRepository.save(updated);
    }

public boolean deleteDeviceForUser(
        Integer deviceId,
        Integer authenticatedUserId) {

    validateAuthenticatedUser(authenticatedUserId);

    if (deviceId == null || deviceId <= 0) {
        return false;
    }

    int deletedRows = sensorDeviceRepository.deleteOwnedDevice(
            deviceId,
            authenticatedUserId
    );

    return deletedRows == 1;
}


    // Legacy deletion. Route remains blocked.
    public void deleteById(Integer deviceId) {
        sensorDeviceRepository.deleteById(deviceId);
    }

    private void validateAuthenticatedUser(
            Integer authenticatedUserId) {

        if (authenticatedUserId == null
                || authenticatedUserId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid authenticated user"
            );
        }
    }
}

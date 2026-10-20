
package com.smart.drop.inventory.infrastructure.persistence.jpa.adapters;

import com.smart.drop.inventory.domain.model.entities.SensorDevice;
import com.smart.drop.inventory.domain.model.repositories.SensorDeviceRepository;
import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.SensorDeviceEntity;
import com.smart.drop.inventory.infrastructure.persistence.jpa.repositories.SensorDeviceJpaRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SensorDeviceRepositoryAdapter
        implements SensorDeviceRepository {

    private final SensorDeviceJpaRepository sensorDeviceJpaRepository;

    public SensorDeviceRepositoryAdapter(
            SensorDeviceJpaRepository sensorDeviceJpaRepository) {

        this.sensorDeviceJpaRepository = sensorDeviceJpaRepository;
    }

    @Override
    public SensorDevice save(SensorDevice sensorDevice) {

        SensorDeviceEntity entity = toEntity(sensorDevice);

        SensorDeviceEntity saved =
                sensorDeviceJpaRepository.save(entity);

        return toDomain(saved);
    }

    @Override
    public Optional<SensorDevice> findById(Integer deviceId) {

        return sensorDeviceJpaRepository.findById(deviceId)
                .map(this::toDomain);
    }

    @Override
    public List<SensorDevice> findByUserId(Integer userId) {

        return sensorDeviceJpaRepository.findByUserId(userId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<SensorDevice> findAll() {

        return sensorDeviceJpaRepository.findAll()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Integer deviceId) {
        sensorDeviceJpaRepository.deleteById(deviceId);
    }

    @Override
    public int deleteOwnedDevice(
            Integer deviceId,
            Integer userId) {

        return sensorDeviceJpaRepository.deleteOwnedDevice(
                deviceId,
                userId
        );
    }

    private SensorDeviceEntity toEntity(SensorDevice sensorDevice) {

        SensorDeviceEntity entity = new SensorDeviceEntity();

        entity.setDeviceId(sensorDevice.deviceId());
        entity.setUserId(sensorDevice.userId());
        entity.setName(sensorDevice.name());
        entity.setLocation(sensorDevice.location());
        entity.setFlow(sensorDevice.flow());
        entity.setDaily(sensorDevice.daily());
        entity.setBattery(sensorDevice.battery());
        entity.setStatus(sensorDevice.status());

        return entity;
    }

    private SensorDevice toDomain(SensorDeviceEntity entity) {

        return new SensorDevice(
                entity.getDeviceId(),
                entity.getUserId(),
                entity.getName(),
                entity.getLocation(),
                entity.getFlow(),
                entity.getDaily(),
                entity.getBattery(),
                entity.getStatus()
        );
    }
}

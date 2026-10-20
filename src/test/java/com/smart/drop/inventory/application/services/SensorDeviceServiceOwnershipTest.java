
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.SensorDevice;
import com.smart.drop.inventory.domain.model.repositories.SensorDeviceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensorDeviceServiceOwnershipTest {

    private SensorDeviceRepository repository;
    private SensorDeviceService service;

    @BeforeEach
    void setUp() {
        repository = mock(SensorDeviceRepository.class);
        service = new SensorDeviceService(repository);
    }

    @Test
    void ownerCanReadOwnSensor() {

        SensorDevice device = new SensorDevice(
                10,
                42,
                "Sensor Principal",
                "Cocina",
                "15",
                "120",
                85,
                "ACTIVE"
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(device));

        Optional<SensorDevice> result =
                service.getByIdForUser(10, 42);

        assertTrue(result.isPresent());
        assertEquals(10, result.get().deviceId());
        assertEquals(42, result.get().userId());

        verify(repository).findById(10);
    }

    @Test
    void userCannotReadAnotherUsersSensor() {

        SensorDevice device = new SensorDevice(
                10,
                99,
                "Sensor Privado",
                "Sala",
                "10",
                "100",
                70,
                "ACTIVE"
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(device));

        Optional<SensorDevice> result =
                service.getByIdForUser(10, 42);

        assertTrue(result.isEmpty());

        verify(repository).findById(10);
    }

    @Test
    void nonexistentSensorMustNotBeReturned() {

        when(repository.findById(999))
                .thenReturn(Optional.empty());

        Optional<SensorDevice> result =
                service.getByIdForUser(999, 42);

        assertTrue(result.isEmpty());

        verify(repository).findById(999);
    }

    @Test
    void invalidAuthenticatedUserMustBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getByIdForUser(10, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getByIdForUser(10, -1)
        );

        verifyNoInteractions(repository);
    }
}

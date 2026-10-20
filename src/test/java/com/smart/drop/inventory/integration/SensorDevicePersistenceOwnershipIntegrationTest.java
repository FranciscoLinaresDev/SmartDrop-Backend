
package com.smart.drop.inventory.integration;

import com.smart.drop.inventory.application.services.SensorDeviceService;
import com.smart.drop.inventory.domain.model.entities.SensorDevice;
import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.SensorDeviceEntity;
import com.smart.drop.inventory.infrastructure.persistence.jpa.repositories.SensorDeviceJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartdrop_sensor_ownership_it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartdrop.demo.seed.enabled=false",
        "smartdrop.two-factor.enabled=false"
})
@ActiveProfiles("dev")
class SensorDevicePersistenceOwnershipIntegrationTest {

    private static final String TEST_JWT_SECRET =
            generateTestSecret();

    @DynamicPropertySource
    static void registerProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "authorization.jwt.secret",
                () -> TEST_JWT_SECRET
        );
    }

    @Autowired
    private SensorDeviceService sensorDeviceService;

    @Autowired
    private SensorDeviceJpaRepository sensorDeviceJpaRepository;

    @BeforeEach
    void cleanDatabase() {
        sensorDeviceJpaRepository.deleteAll();
        sensorDeviceJpaRepository.flush();
    }

    @Test
    void createdSensorMustPersistWithCorrectOwner() {

        SensorDevice created = sensorDeviceService.createDevice(
                42,
                "Sensor Principal",
                "Cocina",
                "15",
                "120",
                85,
                "ACTIVE"
        );

        assertNotNull(created.deviceId());
        assertEquals(42, created.userId());

        SensorDeviceEntity persisted =
                sensorDeviceJpaRepository
                        .findById(created.deviceId())
                        .orElseThrow();

        assertEquals(42, persisted.getUserId());
        assertEquals("Sensor Principal", persisted.getName());
        assertEquals("Cocina", persisted.getLocation());
        assertEquals("15", persisted.getFlow());
        assertEquals("120", persisted.getDaily());
        assertEquals(85, persisted.getBattery());
        assertEquals("ACTIVE", persisted.getStatus());
    }

    @Test
    void usersMustOnlyReadTheirOwnSensors() {

        SensorDeviceEntity first = createSensor(
                42, "Sensor Cocina", "Cocina", 85
        );

        SensorDeviceEntity second = createSensor(
                42, "Sensor Sala", "Sala", 90
        );

        SensorDeviceEntity foreign = createSensor(
                99, "Sensor Privado", "Dormitorio", 70
        );

        List<SensorDevice> ownSensors =
                sensorDeviceService.getByUserId(42);

        assertEquals(2, ownSensors.size());

        assertTrue(
                ownSensors.stream()
                        .allMatch(device ->
                                device.userId().equals(42)
                        )
        );

        assertTrue(
                sensorDeviceService.getByIdForUser(
                        first.getDeviceId(), 42
                ).isPresent()
        );

        assertTrue(
                sensorDeviceService.getByIdForUser(
                        second.getDeviceId(), 42
                ).isPresent()
        );

        assertTrue(
                sensorDeviceService.getByIdForUser(
                        foreign.getDeviceId(), 42
                ).isEmpty()
        );

        assertEquals(
                3,
                sensorDeviceJpaRepository.count()
        );
    }

    @Test
    void userCannotUpdateForeignSensorInDatabase() {

        SensorDeviceEntity foreign = createSensor(
                99, "Sensor Privado", "Dormitorio", 70
        );

        Integer deviceId = foreign.getDeviceId();

        Optional<SensorDevice> result =
                sensorDeviceService.updateDeviceForUser(
                        deviceId,
                        42,
                        "Modificacion Indebida",
                        "Sala",
                        "20",
                        "150",
                        95,
                        "INACTIVE"
                );

        assertTrue(result.isEmpty());

        SensorDeviceEntity persisted =
                sensorDeviceJpaRepository
                        .findById(deviceId)
                        .orElseThrow();

        assertEquals(99, persisted.getUserId());
        assertEquals("Sensor Privado", persisted.getName());
        assertEquals("Dormitorio", persisted.getLocation());
        assertEquals(70, persisted.getBattery());
        assertEquals("ACTIVE", persisted.getStatus());
    }

    @Test
    void ownerCanUpdateAndPersistSensorChanges() {

        SensorDeviceEntity own = createSensor(
                42, "Sensor Original", "Cocina", 80
        );

        Integer deviceId = own.getDeviceId();

        Optional<SensorDevice> result =
                sensorDeviceService.updateDeviceForUser(
                        deviceId,
                        42,
                        "Sensor Actualizado",
                        "Sala",
                        "25",
                        "180",
                        95,
                        "ACTIVE"
                );

        assertTrue(result.isPresent());

        SensorDeviceEntity persisted =
                sensorDeviceJpaRepository
                        .findById(deviceId)
                        .orElseThrow();

        assertEquals(42, persisted.getUserId());
        assertEquals("Sensor Actualizado", persisted.getName());
        assertEquals("Sala", persisted.getLocation());
        assertEquals("25", persisted.getFlow());
        assertEquals("180", persisted.getDaily());
        assertEquals(95, persisted.getBattery());
    }

    @Test
    void deletionMustOnlyRemoveOwnedSensor() {

        SensorDeviceEntity foreign = createSensor(
                99, "Sensor Ajeno", "Dormitorio", 70
        );

        SensorDeviceEntity own = createSensor(
                42, "Sensor Propio", "Cocina", 85
        );

        Integer foreignId = foreign.getDeviceId();
        Integer ownId = own.getDeviceId();

        boolean foreignDeleted =
                sensorDeviceService.deleteDeviceForUser(
                        foreignId, 42
                );

        assertFalse(foreignDeleted);

        assertTrue(
                sensorDeviceJpaRepository.existsById(foreignId)
        );

        boolean ownDeleted =
                sensorDeviceService.deleteDeviceForUser(
                        ownId, 42
                );

        assertTrue(ownDeleted);

        assertFalse(
                sensorDeviceJpaRepository.existsById(ownId)
        );

        assertTrue(
                sensorDeviceJpaRepository.existsById(foreignId)
        );

        assertEquals(
                1,
                sensorDeviceJpaRepository.count()
        );
    }

    @Test
    void invalidSensorDataMustNotPersist() {

        assertThrows(
                IllegalArgumentException.class,
                () -> sensorDeviceService.createDevice(
                        42,
                        "",
                        "Cocina",
                        "15",
                        "120",
                        85,
                        "ACTIVE"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> sensorDeviceService.createDevice(
                        42,
                        "Sensor Invalido",
                        "Cocina",
                        "15",
                        "120",
                        101,
                        "ACTIVE"
                )
        );

        assertEquals(
                0,
                sensorDeviceJpaRepository.count()
        );
    }

    @Test
    void invalidAuthenticatedUserMustNotModifySensor() {

        SensorDeviceEntity own = createSensor(
                42, "Sensor Propio", "Cocina", 85
        );

        Integer deviceId = own.getDeviceId();

        assertThrows(
                IllegalArgumentException.class,
                () -> sensorDeviceService.getByIdForUser(
                        deviceId, null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> sensorDeviceService.updateDeviceForUser(
                        deviceId,
                        -1,
                        "Cambio Indebido",
                        "Sala",
                        "20",
                        "150",
                        90,
                        "ACTIVE"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> sensorDeviceService.deleteDeviceForUser(
                        deviceId, null
                )
        );

        SensorDeviceEntity persisted =
                sensorDeviceJpaRepository
                        .findById(deviceId)
                        .orElseThrow();

        assertEquals(42, persisted.getUserId());
        assertEquals("Sensor Propio", persisted.getName());
        assertEquals(85, persisted.getBattery());
    }

    private SensorDeviceEntity createSensor(
            Integer userId,
            String name,
            String location,
            Integer battery) {

        SensorDeviceEntity entity = new SensorDeviceEntity();

        entity.setUserId(userId);
        entity.setName(name);
        entity.setLocation(location);
        entity.setFlow("15");
        entity.setDaily("120");
        entity.setBattery(battery);
        entity.setStatus("ACTIVE");

        return sensorDeviceJpaRepository.saveAndFlush(entity);
    }

    private static String generateTestSecret() {

        byte[] bytes = new byte[64];

        new SecureRandom().nextBytes(bytes);

        return Base64.getEncoder().encodeToString(bytes);
    }
}


package com.smart.drop.inventory.integration;

import com.smart.drop.inventory.application.services.TankService;
import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.TankEntity;
import com.smart.drop.inventory.infrastructure.persistence.jpa.repositories.TankJpaRepository;

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
        "spring.datasource.url=jdbc:h2:mem:smartdrop_tank_ownership_it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "smartdrop.demo.seed.enabled=false",
        "smartdrop.two-factor.enabled=false"
})
@ActiveProfiles("dev")
class TankPersistenceOwnershipIntegrationTest {

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
    private TankService tankService;

    @Autowired
    private TankJpaRepository tankJpaRepository;

    @BeforeEach
    void cleanDatabase() {
        tankJpaRepository.deleteAll();
        tankJpaRepository.flush();
    }

    @Test
    void createdTankMustPersistWithCorrectOwner() {

        Tank created = tankService.createTank(
                42,
                "Cisterna Principal",
                1000,
                500
        );

        assertNotNull(created.tankId());
        assertEquals(42, created.userId());

        TankEntity persisted = tankJpaRepository
                .findById(created.tankId())
                .orElseThrow();

        assertEquals(42, persisted.getUserId());
        assertEquals("Cisterna Principal", persisted.getName());
        assertEquals(1000, persisted.getCapacity());
        assertEquals(500, persisted.getCurrent());
    }

    @Test
    void usersMustOnlyReadTheirOwnTanks() {

        TankEntity first = createTank(
                42, "Tanque 42-A", 1000, 500
        );

        TankEntity second = createTank(
                42, "Tanque 42-B", 800, 300
        );

        TankEntity foreign = createTank(
                99, "Tanque Privado", 2000, 700
        );

        List<Tank> ownTanks =
                tankService.getByUserId(42);

        assertEquals(2, ownTanks.size());

        assertTrue(ownTanks.stream()
                .allMatch(tank -> tank.userId().equals(42)));

        assertTrue(tankService.getByIdForUser(
                first.getTankId(), 42
        ).isPresent());

        assertTrue(tankService.getByIdForUser(
                second.getTankId(), 42
        ).isPresent());

        assertTrue(tankService.getByIdForUser(
                foreign.getTankId(), 42
        ).isEmpty());
    }

    @Test
    void userCannotUpdateForeignTankInDatabase() {

        TankEntity foreign = createTank(
                99, "Tanque Privado", 1000, 400
        );

        Integer tankId = foreign.getTankId();

        Optional<Tank> result =
                tankService.updateTankForUser(
                        tankId,
                        42,
                        "Modificacion Indebida",
                        2000,
                        1500
                );

        assertTrue(result.isEmpty());

        TankEntity persisted = tankJpaRepository
                .findById(tankId)
                .orElseThrow();

        assertEquals(99, persisted.getUserId());
        assertEquals("Tanque Privado", persisted.getName());
        assertEquals(1000, persisted.getCapacity());
        assertEquals(400, persisted.getCurrent());
    }

    @Test
    void ownerCanUpdateAndPersistTankChanges() {

        TankEntity own = createTank(
                42, "Tanque Original", 1000, 400
        );

        Integer tankId = own.getTankId();

        Optional<Tank> result =
                tankService.updateTankForUser(
                        tankId,
                        42,
                        "Tanque Actualizado",
                        1500,
                        700
                );

        assertTrue(result.isPresent());

        TankEntity persisted = tankJpaRepository
                .findById(tankId)
                .orElseThrow();

        assertEquals(42, persisted.getUserId());
        assertEquals("Tanque Actualizado", persisted.getName());
        assertEquals(1500, persisted.getCapacity());
        assertEquals(700, persisted.getCurrent());
    }

    @Test
    void replenishmentMustRespectOwnershipAndPersist() {

        TankEntity foreign = createTank(
                99, "Tanque Ajeno", 1000, 200
        );

        TankEntity own = createTank(
                42, "Tanque Propio", 1500, 300
        );

        Integer foreignId = foreign.getTankId();
        Integer ownId = own.getTankId();

        Optional<Tank> denied =
                tankService.replenishForUser(
                        foreignId, 42
                );

        assertTrue(denied.isEmpty());

        TankEntity foreignAfter = tankJpaRepository
                .findById(foreignId)
                .orElseThrow();

        assertEquals(200, foreignAfter.getCurrent());

        Optional<Tank> allowed =
                tankService.replenishForUser(
                        ownId, 42
                );

        assertTrue(allowed.isPresent());

        TankEntity ownAfter = tankJpaRepository
                .findById(ownId)
                .orElseThrow();

        assertEquals(1500, ownAfter.getCapacity());
        assertEquals(1500, ownAfter.getCurrent());
    }

    @Test
    void deletionMustOnlyRemoveOwnedTank() {

        TankEntity foreign = createTank(
                99, "Tanque Ajeno", 1000, 500
        );

        TankEntity own = createTank(
                42, "Tanque Propio", 1000, 400
        );

        Integer foreignId = foreign.getTankId();
        Integer ownId = own.getTankId();

        boolean foreignDeleted =
                tankService.deleteTankForUser(
                        foreignId, 42
                );

        assertFalse(foreignDeleted);
        assertTrue(tankJpaRepository.existsById(foreignId));

        boolean ownDeleted =
                tankService.deleteTankForUser(
                        ownId, 42
                );

        assertTrue(ownDeleted);

        assertFalse(tankJpaRepository.existsById(ownId));
        assertTrue(tankJpaRepository.existsById(foreignId));
    }

    private TankEntity createTank(
            Integer userId,
            String name,
            Integer capacity,
            Integer current) {

        TankEntity entity = new TankEntity();

        entity.setUserId(userId);
        entity.setName(name);
        entity.setCapacity(capacity);
        entity.setCurrent(current);

        return tankJpaRepository.saveAndFlush(entity);
    }

    private static String generateTestSecret() {

        byte[] bytes = new byte[64];

        new SecureRandom().nextBytes(bytes);

        return Base64.getEncoder().encodeToString(bytes);
    }
}

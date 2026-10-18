
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.domain.model.repositories.TankRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TankServiceUpdateOwnershipTest {

    private TankRepository repository;
    private TankService service;

    @BeforeEach
    void setUp() {
        repository = mock(TankRepository.class);
        service = new TankService(repository);
    }

    @Test
    void ownerCanUpdateOwnTank() {

        Tank existing = new Tank(
                10, 42, "Tanque Original", 1000, 500
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(existing));

        when(repository.save(any(Tank.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        Optional<Tank> result =
                service.updateTankForUser(
                        10, 42, "Tanque Actualizado", 1500, 700
                );

        assertTrue(result.isPresent());
        assertEquals(42, result.get().userId());
        assertEquals("Tanque Actualizado", result.get().name());
        assertEquals(1500, result.get().capacity());
        assertEquals(700, result.get().current());

        verify(repository).save(any(Tank.class));
    }

    @Test
    void userCannotUpdateAnotherUsersTank() {

        Tank existing = new Tank(
                10, 99, "Tanque Privado", 1000, 500
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(existing));

        Optional<Tank> result =
                service.updateTankForUser(
                        10, 42, "Acceso Indebido", 2000, 1000
                );

        assertTrue(result.isEmpty());

        verify(repository, never()).save(any(Tank.class));
    }

    @Test
    void nonexistentTankCannotBeUpdated() {

        when(repository.findById(999))
                .thenReturn(Optional.empty());

        Optional<Tank> result =
                service.updateTankForUser(
                        999, 42, "Tanque", 1000, 500
                );

        assertTrue(result.isEmpty());

        verify(repository, never()).save(any(Tank.class));
    }

    @Test
    void invalidUserCannotUpdateTank() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateTankForUser(
                        10, null, "Tanque", 1000, 500
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void invalidTankDataCannotBeSaved() {

        Tank existing = new Tank(
                10, 42, "Tanque", 1000, 500
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(existing));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateTankForUser(
                        10, 42, "Tanque", -100, 500
                )
        );

        verify(repository, never()).save(any(Tank.class));
    }
}

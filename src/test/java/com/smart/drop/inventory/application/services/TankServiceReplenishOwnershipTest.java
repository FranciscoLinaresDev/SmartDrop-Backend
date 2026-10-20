
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.domain.model.repositories.TankRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TankServiceReplenishOwnershipTest {

    private TankRepository repository;
    private TankService service;

    @BeforeEach
    void setUp() {
        repository = mock(TankRepository.class);
        service = new TankService(repository);
    }

    @Test
    void ownerCanReplenishOwnTank() {

        Tank existing = new Tank(
                10, 42, "Mi Cisterna", 1000, 250
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(existing));

        when(repository.save(any(Tank.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        Optional<Tank> result =
                service.replenishForUser(10, 42);

        assertTrue(result.isPresent());
        assertEquals(1000, result.get().current());
        assertEquals(1000, result.get().capacity());
        assertEquals(42, result.get().userId());

        verify(repository).save(any(Tank.class));
    }

    @Test
    void userCannotReplenishAnotherUsersTank() {

        Tank existing = new Tank(
                10, 99, "Cisterna Privada", 1000, 250
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(existing));

        Optional<Tank> result =
                service.replenishForUser(10, 42);

        assertTrue(result.isEmpty());

        verify(repository, never())
                .save(any(Tank.class));
    }

    @Test
    void nonexistentTankCannotBeReplenished() {

        when(repository.findById(999))
                .thenReturn(Optional.empty());

        Optional<Tank> result =
                service.replenishForUser(999, 42);

        assertTrue(result.isEmpty());

        verify(repository, never())
                .save(any(Tank.class));
    }

    @Test
    void invalidUserCannotReplenishTank() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replenishForUser(10, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.replenishForUser(10, -1)
        );

        verifyNoInteractions(repository);
    }
}

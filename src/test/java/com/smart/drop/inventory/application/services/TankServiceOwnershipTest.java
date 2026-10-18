
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.domain.model.repositories.TankRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TankServiceOwnershipTest {

    private TankRepository repository;
    private TankService service;

    @BeforeEach
    void setUp() {
        repository = mock(TankRepository.class);
        service = new TankService(repository);
    }

    @Test
    void ownerCanAccessOwnTank() {

        Tank tank = new Tank(
                10,
                42,
                "Cisterna Principal",
                1000,
                820
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(tank));

        Optional<Tank> result =
                service.getByIdForUser(10, 42);

        assertTrue(result.isPresent());
        assertEquals(10, result.get().tankId());
        assertEquals(42, result.get().userId());
    }

    @Test
    void userCannotAccessAnotherUsersTank() {

        Tank tank = new Tank(
                10,
                42,
                "Cisterna Privada",
                1000,
                820
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(tank));

        // User 99 does not own tank 10.
        Optional<Tank> result =
                service.getByIdForUser(10, 99);

        assertTrue(result.isEmpty());

        verify(repository).findById(10);
    }

    @Test
    void nonexistentTankMustNotBeReturned() {

        when(repository.findById(999))
                .thenReturn(Optional.empty());

        Optional<Tank> result =
                service.getByIdForUser(999, 42);

        assertTrue(result.isEmpty());
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

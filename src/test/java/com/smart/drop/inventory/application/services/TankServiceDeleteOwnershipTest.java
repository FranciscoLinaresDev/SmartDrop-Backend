
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.domain.model.repositories.TankRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TankServiceDeleteOwnershipTest {

    private TankRepository repository;
    private TankService service;

    @BeforeEach
    void setUp() {
        repository = mock(TankRepository.class);
        service = new TankService(repository);
    }

    @Test
    void ownerCanDeleteOwnTank() {

        Tank tank = new Tank(
                10, 42, "Mi Cisterna", 1000, 500
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(tank));

        boolean deleted = service.deleteTankForUser(10, 42);

        assertTrue(deleted);
        verify(repository).deleteById(10);
    }

    @Test
    void userCannotDeleteAnotherUsersTank() {

        Tank tank = new Tank(
                10, 99, "Cisterna Privada", 1000, 500
        );

        when(repository.findById(10))
                .thenReturn(Optional.of(tank));

        boolean deleted = service.deleteTankForUser(10, 42);

        assertFalse(deleted);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void nonexistentTankCannotBeDeleted() {

        when(repository.findById(999))
                .thenReturn(Optional.empty());

        boolean deleted = service.deleteTankForUser(999, 42);

        assertFalse(deleted);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void invalidUserCannotDeleteTank() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteTankForUser(10, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteTankForUser(10, -1)
        );

        verifyNoInteractions(repository);
    }
}

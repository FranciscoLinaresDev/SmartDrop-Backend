
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.repositories.TankRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        when(repository.deleteOwnedTank(10, 42))
                .thenReturn(1);

        boolean deleted =
                service.deleteTankForUser(10, 42);

        assertTrue(deleted);

        verify(repository).deleteOwnedTank(10, 42);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void userCannotDeleteAnotherUsersTank() {

        when(repository.deleteOwnedTank(10, 42))
                .thenReturn(0);

        boolean deleted =
                service.deleteTankForUser(10, 42);

        assertFalse(deleted);

        verify(repository).deleteOwnedTank(10, 42);
        verify(repository, never()).findById(any());
    }

    @Test
    void nonexistentTankCannotBeDeleted() {

        when(repository.deleteOwnedTank(999, 42))
                .thenReturn(0);

        boolean deleted =
                service.deleteTankForUser(999, 42);

        assertFalse(deleted);

        verify(repository).deleteOwnedTank(999, 42);
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

    @Test
    void invalidTankIdMustNotExecuteDeletion() {

        assertFalse(service.deleteTankForUser(null, 42));
        assertFalse(service.deleteTankForUser(-1, 42));

        verifyNoInteractions(repository);
    }
}

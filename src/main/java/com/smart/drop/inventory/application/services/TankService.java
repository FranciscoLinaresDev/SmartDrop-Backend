
package com.smart.drop.inventory.application.services;

import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.domain.model.repositories.TankRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TankService {

    private final TankRepository tankRepository;

    public TankService(TankRepository tankRepository) {
        this.tankRepository = tankRepository;
    }

    // Create a tank.
    // The controller supplies the authenticated user ID.
    public Tank createTank(
            Integer userId,
            String name,
            Integer capacity,
            Integer current) {

        Tank tank = Tank.create(
                userId,
                name,
                capacity,
                current
        );

        return tankRepository.save(tank);
    }

    // Legacy internal lookup.
    // Do not expose directly through an HTTP endpoint.
    @Transactional(readOnly = true)
    public Optional<Tank> getById(Integer tankId) {
        return tankRepository.findById(tankId);
    }

    // Read only a tank owned by the authenticated user.
    @Transactional(readOnly = true)
    public Optional<Tank> getByIdForUser(
            Integer tankId,
            Integer authenticatedUserId) {

        validateAuthenticatedUser(authenticatedUserId);

        return tankRepository.findById(tankId)
                .filter(tank ->
                        authenticatedUserId.equals(
                                tank.userId()
                        )
                );
    }

    // Used by the controller with the authenticated user ID.
    @Transactional(readOnly = true)
    public List<Tank> getByUserId(Integer userId) {
        return tankRepository.findByUserId(userId);
    }

    // Legacy internal listing.
    // Do not expose directly through an HTTP endpoint.
    @Transactional(readOnly = true)
    public List<Tank> getAll() {
        return tankRepository.findAll();
    }

    // Update only a tank owned by the authenticated user.
    public Optional<Tank> updateTankForUser(
            Integer tankId,
            Integer authenticatedUserId,
            String name,
            Integer capacity,
            Integer current) {

        validateAuthenticatedUser(authenticatedUserId);

        return tankRepository.findById(tankId)
                .filter(existing ->
                        authenticatedUserId.equals(
                                existing.userId()
                        )
                )
                .map(existing -> {

                    Tank updated = new Tank(
                            existing.tankId(),
                            existing.userId(),
                            name,
                            capacity,
                            current
                    );

                    return tankRepository.save(updated);
                });
    }

    // Replenish only a tank owned by the authenticated user.
    public Optional<Tank> replenishForUser(
            Integer tankId,
            Integer authenticatedUserId) {

        validateAuthenticatedUser(authenticatedUserId);

        return tankRepository.findById(tankId)
                .filter(existing ->
                        authenticatedUserId.equals(
                                existing.userId()
                        )
                )
                .map(existing -> {

                    Tank replenished = new Tank(
                            existing.tankId(),
                            existing.userId(),
                            existing.name(),
                            existing.capacity(),
                            existing.capacity()
                    );

                    return tankRepository.save(replenished);
                });
    }

    // Delete only a tank owned by the authenticated user.

public boolean deleteTankForUser(
        Integer tankId,
        Integer authenticatedUserId) {

    validateAuthenticatedUser(authenticatedUserId);

    if (tankId == null || tankId <= 0) {
        return false;
    }

    int deletedRows = tankRepository.deleteOwnedTank(
            tankId,
            authenticatedUserId
    );

    return deletedRows == 1;
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

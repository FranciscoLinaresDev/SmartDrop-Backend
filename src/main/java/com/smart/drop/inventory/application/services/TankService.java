
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

    // Create a new tank.
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

    // Find tank by ID.
    @Transactional(readOnly = true)
    public Optional<Tank> getById(Integer tankId) {
        return tankRepository.findById(tankId);
    }

    // SECURE: Only return tanks owned by the authenticated user.
    @Transactional(readOnly = true)
    public Optional<Tank> getByIdForUser(
            Integer tankId,
            Integer authenticatedUserId) {

        if (authenticatedUserId == null
                || authenticatedUserId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid authenticated user"
            );
        }

        return tankRepository.findById(tankId)
                .filter(tank ->
                        authenticatedUserId.equals(
                                tank.userId()
                        )
                );
    }

    // Find tanks by user ID.
    @Transactional(readOnly = true)
    public List<Tank> getByUserId(Integer userId) {
        return tankRepository.findByUserId(userId);
    }

    // Find all tanks.
    @Transactional(readOnly = true)
    public List<Tank> getAll() {
        return tankRepository.findAll();
    }


public Optional<Tank> updateTankForUser(
        Integer tankId,
        Integer authenticatedUserId,
        String name,
        Integer capacity,
        Integer current) {

    if (authenticatedUserId == null
            || authenticatedUserId <= 0) {
        throw new IllegalArgumentException(
                "Invalid authenticated user"
        );
    }

    return tankRepository.findById(tankId)
            .filter(existing ->
                    authenticatedUserId.equals(existing.userId())
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

    // Update an existing tank.
    public Tank updateTank(
            Integer tankId,
            String name,
            Integer capacity,
            Integer current) {

        Tank existing = tankRepository.findById(tankId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tank not found: " + tankId
                        )
                );

        Tank updated = new Tank(
                existing.tankId(),
                existing.userId(),
                name,
                capacity,
                current
        );

        return tankRepository.save(updated);
    }

    // Replenish a tank to its maximum capacity.
    public Tank replenish(Integer tankId) {

        Tank existing = tankRepository.findById(tankId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Tank not found: " + tankId
                        )
                );

        Tank replenished = new Tank(
                existing.tankId(),
                existing.userId(),
                existing.name(),
                existing.capacity(),
                existing.capacity()
        );

        return tankRepository.save(replenished);
    }

    // Delete a tank by ID.
    public void deleteById(Integer tankId) {
        tankRepository.deleteById(tankId);
    }
}

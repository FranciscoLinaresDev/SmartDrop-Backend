package com.smart.drop.inventory.domain.model.repositories;

import com.smart.drop.inventory.domain.model.entities.Tank;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for water tanks.
 */
public interface TankRepository {

int deleteOwnedTank(Integer tankId, Integer userId);

    Tank save(Tank tank);

    Optional<Tank> findById(Integer tankId);

    List<Tank> findByUserId(Integer userId);

    List<Tank> findAll();

    void deleteById(Integer tankId);
}

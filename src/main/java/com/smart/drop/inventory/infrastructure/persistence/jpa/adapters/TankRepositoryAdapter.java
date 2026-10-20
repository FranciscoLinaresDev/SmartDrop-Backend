package com.smart.drop.inventory.infrastructure.persistence.jpa.adapters;

import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.domain.model.repositories.TankRepository;
import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.TankEntity;
import com.smart.drop.inventory.infrastructure.persistence.jpa.repositories.TankJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TankRepositoryAdapter implements TankRepository {

    private final TankJpaRepository tankJpaRepository;

    public TankRepositoryAdapter(TankJpaRepository tankJpaRepository) {
        this.tankJpaRepository = tankJpaRepository;
    }

    @Override
    public Tank save(Tank tank) {
        TankEntity entity = toEntity(tank);
        TankEntity saved = tankJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Tank> findById(Integer tankId) {
        return tankJpaRepository.findById(tankId).map(this::toDomain);
    }

    @Override
    public List<Tank> findByUserId(Integer userId) {
        return tankJpaRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Tank> findAll() {
        return tankJpaRepository.findAll().stream().map(this::toDomain).toList();
    }


@Override
public int deleteOwnedTank(
        Integer tankId,
        Integer userId) {

    return tankJpaRepository.deleteOwnedTank(
            tankId,
            userId
    );
}


    @Override
    public void deleteById(Integer tankId) {
        tankJpaRepository.deleteById(tankId);
    }

    private TankEntity toEntity(Tank tank) {
        TankEntity entity = new TankEntity();
        entity.setTankId(tank.tankId());
        entity.setUserId(tank.userId());
        entity.setName(tank.name());
        entity.setCapacity(tank.capacity());
        entity.setCurrent(tank.current());
        return entity;
    }

    private Tank toDomain(TankEntity entity) {
        return new Tank(
                entity.getTankId(),
                entity.getUserId(),
                entity.getName(),
                entity.getCapacity(),
                entity.getCurrent()
        );
    }
}

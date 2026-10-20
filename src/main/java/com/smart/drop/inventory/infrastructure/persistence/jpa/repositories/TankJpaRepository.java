
package com.smart.drop.inventory.infrastructure.persistence.jpa.repositories;

import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.TankEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TankJpaRepository
        extends JpaRepository<TankEntity, Integer> {

    List<TankEntity> findByUserId(Integer userId);

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            DELETE FROM TankEntity t
            WHERE t.tankId = :tankId
              AND t.userId = :userId
            """)
    int deleteOwnedTank(
            @Param("tankId") Integer tankId,
            @Param("userId") Integer userId
    );
}

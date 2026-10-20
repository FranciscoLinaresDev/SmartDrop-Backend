
package com.smart.drop.inventory.infrastructure.persistence.jpa.repositories;

import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.SensorDeviceEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SensorDeviceJpaRepository
        extends JpaRepository<SensorDeviceEntity, Integer> {

    List<SensorDeviceEntity> findByUserId(Integer userId);

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            DELETE FROM SensorDeviceEntity s
            WHERE s.deviceId = :deviceId
              AND s.userId = :userId
            """)
    int deleteOwnedDevice(
            @Param("deviceId") Integer deviceId,
            @Param("userId") Integer userId
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE SensorDeviceEntity s
            SET s.name = :name,
                s.location = :location,
                s.flow = :flow,
                s.daily = :daily,
                s.battery = :battery,
                s.status = :status
            WHERE s.deviceId = :deviceId
              AND s.userId = :userId
            """)
    int updateOwnedDevice(
            @Param("deviceId") Integer deviceId,
            @Param("userId") Integer userId,
            @Param("name") String name,
            @Param("location") String location,
            @Param("flow") String flow,
            @Param("daily") String daily,
            @Param("battery") Integer battery,
            @Param("status") String status
    );
}

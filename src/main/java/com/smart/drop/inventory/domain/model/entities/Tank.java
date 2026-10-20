
package com.smart.drop.inventory.domain.model.entities;

/**
 * Immutable domain entity for water storage tanks.
 */
public record Tank(
        Integer tankId,
        Integer userId,
        String name,
        Integer capacity,
        Integer current
) {

    public Tank {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Tank name cannot be null or blank"
            );
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "UserId must be greater than 0"
            );
        }

        if (capacity == null || capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be greater than 0"
            );
        }

        if (current == null
                || current < 0
                || current > capacity) {

            throw new IllegalArgumentException(
                    "Current level must be between 0 and capacity"
            );
        }
    }

    public static Tank create(
            Integer userId,
            String name,
            Integer capacity,
            Integer current) {

        return new Tank(
                null,
                userId,
                name,
                capacity,
                current
        );
    }
}

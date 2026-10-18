
package com.smart.drop.inventory.interfaces.rest;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.smart.drop.inventory.application.services.TankService;
import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.inventory.interfaces.rest.dto.CreateTankRequest;
import com.smart.drop.inventory.interfaces.rest.dto.TankResponse;
import com.smart.drop.inventory.interfaces.rest.dto.UpdateTankRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(
        name = "3. Inventory & IoT - TankController",
        description = "Gestion y monitoreo de tanques"
)
@RestController
@RequestMapping("/api/v1/inventory/tanks")
public class TankController {

    private final TankService tankService;

    public TankController(TankService tankService) {
        this.tankService = tankService;
    }


@PostMapping
public ResponseEntity<TankResponse> create(
        @RequestBody CreateTankRequest request,
        Authentication authentication) {

    Integer authenticatedUserId =
            getAuthenticatedUserId(authentication);

    Tank created = tankService.createTank(
            authenticatedUserId,
            request.name(),
            request.capacity(),
            request.current()
    );

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(toResponse(created));
}


    @GetMapping("/{tankId}")
    public ResponseEntity<TankResponse> getById(
            @PathVariable Integer tankId,
            Authentication authentication) {

        Integer authenticatedUserId =
                getAuthenticatedUserId(authentication);

        return tankService.getByIdForUser(
                        tankId,
                        authenticatedUserId
                )
                .map(tank ->
                        ResponseEntity.ok(toResponse(tank))
                )
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TankResponse>> getByUserId(
            @PathVariable Integer userId) {

        List<TankResponse> data = tankService
                .getByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(data);
    }

    @GetMapping
    public ResponseEntity<List<TankResponse>> getAll() {

        List<TankResponse> data = tankService
                .getAll()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(data);
    }

    @PutMapping("/{tankId}")
    public ResponseEntity<TankResponse> update(
            @PathVariable Integer tankId,
            @RequestBody UpdateTankRequest request) {

        Tank updated = tankService.updateTank(
                tankId,
                request.name(),
                request.capacity(),
                request.current()
        );

        return ResponseEntity.ok(toResponse(updated));
    }

    @PutMapping("/{tankId}/replenish")
    public ResponseEntity<TankResponse> replenish(
            @PathVariable Integer tankId) {

        Tank updated = tankService.replenish(tankId);

        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{tankId}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer tankId) {

        tankService.deleteById(tankId);

        return ResponseEntity.noContent().build();
    }

    private Integer getAuthenticatedUserId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        try {
            Integer userId = Integer.valueOf(
                    authentication.getName()
            );

            if (userId <= 0) {
                throw new NumberFormatException(
                        "Invalid user ID"
                );
            }

            return userId;

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication identity"
            );
        }
    }

    private TankResponse toResponse(Tank tank) {

        return new TankResponse(
                tank.tankId(),
                tank.userId(),
                tank.name(),
                tank.capacity(),
                tank.current()
        );
    }
}

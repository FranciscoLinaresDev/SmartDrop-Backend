
package com.smart.drop.inventory.interfaces.rest;

import com.smart.drop.inventory.application.services.SensorDeviceService;
import com.smart.drop.inventory.domain.model.entities.SensorDevice;
import com.smart.drop.inventory.interfaces.rest.dto.CreateSensorDeviceRequest;
import com.smart.drop.inventory.interfaces.rest.dto.SensorDeviceResponse;
import com.smart.drop.inventory.interfaces.rest.dto.UpdateSensorDeviceRequest;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(
        name = "3. Inventory & IoT - SensorDeviceController",
        description = "Telemetria, emparejamiento y estado de dispositivos IoT"
)
@RestController
@RequestMapping("/api/v1/inventory/devices")
public class SensorDeviceController {

    private final SensorDeviceService sensorDeviceService;

    public SensorDeviceController(
            SensorDeviceService sensorDeviceService) {

        this.sensorDeviceService = sensorDeviceService;
    }

    // Still blocked by SecurityConfig.
    @PostMapping
    public ResponseEntity<SensorDeviceResponse> create(
            @RequestBody CreateSensorDeviceRequest request) {

        SensorDevice created = sensorDeviceService.createDevice(
                request.userId(),
                request.name(),
                request.location(),
                request.flow(),
                request.daily(),
                request.battery(),
                request.status()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(created));
    }

    // SECURE: Read only sensors owned by the authenticated user.
    @GetMapping("/{deviceId}")
    public ResponseEntity<SensorDeviceResponse> getById(
            @PathVariable Integer deviceId,
            Authentication authentication) {

        Integer authenticatedUserId =
                getAuthenticatedUserId(authentication);

        return sensorDeviceService
                .getByIdForUser(
                        deviceId,
                        authenticatedUserId
                )
                .map(device ->
                        ResponseEntity.ok(toResponse(device))
                )
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // Still blocked by SecurityConfig.
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SensorDeviceResponse>> getByUserId(
            @PathVariable Integer userId) {

        List<SensorDeviceResponse> data =
                sensorDeviceService.getByUserId(userId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(data);
    }

    // Still blocked by SecurityConfig.
    @GetMapping
    public ResponseEntity<List<SensorDeviceResponse>> getAll() {

        List<SensorDeviceResponse> data =
                sensorDeviceService.getAll()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(data);
    }

    // Still blocked by SecurityConfig.
    @PutMapping("/{deviceId}")
    public ResponseEntity<SensorDeviceResponse> update(
            @PathVariable Integer deviceId,
            @RequestBody UpdateSensorDeviceRequest request) {

        SensorDevice updated = sensorDeviceService.updateDevice(
                deviceId,
                request.name(),
                request.location(),
                request.flow(),
                request.daily(),
                request.battery(),
                request.status()
        );

        return ResponseEntity.ok(toResponse(updated));
    }

    // Still blocked by SecurityConfig.
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer deviceId) {

        sensorDeviceService.deleteById(deviceId);

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

    private SensorDeviceResponse toResponse(
            SensorDevice device) {

        return new SensorDeviceResponse(
                device.deviceId(),
                device.userId(),
                device.name(),
                device.location(),
                device.flow(),
                device.daily(),
                device.battery(),
                device.status()
        );
    }
}

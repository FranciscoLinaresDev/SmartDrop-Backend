package com.smart.drop.iam.interfaces.rest;

import com.smart.drop.shared.infrastructure.security.JwtService;
import com.smart.drop.iam.interfaces.rest.dto.LoginResponseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smart.drop.iam.application.services.AuthService;
import com.smart.drop.iam.interfaces.rest.dto.LoginUserRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.RegisterUserRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.UserResponseDto;
import com.smart.drop.iam.interfaces.rest.dto.AssignRoleRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.ForgotPasswordRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.ResetPasswordRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.Enable2FARequestDto;
import com.smart.drop.iam.interfaces.rest.dto.Verify2FARequestDto;
import com.smart.drop.iam.interfaces.rest.dto.ChangePasswordRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.ChangePasswordRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.Disable2FARequestDto;
import com.smart.drop.iam.interfaces.rest.dto.ChangeNameRequestDto;
import com.smart.drop.iam.domain.exceptions.TwoFactorRequiredException;
import com.smart.drop.iam.domain.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Controlador REST para gestión de autenticación y usuarios.
 * Mapea requests HTTP a casos de uso del dominio.
 */
@Tag(name = "1. IAM — AuthController", description = "Autenticación JWT, registro de usuarios, control de sesiones y 2FA")
@RestController
@RequestMapping({"/api/v1/iam/auth", "/api/iam/auth", "/api/identity/auth"})
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            JwtService jwtService) {

        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * Registra un nuevo usuario.
     * POST /api/identity/auth/register
     *
     * @param request datos del usuario a registrar
     * @return ResponseEntity con el usuario registrado
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> registerUser(@RequestBody RegisterUserRequestDto request) {
        // En una aplicación real, aquí se cifraría la contraseña (ej: BCrypt)
        User user = authService.registerUser(request.fullName(), request.email(), request.password());
        UserResponseDto response = toUserResponseDto(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Inicia sesión de un usuario.
     * POST /api/identity/auth/login
     *
     * @param request credenciales del usuario
     * @return ResponseEntity con el usuario autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @RequestBody LoginUserRequestDto request) {

        try {
            User user = authService.loginUser(
                    request.email(),
                    request.password()
            );

            String accessToken = jwtService.createAccessToken(
                    user.getId(),
                    user.getEmail()
            );

            LoginResponseDto response = new LoginResponseDto(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getCreatedAt(),
                    accessToken,
                    "Bearer"
            );

            return ResponseEntity.ok(response);

        } catch (TwoFactorRequiredException e) {

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "requires2FA", true,
                            "message", "Additional verification required."
                    ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("success", true, "message", "Password successfully updated."));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<?> enable2FA(@RequestBody Enable2FARequestDto request) {
        authService.enable2FA(request.email());
        return ResponseEntity.ok(Map.of("success", true, "message", "2FA has been enabled successfully via Email."));
    }

    @GetMapping("/2fa/status/{email}")
    public ResponseEntity<?> get2FAStatus(@PathVariable String email) {
        return authService.getUserByEmail(email)
                .map(user -> ResponseEntity.ok(Map.of("enabled", Boolean.TRUE.equals(user.getIsTwoFactorEnabled()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<UserResponseDto> verify2FA(@RequestBody Verify2FARequestDto request) {
        User user = authService.verify2FA(request.email(), request.code());
        UserResponseDto response = toUserResponseDto(user);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequestDto request) {
        authService.changePassword(request.email(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
    }

    @PutMapping("/change-name")
    public ResponseEntity<?> changeName(@RequestBody ChangeNameRequestDto request) {
        authService.changeName(request.email(), request.newName());
        return ResponseEntity.ok(Map.of("success", true, "message", "Name changed successfully."));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disable2FA(@RequestBody Disable2FARequestDto request) {
        authService.disable2FA(request.email());
        return ResponseEntity.ok(Map.of("success", true, "message", "2FA has been disabled."));
    }

    /**
     * Obtiene un usuario por su ID.
     * GET /api/identity/auth/users/{userId}
     *
     * @param userId el ID del usuario
     * @return ResponseEntity con el usuario, o 404 si no existe
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable Integer userId) {
        return authService.getUser(userId)
                .map(user -> ResponseEntity.ok(toUserResponseDto(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtiene todos los usuarios.
     * GET /api/identity/auth/users
     *
     * @return ResponseEntity con la lista de usuarios
     */
    @GetMapping("/users")
    public ResponseEntity<java.util.List<UserResponseDto>> getAllUsers() {
        java.util.List<UserResponseDto> users = authService.getAllUsers().stream()
                .map(this::toUserResponseDto)
                .toList();
        return ResponseEntity.ok(users);
    }

    /**
     * Obtiene un usuario por su email.
     * GET /api/identity/auth/users/by-email/{email}
     *
     * @param email el email del usuario
     * @return ResponseEntity con el usuario, o 404 si no existe
     */
    @GetMapping("/users/by-email/{email}")
    public ResponseEntity<UserResponseDto> getUserByEmail(@PathVariable String email) {
        return authService.getUserByEmail(email)
                .map(user -> ResponseEntity.ok(toUserResponseDto(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Asigna un rol a un usuario.
     * POST /api/identity/auth/assign-role
     *
     * @param request datos de userId y roleName
     * @return ResponseEntity con el usuario actualizado
     */
    @PostMapping("/assign-role")
    public ResponseEntity<UserResponseDto> assignRoleToUser(@RequestBody AssignRoleRequestDto request) {
        User user = authService.assignRoleToUser(request.userId(), request.roleName());
        UserResponseDto response = toUserResponseDto(user);
        return ResponseEntity.ok(response);
    }

    /**
     * Revoca un rol de un usuario.
     * POST /api/identity/auth/revoke-role
     *
     * @param request datos de userId y roleName
     * @return ResponseEntity con el usuario actualizado
     */
    @PostMapping("/revoke-role")
    public ResponseEntity<UserResponseDto> revokeRoleFromUser(@RequestBody AssignRoleRequestDto request) {
        User user = authService.revokeRoleFromUser(request.userId(), request.roleName());
        UserResponseDto response = toUserResponseDto(user);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifica si un usuario tiene un rol específico.
     * GET /api/identity/auth/users/{userId}/has-role/{roleName}
     *
     * @param userId el ID del usuario
     * @param roleName el nombre del rol
     * @return ResponseEntity con true/false
     */
    @GetMapping("/users/{userId}/has-role/{roleName}")
    public ResponseEntity<Boolean> userHasRole(@PathVariable Integer userId, @PathVariable String roleName) {
        boolean hasRole = authService.userHasRole(userId, roleName);
        return ResponseEntity.ok(hasRole);
    }

    /**
     * Obtiene el número total de usuarios registrados.
     * GET /api/identity/auth/total-users
     *
     * @return ResponseEntity con el total
     */
    @GetMapping("/total-users")
    public ResponseEntity<Long> getTotalUsers() {
        long totalUsers = authService.getTotalUsers();
        return ResponseEntity.ok(totalUsers);
    }

    /**
     * Convierte una entidad de dominio User a DTO de respuesta.
     *
     * @param user la entidad de dominio
     * @return el DTO de respuesta
     */
    private UserResponseDto toUserResponseDto(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}


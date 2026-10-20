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
import com.smart.drop.iam.interfaces.rest.dto.VerifyTwoFactorChallengeRequestDto;
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

import com.smart.drop.iam.application.services.TwoFactorChallengeService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private TwoFactorChallengeService twoFactorChallengeService;

    public AuthController(
            AuthService authService,
            JwtService jwtService) {

        this.authService = authService;
        this.jwtService = jwtService;
    }

    @Autowired(required = false)
    public void setTwoFactorChallengeService(TwoFactorChallengeService service) {
        this.twoFactorChallengeService = service;
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
    public ResponseEntity<?> loginUser(@RequestBody LoginUserRequestDto request) {
        try {
            User user = authService.loginUser(request.email(), request.password());
            String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail());
            return ResponseEntity.ok(new LoginResponseDto(
                    user.getId(), user.getFullName(), user.getEmail(),
                    user.getCreatedAt(), accessToken, "Bearer"
            ));
        } catch (TwoFactorRequiredException e) {
            // An incomplete/misconfigured second factor must never grant a JWT.
            if (twoFactorChallengeService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Two-factor verification is not configured."));
            }

            User user = authService.getUserByEmail(request.email())
                    .orElseThrow(() -> new IllegalStateException("Authenticated account is missing"));
            if (!Boolean.TRUE.equals(user.getIsTwoFactorEnabled())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Account verification state changed. Retry login."));
            }

            try {
                String challengeId = twoFactorChallengeService
                        .createAfterPasswordVerified(user.getId(), user.getEmail());
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of(
                                "requires2FA", true,
                                "challengeId", challengeId,
                                "message", "Check your registered email for a verification code."
                        ));
            } catch (IllegalStateException rateLimited) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("message", "A verification code was recently requested."));
            }
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("success", true, "message", "Password successfully updated."));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<?> enable2FA(@RequestBody Enable2FARequestDto request) {
        // Never enable 2FA based solely on a caller-supplied email.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "2FA account management is not yet available."));
    }

    @GetMapping("/2fa/status/{email}")
    public ResponseEntity<?> get2FAStatus(@PathVariable String email) {
        return authService.getUserByEmail(email)
                .map(user -> ResponseEntity.ok(Map.of("enabled", Boolean.TRUE.equals(user.getIsTwoFactorEnabled()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verify2FA(@RequestBody VerifyTwoFactorChallengeRequestDto request) {
        if (twoFactorChallengeService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Two-factor verification is not configured."));
        }
        if (request == null || request.challengeId() == null ||
                request.challengeId().isBlank() || request.code() == null ||
                !request.code().matches("[0-9]{6}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid verification request."));
        }

        var verified = twoFactorChallengeService.verify(request.challengeId(), request.code());
        if (!verified.verified()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired verification code."));
        }

        User user = authService.getUser(verified.userId()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsTwoFactorEnabled())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Account is not eligible for two-factor login."));
        }

        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new LoginResponseDto(
                user.getId(), user.getFullName(), user.getEmail(),
                user.getCreatedAt(), accessToken, "Bearer"
        ));
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
        // Never disable 2FA based solely on a caller-supplied email.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "2FA account management is not yet available."));
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


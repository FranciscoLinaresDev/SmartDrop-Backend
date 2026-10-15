package com.smart.drop.iam.application.services;

import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.domain.model.Role;
import com.smart.drop.iam.domain.model.repositories.UserRepository;
import com.smart.drop.iam.domain.model.repositories.RoleRepository;
import com.smart.drop.iam.domain.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Random;

/**
 * Servicio de aplicación para autenticación utilizando inyección de dependencias.
 * Orquesta la lógica de negocio entre el dominio y la persistencia.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }


    /**
     * Registra un nuevo usuario sin roles.
     *
     * @param fullName nombre completo del usuario
     * @param email email del usuario
     * @param passwordHash hash de la contraseña
     * @return el usuario creado y persistido
     */
     public User registerUser(String fullName, String email, String password) {
         if (userRepository.findByEmail(email).isPresent()) {
             throw UserAlreadyExistsException.withEmail(email);
         }

         if (password == null || password.isBlank()) {
             throw new IllegalArgumentException("Password cannot be empty");
         }

         String passwordHash = passwordEncoder.encode(password);

         User user = User.create(fullName, email, passwordHash);

         return userRepository.save(user);
     }

    /**
     * Valida las credenciales e inicia sesión para un usuario.
     *
     * @param email el email del usuario
     * @return el usuario validado
     */
    @Transactional(noRollbackFor = TwoFactorRequiredException.class)
    public User loginUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));

        if (password == null ||
                !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (Boolean.TRUE.equals(user.getIsTwoFactorEnabled())) {
            String code = String.format("%06d", new Random().nextInt(999999));
            user.setTwoFactorCode(code);
            userRepository.update(user);
            System.out.println("\n=======================================================");
            System.out.println("2FA CODE GENERATED FOR " + email + ": " + code);
            System.out.println("=======================================================\n");
            throw new TwoFactorRequiredException(email, code);
        }

        return user;
    }

    public String generatePasswordResetToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));
        
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        userRepository.update(user);
        
        System.out.println("\n=======================================================");
        System.out.println("PASSWORD RESET TOKEN FOR " + email + ": " + token);
        System.out.println("=======================================================\n");
        
        return token;
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getResetToken()))
                .findFirst()
                .orElseThrow(InvalidTokenException::new);

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userRepository.update(user);
    }

    public void enable2FA(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));
        user.setIsTwoFactorEnabled(true);
        userRepository.update(user);
    }

    public User verify2FA(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));
                
        if (code == null || !code.equals(user.getTwoFactorCode())) {
            throw new IllegalArgumentException("Invalid 2FA code");
        }
        
        user.setTwoFactorCode(null);
        return userRepository.update(user);
    }

    public void changePassword(
            String email,
            String currentPassword,
            String newPassword) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));

        if (currentPassword == null ||
                !passwordEncoder.matches(
                        currentPassword,
                        user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Current password is incorrect");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.update(user);
    }

    public void disable2FA(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));
        user.setIsTwoFactorEnabled(false);
        userRepository.update(user);
    }

    public void changeName(String email, String newName) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> UserNotFoundException.withEmail(email));
        user.setFullName(newName);
        userRepository.update(user);
    }

    /**
     * Obtiene un usuario por ID.
     *
     * @param userId el ID del usuario
     * @return Optional con el usuario si existe
     */
    public Optional<User> getUser(Integer userId) {
        return userRepository.findById(userId);
    }

    /**
     * Obtiene todos los usuarios registrados.
     *
     * @return una lista con todos los usuarios
     */
    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Obtiene un usuario por email.
     *
     * @param email el email del usuario
     * @return Optional con el usuario si existe
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Asigna un rol a un usuario.
     *
     * @param userId el ID del usuario
     * @param roleName el nombre del rol
     * @return el usuario actualizado con el rol asignado
     */
    public User assignRoleToUser(Integer userId, String roleName) {
        // Obtiene el usuario
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // Obtiene el rol
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> RoleNotFoundException.withName(roleName));

        // Valida que el usuario no tenga ya ese rol
        if (user.hasRole(roleName)) {
            throw UserAlreadyHasRoleException.forUserAndRole(userId, roleName);
        }

        // Añade el rol
        user.addRole(role);

        // Persiste
        return userRepository.update(user);
    }

    /**
     * Revoca un rol de un usuario.
     *
     * @param userId el ID del usuario
     * @param roleName el nombre del rol
     * @return el usuario actualizado sin el rol
     */
    public User revokeRoleFromUser(Integer userId, String roleName) {
        // Obtiene el usuario
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // Obtiene el rol
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> RoleNotFoundException.withName(roleName));

        // Valida que el usuario tenga ese rol
        if (!user.hasRole(roleName)) {
            throw new IllegalArgumentException("User does not have role: " + roleName);
        }

        // Revoca el rol
        user.removeRole(role);

        // Persiste
        return userRepository.update(user);
    }

    /**
     * Verifica si un usuario tiene un rol específico.
     *
     * @param userId el ID del usuario
     * @param roleName el nombre del rol
     * @return true si el usuario tiene el rol, false en caso contrario
     */
    public boolean userHasRole(Integer userId, String roleName) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> u.hasRole(roleName)).orElse(false);
    }

    /**
     * Obtiene el total de usuarios registrados.
     *
     * @return el número total de usuarios
     */
    public long getTotalUsers() {
        return userRepository.count();
    }
}


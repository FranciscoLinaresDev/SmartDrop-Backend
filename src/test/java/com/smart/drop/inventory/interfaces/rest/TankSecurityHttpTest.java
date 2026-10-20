package com.smart.drop.inventory.interfaces.rest;

import com.smart.drop.iam.domain.model.Role;
import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.domain.model.repositories.UserRepository;
import com.smart.drop.inventory.application.services.TankService;
import com.smart.drop.inventory.domain.model.entities.Tank;
import com.smart.drop.shared.infrastructure.security.JwtService;
import com.smart.drop.shared.infrastructure.security.SecurityConfig;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TankController.class)
@Import(SecurityConfig.class)
class TankSecurityHttpTest {

    private static final String BASE =
            "/api/v1/inventory/tanks";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TankService tankService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    // Prevent JPA auditing from requiring a real
    // JPA metamodel in this MVC slice test.
    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;


@Test
void userCannotCreateTankForAnotherAccount()
        throws Exception {

    mockAuthentication(
            "owner-create-token",
            42,
            "ROLE_USER"
    );

    Tank created = new Tank(
            25,
            42,
            "Mi Cisterna",
            1000,
            500
    );

    when(tankService.createTank(
            42,
            "Mi Cisterna",
            1000,
            500
    )).thenReturn(created);

    mockMvc.perform(
            post(BASE)
                    .header(
                            "Authorization",
                            "Bearer owner-create-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "userId": 999,
                          "name": "Mi Cisterna",
                          "capacity": 1000,
                          "current": 500
                        }
                        """)
    )
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.userId").value(42));

    verify(tankService).createTank(
            42,
            "Mi Cisterna",
            1000,
            500
    );

    verify(tankService, never()).createTank(
            eq(999),
            anyString(),
            anyInt(),
            anyInt()
    );
}


    @Test
    void ownerCanReadOwnTank() throws Exception {

        mockAuthentication(
                "owner-token",
                42,
                "ROLE_USER"
        );

        Tank tank = new Tank(
                10,
                42,
                "Cisterna Principal",
                1000,
                820
        );

        when(tankService.getByIdForUser(10, 42))
                .thenReturn(Optional.of(tank));

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer owner-token"
                        )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tankId").value(10))
        .andExpect(jsonPath("$.userId").value(42))
        .andExpect(
                jsonPath("$.name")
                        .value("Cisterna Principal")
        );

        verify(tankService).getByIdForUser(10, 42);
    }

    @Test
    void userCannotReadAnotherUsersTank()
            throws Exception {

        mockAuthentication(
                "other-user-token",
                99,
                "ROLE_USER"
        );

        when(tankService.getByIdForUser(10, 99))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer other-user-token"
                        )
        )
        .andExpect(status().isNotFound());

        verify(tankService).getByIdForUser(10, 99);

        verify(tankService, never())
                .getByIdForUser(10, 42);
    }

    @Test
    void unauthenticatedUserCannotReadTank()
            throws Exception {

        mockMvc.perform(
                get(BASE + "/10")
        )
        .andExpect(status().isUnauthorized());

        verifyNoInteractions(tankService);
    }

    @Test
    void invalidJwtCannotReadTank()
            throws Exception {

        when(jwtService.verify("invalid-token"))
                .thenThrow(
                        new JwtException("Invalid JWT")
                );

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        )
        )
        .andExpect(status().isUnauthorized());

        verifyNoInteractions(tankService);
    }

    @Test
    void userWithoutPermittedRoleCannotReadTank()
            throws Exception {

        mockAuthentication(
                "roleless-token",
                50,
                null
        );

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer roleless-token"
                        )
        )
        .andExpect(status().isForbidden());

        verifyNoInteractions(tankService);
    }

    @Test
    void administratorDoesNotAutomaticallyBypassOwnership()
            throws Exception {

        mockAuthentication(
                "admin-token",
                1,
                "ROLE_ADMIN"
        );

        when(tankService.getByIdForUser(10, 1))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer admin-token"
                        )
        )
        .andExpect(status().isNotFound());

        verify(tankService).getByIdForUser(10, 1);
    }



@Test
void otherTankOperationsMustRemainBlocked()
        throws Exception {

    mockAuthentication(
            "authenticated-token",
            42,
            "ROLE_USER"
    );

    // Global tank listing remains blocked.
    mockMvc.perform(
            get(BASE)
                    .header(
                            "Authorization",
                            "Bearer authenticated-token"
                    )
    )
    .andExpect(status().isForbidden());

    // Arbitrary user tank listing remains blocked.
    mockMvc.perform(
            get(BASE + "/user/42")
                    .header(
                            "Authorization",
                            "Bearer authenticated-token"
                    )
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(tankService);
}

@Test
void invalidTankCreationDataMustReturnBadRequest()
        throws Exception {

    mockAuthentication(
            "validation-test-token",
            42,
            "ROLE_USER"
    );

    String[] invalidRequests = {

            // Empty tank name.
            """
            {
              "name": "",
              "capacity": 1000,
              "current": 500
            }
            """,

            // Negative capacity.
            """
            {
              "name": "Mi Cisterna",
              "capacity": -100,
              "current": 50
            }
            """,

            // Current level exceeds capacity.
            """
            {
              "name": "Mi Cisterna",
              "capacity": 1000,
              "current": 1500
            }
            """,

            // Negative current level.
            """
            {
              "name": "Mi Cisterna",
              "capacity": 1000,
              "current": -10
            }
            """,

            // Missing capacity.
            """
            {
              "name": "Mi Cisterna",
              "current": 500
            }
            """
    };

    for (String request : invalidRequests) {

        mockMvc.perform(
                post(BASE)
                        .header(
                                "Authorization",
                                "Bearer validation-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
        )
        .andExpect(status().isBadRequest());
    }

    // Invalid requests must never reach the service.
    verifyNoInteractions(tankService);
}

@Test
void ownerCanUpdateOwnTank() throws Exception {

    mockAuthentication(
            "update-owner-token",
            42,
            "ROLE_USER"
    );

    Tank updated = new Tank(
            10,
            42,
            "Tanque Actualizado",
            1500,
            700
    );

    when(tankService.updateTankForUser(
            10, 42, "Tanque Actualizado", 1500, 700
    )).thenReturn(Optional.of(updated));

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer update-owner-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Tanque Actualizado",
                          "capacity": 1500,
                          "current": 700
                        }
                        """)
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.tankId").value(10))
    .andExpect(jsonPath("$.userId").value(42))
    .andExpect(jsonPath("$.capacity").value(1500));

    verify(tankService).updateTankForUser(
            10, 42, "Tanque Actualizado", 1500, 700
    );
}

@Test
void userCannotUpdateAnotherUsersTank() throws Exception {

    mockAuthentication(
            "update-foreign-token",
            42,
            "ROLE_USER"
    );

    when(tankService.updateTankForUser(
            10, 42, "Intento Indebido", 1000, 500
    )).thenReturn(Optional.empty());

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer update-foreign-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Intento Indebido",
                          "capacity": 1000,
                          "current": 500
                        }
                        """)
    )
    .andExpect(status().isNotFound());

    verify(tankService).updateTankForUser(
            10, 42, "Intento Indebido", 1000, 500
    );
}

@Test
void invalidTankUpdateMustReturnBadRequest() throws Exception {

    mockAuthentication(
            "invalid-update-token",
            42,
            "ROLE_USER"
    );

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer invalid-update-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Tanque",
                          "capacity": -100,
                          "current": 500
                        }
                        """)
    )
    .andExpect(status().isBadRequest());

    verifyNoInteractions(tankService);
}

@Test
void unauthenticatedUserCannotUpdateTank() throws Exception {

    mockMvc.perform(
            put(BASE + "/10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Tanque",
                          "capacity": 1000,
                          "current": 500
                        }
                        """)
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(tankService);
}

@Test
void userWithoutAllowedRoleCannotUpdateTank() throws Exception {

    mockAuthentication(
            "roleless-update-token",
            42,
            null
    );

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer roleless-update-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Tanque",
                          "capacity": 1000,
                          "current": 500
                        }
                        """)
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(tankService);
}

@Test
void nonexistentTankCannotBeUpdatedThroughHttp() throws Exception {

    mockAuthentication(
            "missing-update-token",
            42,
            "ROLE_USER"
    );

    when(tankService.updateTankForUser(
            999, 42, "Tanque", 1000, 500
    )).thenReturn(Optional.empty());

    mockMvc.perform(
            put(BASE + "/999")
                    .header(
                            "Authorization",
                            "Bearer missing-update-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Tanque",
                          "capacity": 1000,
                          "current": 500
                        }
                        """)
    )
    .andExpect(status().isNotFound());
}


@Test
void ownerCanReplenishOwnTankThroughHttp()
        throws Exception {

    mockAuthentication(
            "replenish-owner-token",
            42,
            "ROLE_USER"
    );

    Tank replenished = new Tank(
            10,
            42,
            "Mi Cisterna",
            1000,
            1000
    );

    when(tankService.replenishForUser(10, 42))
            .thenReturn(Optional.of(replenished));

    mockMvc.perform(
            put(BASE + "/10/replenish")
                    .header(
                            "Authorization",
                            "Bearer replenish-owner-token"
                    )
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.tankId").value(10))
    .andExpect(jsonPath("$.userId").value(42))
    .andExpect(jsonPath("$.capacity").value(1000))
    .andExpect(jsonPath("$.current").value(1000));

    verify(tankService).replenishForUser(10, 42);
}

@Test
void userCannotReplenishAnotherUsersTankThroughHttp()
        throws Exception {

    mockAuthentication(
            "replenish-foreign-token",
            42,
            "ROLE_USER"
    );

    when(tankService.replenishForUser(10, 42))
            .thenReturn(Optional.empty());

    mockMvc.perform(
            put(BASE + "/10/replenish")
                    .header(
                            "Authorization",
                            "Bearer replenish-foreign-token"
                    )
    )
    .andExpect(status().isNotFound());

    verify(tankService).replenishForUser(10, 42);
}

@Test
void nonexistentTankCannotBeReplenishedThroughHttp()
        throws Exception {

    mockAuthentication(
            "replenish-missing-token",
            42,
            "ROLE_USER"
    );

    when(tankService.replenishForUser(999, 42))
            .thenReturn(Optional.empty());

    mockMvc.perform(
            put(BASE + "/999/replenish")
                    .header(
                            "Authorization",
                            "Bearer replenish-missing-token"
                    )
    )
    .andExpect(status().isNotFound());

    verify(tankService).replenishForUser(999, 42);
}

@Test
void unauthenticatedUserCannotReplenishTank()
        throws Exception {

    mockMvc.perform(
            put(BASE + "/10/replenish")
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(tankService);
}

@Test
void userWithoutAllowedRoleCannotReplenishTank()
        throws Exception {

    mockAuthentication(
            "replenish-roleless-token",
            42,
            null
    );

    mockMvc.perform(
            put(BASE + "/10/replenish")
                    .header(
                            "Authorization",
                            "Bearer replenish-roleless-token"
                    )
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(tankService);
}

@Test
void invalidJwtCannotReplenishTank()
        throws Exception {

    when(jwtService.verify("invalid-replenish-token"))
            .thenThrow(
                    new JwtException("Invalid JWT")
            );

    mockMvc.perform(
            put(BASE + "/10/replenish")
                    .header(
                            "Authorization",
                            "Bearer invalid-replenish-token"
                    )
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(tankService);
}


@Test
void ownerCanDeleteOwnTankThroughHttp()
        throws Exception {

    mockAuthentication(
            "delete-owner-token",
            42,
            "ROLE_USER"
    );

    when(tankService.deleteTankForUser(10, 42))
            .thenReturn(true);

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer delete-owner-token"
                    )
    )
    .andExpect(status().isNoContent());

    verify(tankService).deleteTankForUser(10, 42);
    verify(tankService, never()).deleteById(any());
}

@Test
void userCannotDeleteAnotherUsersTankThroughHttp()
        throws Exception {

    mockAuthentication(
            "delete-foreign-token",
            42,
            "ROLE_USER"
    );

    when(tankService.deleteTankForUser(10, 42))
            .thenReturn(false);

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer delete-foreign-token"
                    )
    )
    .andExpect(status().isNotFound());

    verify(tankService).deleteTankForUser(10, 42);
    verify(tankService, never()).deleteById(any());
}

@Test
void nonexistentTankCannotBeDeletedThroughHttp()
        throws Exception {

    mockAuthentication(
            "delete-missing-token",
            42,
            "ROLE_USER"
    );

    when(tankService.deleteTankForUser(999, 42))
            .thenReturn(false);

    mockMvc.perform(
            delete(BASE + "/999")
                    .header(
                            "Authorization",
                            "Bearer delete-missing-token"
                    )
    )
    .andExpect(status().isNotFound());

    verify(tankService).deleteTankForUser(999, 42);
}

@Test
void unauthenticatedUserCannotDeleteTank()
        throws Exception {

    mockMvc.perform(
            delete(BASE + "/10")
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(tankService);
}

@Test
void userWithoutAllowedRoleCannotDeleteTank()
        throws Exception {

    mockAuthentication(
            "delete-roleless-token",
            42,
            null
    );

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer delete-roleless-token"
                    )
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(tankService);
}

@Test
void invalidJwtCannotDeleteTank()
        throws Exception {

    when(jwtService.verify("invalid-delete-token"))
            .thenThrow(
                    new JwtException("Invalid JWT")
            );

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer invalid-delete-token"
                    )
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(tankService);
}




    private void mockAuthentication(
            String token,
            Integer userId,
            String roleName) {

        Set<Role> roles = new HashSet<>();

        if (roleName != null) {
            roles.add(
                    Role.createFromDatabase(
                            "ROLE_ADMIN".equals(roleName)
                                    ? 1 : 2,
                            roleName,
                            "Test role"
                    )
            );
        }

        User user = User.createFromDatabase(
                userId,
                "Test User",
                "user-" + userId + "@example.com",
                "hashed-password",
                roles,
                LocalDateTime.now(),
                null,
                false,
                null
        );

        when(jwtService.verify(token))
                .thenReturn(
                        Jwts.claims()
                                .subject(userId.toString())
                                .build()
                );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
    }
}

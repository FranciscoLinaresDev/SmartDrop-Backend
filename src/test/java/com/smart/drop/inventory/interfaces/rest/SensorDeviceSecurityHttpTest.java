
package com.smart.drop.inventory.interfaces.rest;

import com.smart.drop.iam.domain.model.Role;
import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.domain.model.repositories.UserRepository;

import com.smart.drop.inventory.application.services.SensorDeviceService;
import com.smart.drop.inventory.domain.model.entities.SensorDevice;

import com.smart.drop.shared.infrastructure.security.JwtService;
import com.smart.drop.shared.infrastructure.security.SecurityConfig;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;


import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SensorDeviceController.class)
@Import(SecurityConfig.class)
class SensorDeviceSecurityHttpTest {

    private static final String BASE =
            "/api/v1/inventory/devices";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SensorDeviceService sensorDeviceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void ownerCanReadOwnSensor() throws Exception {

        mockAuthentication(
                "sensor-owner-token",
                42,
                "ROLE_USER"
        );

        SensorDevice device = new SensorDevice(
                10,
                42,
                "Sensor Principal",
                "Cocina",
                "15",
                "120",
                85,
                "ACTIVE"
        );

        when(sensorDeviceService.getByIdForUser(10, 42))
                .thenReturn(Optional.of(device));

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer sensor-owner-token"
                        )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(10))
        .andExpect(jsonPath("$.userId").value(42))
        .andExpect(
                jsonPath("$.name").value("Sensor Principal")
        );

        verify(sensorDeviceService)
                .getByIdForUser(10, 42);
    }

    @Test
    void userCannotReadAnotherUsersSensor()
            throws Exception {

        mockAuthentication(
                "sensor-foreign-token",
                42,
                "ROLE_USER"
        );

        when(sensorDeviceService.getByIdForUser(10, 42))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer sensor-foreign-token"
                        )
        )
        .andExpect(status().isNotFound());

        verify(sensorDeviceService)
                .getByIdForUser(10, 42);
    }

    @Test
    void nonexistentSensorReturnsNotFound()
            throws Exception {

        mockAuthentication(
                "sensor-missing-token",
                42,
                "ROLE_USER"
        );

        when(sensorDeviceService.getByIdForUser(999, 42))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                get(BASE + "/999")
                        .header(
                                "Authorization",
                                "Bearer sensor-missing-token"
                        )
        )
        .andExpect(status().isNotFound());

        verify(sensorDeviceService)
                .getByIdForUser(999, 42);
    }

    @Test
    void unauthenticatedUserCannotReadSensor()
            throws Exception {

        mockMvc.perform(
                get(BASE + "/10")
        )
        .andExpect(status().isUnauthorized());

        verifyNoInteractions(sensorDeviceService);
    }

    @Test
    void invalidJwtCannotReadSensor()
            throws Exception {

        when(jwtService.verify("invalid-sensor-token"))
                .thenThrow(
                        new JwtException("Invalid JWT")
                );

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer invalid-sensor-token"
                        )
        )
        .andExpect(status().isUnauthorized());

        verifyNoInteractions(sensorDeviceService);
    }

    @Test
    void userWithoutPermittedRoleCannotReadSensor()
            throws Exception {

        mockAuthentication(
                "sensor-roleless-token",
                42,
                null
        );

        mockMvc.perform(
                get(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer sensor-roleless-token"
                        )
        )
        .andExpect(status().isForbidden());

        verifyNoInteractions(sensorDeviceService);
    }



@Test
void otherSensorOperationsRemainBlocked()
        throws Exception {

    mockAuthentication(
            "sensor-blocked-token",
            42,
            "ROLE_USER"
    );

    mockMvc.perform(
            get(BASE + "/user/999")
                    .header(
                            "Authorization",
                            "Bearer sensor-blocked-token"
                    )
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(sensorDeviceService);
}


@Test
void userCannotCreateSensorForAnotherAccount()
        throws Exception {

    mockAuthentication(
            "sensor-create-token",
            42,
            "ROLE_USER"
    );

    SensorDevice created = new SensorDevice(
            10,
            42,
            "Sensor Principal",
            "Cocina",
            "15",
            "120",
            85,
            "ACTIVE"
    );

    when(sensorDeviceService.createDevice(
            42,
            "Sensor Principal",
            "Cocina",
            "15",
            "120",
            85,
            "ACTIVE"
    )).thenReturn(created);

    mockMvc.perform(
            post(BASE)
                    .header(
                            "Authorization",
                            "Bearer sensor-create-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "userId": 999,
                          "name": "Sensor Principal",
                          "location": "Cocina",
                          "flow": "15",
                          "daily": "120",
                          "battery": 85,
                          "status": "ACTIVE"
                        }
                        """)
    )
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.deviceId").value(10))
    .andExpect(jsonPath("$.userId").value(42));

    verify(sensorDeviceService).createDevice(
            42,
            "Sensor Principal",
            "Cocina",
            "15",
            "120",
            85,
            "ACTIVE"
    );

    verify(sensorDeviceService, never()).createDevice(
            eq(999),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
    );
}

@Test
void invalidSensorCreationMustReturnBadRequest()
        throws Exception {

    mockAuthentication(
            "sensor-invalid-create-token",
            42,
            "ROLE_USER"
    );

    String[] invalidRequests = {
            """
            {
              "name": "",
              "battery": 85
            }
            """,
            """
            {
              "name": "Sensor Principal",
              "battery": -1
            }
            """,
            """
            {
              "name": "Sensor Principal",
              "battery": 101
            }
            """
    };

    for (String request : invalidRequests) {

        mockMvc.perform(
                post(BASE)
                        .header(
                                "Authorization",
                                "Bearer sensor-invalid-create-token"
                        )
                        .contentType(
                                org.springframework.http.MediaType.APPLICATION_JSON
                        )
                        .content(request)
        )
        .andExpect(status().isBadRequest());
    }

    verifyNoInteractions(sensorDeviceService);
}

@Test
void unauthenticatedUserCannotCreateSensor()
        throws Exception {

    mockMvc.perform(
            post(BASE)
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("{}")
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void userWithoutAllowedRoleCannotCreateSensor()
        throws Exception {

    mockAuthentication(
            "sensor-create-roleless-token",
            42,
            null
    );

    mockMvc.perform(
            post(BASE)
                    .header(
                            "Authorization",
                            "Bearer sensor-create-roleless-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("{}")
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void ownerCanUpdateOwnSensorThroughHttp()
        throws Exception {

    mockAuthentication(
            "sensor-update-owner-token",
            42,
            "ROLE_USER"
    );

    SensorDevice updated = new SensorDevice(
            10, 42,
            "Sensor Actualizado",
            "Sala",
            "20",
            "150",
            90,
            "ACTIVE"
    );

    when(sensorDeviceService.updateDeviceForUser(
            10, 42,
            "Sensor Actualizado",
            "Sala",
            "20",
            "150",
            90,
            "ACTIVE"
    )).thenReturn(Optional.of(updated));

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer sensor-update-owner-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "name": "Sensor Actualizado",
                          "location": "Sala",
                          "flow": "20",
                          "daily": "150",
                          "battery": 90,
                          "status": "ACTIVE"
                        }
                        """)
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.deviceId").value(10))
    .andExpect(jsonPath("$.userId").value(42))
    .andExpect(jsonPath("$.battery").value(90));

    verify(sensorDeviceService).updateDeviceForUser(
            10, 42,
            "Sensor Actualizado",
            "Sala",
            "20",
            "150",
            90,
            "ACTIVE"
    );

}

@Test
void userCannotUpdateAnotherUsersSensorThroughHttp()
        throws Exception {

    mockAuthentication(
            "sensor-update-foreign-token",
            42,
            "ROLE_USER"
    );

    when(sensorDeviceService.updateDeviceForUser(
            10, 42,
            "Intento Indebido",
            "Sala",
            "20",
            "150",
            90,
            "ACTIVE"
    )).thenReturn(Optional.empty());

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer sensor-update-foreign-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "name": "Intento Indebido",
                          "location": "Sala",
                          "flow": "20",
                          "daily": "150",
                          "battery": 90,
                          "status": "ACTIVE"
                        }
                        """)
    )
    .andExpect(status().isNotFound());

    verify(sensorDeviceService).updateDeviceForUser(
            10, 42,
            "Intento Indebido",
            "Sala",
            "20",
            "150",
            90,
            "ACTIVE"
    );
}

@Test
void nonexistentSensorCannotBeUpdatedThroughHttp()
        throws Exception {

    mockAuthentication(
            "sensor-update-missing-token",
            42,
            "ROLE_USER"
    );

    mockMvc.perform(
            put(BASE + "/999")
                    .header(
                            "Authorization",
                            "Bearer sensor-update-missing-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "name": "Sensor",
                          "location": "Sala",
                          "flow": "20",
                          "daily": "150",
                          "battery": 90,
                          "status": "ACTIVE"
                        }
                        """)
    )
    .andExpect(status().isNotFound());

    verify(sensorDeviceService).updateDeviceForUser(
            999, 42,
            "Sensor",
            "Sala",
            "20",
            "150",
            90,
            "ACTIVE"
    );
}

@Test
void invalidSensorUpdateMustReturnBadRequest()
        throws Exception {

    mockAuthentication(
            "sensor-invalid-update-token",
            42,
            "ROLE_USER"
    );

    String[] invalidRequests = {
            """
            {
              "name": "",
              "battery": 85
            }
            """,
            """
            {
              "name": "Sensor",
              "battery": -1
            }
            """,
            """
            {
              "name": "Sensor",
              "battery": 101
            }
            """
    };

    for (String request : invalidRequests) {

        mockMvc.perform(
                put(BASE + "/10")
                        .header(
                                "Authorization",
                                "Bearer sensor-invalid-update-token"
                        )
                        .contentType(
                                org.springframework.http.MediaType.APPLICATION_JSON
                        )
                        .content(request)
        )
        .andExpect(status().isBadRequest());
    }

    verifyNoInteractions(sensorDeviceService);
}

@Test
void unauthenticatedUserCannotUpdateSensor()
        throws Exception {

    mockMvc.perform(
            put(BASE + "/10")
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "name": "Sensor",
                          "battery": 85
                        }
                        """)
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void userWithoutAllowedRoleCannotUpdateSensor()
        throws Exception {

    mockAuthentication(
            "sensor-update-roleless-token",
            42,
            null
    );

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer sensor-update-roleless-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "name": "Sensor",
                          "battery": 85
                        }
                        """)
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void invalidJwtCannotUpdateSensor()
        throws Exception {

    when(jwtService.verify("invalid-sensor-update-token"))
            .thenThrow(
                    new JwtException("Invalid JWT")
            );

    mockMvc.perform(
            put(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer invalid-sensor-update-token"
                    )
                    .contentType(
                            org.springframework.http.MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "name": "Sensor",
                          "battery": 85
                        }
                        """)
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}


@Test
void ownerCanDeleteOwnSensorThroughHttp()
        throws Exception {

    mockAuthentication(
            "sensor-delete-owner-token",
            42,
            "ROLE_USER"
    );

    when(sensorDeviceService.deleteDeviceForUser(10, 42))
            .thenReturn(true);

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer sensor-delete-owner-token"
                    )
    )
    .andExpect(status().isNoContent());

    verify(sensorDeviceService).deleteDeviceForUser(10, 42);
}

@Test
void userCannotDeleteAnotherUsersSensorThroughHttp()
        throws Exception {

    mockAuthentication(
            "sensor-delete-foreign-token",
            42,
            "ROLE_USER"
    );

    when(sensorDeviceService.deleteDeviceForUser(10, 42))
            .thenReturn(false);

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer sensor-delete-foreign-token"
                    )
    )
    .andExpect(status().isNotFound());

    verify(sensorDeviceService).deleteDeviceForUser(10, 42);
}

@Test
void nonexistentSensorCannotBeDeletedThroughHttp()
        throws Exception {

    mockAuthentication(
            "sensor-delete-missing-token",
            42,
            "ROLE_USER"
    );

    when(sensorDeviceService.deleteDeviceForUser(999, 42))
            .thenReturn(false);

    mockMvc.perform(
            delete(BASE + "/999")
                    .header(
                            "Authorization",
                            "Bearer sensor-delete-missing-token"
                    )
    )
    .andExpect(status().isNotFound());

    verify(sensorDeviceService).deleteDeviceForUser(999, 42);
}

@Test
void unauthenticatedUserCannotDeleteSensor()
        throws Exception {

    mockMvc.perform(
            delete(BASE + "/10")
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void userWithoutAllowedRoleCannotDeleteSensor()
        throws Exception {

    mockAuthentication(
            "sensor-delete-roleless-token",
            42,
            null
    );

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer sensor-delete-roleless-token"
                    )
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void invalidJwtCannotDeleteSensor()
        throws Exception {

    when(jwtService.verify("invalid-sensor-delete-token"))
            .thenThrow(
                    new JwtException("Invalid JWT")
            );

    mockMvc.perform(
            delete(BASE + "/10")
                    .header(
                            "Authorization",
                            "Bearer invalid-sensor-delete-token"
                    )
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void userCanListOnlyOwnSensors()
        throws Exception {

    mockAuthentication(
            "sensor-list-owner-token",
            42,
            "ROLE_USER"
    );

    SensorDevice first = new SensorDevice(
            10, 42, "Sensor Cocina", "Cocina",
            "15", "120", 85, "ACTIVE"
    );

    SensorDevice second = new SensorDevice(
            11, 42, "Sensor Sala", "Sala",
            "20", "150", 90, "ACTIVE"
    );

    when(sensorDeviceService.getByUserId(42))
            .thenReturn(java.util.List.of(first, second));

    mockMvc.perform(
            get(BASE)
                    .header(
                            "Authorization",
                            "Bearer sensor-list-owner-token"
                    )
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.length()").value(2))
    .andExpect(jsonPath("$[0].deviceId").value(10))
    .andExpect(jsonPath("$[0].userId").value(42))
    .andExpect(jsonPath("$[1].deviceId").value(11))
    .andExpect(jsonPath("$[1].userId").value(42));

    verify(sensorDeviceService).getByUserId(42);
    verify(sensorDeviceService, never()).getByUserId(999);
}

@Test
void userWithoutSensorsGetsEmptyList()
        throws Exception {

    mockAuthentication(
            "sensor-list-empty-token",
            42,
            "ROLE_USER"
    );

    when(sensorDeviceService.getByUserId(42))
            .thenReturn(java.util.List.of());

    mockMvc.perform(
            get(BASE)
                    .header(
                            "Authorization",
                            "Bearer sensor-list-empty-token"
                    )
    )
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.length()").value(0));

    verify(sensorDeviceService).getByUserId(42);
}

@Test
void unauthenticatedUserCannotListSensors()
        throws Exception {

    mockMvc.perform(
            get(BASE)
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void userWithoutAllowedRoleCannotListSensors()
        throws Exception {

    mockAuthentication(
            "sensor-list-roleless-token",
            42,
            null
    );

    mockMvc.perform(
            get(BASE)
                    .header(
                            "Authorization",
                            "Bearer sensor-list-roleless-token"
                    )
    )
    .andExpect(status().isForbidden());

    verifyNoInteractions(sensorDeviceService);
}

@Test
void invalidJwtCannotListSensors()
        throws Exception {

    when(jwtService.verify("invalid-sensor-list-token"))
            .thenThrow(
                    new JwtException("Invalid JWT")
            );

    mockMvc.perform(
            get(BASE)
                    .header(
                            "Authorization",
                            "Bearer invalid-sensor-list-token"
                    )
    )
    .andExpect(status().isUnauthorized());

    verifyNoInteractions(sensorDeviceService);
}


    @Test
    void demoCanReadSensorsButCannotModifyThem() throws Exception {

        mockAuthentication("demo-sensor-token", 42, "ROLE_DEMO");

        when(sensorDeviceService.getByUserId(42))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get(BASE)
                        .header("Authorization", "Bearer demo-sensor-token"))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE)
                        .header("Authorization", "Bearer demo-sensor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"name":"Sensor Demo","location":"Cisterna"}
                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/10")
                        .header("Authorization", "Bearer demo-sensor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"name":"Sensor Modificado","location":"Cisterna"}
                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(BASE + "/10")
                        .header("Authorization", "Bearer demo-sensor-token"))
                .andExpect(status().isForbidden());

        verify(sensorDeviceService).getByUserId(42);
        verifyNoMoreInteractions(sensorDeviceService);
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

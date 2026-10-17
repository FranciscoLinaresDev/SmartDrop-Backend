
package com.smart.drop.config;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.RoleEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.RoleJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.SensorDeviceEntity;
import com.smart.drop.inventory.infrastructure.persistence.jpa.entities.TankEntity;
import com.smart.drop.inventory.infrastructure.persistence.jpa.repositories.SensorDeviceJpaRepository;
import com.smart.drop.inventory.infrastructure.persistence.jpa.repositories.TankJpaRepository;
import com.smart.drop.support.infrastructure.persistence.jpa.entities.AlertEntity;
import com.smart.drop.support.infrastructure.persistence.jpa.repositories.AlertJpaRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

@Component
@Profile("dev & !mysql & !prod")
@ConditionalOnProperty(
        prefix = "smartdrop.demo.seed",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DataInitializer.class);

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final TankJpaRepository tankRepository;
    private final SensorDeviceJpaRepository deviceRepository;
    private final AlertJpaRepository alertRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    private final String demoUserPassword;
    private final String demoAdminPassword;

    public DataInitializer(
            UserJpaRepository userRepository,
            RoleJpaRepository roleRepository,
            TankJpaRepository tankRepository,
            SensorDeviceJpaRepository deviceRepository,
            AlertJpaRepository alertRepository,
            PasswordEncoder passwordEncoder,
            DataSource dataSource,
            @Value("${SMARTDROP_DEMO_USER_PASSWORD}")
            String demoUserPassword,
            @Value("${SMARTDROP_DEMO_ADMIN_PASSWORD}")
            String demoAdminPassword) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tankRepository = tankRepository;
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
        this.demoUserPassword = demoUserPassword;
        this.demoAdminPassword = demoAdminPassword;
    }

    @Override
    public void run(String... args) throws Exception {

        // Never seed demo accounts into a non-H2 database.
        try (Connection connection = dataSource.getConnection()) {

            String database =
                    connection.getMetaData().getDatabaseProductName();

            if (!"H2".equalsIgnoreCase(database)) {
                throw new IllegalStateException(
                        "Demo data seeding is permitted only on H2."
                );
            }
        }

        // Do not overwrite or duplicate existing user records.
        if (userRepository.count() > 0) {
            log.info(
                    "Demo seeding skipped: users already exist."
            );
            return;
        }

        validateDemoPasswords();

        log.info("Creating local SmartDrop demo data.");

        // Roles
        RoleEntity roleUser = roleRepository.save(
                new RoleEntity(
                        "ROLE_USER",
                        "Usuario Estandar de la Plataforma"
                )
        );

        RoleEntity roleAdmin = roleRepository.save(
                new RoleEntity(
                        "ROLE_ADMIN",
                        "Administrador Global del Sistema"
                )
        );

        RoleEntity roleBrewer = roleRepository.save(
                new RoleEntity(
                        "ROLE_BREWER",
                        "Operador de Planta de Bebidas"
                )
        );

        // Demo users with freshly generated BCrypt hashes.
        UserEntity userDemo = new UserEntity(
                "Juan Perez (Residencial)",
                "demo@smartdrop.io",
                passwordEncoder.encode(demoUserPassword)
        );

        userDemo.getRoles().add(roleUser);
        userDemo = userRepository.save(userDemo);

        UserEntity userBrewer = new UserEntity(
                "Carlos Bohorquez (Cerveza Bohorquez)",
                "brewer@bohorquez.pe",
                passwordEncoder.encode(demoUserPassword)
        );

        userBrewer.getRoles().add(roleBrewer);
        userBrewer = userRepository.save(userBrewer);

        UserEntity userAdmin = new UserEntity(
                "Marco Ochante (Admin)",
                "admin@smartdrop.io",
                passwordEncoder.encode(demoAdminPassword)
        );

        userAdmin.getRoles().add(roleAdmin);
        userRepository.save(userAdmin);

        // Demo tanks
        TankEntity tank1 = new TankEntity();
        tank1.setUserId(userDemo.getUserId());
        tank1.setName("Cisterna Principal Residencial");
        tank1.setCapacity(1000);
        tank1.setCurrent(820);
        tankRepository.save(tank1);

        TankEntity tank2 = new TankEntity();
        tank2.setUserId(userBrewer.getUserId());
        tank2.setName("Fermentador Conico Ale #1");
        tank2.setCapacity(500);
        tank2.setCurrent(480);
        tankRepository.save(tank2);

        TankEntity tank3 = new TankEntity();
        tank3.setUserId(userBrewer.getUserId());
        tank3.setName("Tanque de Agua Helada (HLT)");
        tank3.setCapacity(1200);
        tank3.setCurrent(950);
        tankRepository.save(tank3);

        // Demo sensors
        SensorDeviceEntity device1 = new SensorDeviceEntity();
        device1.setUserId(userDemo.getUserId());
        device1.setName("Sensor Ultrasonico HC-SR04 Cisterna");
        device1.setLocation("Techo / Cisterna Residencial");
        device1.setFlow("0.0 L/min");
        device1.setDaily("180 L");
        device1.setBattery(94);
        device1.setStatus("ONLINE");
        deviceRepository.save(device1);

        SensorDeviceEntity device2 = new SensorDeviceEntity();
        device2.setUserId(userBrewer.getUserId());
        device2.setName("Sonda Termica Digital PT100 Fermentador");
        device2.setLocation("Planta Fermentacion Bohorquez");
        device2.setFlow("1.2 L/min");
        device2.setDaily("480 L");
        device2.setBattery(100);
        device2.setStatus("ONLINE");
        deviceRepository.save(device2);

        // Demo alerts
        AlertEntity alert1 = new AlertEntity();
        alert1.setUserId(userDemo.getUserId());
        alert1.setType("NIGHT_LEAK");
        alert1.setTitle("Alerta de Fuga Nocturna en Cisterna");
        alert1.setDescription(
                "Descenso volumetrico anomalo detectado."
        );
        alert1.setResolved(false);
        alert1.setCreatedAt(
                LocalDateTime.now().minusHours(3)
        );
        alertRepository.save(alert1);

        AlertEntity alert2 = new AlertEntity();
        alert2.setUserId(userBrewer.getUserId());
        alert2.setType("THERMAL_EXCURSION");
        alert2.setTitle("Desviacion Termica Critica");
        alert2.setDescription(
                "La temperatura supero el umbral configurado."
        );
        alert2.setResolved(false);
        alert2.setCreatedAt(
                LocalDateTime.now().minusMinutes(45)
        );
        alertRepository.save(alert2);

        log.info(
                "Demo seeding completed: 3 users, 3 tanks, "
                + "2 sensors and 2 alerts."
        );
    }

    private void validateDemoPasswords() {

        if (demoUserPassword == null
                || demoUserPassword.length() < 16
                || demoAdminPassword == null
                || demoAdminPassword.length() < 16) {

            throw new IllegalStateException(
                    "Demo passwords must contain at least "
                    + "16 characters."
            );
        }

        if (demoUserPassword.equals(demoAdminPassword)) {

            throw new IllegalStateException(
                    "Demo administrator and user passwords "
                    + "must be different."
            );
        }
    }
}

# SmartDrop — Backend

**Spring Boot REST API for water and liquid-resource inventory management, JWT authentication, and a read-only portfolio demonstration.**

[**Live frontend**](https://smartdrop-portfolio.netlify.app/) · [**Frontend repository**](https://github.com/FranciscoLinaresDev/SmartDrop-Frontend)

## Overview

SmartDrop's backend is a **single Spring Boot 4 application organized into domain-focused packages**. It exposes REST endpoints for authentication and inventory operations and persists application data in MySQL in production.

The source tree includes packages for identity, inventory, analytics, administration, finance, support, planning, profiles, and shared infrastructure. **These packages are part of one deployable application, not five independently deployed microservices.** Some controllers or business modules are present in source code but are not exposed for general use by the current production security policy.

The backend is hosted on **Railway**. The Angular frontend, deployed on **Netlify**, accesses it through a reverse proxy for `/api/*` routes.

## Currently enabled API capabilities

- **JWT-based login:** authentication through the IAM login endpoint, with BCrypt password hashing and stateless Spring Security.
- **Inventory read access:** authenticated users with applicable roles can retrieve their tank and device records.
- **Inventory management:** create, update, and delete operations for tanks and devices are restricted to `ROLE_USER`, `ROLE_BREWER`, and `ROLE_ADMIN` under the configured security rules.
- **Tank replenishment:** restricted to authorized non-demo roles.
- **Read-only demonstration:** `ROLE_DEMO` may use supported inventory GET routes but cannot create, update, delete, or replenish records.
- **Resource ownership checks:** inventory controllers/services use the authenticated user identity rather than trusting a client-supplied owner ID.
- **HTTP authorization behavior:** unauthorized requests return `401 Unauthorized`; validly authenticated users lacking permission receive `403 Forbidden`.

### Important production scope

The current `SecurityConfig` explicitly allows selected IAM and inventory routes and **denies all other routes by default**. A controller, Swagger annotation, or frontend screen in the repository should not be mistaken for a publicly available production feature.

The UI's monitoring values come from persisted records; this deployment does **not** claim a live hardware IoT integration.

## Technology stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Authentication and authorization | Spring Security, JWT, role-based access |
| Persistence | Spring Data JPA / Hibernate |
| Production database | MySQL |
| Local development database | H2 in-memory, MySQL-compatible mode |
| Build and tests | Maven Wrapper, JUnit / Spring test tooling |
| Deployment | Railway |

## Project structure

```text
src/main/java/com/smart/drop/
├── iam/             # Identity and access management
├── inventory/       # Tank and device endpoints and services
├── analytics/       # Analytics-related modules
├── administration/  # Administration modules
├── finance/         # Finance-related modules
├── support/         # Support-related modules
├── planning/        # Planning modules
├── profiles/        # Profile-related modules
└── shared/          # Security and common infrastructure
src/main/resources/
├── application.properties
├── application-dev.properties
└── application-mysql.properties
```

The root `pom.xml` defines **one Spring Boot application**. Do not treat this project as a Maven multi-module build or assume Docker Compose orchestration unless those capabilities are introduced and verified separately.

## Running locally

**Requirements:** JDK 21, the included Maven Wrapper, and a local environment variable holding a valid JWT signing secret.

The JWT signing secret must be a Base64-encoded value containing at least **32 random bytes** when decoded. Keep it private and outside Git.

**Windows PowerShell example:**

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SMARTDROP_JWT_SECRET = "<YOUR_PRIVATE_BASE64_32_BYTE_SECRET>"
.\mvnw.cmd spring-boot:run
```

The default `dev` profile uses an in-memory H2 database. Data in this development database is **not persistent across restarts**. The default port is `8080`, unless `PORT` is provided.

### MySQL profile

The `mysql` profile reads connection settings from environment variables:

```text
DATABASE_URL       MySQL hostname
DATABASE_PORT      MySQL port (typically 3306)
DATABASE_NAME      Database name
DATABASE_USER      Database username
DATABASE_PASSWORD  Database password
DATABASE_USE_SSL   MySQL SSL option
```

Use the `SPRING_PROFILES_ACTIVE=mysql` profile only with a database you control. In deployed environments, configure values through the hosting platform's secret/environment settings. Do not place passwords or production connections in the README.

> **Security note:** The current `application-mysql.properties` contains development-style fallback values. Production should explicitly set `DATABASE_USER` and `DATABASE_PASSWORD`, and those fallback values should be removed in a separate hardening change after testing.

Local authenticated requests require appropriately provisioned user accounts and roles; do not assume that a clean H2 database includes a demo user automatically.

## Selected endpoints

| Method | Route | Access |
| --- | --- | --- |
| POST | `/api/iam/auth/login` | Public login |
| GET | `/api/v1/inventory/devices` | USER, BREWER, ADMIN, DEMO |
| GET | `/api/v1/inventory/devices/{deviceId}` | USER, BREWER, ADMIN, DEMO |
| POST / PUT / DELETE | `/api/v1/inventory/devices` and `/{deviceId}` | USER, BREWER, ADMIN |
| GET | `/api/v1/inventory/tanks` | USER, BREWER, ADMIN, DEMO |
| GET | `/api/v1/inventory/tanks/{tankId}` | USER, BREWER, ADMIN, DEMO |
| POST / PUT / DELETE | `/api/v1/inventory/tanks` and `/{tankId}` | USER, BREWER, ADMIN |
| PUT | `/api/v1/inventory/tanks/{tankId}/replenish` | USER, BREWER, ADMIN |

Ownership checks apply to authenticated inventory resources. Routes not explicitly allowed by Spring Security are denied.

## Tests and verification

On Windows:

```powershell
.\mvnw.cmd clean test
```

The backend test suite includes HTTP authorization scenarios checking permitted demo GET requests and forbidden demo write requests. The `sendError`/`setStatus` HTTP response fix was verified in the production workflow: the user confirmed **GET devices = 200 OK** and **POST devices = 403 Forbidden** for the demo account after deployment.

Testing and deployment status must be rechecked after future changes; a previously successful build is not a guarantee for new commits.

## Portfolio demonstration

**Frontend:** https://smartdrop-portfolio.netlify.app/

**Read-only account:** `demo@smartdrop.io`. Its password is intentionally not included in source control. Use a dedicated demo credential when sharing access with reviewers.

This account is assigned `ROLE_DEMO`; permissions are enforced by Spring Security rather than by Angular buttons alone.

## Related repositories

- [SmartDrop Frontend](https://github.com/FranciscoLinaresDev/SmartDrop-Frontend)
- [SmartDrop Landing](https://github.com/FranciscoLinaresDev/SmartDrop-Landing)

## Development

**Backend implementation:** Francisco Javier Uribe Linares.

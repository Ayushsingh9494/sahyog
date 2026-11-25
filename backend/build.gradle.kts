import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    // Kotlin version recommended by Spring Boot 4.1 (spring-boot-dependencies kotlin.version).
    kotlin("jvm") version "2.3.21"
    // Opens @Component/@Configuration classes, which Spring needs to subclass.
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.3"
}

group = "com.sahyog"
version = "0.1.0"

java {
    toolchain {
        // LTS; the same JDK Android Studio ships, so nothing extra to install.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        // Nullability annotations in Spring are enforced, not just hints.
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    // Spring Boot's tested set of library versions.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    // REST API (Spring MVC on embedded Tomcat) with JSON via Jackson's Kotlin support.
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Request body validation (@Valid, @Size...).
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Verifies the Firebase ID token on every request (a standard JWT signed by Google).
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    // Plain SQL via JdbcClient: explicit queries suit PostGIS better than an ORM.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Versioned schema migrations (src/main/resources/db/migration), applied on start-up.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    // /actuator/health for uptime checks (Cloud Run later).
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Google Cloud (production profile only is configured to use them):
    // - Cloud SQL connector: private, IAM-authorised connection to the database, no public IP rules.
    runtimeOnly("com.google.cloud.sql:postgres-socket-factory:1.31.1")
    // - Cloud Storage for ID photos, in a private bucket.
    implementation(platform("com.google.cloud:libraries-bom:26.94.0"))
    implementation("com.google.cloud:google-cloud-storage")

    // Local development only: starts compose.yaml (PostGIS in Docker) with ./gradlew bootRun.
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    // Tests run against a real PostGIS in a throwaway Docker container.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    // A fixed name, so the Dockerfile can copy it.
    archiveFileName = "sahyog-server.jar"
}

tasks.bootRun {
    // Local runs use the dev profile (sample workers, local document folder).
    systemProperty("spring.profiles.active", "dev")
}

tasks.test {
    useJUnitPlatform()
}

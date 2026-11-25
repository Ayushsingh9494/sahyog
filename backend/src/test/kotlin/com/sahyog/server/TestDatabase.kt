package com.sahyog.server

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * A real PostGIS (same image as compose.yaml) in a throwaway container, shared by all
 * tests in a run. Spring points the DataSource at it via @ServiceConnection.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestDatabase {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
}

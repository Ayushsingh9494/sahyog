package com.sahyog.server.time

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.ZoneId

/**
 * One clock for all time rules (slot lead time, expiry, review window), in the cooperative's
 * time zone: visit slots like "11 AM – 1 PM" are local times. Tests replace it with a clock
 * they can move.
 */
@Configuration
@EnableScheduling
class ClockConfig {
    @Bean
    fun clock(@Value("\${sahyog.time-zone}") zone: String): Clock = Clock.system(ZoneId.of(zone))
}

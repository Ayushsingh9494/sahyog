package com.sahyog.server.dev

import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

/**
 * DEVELOPMENT ONLY. The fictional sample workers (uids starting "w-", loaded from db/devdata)
 * aren't people who could open the app, so they "accept" requests after a short delay, as the
 * app's demo did. Never active in production.
 */
@Component
@Profile("dev")
class SampleWorkerAutoAccept(private val jdbc: JdbcClient, private val clock: Clock) {
    private val random = SecureRandom()

    @Scheduled(fixedDelayString = "PT5S")
    @Transactional
    fun acceptWaitingRequests() {
        val due = clock.instant().minus(DELAY)
        val ids = jdbc.sql(
            "SELECT id FROM bookings WHERE status = 'Requested' AND worker_uid LIKE 'w-%' AND created_at <= :due",
        ).param("due", due.atOffset(java.time.ZoneOffset.UTC)).query { rs, _ -> rs.getString("id") }.list()
        ids.forEach { id ->
            jdbc.sql("UPDATE bookings SET status = 'Accepted', start_code = :code WHERE id = :id AND status = 'Requested'")
                .param("id", id).param("code", (1_000 + random.nextInt(9_000)).toString()).update()
        }
    }

    private companion object {
        val DELAY: Duration = Duration.ofSeconds(20)
    }
}

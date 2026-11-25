package com.sahyog.server.admin

import com.sahyog.server.user.WorkerStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** Appends to `worker_status_changes`: every verification status change, who made it and why. */
@Repository
class StatusAudit(private val jdbc: JdbcClient) {
    fun record(workerUid: String, from: WorkerStatus?, to: WorkerStatus, changedBy: String?, reason: String? = null) {
        jdbc.sql(
            """
            INSERT INTO worker_status_changes (worker_uid, from_status, to_status, changed_by, reason)
            VALUES (:worker, :from, :to, :by, :reason)
            """.trimIndent(),
        )
            .param("worker", workerUid).param("from", from?.name).param("to", to.name)
            .param("by", changedBy).param("reason", reason)
            .update()
    }
}

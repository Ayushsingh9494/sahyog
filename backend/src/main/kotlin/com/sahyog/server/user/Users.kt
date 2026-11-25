package com.sahyog.server.user

import com.sahyog.server.api.ValidationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/** Same names as the app's `UserRole` / `WorkerVerificationStatus` enums. */
enum class Role { Customer, Worker }

enum class WorkerStatus { NotSubmitted, Pending, Verified, Rejected }

data class User(
    val uid: String,
    val phoneNumber: String,
    val name: String,
    val roles: Set<Role>,
    val workerStatus: WorkerStatus?,
    val isAdmin: Boolean,
)

/** Field names in validation errors; the app highlights the matching input. */
object UserErrorFields {
    const val NAME = "name"
    const val ROLES = "roles"
    const val PHONE = "phone"
}

/** The same rule the app checks on the onboarding form (`NameValidator`). */
object NameRules {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 60
    private val allowed = Regex("^[\\p{L}\\p{M} .'-]+$")

    fun isValid(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length in MIN_LENGTH..MAX_LENGTH && allowed.matches(trimmed)
    }
}

@Repository
class UserRepository(private val jdbc: JdbcClient) {

    fun find(uid: String): User? = jdbc.sql("SELECT * FROM users WHERE uid = :uid")
        .param("uid", uid)
        .query { rs, _ -> rs.toUser() }
        .optional()
        .orElse(null)

    fun upsert(user: User) {
        jdbc.sql(
            """
            INSERT INTO users (uid, phone_number, name, is_customer, is_worker, worker_status)
            VALUES (:uid, :phone, :name, :customer, :worker, :status)
            ON CONFLICT (uid) DO UPDATE SET
                phone_number = EXCLUDED.phone_number,
                name = EXCLUDED.name,
                is_customer = EXCLUDED.is_customer,
                is_worker = EXCLUDED.is_worker,
                worker_status = EXCLUDED.worker_status,
                updated_at = now()
            """.trimIndent(),
        )
            .param("uid", user.uid)
            .param("phone", user.phoneNumber)
            .param("name", user.name)
            .param("customer", Role.Customer in user.roles)
            .param("worker", Role.Worker in user.roles)
            .param("status", user.workerStatus?.name)
            .update()
    }

    private fun ResultSet.toUser() = User(
        uid = getString("uid"),
        phoneNumber = getString("phone_number"),
        name = getString("name"),
        roles = buildSet {
            if (getBoolean("is_customer")) add(Role.Customer)
            if (getBoolean("is_worker")) add(Role.Worker)
        },
        workerStatus = getString("worker_status")?.let(WorkerStatus::valueOf),
        isAdmin = getBoolean("is_admin"),
    )
}

@Service
class UserService(private val users: UserRepository) {

    fun find(uid: String): User? = users.find(uid)

    /**
     * Onboarding, and later profile edits. Roles can be added but never dropped here: a
     * worker with jobs or a verification in progress can't make that disappear by
     * un-ticking a box (leaving is an account-level action, handled with deletion).
     */
    @Transactional
    fun saveProfile(uid: String, phoneNumber: String?, name: String, roles: Set<Role>): User {
        if (phoneNumber.isNullOrBlank()) throw ValidationException(UserErrorFields.PHONE)
        if (!NameRules.isValid(name)) throw ValidationException(UserErrorFields.NAME)
        if (roles.isEmpty()) throw ValidationException(UserErrorFields.ROLES)

        val existing = users.find(uid)
        val allRoles = roles + existing?.roles.orEmpty()
        val user = User(
            uid = uid,
            phoneNumber = phoneNumber,
            name = name.trim(),
            roles = allRoles,
            // Becoming a worker starts verification from the beginning; an existing status is kept.
            workerStatus = if (Role.Worker in allRoles) existing?.workerStatus ?: WorkerStatus.NotSubmitted else null,
            isAdmin = existing?.isAdmin ?: false,
        )
        users.upsert(user)
        return user
    }
}

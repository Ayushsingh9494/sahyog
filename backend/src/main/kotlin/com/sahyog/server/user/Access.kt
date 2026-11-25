package com.sahyog.server.user

import com.sahyog.server.api.ForbiddenException
import org.springframework.stereotype.Component

/**
 * Who may do what. Every check reads the database, so a role or status change (e.g. an admin
 * approving a worker) applies on the very next request, with no token refresh.
 */
@Component
class Access(private val users: UserRepository) {

    /** Signed in *and* onboarded. */
    fun user(uid: String): User = users.find(uid) ?: throw ForbiddenException()

    fun worker(uid: String): User = user(uid).also { if (Role.Worker !in it.roles) throw ForbiddenException() }

    /** Cooperative staff. Admin is granted by hand in the database, never through the API. */
    fun admin(uid: String): User = user(uid).also { if (!it.isAdmin) throw ForbiddenException() }
}

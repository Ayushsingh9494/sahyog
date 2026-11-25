package com.sahyog.server.user

import com.sahyog.server.api.NotFoundException
import com.sahyog.server.security.toCaller
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The signed-in person's own profile, as the app's `User` model needs it. */
data class MeResponse(
    val uid: String,
    val phoneNumber: String,
    val name: String,
    val roles: Set<Role>,
    val workerStatus: WorkerStatus?,
)

data class ProfileRequest(val name: String, val roles: Set<Role>)

@RestController
@RequestMapping("/api/v1/me")
class MeController(private val users: UserService) {

    /** 404 means "signed in but not onboarded yet": the app shows the onboarding screen. */
    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse =
        users.find(jwt.toCaller().uid)?.toResponse() ?: throw NotFoundException()

    /** Completes onboarding (first call) or updates the profile. The phone number comes from the token. */
    @PutMapping
    fun save(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: ProfileRequest): MeResponse {
        val caller = jwt.toCaller()
        return users.saveProfile(caller.uid, caller.phoneNumber, request.name, request.roles).toResponse()
    }

    private fun User.toResponse() = MeResponse(uid, phoneNumber, name, roles, workerStatus)
}

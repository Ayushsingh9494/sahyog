package com.sahyog.server.security

import com.sahyog.server.api.ErrorBody
import com.sahyog.server.api.UnauthorizedException
import com.sahyog.server.api.codeFor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.json.JsonMapper
import jakarta.servlet.http.HttpServletResponse

/**
 * Every `/api` request must carry a valid Firebase ID token (`Authorization: Bearer <token>`).
 * The server keeps no sessions or cookies: each request proves who it's from.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, json: JsonMapper): SecurityFilterChain {
        fun HttpServletResponse.writeError(status: HttpStatus) {
            this.status = status.value()
            contentType = MediaType.APPLICATION_JSON_VALUE
            writer.write(json.writeValueAsString(ErrorBody(codeFor(status))))
        }
        http
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                it.requestMatchers("/api/**").authenticated()
                it.anyRequest().denyAll()
            }
            .oauth2ResourceServer {
                it.jwt(Customizer.withDefaults())
                it.authenticationEntryPoint { _, response, _ -> response.writeError(HttpStatus.UNAUTHORIZED) }
                it.accessDeniedHandler { _, response, _ -> response.writeError(HttpStatus.FORBIDDEN) }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> response.writeError(HttpStatus.UNAUTHORIZED) }
                it.accessDeniedHandler { _, response, _ -> response.writeError(HttpStatus.FORBIDDEN) }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Token-authenticated JSON API with no cookies, so cross-site request forgery doesn't apply.
            .csrf { it.disable() }
        return http.build()
    }
}

/** Who is calling, as proven by their Firebase ID token. */
data class Caller(
    /** Firebase uid: the stable id of the person. */
    val uid: String,
    /** E.164, e.g. "+919876543210"; present for phone sign-in. */
    val phoneNumber: String?,
)

/** Controllers take `@AuthenticationPrincipal jwt: Jwt` (already verified) and call this. */
fun Jwt.toCaller(): Caller =
    Caller(uid = subject ?: throw UnauthorizedException(), phoneNumber = getClaimAsString("phone_number"))

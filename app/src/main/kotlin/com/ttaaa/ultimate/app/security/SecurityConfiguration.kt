package com.ttaaa.ultimate.app.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * The one place that decides who may call the app (spec 7.5, rule 3): one SecurityFilterChain per profile. Adding
 * login means changing the `server` chain here, not the controllers. Both profiles have an empty user store, so Spring
 * Boot never creates a default user with a logged password.
 */
@Configuration
@ConditionalOnWebApplication
class SecurityConfiguration {

    /** The `local` POC is single-user and bound to localhost: everything is open. */
    @Bean
    @Profile("local")
    fun localSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        // No sessions or cookies are used for authentication in the local profile, so there is nothing to forge.
        .csrf { it.disable() }
        .build()

    /**
     * The future server deployment requires a login (target: OIDC, spec 7.5). Until that exists, only the health
     * probes are reachable and everything else is denied, so a server deployment can never be open by accident.
     */
    @Bean
    @Profile("server")
    fun serverSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health/**").permitAll()
            it.anyRequest().denyAll()
        }
        .build()

    @Bean
    @Profile("local | server")
    fun emptyUserDetailsService(): UserDetailsService = InMemoryUserDetailsManager()
}

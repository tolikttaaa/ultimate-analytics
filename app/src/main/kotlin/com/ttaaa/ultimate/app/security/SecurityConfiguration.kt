package com.ttaaa.ultimate.app.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * The one place that decides who may call the app (spec 7.5, rule 3). The `local` POC is single-user and bound to
 * localhost, so its chain lets everything through; a `server` chain with login is added here later without touching
 * the controllers. Without a matching profile, Spring Boot's default chain locks everything.
 */
@Configuration
class SecurityConfiguration {

    @Bean
    @Profile("local")
    fun localSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        // No sessions or cookies are used for authentication in the local profile, so there is nothing to forge.
        .csrf { it.disable() }
        .build()
}

package com.example.MedRational.Security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Auth endpoints & Swagger
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Public account deletion page (Google Play link); its API is under /api/v1/auth/**
                        .requestMatchers(HttpMethod.GET, "/delete-account", "/delete-account.html").permitAll()

                        // 2. Public read & download endpoints (Students / Guests)
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reasonings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/downloads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/downloads/**").permitAll()

                        // 3. Admin-only operations (Write / Edit / Delete)
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasAuthority("ROLE_ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/v1/reasonings/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/reasonings/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reasonings/**").hasAuthority("ROLE_ADMIN")

                        // Any signed-in user may rate files; must come before the admin-only /files/** rules
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/*/ratings").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/*/views").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/files/*/ratings").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/v1/files/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/files/**").hasAuthority("ROLE_ADMIN")

                        // Statistics and other admin tools
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                        // Includes /api/v1/users/me (profile and account deletion), which take the user from the JWT
                        .anyRequest().authenticated()
                )
                // No token, or an expired one: 401, so the app renews its session and retries.
                // Signed in but not allowed (e.g. admin-only): 403, as before
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
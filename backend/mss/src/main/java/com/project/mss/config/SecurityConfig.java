package com.project.mss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    public SecurityConfig(SecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                    // Public resources / docs
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()

                    // Public authentication. User registration is restricted to administrators.
                    .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/logout", "/user/forgot-password", "/user/reset-password").permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/register").hasAnyAuthority("ADMIN", "MASTER")

                    // Any authenticated user can change their own password and read their profile
                    .requestMatchers(HttpMethod.PATCH, "/user/change-password").authenticated()
                    .requestMatchers(HttpMethod.GET, "/user/me", "/auth/session").authenticated()

                    // Master data, imports, replenishment and management reports: administrators only
                    .requestMatchers(HttpMethod.POST, "/hospitals/**", "/materials/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers(HttpMethod.PUT, "/hospitals/**", "/materials/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/imports/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/deliveries/**", "/supplier-orders/**", "/hospitals/*/replenishment-suggestions").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/reports/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers(HttpMethod.PATCH, "/pending-issues/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/stock/entry", "/stock/adjustment").hasAnyAuthority("ADMIN", "MASTER")

                    // User management and monitoring
                    .requestMatchers("/user/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/monitoring/**").hasAnyAuthority("ADMIN", "MASTER")

                    // Operations: surgical techs and administrators only
                    .requestMatchers("/surgeries/**", "/pending-issues/**").hasAnyAuthority("SURGICAL_TECH", "ADMIN", "MASTER")

                    // Read-only queries (stock, hospitals, materials): any authenticated user.
                    // Per-hospital access is enforced in the services: non-admin users
                    // (USER and SURGICAL_TECH) only see the hospitals they are assigned to.
                    .requestMatchers(HttpMethod.GET, "/stock/**", "/hospitals/**", "/materials/**").authenticated()

                    // Anything else: administrators only
                    .anyRequest().hasAnyAuthority("ADMIN", "MASTER")
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Not authenticated"))
                    .accessDeniedHandler((req, res, e) -> res.sendError(403, "Access denied")))
            .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }       

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}

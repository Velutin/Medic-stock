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

                    // Public: login, logout, first access and password reset
                    .requestMatchers(HttpMethod.POST, "/auth/session", "/auth/password-reset-requests", "/auth/password-resets").permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/auth/session").permitAll()

                    // Any authenticated user: own session, own data and own password
                    .requestMatchers(HttpMethod.GET, "/auth/session", "/users/me").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/users/me/password").authenticated()

                    // Master data, imports, replenishment and management reports: administrators only
                    .requestMatchers(HttpMethod.POST, "/hospitals/**", "/materials/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers(HttpMethod.PUT, "/hospitals/**", "/materials/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/imports/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/deliveries/**", "/supplier-orders/**", "/hospitals/*/replenishment-suggestions", "/hospitals/*/minimums/**", "/hospitals/*/prices/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/reports/**", "/billing/**", "/billing-rates/**", "/dashboard/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers(HttpMethod.PATCH, "/pending-issues/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/stock/entry", "/stock/adjustment").hasAnyAuthority("ADMIN", "MASTER")

                    // User management and monitoring
                    .requestMatchers("/users/**").hasAnyAuthority("ADMIN", "MASTER")
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
                    .authenticationEntryPoint((req, res, e) -> writeError(res, 401, "Not authenticated"))
                    .accessDeniedHandler((req, res, e) -> writeError(res, 403, "Access denied")))
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


    /** Same JSON error format as the API exception handler. */
    private static void writeError(jakarta.servlet.http.HttpServletResponse res, int status, String message)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}

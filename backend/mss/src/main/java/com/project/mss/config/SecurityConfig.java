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
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                    // Public authentication. User registration is restricted to administrators.
                    .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/logout", "/user/forgot-password", "/user/reset-password").permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/register").hasAnyAuthority("ADMIN", "MASTER")

                    // Any authenticated user can change their own password and read their profile
                    .requestMatchers(HttpMethod.PATCH, "/user/change-password").authenticated()
                    .requestMatchers(HttpMethod.GET, "/user/me").authenticated()

                    // Master data, imports, replenishment and management reports: administrators only
                    .requestMatchers(HttpMethod.POST, "/hospitals/**", "/materials/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers(HttpMethod.PUT, "/hospitals/**", "/materials/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/imports/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/replenishment/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/reports/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers(HttpMethod.PATCH, "/pending-issues/**", "/loans/*/notified").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/stock/entry", "/stock/adjustment").hasAnyAuthority("ADMIN", "MASTER")

                    // User management and monitoring
                    .requestMatchers("/user/**").hasAnyAuthority("ADMIN", "MASTER")
                    .requestMatchers("/monitoring/**").hasAnyAuthority("ADMIN", "MASTER")

                    // Stock, surgeries, loans and pending issues: authenticated; per-hospital access
                    // is enforced in the services (surgical techs only see the hospitals they work at)
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Not authenticated"))
                    .accessDeniedHandler((req, res, e) -> res.sendError(403, "Acesso negado")))
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

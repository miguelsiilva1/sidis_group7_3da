package aisafe.flightops.infrastructure;

import aisafe.shared.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Access rules of the Flight Operations service: the flight rules of the monolith,
 * plus the internal routes used by other services and peers. Tokens are issued by the login
 * service and validated here without calling it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_BACKOFFICE_OPERATOR
                ROLE_ADMIN > ROLE_ATCC
                ROLE_ADMIN > ROLE_MAINTENANCE_TECHNICIAN
                ROLE_ADMIN > ROLE_MAINTENANCE_SUPERVISOR
                """);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Replication between replicas of this service: only the replicas themselves (role SERVICE).
                        .requestMatchers("/internal/replicas/**").hasRole("SERVICE")
                        // Internal routes used by other services and peers.
                        .requestMatchers("/internal/**").authenticated()
                        // WP #3A/3B - Flights
                        .requestMatchers(HttpMethod.POST, "/api/flights/import").hasRole("BACKOFFICE_OPERATOR") // US225 (BONUS)
                        .requestMatchers(HttpMethod.POST, "/api/flights").hasRole("ATCC") // US212
                        .requestMatchers(HttpMethod.GET, "/api/flights").hasRole("ATCC") // US213
                        .requestMatchers(HttpMethod.GET, "/api/flights/reports/utilization").hasRole("BACKOFFICE_OPERATOR") // US229 (BONUS)
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

        return http.build();
    }
}

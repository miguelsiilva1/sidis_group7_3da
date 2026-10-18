package aisafe.airportsroutes.infrastructure;

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
 * Access rules of the Airports &amp; Routes service: the airport and route rules of the monolith,
 * the aircraft rules that moved here (US203, US227), plus the internal routes used by other
 * services and peers. Tokens are issued by the login service and validated here without calling it.
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
                        // Internal routes (copies of flights sent by flight-ops, queries from other services).
                        // They carry the user's token; checking the calling service's TLS identity comes with mutual TLS.
                        .requestMatchers("/internal/**").authenticated()
                        // WP #2A/2B - Airports
                        .requestMatchers(HttpMethod.POST, "/api/airports").hasRole("BACKOFFICE_OPERATOR") // US106, US207
                        .requestMatchers(HttpMethod.POST, "/api/airports/import").hasRole("BACKOFFICE_OPERATOR") // US225 (BONUS)
                        .requestMatchers(HttpMethod.POST, "/api/airports/*/photos").hasRole("BACKOFFICE_OPERATOR") // US207
                        .requestMatchers(HttpMethod.POST, "/api/airports/*/certifications").hasAnyRole("BACKOFFICE_OPERATOR", "ATCC") // US106a
                        .requestMatchers(HttpMethod.GET, "/api/airports/search").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR") // US108
                        .requestMatchers(HttpMethod.GET, "/api/airports/statistics/**").hasRole("BACKOFFICE_OPERATOR") // US210
                        .requestMatchers(HttpMethod.GET, "/api/airports/grouped").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR") // US211
                        .requestMatchers(HttpMethod.GET, "/api/airports/*/photos/*").hasAnyRole("BACKOFFICE_OPERATOR", "ATCC") // US207
                        .requestMatchers(HttpMethod.GET, "/api/airports/*/routes").hasRole("ATCC") // US209
                        .requestMatchers(HttpMethod.GET, "/api/airports/*").hasAnyRole("BACKOFFICE_OPERATOR", "ATCC") // US107
                        .requestMatchers(HttpMethod.PATCH, "/api/airports/*/status").hasRole("BACKOFFICE_OPERATOR") // US109
                        .requestMatchers(HttpMethod.PATCH, "/api/airports/*/details").hasRole("BACKOFFICE_OPERATOR") // US208
                        .requestMatchers(HttpMethod.DELETE, "/api/airports/*").hasRole("BACKOFFICE_OPERATOR") // EXTRAS (Delete Airport)
                        // WP #3A/3B - Routes
                        .requestMatchers(HttpMethod.GET, "/api/routes/export").hasRole("BACKOFFICE_OPERATOR") // US228 (BONUS)
                        .requestMatchers(HttpMethod.POST, "/api/routes/import").hasRole("BACKOFFICE_OPERATOR") // US225 (BONUS)
                        .requestMatchers(HttpMethod.GET, "/api/network/total-distance").hasRole("ATCC") // US215
                        .requestMatchers(HttpMethod.GET, "/api/routes/alternatives").hasRole("ATCC") // US216
                        .requestMatchers(HttpMethod.GET, "/api/routes/compatible").hasRole("ATCC") // US203 (moved from /api/aircrafts, D2)
                        .requestMatchers(HttpMethod.GET, "/api/routes/*/*/fuel-efficiency").hasRole("ATCC") // US227 (BONUS, moved from /api/aircrafts, D2)
                        .requestMatchers(HttpMethod.POST, "/api/routes").hasRole("ATCC") // US110
                        .requestMatchers(HttpMethod.PUT, "/api/routes/*/*").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR") // US112
                        .requestMatchers(HttpMethod.PATCH, "/api/routes/*/*/deactivate").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR") // US112
                        .requestMatchers(HttpMethod.GET, "/api/routes/airport/*").hasRole("ATCC") // US113
                        .requestMatchers(HttpMethod.GET, "/api/routes/search").hasRole("ATCC") // US114
                        .requestMatchers(HttpMethod.GET, "/api/routes/*/*").hasRole("ATCC") // US113
                        .requestMatchers(HttpMethod.GET, "/api/routes/*/*/history").hasRole("ATCC") // US111
                        .requestMatchers(HttpMethod.GET, "/api/routes").hasRole("ATCC") // US214
                        .requestMatchers(HttpMethod.DELETE, "/api/routes/*/*").hasRole("ATCC") // EXTRAS (Delete Route)
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

        return http.build();
    }
}

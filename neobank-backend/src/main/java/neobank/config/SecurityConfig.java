package neobank.config;

import lombok.RequiredArgsConstructor;
import neobank.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // FIX (found while writing AuthControllerIT): this used to be a
                        // blanket "/api/auth/**".permitAll(), which also covered
                        // /api/auth/logout and /api/auth/change-password - both of which
                        // require an authenticated principal internally
                        // (@AuthenticationPrincipal UserPrincipal in logout(), a required
                        // Authorization header in changePassword()). With no token
                        // present, logout() NPEs on a null UserPrincipal and
                        // changePassword() throws MissingRequestHeaderException - neither
                        // is handled by GlobalExceptionHandler's specific handlers, so both
                        // fell through to the generic Exception.class -> 500 handler
                        // instead of a proper 401. Narrowed permitAll() to exactly the
                        // sub-paths that are genuinely public; logout/change-password now
                        // correctly require authentication like every other endpoint.
                        .requestMatchers(
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-code",
                                "/api/auth/refresh-token",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers(
                                org.springframework.http.HttpMethod.OPTIONS, "/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
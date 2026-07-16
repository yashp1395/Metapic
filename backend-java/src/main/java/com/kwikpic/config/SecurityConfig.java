package com.kwikpic.config;

import com.kwikpic.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * <p>Auth is NOT handled via Spring Security's filter chain.
 * Instead, each controller method explicitly calls
 * {@code AuthResolverService.resolveUser()} or {@code .resolvePhotographer()},
 * mirroring the Node pattern where each route attaches its own middleware.
 *
 * <p>Spring Security is used here ONLY for:
 * <ul>
 *   <li>CORS configuration (matching Node's cors({origin: true, credentials: true}))</li>
 *   <li>CSRF disabled (stateless REST API)</li>
 *   <li>Stateless session management</li>
 *   <li>BCrypt password encoder bean</li>
 *   <li>Rate limiting filter insertion</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // All requests are permitted — auth is handled per-controller
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // Insert rate limiting filter before Spring Security's auth filters
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt encoder with strength 10, matching Node's bcrypt.genSalt(10).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * CORS config matching Node's cors({ origin: true, credentials: true,
     * methods: ["GET","POST","PUT","DELETE"], allowedHeaders: ["Content-Type","Authorization"] }).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

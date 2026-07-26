package com.metapic.service;

import com.metapic.model.Photographer;
import com.metapic.model.User;
import com.metapic.repository.PhotographerRepository;
import com.metapic.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explicit per-controller auth resolution — direct translation of
 * the Node middleware pattern where each route explicitly attaches
 * either {@code userJwt} or {@code photographerJwt}.
 *
 * <p>Usage in a controller method:
 * <pre>
 *   User user = authResolver.resolveUser(request);
 *   Photographer ph = authResolver.resolvePhotographer(request);
 * </pre>
 *
 * <p>No URL-pattern inference. Each controller method decides which
 * entity type it needs and calls the matching resolver.
 */
@Service
public class AuthResolverService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PhotographerRepository photographerRepository;

    public AuthResolverService(JwtService jwtService,
                               UserRepository userRepository,
                               PhotographerRepository photographerRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.photographerRepository = photographerRepository;
    }

    /**
     * Equivalent of Node's {@code userJwt} middleware.
     * Extracts Bearer token → decodes _id → finds User → returns.
     *
     * @throws ResponseStatusException 401 if token missing, invalid, or user not found.
     */
    public User resolveUser(HttpServletRequest request) {
        String userId = extractUserId(request);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Please authenticate as a User."));
    }

    /**
     * Equivalent of Node's {@code photographerJwt} middleware.
     * Extracts Bearer token → decodes _id → finds Photographer → returns.
     *
     * @throws ResponseStatusException 401 if token missing, invalid, or photographer not found.
     */
    public Photographer resolvePhotographer(HttpServletRequest request) {
        String userId = extractUserId(request);
        return photographerRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Please authenticate as a Photographer."));
    }

    // ─── Internals ──────────────────────────────────────────────

    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header");
        }
        String token = authHeader.substring(7);
        try {
            return jwtService.extractUserId(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token");
        }
    }
}

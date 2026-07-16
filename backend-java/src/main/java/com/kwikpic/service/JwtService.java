package com.kwikpic.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT utilities — compatible with the Node jsonwebtoken library.
 *
 * Node signs tokens as: jwt.sign({ _id: id }, secret, { expiresIn: '7d' })
 * using HS256 by default. We replicate exactly the same behaviour here so
 * tokens issued by the old Node backend remain valid during migration, and
 * tokens issued here work if anyone temporarily falls back to Node.
 *
 * Uses SecretKeySpec directly (rather than Keys.hmacShaKeyFor) to handle
 * secrets shorter than 256 bits — Node's jsonwebtoken allows short secrets,
 * JJWT's Keys helper does not.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:supersecretkey}")
    private String secret;

    private static final long EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days

    /**
     * Issue a JWT with the same claim structure as the Node backend.
     * Payload: { "_id": userId, iat: ..., exp: ... }
     */
    public String generateToken(String userId) {
        return Jwts.builder()
                .claim("_id", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extract the "_id" claim from a validated token.
     *
     * @throws io.jsonwebtoken.JwtException if expired, malformed, or wrong signature.
     */
    public String extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("_id", String.class);
    }

    /**
     * Build the HMAC-SHA256 key from the raw secret string, byte-for-byte
     * compatible with Node's jsonwebtoken default behaviour.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}

package com.example.demo.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "clave-de-prueba-para-jwt-service-0123456789";
    private static final String OTHER_SECRET = "otra-clave-distinta-para-firmar-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 3600000);

    @Test
    void generatedTokenContainsExpectedClaims() {
        String token = jwtService.generateToken("usuario@example.com", 7L, "Usuario Prueba");

        Jws<Claims> jws = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token);
        Claims claims = jws.getPayload();

        assertEquals("HS256", jws.getHeader().getAlgorithm());

        assertEquals("usuario@example.com", claims.getSubject());
        assertEquals(7L, claims.get("userId", Number.class).longValue());
        assertEquals("Usuario Prueba", claims.get("name", String.class));
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void tokenIsValidForItsOwner() {
        String token = jwtService.generateToken("usuario@example.com", 7L, "Usuario Prueba");
        User owner = User.builder().email("usuario@example.com").build();

        assertEquals("usuario@example.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token, owner));
    }

    @Test
    void tokenIsNotValidForAnotherUser() {
        String token = jwtService.generateToken("usuario@example.com", 7L, "Usuario Prueba");
        User other = User.builder().email("otro@example.com").build();

        assertFalse(jwtService.isTokenValid(token, other));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiredJwtService = new JwtService(SECRET, -1000);
        String token = expiredJwtService.generateToken("usuario@example.com", 7L, "Usuario Prueba");

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractEmail(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = new JwtService(OTHER_SECRET, 3600000)
                .generateToken("usuario@example.com", 7L, "Usuario Prueba");

        assertThrows(JwtException.class, () -> jwtService.extractEmail(token));
    }

    @Test
    void malformedTokenIsRejected() {
        assertThrows(JwtException.class, () -> jwtService.extractEmail("abc.def.ghi"));
    }

    @Test
    void longSecretStillSignsWithHs256() {
        String longSecret = "0123456789abcdef".repeat(6);
        String token = new JwtService(longSecret, 3600000)
                .generateToken("usuario@example.com", 7L, "Usuario Prueba");

        String algorithm = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getHeader()
                .getAlgorithm();

        assertEquals("HS256", algorithm);
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("clave-corta", 3600000));
    }
}

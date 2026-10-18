package aisafe.shared.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenValidatorTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String OTHER_SECRET = "5A7134743777217A25432A462D4A614E645267556B58703273357638792F423F";

    private final JwtTokenValidator validator = new JwtTokenValidator(SECRET);

    private static String token(String secret, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.builder()
                .subject("atcc")
                .claim("roles", List.of("ATCC"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    void ensureTokenSignedWithSameSecretIsValid() {
        String token = token(SECRET, 60_000);
        assertTrue(validator.isTokenValid(token));
        assertEquals("atcc", validator.extractSubject(token));
        assertEquals(List.of("ATCC"), validator.extractRoles(token));
    }

    @Test
    void ensureTokenSignedWithOtherSecretIsInvalid() {
        assertFalse(validator.isTokenValid(token(OTHER_SECRET, 60_000)));
    }

    @Test
    void ensureExpiredTokenIsInvalid() {
        assertFalse(validator.isTokenValid(token(SECRET, -1_000)));
    }

    @Test
    void ensureMalformedTokenIsInvalid() {
        assertFalse(validator.isTokenValid("not-a-jwt"));
        assertFalse(validator.isTokenValid(""));
    }
}

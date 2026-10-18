package aisafe.shared.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Validates the JWTs issued by the login service and reads the user and roles from them.
 *
 * <p>Every service validates tokens on its own, without calling the login service.
 * The signing secret comes from configuration ({@code security.jwt.secret}), never from code.</p>
 */
@Component
public class JwtTokenValidator {

    private final SecretKey key;

    public JwtTokenValidator(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * Extracts the subject (username) from the given JWT token.
     * @param token the JWT token from which to extract the subject
     * @return the subject (username) contained in the token
     */
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the roles from the given JWT token.
     * @param token the JWT token from which to extract the roles
     * @return a list of roles contained in the token
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) parseClaims(token).get("roles");
    }

    /**
     * Checks whether the token has a valid signature and has not expired.
     * @param token the JWT token to check
     * @return true if the token is valid
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

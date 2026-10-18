package aisafe.shared.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Tokens a replica uses when it calls its peers on its own behalf (replication, recovery),
 * not on behalf of a user: subject {@code service:<node id>}, role {@code SERVICE}.
 *
 * <p>Provisional service identity, signed with the same secret as user tokens. Mutual TLS
 * (plan, §6) will identify the calling service by its certificate instead.</p>
 */
@Component
public class ServiceTokens {

    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(2);

    private final SecretKey key;
    private final String nodeId;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokens(@Value("${security.jwt.secret}") String secret,
                         @Value("${aisafe.node.id}") String nodeId) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.nodeId = nodeId;
    }

    /** A valid token for this replica, renewed shortly before it expires. */
    public synchronized String current() {
        Instant now = Instant.now();
        if (token == null || now.isAfter(expiresAt.minus(RENEW_BEFORE))) {
            expiresAt = now.plus(LIFETIME);
            token = Jwts.builder()
                    .subject("service:" + nodeId)
                    .claim("roles", List.of("SERVICE"))
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiresAt))
                    .signWith(key)
                    .compact();
        }
        return token;
    }
}

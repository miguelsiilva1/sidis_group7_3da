package aisafe.security.infrastructure.persistence.jpa;

import aisafe.security.domain.Role;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID userID;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected UserJpaEntity() {}

    /** A newer copy of the same account, received from another login. */
    public void changeCredentials(String passwordHash, Role role) {
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public UserJpaEntity(UUID userID, String username, String passwordHash, Role role) {
        this.userID = userID;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

}

package aisafe.login.infrastructure;

import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void ensureOnlyTheSeedNodeCreatesTheSampleUsers() {
        new Bootstrap(userRepository, passwordEncoder, "login-1", "login-0").run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void ensureSeedNodeCreatesTheSampleUsersWhenThereAreNone() {
        when(passwordEncoder.encode(any())).thenReturn("hash");

        new Bootstrap(userRepository, passwordEncoder, "login-0", "login-0").run(new DefaultApplicationArguments());

        verify(userRepository, times(5)).save(any(User.class));
    }

    @Test
    void ensureEveryLoginCreatesThemWithoutASeedNode() {
        when(passwordEncoder.encode(any())).thenReturn("hash");

        new Bootstrap(userRepository, passwordEncoder, "local", "").run(new DefaultApplicationArguments());

        verify(userRepository, times(5)).save(any(User.class));
    }
}

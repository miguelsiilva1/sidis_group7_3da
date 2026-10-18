package aisafe.login.infrastructure;

import aisafe.security.domain.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginExceptionHandlerTest {

    @Test
    void ensureHandleInvalidCredentials() {
        var resp = new LoginExceptionHandler().handleInvalidCredentials(new InvalidCredentialsException());
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("Invalid credentials", resp.getBody().message());
    }
}

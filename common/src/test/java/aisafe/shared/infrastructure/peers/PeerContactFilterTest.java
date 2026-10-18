package aisafe.shared.infrastructure.peers;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PeerContactFilterTest {

    private final PeerClient peerClient = mock(PeerClient.class);
    private final PeerContactFilter filter = new PeerContactFilter(peerClient);

    @Test
    void ensureRequestFromAPeerTellsThePeerClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/replicas/aircrafts");
        request.addHeader(PeerHeaders.FORWARDED, "am-1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(peerClient).heardFrom("am-1");
        assertNotNull(chain.getRequest(), "the request goes on");
    }

    @Test
    void ensureClientRequestTellsNothing() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/aircrafts"), new MockHttpServletResponse(), new MockFilterChain());

        verify(peerClient, never()).heardFrom(any());
    }
}

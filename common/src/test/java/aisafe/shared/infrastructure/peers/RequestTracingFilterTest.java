package aisafe.shared.infrastructure.peers;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestTracingFilterTest {

    private final RequestTracingFilter filter = new RequestTracingFilter("node-1");

    @Test
    void ensureResponseNamesTheReplicaAndKeepsTheCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/things");
        request.addHeader(PeerHeaders.CORRELATION_ID, "corr-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> correlationInChain.set(MDC.get(RequestTracingFilter.MDC_CORRELATION_ID));

        filter.doFilter(request, response, chain);

        assertEquals("node-1", response.getHeader(PeerHeaders.NODE_ID));
        assertEquals("corr-42", response.getHeader(PeerHeaders.CORRELATION_ID));
        assertEquals("corr-42", correlationInChain.get());
        assertNull(MDC.get(RequestTracingFilter.MDC_CORRELATION_ID), "the logging context is cleared afterwards");
    }

    @Test
    void ensureCorrelationIdIsCreatedWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/things"), response, (req, res) -> { });

        assertNotNull(response.getHeader(PeerHeaders.CORRELATION_ID));
        assertFalse(response.getHeader(PeerHeaders.CORRELATION_ID).isBlank());
    }
}

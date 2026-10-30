package aisafe.shared.infrastructure.security;

import aisafe.shared.infrastructure.peers.PeerHeaders;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditFilterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AuditFilter filter = new AuditFilter("node-1", objectMapper);
    private final Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    @BeforeEach
    void setUp() {
        lines.start();
        audit.addAppender(lines);
    }

    @AfterEach
    void tearDown() {
        audit.detachAppender(lines);
    }

    private JsonNode onlyLine() {
        assertEquals(1, lines.list.size());
        return objectMapper.readTree(lines.list.getFirst().getFormattedMessage());
    }

    @Test
    void ensureAWriteIsRecordedWithWhoWhatResultAndWhere() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights");
        request.setQueryString("dryRun=false");
        request.addHeader(PeerHeaders.FORWARDED, "flight-ops-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader(PeerHeaders.CORRELATION_ID, "corr-42");

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(AuditFilter.USER_ATTRIBUTE, "atcc");
            req.setAttribute(AuditFilter.ROLES_ATTRIBUTE, List.of("ATCC"));
            ((MockHttpServletResponse) res).setStatus(201);
        });

        JsonNode line = onlyLine();
        assertEquals("atcc", line.get("user").asString());
        assertEquals("ATCC", line.get("roles").get(0).asString());
        assertEquals("POST", line.get("method").asString());
        assertEquals("/api/flights?dryRun=false", line.get("path").asString());
        assertEquals(201, line.get("status").asInt());
        assertEquals("node-1", line.get("node").asString());
        assertEquals("corr-42", line.get("correlationId").asString());
        assertEquals("flight-ops-2", line.get("forwardedBy").asString());
        assertNotNull(line.get("time").asString());
    }

    @Test
    void ensureARequestRefusedWithoutAValidTokenIsRecordedWithNoUser() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("DELETE", "/api/aircrafts/CS-TKA"), response,
                (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

        JsonNode line = onlyLine();
        assertTrue(line.get("user").isNull());
        assertTrue(line.get("roles").isEmpty());
        assertEquals(401, line.get("status").asInt());
    }

    @Test
    void ensureTokensAndBodiesAreNeverWritten() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("Authorization", "Bearer the-token");
        request.setContent("{\"username\":\"atcc\",\"password\":\"atcc123\"}".getBytes());

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        String line = lines.list.getFirst().getFormattedMessage();
        assertFalse(line.contains("the-token"));
        assertFalse(line.contains("atcc123"));
    }

    @Test
    void ensureAFailureThatEscapesIsRecordedAs500() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/routes/LIS/OPO");

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("boom"); }));

        assertEquals(500, onlyLine().get("status").asInt());
    }

    @Test
    void ensureRequestsOutsideTheApiAreNotRecorded() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/internal/replicas/aircraft"),
                new MockHttpServletResponse(), (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), (req, res) -> { });

        assertTrue(lines.list.isEmpty());
    }
}

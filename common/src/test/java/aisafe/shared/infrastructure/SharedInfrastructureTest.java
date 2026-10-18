package aisafe.shared.infrastructure;

import aisafe.shared.application.dtos.BulkImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SharedInfrastructureTest {

    @Test
    void ensureETagUtilsParsesValidETags() {
        assertEquals(5L, ETagUtils.parseVersion("5"));
        assertEquals(5L, ETagUtils.parseVersion("W/\"5\""));
        assertEquals(10L, ETagUtils.parseVersion("\"10\""));
        assertEquals(123L, ETagUtils.parseVersion("W/123"));
    }

    @Test
    void ensureETagUtilsThrowsOnInvalidETags() {
        assertThrows(IllegalArgumentException.class, () -> ETagUtils.parseVersion(null));
        assertThrows(IllegalArgumentException.class, () -> ETagUtils.parseVersion("   "));
        assertThrows(IllegalArgumentException.class, () -> ETagUtils.parseVersion("W/abc"));
    }

    @Test
    void ensureETagUtilsPrivateConstructor() throws Exception {
        Constructor<ETagUtils> constructor = ETagUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    @Test
    void ensureBulkImportResponseBuilderCreated() {
        BulkImportResult<String> result = new BulkImportResult<>();
        result.addSuccess("Item 1");
        
        ResponseEntity<Map<String, Object>> response = BulkImportResponseBuilder.buildResponse(result);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().get("totalProcessed"));
        assertEquals(1, response.getBody().get("successfulCount"));
        assertEquals(0, response.getBody().get("errorCount"));
    }

    @Test
    void ensureBulkImportResponseBuilderPrivateConstructor() throws Exception {
        Constructor<BulkImportResponseBuilder> constructor = BulkImportResponseBuilder.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void ensureBulkImportResponseBuilderMultiStatus() {
        BulkImportResult<String> result = new BulkImportResult<>();
        result.addSuccess("Item 1");
        result.addError(2, "bad data", "format error");
        
        ResponseEntity<Map<String, Object>> response = BulkImportResponseBuilder.buildResponse(result);
        assertEquals(HttpStatus.MULTI_STATUS, response.getStatusCode());
        assertEquals(2, response.getBody().get("totalProcessed"));
        assertEquals(1, response.getBody().get("successfulCount"));
        assertEquals(1, response.getBody().get("errorCount"));
        assertNotNull(response.getBody().get("errors"));
    }

    @Test
    void ensureBulkImportResponseBuilderBadRequest() {
        BulkImportResult<String> result = new BulkImportResult<>();
        result.addError(1, "bad data", "format error");
        
        ResponseEntity<Map<String, Object>> response = BulkImportResponseBuilder.buildResponse(result);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1, response.getBody().get("totalProcessed"));
        assertEquals(0, response.getBody().get("successfulCount"));
        assertEquals(1, response.getBody().get("errorCount"));
    }

    @Test
    void ensureBulkImportResultWithNoRowsIsFullySuccessfulFalse() {
        BulkImportResult<String> result = new BulkImportResult<>();
        assertFalse(result.isFullySuccessful());
        assertFalse(result.hasAnySuccess());
    }

}

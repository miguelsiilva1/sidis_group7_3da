package aisafe.flightops.infrastructure.saga;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.fleet.domain.FlightCopy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaCompensationServiceTest {

    @Mock
    private SagaCompensationRepository repository;

    @Mock
    private Fleet fleet;

    @InjectMocks
    private SagaCompensationService service;

    private FlightCopy sampleCopy() {
        return new FlightCopy(
                "1",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "CANCELED",
                "LIS",
                "OPO",
                "CS-TKA",
                2L
        );
    }

    @Test
    void ensureRecordFailedCompensationSavesEntity() {
        FlightCopy copy = sampleCopy();
        service.recordFailedCompensation(copy, "AIRCRAFT", "Network timeout");

        ArgumentCaptor<SagaCompensationJpaEntity> captor = ArgumentCaptor.forClass(SagaCompensationJpaEntity.class);
        verify(repository).save(captor.capture());

        SagaCompensationJpaEntity saved = captor.getValue();
        assertEquals("1", saved.getFlightId());
        assertEquals("AIRCRAFT", saved.getTargetService());
        assertEquals("CANCELED", saved.getStatus());
        assertEquals("CS-TKA", saved.getAircraftRegistration());
        assertEquals("Network timeout", saved.getLastError());
        assertEquals(1, saved.getAttempts());
    }

    @Test
    void ensureRetryCompensationsDeletesOnSuccess() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SagaCompensationJpaEntity entity = new SagaCompensationJpaEntity(
                "1", "AIRCRAFT", now, now.plusHours(2), "CANCELED", "LIS", "OPO", "CS-TKA", 2L,
                1, now, "Previous error"
        );
        when(repository.findAll()).thenReturn(List.of(entity));

        service.retryCompensations();

        verify(fleet).sendFlightToAircraft(argThat(c -> "1".equals(c.id()) && "CANCELED".equals(c.status())));
        verify(repository).delete(entity);
    }

    @Test
    void ensureRetryCompensationsIncrementsAttemptsOnNetworkFailure() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SagaCompensationJpaEntity entity = new SagaCompensationJpaEntity(
                "1", "AIRCRAFT", now, now.plusHours(2), "CANCELED", "LIS", "OPO", "CS-TKA", 2L,
                1, now, "Previous error"
        );
        when(repository.findAll()).thenReturn(List.of(entity));
        doThrow(new FleetUnavailableException("Still down")).when(fleet).sendFlightToAircraft(any());

        service.retryCompensations();

        verify(repository, never()).delete(any());
        verify(repository).save(entity);
        assertEquals(2, entity.getAttempts());
        assertTrue(entity.getLastError().contains("Still down"));
    }
}

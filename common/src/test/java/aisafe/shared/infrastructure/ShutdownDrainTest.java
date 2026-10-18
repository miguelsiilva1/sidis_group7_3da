package aisafe.shared.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ShutdownDrainTest {

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    @Test
    void ensureStoppingSaysNotReadyAndWaitsBeforeTheRestStops() {
        ShutdownDrain drain = new ShutdownDrain(publisher, 200);
        drain.start();

        long started = System.nanoTime();
        drain.stop();

        assertTrue((System.nanoTime() - started) / 1_000_000 >= 200);
        verify(publisher).publishEvent(argThat((ApplicationEvent e) -> e instanceof AvailabilityChangeEvent<?> event
                && event.getState() == ReadinessState.REFUSING_TRAFFIC));
        assertFalse(drain.isRunning());
        assertTrue(drain.getPhase() > SmartLifecycle.DEFAULT_PHASE - 1024, "before the web server's graceful shutdown");
    }

    @Test
    void ensureNoWaitWhenDisabled() {
        ShutdownDrain drain = new ShutdownDrain(publisher, 0);
        drain.start();
        assertTrue(drain.isRunning());

        drain.stop();

        verify(publisher, never()).publishEvent(any(ApplicationEvent.class));
        assertFalse(drain.isRunning());
    }
}

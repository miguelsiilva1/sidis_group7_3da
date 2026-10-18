package aisafe.shared.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * On shutdown, the replica says it is not ready and waits a moment before the web server stops.
 * The entries in front of the replicas check readiness every second, so they stop sending requests
 * here before the connections are closed; otherwise a request sent just then would fail.
 */
@Component
public class ShutdownDrain implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ShutdownDrain.class);

    private final ApplicationEventPublisher publisher;
    private final long drainMs;
    private volatile boolean running;

    public ShutdownDrain(ApplicationEventPublisher publisher, @Value("${aisafe.shutdown.drain-ms:0}") long drainMs) {
        this.publisher = publisher;
        this.drainMs = drainMs;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        if (drainMs > 0) {
            AvailabilityChangeEvent.publish(publisher, this, ReadinessState.REFUSING_TRAFFIC);
            log.info("Shutting down: not ready any more, waiting {} ms for requests to go elsewhere", drainMs);
            try {
                Thread.sleep(drainMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Stops before everything else, the web server included. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}

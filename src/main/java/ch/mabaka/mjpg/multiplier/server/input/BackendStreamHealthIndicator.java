package ch.mabaka.mjpg.multiplier.server.input;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BackendStreamHealthIndicator implements HealthIndicator {
    private final MjpegInputStreamReaderComponent mjpegInputStreamReaderComponent;

    public BackendStreamHealthIndicator(MjpegInputStreamReaderComponent mjpegInputStreamReaderComponent) {
        this.mjpegInputStreamReaderComponent = mjpegInputStreamReaderComponent;
    }

    @Override
    public Health health() {
        boolean available = mjpegInputStreamReaderComponent.isBackendStreamAvailable();
        if (available) {
            return Health.up().withDetail("backendStreamAvailable", true).build();
        } else {
            return Health.down().withDetail("backendStreamAvailable", false).build();
        }
    }
}

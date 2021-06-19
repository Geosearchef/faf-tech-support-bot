package de.geosearchef.scanner;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

public abstract class ServiceScanner {

    private static final Duration UP_CHECK_TIMEOUT = Duration.ofSeconds(10);

    @Getter protected String representation;
    @Getter protected volatile Instant lastChecked = null;
    @Getter protected volatile Instant lastResponse = null;

    public ServiceScanner(String representation) {
        this.representation = representation;
    }

    public final boolean isUp() {
        return lastChecked != null && lastResponse != null &&
                ! (lastResponse.isBefore(lastChecked) && lastChecked.plus(UP_CHECK_TIMEOUT).isBefore(Instant.now()));
    }

    public final void scan() {
        lastChecked = Instant.now();
        new Thread(this::runScanAsync).start();
    }

    protected abstract void runScanAsync();
}

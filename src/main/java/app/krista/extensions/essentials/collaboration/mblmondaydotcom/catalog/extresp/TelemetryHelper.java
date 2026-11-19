package app.krista.extensions.essentials.collaboration.mblmondaydotcom.catalog.extresp;

import app.krista.ksdk.telemetry.TelemetryMetrics;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Telemetry lifecycle wrapper.
 *
 * <p>Recording must never break business logic, so every call swallows its own failures. Tags stay
 * low-cardinality — operation names only, never ids, emails, board names or URLs.
 */
@Service
public class TelemetryHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryHelper.class);

    private final TelemetryMetrics metrics;

    @Inject
    public TelemetryHelper(TelemetryMetrics metrics) {
        this.metrics = metrics;
    }

    /** Call at request start; returns the start timestamp. */
    public long start(String prefix) {
        try {
            metrics.incrementCounter(prefix + ".requests", 1, Map.of());
        } catch (Exception ignored) {
            LOG.trace("telemetry start failed for {}", prefix);
        }
        return System.currentTimeMillis();
    }

    public void recordSuccess(String prefix, long startTime, Map<String, String> tags) {
        try {
            long elapsed = System.currentTimeMillis() - startTime;
            Map<String, String> allTags = new HashMap<>(tags);
            allTags.put("status", "success");
            metrics.incrementCounter(prefix + ".success", 1, allTags);
            metrics.recordDuration(prefix + ".duration", elapsed, allTags);
        } catch (Exception ignored) {
            LOG.trace("telemetry success failed for {}", prefix);
        }
    }

    public void recordError(String prefix, long startTime, Exception exception, Map<String, String> tags) {
        try {
            long elapsed = System.currentTimeMillis() - startTime;
            Map<String, String> allTags = new HashMap<>(tags);
            allTags.put("status", "error");
            allTags.put("error_class", exception.getClass().getSimpleName());
            metrics.incrementCounter(prefix + ".failure", 1, allTags);
            metrics.recordDuration(prefix + ".duration", elapsed, allTags);
        } catch (Exception ignored) {
            LOG.trace("telemetry error failed for {}", prefix);
        }
    }
}

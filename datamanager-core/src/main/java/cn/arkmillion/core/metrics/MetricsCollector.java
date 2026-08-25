package cn.arkmillion.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class MetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);

    private static volatile long slowQueryThresholdMs = 1_000L;

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> slowQueries = new ConcurrentHashMap<>();

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    private MetricsCollector() {
    }

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    public static void setSlowQueryThresholdMs(long thresholdMs) {
        slowQueryThresholdMs = thresholdMs;
    }

    public static long getSlowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }

    public void recordSlowQuery(String sql, long elapsedMs) {
        LOG.warn("Slow query detected ({} ms): {}", elapsedMs, abbreviate(sql));
        slowQueries.computeIfAbsent(abbreviate(sql), k -> new LongAdder()).increment();
    }

    public void recordPoolStats(String poolName, int active, int idle, int waiting) {
        LOG.debug("Pool [{}] active={} idle={} waiting={}", poolName, active, idle, waiting);
    }

    public void incrementCounter(String operation, String dbType) {
        counters.computeIfAbsent(operation + ":" + dbType, k -> new LongAdder()).increment();
    }

    public long counterValue(String operation, String dbType) {
        LongAdder adder = counters.get(operation + ":" + dbType);
        return adder == null ? 0L : adder.sum();
    }

    public Map<String, Long> snapshotCounters() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, LongAdder> e : counters.entrySet()) {
            snapshot.put(e.getKey(), e.getValue().sum());
        }
        return snapshot;
    }

    private String abbreviate(String sql) {
        if (sql == null) {
            return "";
        }
        String s = sql.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}

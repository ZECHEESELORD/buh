package sh.harold.fulcrum.common.data.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class DataMetrics {

    private static final DataMetrics NOOP = new DataMetrics(true);

    private final boolean noop;
    private final ConcurrentHashMap<String, LongAdder> success = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> failure = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> totalTimeMillis = new ConcurrentHashMap<>();

    private DataMetrics(boolean noop) {
        this.noop = noop;
    }

    public DataMetrics() {
        this(false);
    }

    public static DataMetrics noop() {
        return NOOP;
    }

    public void record(String operation, String collection, long durationMillis, boolean succeeded) {
        if (noop) {
            return;
        }
        String key = operationKey(operation, collection);
        increment(succeeded ? success : failure, key);
        increment(totalTimeMillis, key, durationMillis);
    }

    public Map<String, Long> successCounts() {
        return snapshot(success);
    }

    public Map<String, Long> failureCounts() {
        return snapshot(failure);
    }

    public Map<String, Long> totalTimeMillis() {
        return snapshot(totalTimeMillis);
    }

    private String operationKey(String operation, String collection) {
        return operation + ":" + collection;
    }

    private void increment(ConcurrentHashMap<String, LongAdder> map, String key) {
        increment(map, key, 1L);
    }

    private void increment(ConcurrentHashMap<String, LongAdder> map, String key, long amount) {
        map.computeIfAbsent(key, ignored -> new LongAdder()).add(amount);
    }

    private Map<String, Long> snapshot(ConcurrentHashMap<String, LongAdder> map) {
        Map<String, Long> copy = new ConcurrentHashMap<>();
        map.forEach((key, value) -> copy.put(key, value.sum()));
        return copy;
    }
}

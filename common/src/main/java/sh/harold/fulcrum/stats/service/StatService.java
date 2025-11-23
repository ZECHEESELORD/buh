package sh.harold.fulcrum.stats.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.core.StatValueChange;

public final class StatService {

    private static final double CHANGE_EPSILON = 1.0E-9;

    private final StatRegistry registry;
    private final Map<EntityKey, StatContainer> containers = new ConcurrentHashMap<>();
    private final List<StatChangeListener> listeners = new CopyOnWriteArrayList<>();

    public StatService(StatRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public StatContainer getContainer(EntityKey entityKey) {
        Objects.requireNonNull(entityKey, "entityKey");
        return containers.computeIfAbsent(entityKey, this::createContainer);
    }

    public void removeContainer(EntityKey entityKey) {
        Objects.requireNonNull(entityKey, "entityKey");
        containers.remove(entityKey);
    }

    public void addListener(StatChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(StatChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.remove(listener);
    }

    private StatContainer createContainer(EntityKey entityKey) {
        return new StatContainer(registry, change -> handleChange(entityKey, change));
    }

    private void handleChange(EntityKey entityKey, StatValueChange change) {
        if (!change.hasChanged(CHANGE_EPSILON)) {
            return;
        }

        StatChange statChange = new StatChange(entityKey, change.statId(), change.oldValue(), change.newValue());
        for (StatChangeListener listener : listeners) {
            listener.onStatChanged(statChange);
        }
    }
}

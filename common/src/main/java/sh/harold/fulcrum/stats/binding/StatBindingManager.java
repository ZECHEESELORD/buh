package sh.harold.fulcrum.stats.binding;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.service.StatChange;
import sh.harold.fulcrum.stats.service.StatChangeListener;

public final class StatBindingManager implements StatChangeListener {

    private final Map<StatId, List<StatBinding>> bindings = new ConcurrentHashMap<>();

    public void registerBinding(StatBinding binding) {
        Objects.requireNonNull(binding, "binding");
        bindings.computeIfAbsent(binding.getStatId(), ignored -> new CopyOnWriteArrayList<>()).add(binding);
    }

    public void unregisterBinding(StatBinding binding) {
        Objects.requireNonNull(binding, "binding");
        List<StatBinding> registered = bindings.get(binding.getStatId());
        if (registered == null) {
            return;
        }

        registered.remove(binding);
        if (registered.isEmpty()) {
            bindings.remove(binding.getStatId());
        }
    }

    @Override
    public void onStatChanged(StatChange change) {
        List<StatBinding> statBindings = bindings.get(change.statId());
        if (statBindings == null) {
            return;
        }

        for (StatBinding binding : statBindings) {
            binding.onStatChanged(change);
        }
    }
}

package sh.harold.fulcrum.common.loader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ModuleLoader {

    private final Map<ModuleId, FulcrumModule> modules;
    private final List<ModuleId> loadOrder;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    public ModuleLoader(Collection<? extends FulcrumModule> modules) {
        Map<ModuleId, FulcrumModule> modulesById = new HashMap<>();
        for (FulcrumModule module : modules) {
            ModuleId moduleId = module.descriptor().id();
            if (modulesById.putIfAbsent(moduleId, module) != null) {
                throw new ModuleGraphException("Duplicate module id encountered while creating loader: " + moduleId);
            }
        }

        this.modules = Map.copyOf(modulesById);
        this.loadOrder = ModuleGraph.build(this.modules.values().stream()
            .map(FulcrumModule::descriptor)
            .toList()).loadOrder();
    }

    public CompletionStage<Void> enableAll() {
        if (!state.compareAndSet(State.IDLE, State.ENABLING)) {
            return failedFuture("Modules are already enabled or mid-transition: " + state.get());
        }

        List<ModuleId> loadedModules = new ArrayList<>();
        CompletableFuture<Void> pipeline = CompletableFuture.completedFuture(null);
        for (ModuleId moduleId : loadOrder) {
            FulcrumModule module = modules.get(moduleId);
            pipeline = pipeline.thenCompose(ignored -> module.enable())
                .thenRun(() -> loadedModules.add(moduleId));
        }

        return pipeline.handle((ignored, throwable) -> {
            if (throwable == null) {
                state.set(State.ENABLED);
                return CompletableFuture.<Void>completedFuture(null);
            }

            state.set(State.IDLE);
            List<ModuleId> rollbackTargets = List.copyOf(loadedModules);
            return disableInReverse(rollbackTargets)
                .handle((rollbackIgnored, rollbackThrowable) -> {
                    if (rollbackThrowable != null) {
                        throwable.addSuppressed(rollbackThrowable);
                    }
                    return CompletableFuture.<Void>failedFuture(throwable);
                })
                .thenCompose(Function.identity());
        }).thenCompose(Function.identity());
    }

    public CompletionStage<Void> disableAll() {
        if (!state.compareAndSet(State.ENABLED, State.DISABLING)) {
            return failedFuture("Modules are not enabled: " + state.get());
        }

        return disableInReverse(loadOrder)
            .whenComplete((ignored, throwable) -> state.set(State.IDLE));
    }

    public Optional<FulcrumModule> module(ModuleId id) {
        return Optional.ofNullable(modules.get(id));
    }

    public List<ModuleId> loadOrder() {
        return loadOrder;
    }

    private CompletableFuture<Void> disableInReverse(List<ModuleId> ids) {
        List<ModuleId> reverseOrder = new ArrayList<>(ids);
        Collections.reverse(reverseOrder);

        CompletableFuture<Void> pipeline = CompletableFuture.completedFuture(null);
        for (ModuleId moduleId : reverseOrder) {
            FulcrumModule module = modules.get(moduleId);
            pipeline = pipeline.thenCompose(ignored -> module.disable());
        }
        return pipeline;
    }

    private CompletableFuture<Void> failedFuture(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }

    private enum State {
        IDLE,
        ENABLING,
        ENABLED,
        DISABLING
    }
}

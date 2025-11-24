package sh.harold.fulcrum.common.loader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ModuleLoader {

    private final Map<ModuleId, FulcrumModule> modules;
    private final List<ModuleId> loadOrder;
    private final ModuleGraph moduleGraph;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private List<ModuleId> enabledModules = List.of();

    public ModuleLoader(Collection<? extends FulcrumModule> modules) {
        Map<ModuleId, FulcrumModule> modulesById = new HashMap<>();
        for (FulcrumModule module : modules) {
            ModuleId moduleId = module.descriptor().id();
            if (modulesById.putIfAbsent(moduleId, module) != null) {
                throw new ModuleGraphException("Duplicate module id encountered while creating loader: " + moduleId);
            }
        }

        this.modules = Map.copyOf(modulesById);
        moduleGraph = ModuleGraph.build(this.modules.values().stream()
            .map(FulcrumModule::descriptor)
            .toList());
        loadOrder = moduleGraph.loadOrder();
    }

    public CompletionStage<Void> enableAll() {
        return enableAll(ModuleActivation.enableAll(modules.keySet()), skip -> {
        });
    }

    public CompletionStage<Void> enableAll(ModuleActivation activation, Consumer<SkippedModule> skipHandler) {
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(skipHandler, "skipHandler");
        if (!state.compareAndSet(State.IDLE, State.ENABLING)) {
            return failedFuture("Modules are already enabled or mid-transition: " + state.get());
        }

        List<ModuleId> activationOrder = determineActivationOrder(activation, skipHandler);
        List<ModuleId> loadedModules = new ArrayList<>();
        CompletableFuture<Void> result = new CompletableFuture<>();

        try {
            for (ModuleId moduleId : activationOrder) {
                FulcrumModule module = modules.get(moduleId);
                module.enable().toCompletableFuture().join();
                loadedModules.add(moduleId);
            }
            enabledModules = List.copyOf(loadedModules);
            state.set(State.ENABLED);
            result.complete(null);
        } catch (Throwable throwable) {
            List<ModuleId> rollbackTargets = List.copyOf(loadedModules);
            disableInReverse(rollbackTargets)
                .whenComplete((rollbackIgnored, rollbackThrowable) -> {
                    if (rollbackThrowable != null) {
                        throwable.addSuppressed(rollbackThrowable);
                    }
                    state.set(State.IDLE);
                    enabledModules = List.of();
                    result.completeExceptionally(throwable);
                });
        }

        return result;
    }

    public CompletionStage<Void> disableAll() {
        if (!state.compareAndSet(State.ENABLED, State.DISABLING)) {
            return failedFuture("Modules are not enabled: " + state.get());
        }

        List<ModuleId> toDisable = List.copyOf(enabledModules);
        return disableInReverse(toDisable)
            .whenComplete((ignored, throwable) -> {
                enabledModules = List.of();
                state.set(State.IDLE);
            });
    }

    public Optional<FulcrumModule> module(ModuleId id) {
        return Optional.ofNullable(modules.get(id));
    }

    public List<ModuleId> loadOrder() {
        return loadOrder;
    }

    public List<ModuleId> enabledModules() {
        return enabledModules;
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

    private List<ModuleId> determineActivationOrder(ModuleActivation activation, Consumer<SkippedModule> skipHandler) {
        Set<ModuleId> readyModules = new LinkedHashSet<>();
        for (ModuleId moduleId : loadOrder) {
            if (!activation.enabled(moduleId)) {
                skipHandler.accept(new SkippedModule(moduleId, "disabled in configuration"));
                continue;
            }

            List<ModuleId> missingDependencies = moduleGraph.dependenciesFor(moduleId).stream()
                .filter(dependency -> !readyModules.contains(dependency))
                .toList();
            if (!missingDependencies.isEmpty()) {
                skipHandler.accept(new SkippedModule(moduleId, "missing dependencies: " + missingDependencies));
                continue;
            }

            readyModules.add(moduleId);
        }
        return List.copyOf(readyModules);
    }

    public record SkippedModule(ModuleId moduleId, String reason) {
    }

    private enum State {
        IDLE,
        ENABLING,
        ENABLED,
        DISABLING
    }
}

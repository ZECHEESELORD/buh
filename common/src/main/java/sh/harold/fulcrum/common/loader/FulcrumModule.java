package sh.harold.fulcrum.common.loader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface FulcrumModule {

    ModuleDescriptor descriptor();

    CompletionStage<Void> enable();

    default CompletionStage<Void> disable() {
        return CompletableFuture.completedFuture(null);
    }
}

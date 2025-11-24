package sh.harold.fulcrum.common.loader;

import java.util.concurrent.CompletionStage;

/**
 * Marker for modules that can reload their configuration without a full restart.
 */
public interface ConfigurableModule extends FulcrumModule {

    CompletionStage<Void> reloadConfig();
}

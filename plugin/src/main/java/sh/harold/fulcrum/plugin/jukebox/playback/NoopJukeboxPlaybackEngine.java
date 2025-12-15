package sh.harold.fulcrum.plugin.jukebox.playback;

import org.bukkit.Location;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class NoopJukeboxPlaybackEngine implements JukeboxPlaybackEngine {

    private final String failureMessage;

    public NoopJukeboxPlaybackEngine(String failureMessage) {
        this.failureMessage = Objects.requireNonNull(failureMessage, "failureMessage");
    }

    @Override
    public CompletionStage<StartResult> start(Location location, String trackId) {
        return CompletableFuture.completedFuture(StartResult.failed(failureMessage));
    }

    @Override
    public boolean stop(Location location) {
        return false;
    }

    @Override
    public void stopAll() {
    }

    @Override
    public void close() {
    }
}


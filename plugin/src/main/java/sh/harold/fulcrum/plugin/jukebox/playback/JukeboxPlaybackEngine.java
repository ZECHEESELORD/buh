package sh.harold.fulcrum.plugin.jukebox.playback;

import org.bukkit.Location;

import java.util.concurrent.CompletionStage;

public interface JukeboxPlaybackEngine extends AutoCloseable {

    record StartResult(boolean started, String message) {
        public static StartResult ok() {
            return new StartResult(true, "");
        }

        public static StartResult failed(String message) {
            return new StartResult(false, message == null ? "" : message);
        }
    }

    CompletionStage<StartResult> start(Location location, String trackId);

    boolean stop(Location location);

    void stopAll();

    @Override
    void close();
}


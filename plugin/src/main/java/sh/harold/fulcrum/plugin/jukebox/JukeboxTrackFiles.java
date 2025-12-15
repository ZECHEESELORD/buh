package sh.harold.fulcrum.plugin.jukebox;

import java.nio.file.Path;
import java.util.Objects;

public record JukeboxTrackFiles(Path pcmPath, Path jsonPath) {

    public JukeboxTrackFiles {
        Objects.requireNonNull(pcmPath, "pcmPath");
        Objects.requireNonNull(jsonPath, "jsonPath");
    }

    public static JukeboxTrackFiles forTrack(Path tracksDirectory, String trackId) {
        Objects.requireNonNull(tracksDirectory, "tracksDirectory");
        Objects.requireNonNull(trackId, "trackId");
        return new JukeboxTrackFiles(
            tracksDirectory.resolve(trackId + ".pcm"),
            tracksDirectory.resolve(trackId + ".json")
        );
    }
}


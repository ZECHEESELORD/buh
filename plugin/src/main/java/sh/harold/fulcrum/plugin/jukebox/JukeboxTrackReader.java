package sh.harold.fulcrum.plugin.jukebox;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class JukeboxTrackReader {

    private final ObjectMapper objectMapper;

    public JukeboxTrackReader() {
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Optional<JukeboxTrackMetadata> read(Path jsonPath) throws IOException {
        Objects.requireNonNull(jsonPath, "jsonPath");
        if (!Files.exists(jsonPath)) {
            return Optional.empty();
        }
        return Optional.ofNullable(objectMapper.readValue(jsonPath.toFile(), JukeboxTrackMetadata.class));
    }
}


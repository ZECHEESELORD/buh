package sh.harold.fulcrum.plugin.jukebox.mint;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class JukeboxTokenStore {

    private final Path tokenDirectory;
    private final ObjectMapper objectMapper;

    public JukeboxTokenStore(Path tokenDirectory) {
        this.tokenDirectory = Objects.requireNonNull(tokenDirectory, "tokenDirectory");
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<JukeboxMintTokenFile> load(String trackId) throws IOException {
        Objects.requireNonNull(trackId, "trackId");
        Path tokenFile = tokenFile(trackId);
        if (!Files.exists(tokenFile)) {
            return Optional.empty();
        }
        return Optional.ofNullable(objectMapper.readValue(tokenFile.toFile(), JukeboxMintTokenFile.class));
    }

    public void save(JukeboxMintTokenFile tokenFile) throws IOException {
        Objects.requireNonNull(tokenFile, "tokenFile");
        Files.createDirectories(tokenDirectory);
        Path file = tokenFile(tokenFile.trackId());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        String json = objectMapper.writeValueAsString(tokenFile);
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public boolean delete(String trackId) throws IOException {
        Objects.requireNonNull(trackId, "trackId");
        return Files.deleteIfExists(tokenFile(trackId));
    }

    public Path tokenFile(String trackId) {
        Objects.requireNonNull(trackId, "trackId");
        return tokenDirectory.resolve(trackId + ".json");
    }
}


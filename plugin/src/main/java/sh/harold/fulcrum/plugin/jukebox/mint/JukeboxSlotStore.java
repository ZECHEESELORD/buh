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
import java.util.UUID;

public final class JukeboxSlotStore {

    private final Path slotsDirectory;
    private final ObjectMapper objectMapper;

    public JukeboxSlotStore(Path slotsDirectory) {
        this.slotsDirectory = Objects.requireNonNull(slotsDirectory, "slotsDirectory");
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public JukeboxPlayerSlots loadOrCreate(UUID ownerUuid, int slotCount) throws IOException {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Path file = slotsFile(ownerUuid);
        if (!Files.exists(file)) {
            JukeboxPlayerSlots created = JukeboxPlayerSlots.empty(ownerUuid, slotCount);
            save(created);
            return created;
        }
        JukeboxPlayerSlots loaded = objectMapper.readValue(file.toFile(), JukeboxPlayerSlots.class);
        if (loaded == null) {
            JukeboxPlayerSlots created = JukeboxPlayerSlots.empty(ownerUuid, slotCount);
            save(created);
            return created;
        }
        if (!ownerUuid.equals(loaded.ownerUuid())) {
            throw new IOException("Slot file owner mismatch");
        }
        JukeboxPlayerSlots resized = loaded.resized(slotCount);
        if (resized != loaded) {
            save(resized);
        }
        return resized;
    }

    public void save(JukeboxPlayerSlots slots) throws IOException {
        Objects.requireNonNull(slots, "slots");
        Files.createDirectories(slotsDirectory);
        Path file = slotsFile(slots.ownerUuid());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        String json = objectMapper.writeValueAsString(slots);
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public Path slotsFile(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return slotsDirectory.resolve(ownerUuid + ".json");
    }
}


package sh.harold.fulcrum.plugin.jukebox.mint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JukeboxPlayerSlots(
    @JsonProperty("schemaVersion") int schemaVersion,
    @JsonProperty("ownerUuid") UUID ownerUuid,
    @JsonProperty("slots") List<String> slots
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public JukeboxPlayerSlots {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        slots = slots == null ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public static JukeboxPlayerSlots empty(UUID ownerUuid, int slotCount) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        int count = Math.max(1, slotCount);
        List<String> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            slots.add(null);
        }
        return new JukeboxPlayerSlots(CURRENT_SCHEMA_VERSION, ownerUuid, slots);
    }

    public JukeboxPlayerSlots resized(int slotCount) {
        int count = Math.max(1, slotCount);
        if (slots.size() == count) {
            return this;
        }
        List<String> adjusted = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            adjusted.add(index < slots.size() ? slots.get(index) : null);
        }
        return new JukeboxPlayerSlots(schemaVersion, ownerUuid, adjusted);
    }
}

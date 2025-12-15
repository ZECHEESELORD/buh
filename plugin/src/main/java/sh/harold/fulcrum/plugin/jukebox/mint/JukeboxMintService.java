package sh.harold.fulcrum.plugin.jukebox.mint;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import sh.harold.fulcrum.plugin.jukebox.JukeboxConfig;
import sh.harold.fulcrum.plugin.jukebox.JukeboxPcmFormat;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackFiles;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackMetadata;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackReader;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JukeboxMintService implements AutoCloseable {

    public record MintResult(
        boolean minted,
        String message,
        int slotIndex,
        String trackId,
        String uploadUrl
    ) {
        public static MintResult failed(String message) {
            return new MintResult(false, message == null ? "" : message, -1, "", "");
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Logger logger;
    private final JukeboxConfig config;
    private final JukeboxSlotStore slotStore;
    private final JukeboxTokenStore tokenStore;
    private final JukeboxTrackReader trackReader;
    private final ObjectMapper objectMapper;
    private final ExecutorService ioExecutor;

    public JukeboxMintService(Logger logger, JukeboxConfig config) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.slotStore = new JukeboxSlotStore(config.slotsDirectory());
        this.tokenStore = new JukeboxTokenStore(config.tokenDirectory());
        this.trackReader = new JukeboxTrackReader();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public CompletionStage<JukeboxPlayerSlots> loadSlots(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return slotStore.loadOrCreate(ownerUuid, config.slotCount());
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load slots", exception);
            }
        }, ioExecutor);
    }

    public CompletionStage<MintResult> mint(UUID ownerUuid, int slotIndex) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return CompletableFuture.supplyAsync(() -> mintSync(ownerUuid, slotIndex), ioExecutor)
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Mint failed for " + ownerUuid, throwable);
                return MintResult.failed("Could not start minting.");
            });
    }

    public CompletionStage<Boolean> clearSlot(UUID ownerUuid, int slotIndex) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return CompletableFuture.supplyAsync(() -> clearSlotSync(ownerUuid, slotIndex), ioExecutor)
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to clear slot " + slotIndex + " for " + ownerUuid, throwable);
                return false;
            });
    }

    @Override
    public void close() {
        ioExecutor.close();
    }

    private MintResult mintSync(UUID ownerUuid, int slotIndex) {
        JukeboxPlayerSlots slots;
        try {
            slots = slotStore.loadOrCreate(ownerUuid, config.slotCount());
        } catch (IOException exception) {
            return MintResult.failed("Slot storage is unavailable.");
        }

        if (slotIndex < 0 || slotIndex >= slots.slots().size()) {
            return MintResult.failed("That slot does not exist.");
        }
        String current = slots.slots().get(slotIndex);
        if (current != null && !current.isBlank()) {
            return MintResult.failed("That slot is already in use.");
        }

        ActiveMint activeMint = findActiveMint(ownerUuid, slots).orElse(null);
        if (activeMint != null) {
            return MintResult.failed("You already have a mint in progress.");
        }

        String trackId = UUID.randomUUID().toString();
        String token = generateToken();
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long expiresAt = nowEpochSeconds + config.tokenTtl().toSeconds();

        JukeboxMintTokenFile tokenFile = new JukeboxMintTokenFile(
            JukeboxMintTokenFile.CURRENT_SCHEMA_VERSION,
            trackId,
            ownerUuid,
            token,
            nowEpochSeconds,
            expiresAt,
            null
        );

        try {
            tokenStore.save(tokenFile);
        } catch (IOException exception) {
            return MintResult.failed("Could not create an upload token.");
        }

        JukeboxTrackMetadata metadata = new JukeboxTrackMetadata(
            1,
            trackId,
            ownerUuid,
            JukeboxTrackStatus.WAITING_UPLOAD,
            Instant.now().toString(),
            null,
            JukeboxPcmFormat.SAMPLE_RATE_HZ,
            JukeboxPcmFormat.CHANNELS,
            JukeboxPcmFormat.SAMPLE_FORMAT,
            JukeboxPcmFormat.BYTES_PER_SAMPLE,
            JukeboxPcmFormat.FRAME_SAMPLES_PER_CHANNEL,
            JukeboxPcmFormat.FRAME_BYTES,
            0L,
            0L,
            null
        );

        try {
            writeTrackMetadata(metadata);
        } catch (IOException exception) {
            try {
                tokenStore.delete(trackId);
            } catch (IOException ignored) {
            }
            return MintResult.failed("Could not create a track record.");
        }

        List<String> updatedSlots = new ArrayList<>(slots.slots());
        updatedSlots.set(slotIndex, trackId);
        try {
            slotStore.save(new JukeboxPlayerSlots(slots.schemaVersion(), ownerUuid, updatedSlots));
        } catch (IOException exception) {
            try {
                tokenStore.delete(trackId);
                deleteTrackFiles(trackId);
            } catch (IOException ignored) {
            }
            return MintResult.failed("Could not reserve a slot.");
        }

        String uploadUrl = uploadUrl(trackId, token);
        return new MintResult(true, "", slotIndex, trackId, uploadUrl);
    }

    private boolean clearSlotSync(UUID ownerUuid, int slotIndex) {
        JukeboxPlayerSlots slots;
        try {
            slots = slotStore.loadOrCreate(ownerUuid, config.slotCount());
        } catch (IOException exception) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= slots.slots().size()) {
            return false;
        }
        String trackId = slots.slots().get(slotIndex);
        if (trackId == null || trackId.isBlank()) {
            return true;
        }

        List<String> updatedSlots = new ArrayList<>(slots.slots());
        updatedSlots.set(slotIndex, null);
        try {
            slotStore.save(new JukeboxPlayerSlots(slots.schemaVersion(), ownerUuid, updatedSlots));
            tokenStore.delete(trackId);
            deleteTrackFiles(trackId);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private Optional<ActiveMint> findActiveMint(UUID ownerUuid, JukeboxPlayerSlots slots) {
        long now = Instant.now().getEpochSecond();
        for (int index = 0; index < slots.slots().size(); index++) {
            String trackId = slots.slots().get(index);
            if (trackId == null || trackId.isBlank()) {
                continue;
            }
            if (!isTrackInProgress(ownerUuid, trackId, now)) {
                continue;
            }
            return Optional.of(new ActiveMint(index, trackId));
        }
        return Optional.empty();
    }

    private boolean isTrackInProgress(UUID ownerUuid, String trackId, long nowEpochSeconds) {
        JukeboxTrackFiles files = JukeboxTrackFiles.forTrack(config.tracksDirectory(), trackId);
        JukeboxTrackMetadata metadata;
        try {
            metadata = trackReader.read(files.jsonPath()).orElse(null);
        } catch (IOException exception) {
            return false;
        }
        if (metadata == null || !ownerUuid.equals(metadata.ownerUuid())) {
            return false;
        }
        if (metadata.status() == JukeboxTrackStatus.PROCESSING) {
            return true;
        }
        if (metadata.status() != JukeboxTrackStatus.WAITING_UPLOAD) {
            return false;
        }
        try {
            JukeboxMintTokenFile token = tokenStore.load(trackId).orElse(null);
            if (token == null || token.isUsed()) {
                return false;
            }
            return !token.isExpired(nowEpochSeconds);
        } catch (IOException exception) {
            return false;
        }
    }

    private void writeTrackMetadata(JukeboxTrackMetadata metadata) throws IOException {
        Path tracksDirectory = config.tracksDirectory();
        Files.createDirectories(tracksDirectory);
        Path jsonPath = JukeboxTrackFiles.forTrack(tracksDirectory, metadata.trackId()).jsonPath();
        Path tempPath = jsonPath.resolveSibling(jsonPath.getFileName() + ".tmp");
        String json = objectMapper.writeValueAsString(metadata);
        Files.writeString(tempPath, json, StandardCharsets.UTF_8);
        Files.move(tempPath, jsonPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void deleteTrackFiles(String trackId) throws IOException {
        JukeboxTrackFiles files = JukeboxTrackFiles.forTrack(config.tracksDirectory(), trackId);
        Files.deleteIfExists(files.jsonPath());
        Files.deleteIfExists(files.pcmPath());
    }

    private String uploadUrl(String trackId, String token) {
        String template = config.uploadUrlTemplate();
        return template
            .replace("{trackId}", trackId)
            .replace("{token}", token);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record ActiveMint(int slotIndex, String trackId) {
    }
}

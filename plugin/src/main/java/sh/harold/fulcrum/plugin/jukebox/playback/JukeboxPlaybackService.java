package sh.harold.fulcrum.plugin.jukebox.playback;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.jukebox.JukeboxConfig;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackFiles;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackMetadata;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackReader;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackValidation;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackValidator;
import sh.harold.fulcrum.plugin.jukebox.voicechat.JukeboxVoicechatApi;
import sh.harold.fulcrum.plugin.jukebox.voicechat.JukeboxVoicechatPlugin;
import sh.harold.fulcrum.plugin.jukebox.voicechat.VoicechatBukkitBridge;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JukeboxPlaybackService implements JukeboxPlaybackEngine {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final JukeboxConfig config;
    private final JukeboxVoicechatApi voicechatApi;
    private final JukeboxTrackReader trackReader;
    private final ExecutorService ioExecutor;
    private final Map<JukeboxPlaybackKey, JukeboxPlaybackSession> sessions;

    public JukeboxPlaybackService(JavaPlugin plugin, JukeboxConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.config = Objects.requireNonNull(config, "config");
        this.voicechatApi = new JukeboxVoicechatApi();
        this.trackReader = new JukeboxTrackReader();
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.sessions = new ConcurrentHashMap<>();
        registerVoicechatPlugin();
    }

    private void registerVoicechatPlugin() {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            logger.warning("jukebox: Simple Voice Chat not found; playback will be unavailable");
            return;
        }
        try {
            service.registerPlugin(new JukeboxVoicechatPlugin(voicechatApi));
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "jukebox: Failed to register Simple Voice Chat plugin", throwable);
        }
    }

    public CompletionStage<StartResult> start(Location location, String trackId) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(trackId, "trackId");
        if (!voicechatApi.isReady()) {
            return CompletableFuture.completedFuture(StartResult.failed("Voice chat is not available."));
        }
        return CompletableFuture.supplyAsync(() -> loadTrack(trackId), ioExecutor)
            .thenCompose(load -> runOnMainThread(() -> startSync(location, load)))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to start jukebox playback for " + trackId, throwable);
                return StartResult.failed("Could not start playback.");
            });
    }

    public boolean stop(Location location) {
        Objects.requireNonNull(location, "location");
        return stop(JukeboxPlaybackKey.fromLocation(location));
    }

    public boolean stop(JukeboxPlaybackKey key) {
        Objects.requireNonNull(key, "key");
        JukeboxPlaybackSession session = sessions.remove(key);
        if (session == null) {
            return false;
        }
        stopSession(session);
        return true;
    }

    public void stopAll() {
        sessions.values().forEach(this::stopSession);
        sessions.clear();
    }

    @Override
    public void close() {
        stopAll();
        voicechatApi.clear();
        ioExecutor.close();
    }

    private TrackLoad loadTrack(String trackId) {
        JukeboxTrackFiles files = JukeboxTrackFiles.forTrack(config.tracksDirectory(), trackId);
        Optional<JukeboxTrackMetadata> metadata;
        try {
            metadata = trackReader.read(files.jsonPath());
        } catch (IOException exception) {
            return new TrackLoad(trackId, null, files, JukeboxTrackValidation.invalid("Track metadata unreadable."));
        }
        if (metadata.isEmpty()) {
            return new TrackLoad(trackId, null, files, JukeboxTrackValidation.invalid("Track metadata missing."));
        }
        JukeboxTrackMetadata resolved = metadata.get();
        if (!trackId.equals(resolved.trackId())) {
            return new TrackLoad(trackId, resolved, files, JukeboxTrackValidation.invalid("Track ID mismatch."));
        }
        return new TrackLoad(trackId, resolved, files, JukeboxTrackValidator.validateReady(resolved, config, files.pcmPath()));
    }

    private StartResult startSync(Location location, TrackLoad load) {
        if (!load.validation.valid()) {
            return StartResult.failed(load.validation.message());
        }
        VoicechatServerApi api = voicechatApi.serverApi().orElse(null);
        if (api == null) {
            return StartResult.failed("Voice chat is not ready yet.");
        }
        World world = location.getWorld();
        if (world == null) {
            return StartResult.failed("No world available for playback.");
        }
        JukeboxPlaybackKey key = JukeboxPlaybackKey.fromLocation(location);
        stop(key);

        ServerLevel serverLevel;
        try {
            serverLevel = VoicechatBukkitBridge.serverLevel(api, world);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to adapt world for voice chat", exception);
            return StartResult.failed("Voice chat world adapter failed.");
        }

        double x = location.getBlockX() + 0.5D;
        double y = location.getBlockY() + 0.5D;
        double z = location.getBlockZ() + 0.5D;

        LocationalAudioChannel channel = api.createLocationalAudioChannel(UUID.randomUUID(), serverLevel, api.createPosition(x, y, z));
        channel.setDistance((float) config.audibleRadiusBlocks());

        OpusEncoder encoder = api.createEncoder(OpusEncoderMode.AUDIO);
        PcmTrackStream stream = new PcmTrackStream(load.files.pcmPath(), 50, config.fadeIn());
        stream.start();

        AudioPlayer player = api.createAudioPlayer(channel, encoder, stream);
        JukeboxPlaybackSession session = new JukeboxPlaybackSession(load.trackId, channel, player, encoder, stream);
        sessions.put(key, session);
        player.setOnStopped(() -> plugin.getServer().getScheduler().runTask(plugin, () -> handleStopped(key, session)));
        player.startPlaying();
        return StartResult.ok();
    }

    private void handleStopped(JukeboxPlaybackKey key, JukeboxPlaybackSession session) {
        JukeboxPlaybackSession current = sessions.get(key);
        if (current != session) {
            return;
        }
        sessions.remove(key);
        stopSession(session, false);
    }

    private void stopSession(JukeboxPlaybackSession session) {
        stopSession(session, true);
    }

    private void stopSession(JukeboxPlaybackSession session, boolean stopPlayer) {
        if (stopPlayer) {
            try {
                session.player().stopPlaying();
            } catch (Throwable throwable) {
                logger.log(Level.FINE, "Failed to stop audio player", throwable);
            }
        }
        try {
            session.stream().close();
        } catch (Throwable throwable) {
            logger.log(Level.FINE, "Failed to close PCM stream", throwable);
        }
        try {
            session.encoder().close();
        } catch (Throwable throwable) {
            logger.log(Level.FINE, "Failed to close opus encoder", throwable);
        }
        try {
            session.channel().flush();
        } catch (Throwable throwable) {
            logger.log(Level.FINE, "Failed to flush audio channel", throwable);
        }
    }

    private <T> CompletionStage<T> runOnMainThread(java.util.concurrent.Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(action.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private record TrackLoad(String trackId, JukeboxTrackMetadata metadata, JukeboxTrackFiles files, JukeboxTrackValidation validation) {
    }
}

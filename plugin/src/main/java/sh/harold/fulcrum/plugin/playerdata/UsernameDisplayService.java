package sh.harold.fulcrum.plugin.playerdata;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.common.data.DataApi;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UsernameDisplayService implements Listener {

    private static final int CUSTOM_NAME_INDEX = 2;
    private static final int CUSTOM_NAME_VISIBILITY_INDEX = 3;
    private static final int NO_GRAVITY_INDEX = 5;
    private static final long HEALTH_REFRESH_INTERVAL_MILLIS = 500L;
    private static final double CARRIER_Y_OFFSET = 2.25D;

    private final Plugin plugin;
    private final Logger logger;
    private final PlayerSettingsService settingsService;
    private final LinkedAccountService linkedAccountService;
    private final UsernameBaseNameResolver baseNameResolver;
    private final TabNameDecorator tabNameDecorator;
    private final NametagDecorator nametagDecorator;
    private final ChatNameDecorator chatNameDecorator;
    private final Map<UUID, String> vanillaNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentHealthRefresh = new ConcurrentHashMap<>();
    private final AtomicInteger nextCarrierEntityId = new AtomicInteger(Integer.MAX_VALUE);
    private final Map<UUID, ViewerNametagState> viewerNametagStates = new ConcurrentHashMap<>();
    private final boolean debug = false;

    public UsernameDisplayService(Plugin plugin, DataApi dataApi, PlayerSettingsService settingsService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.linkedAccountService = new LinkedAccountService(Objects.requireNonNull(dataApi, "dataApi"), logger);
        this.baseNameResolver = new UsernameBaseNameResolver(settingsService, linkedAccountService);
        this.tabNameDecorator = new TabNameDecorator(settingsService);
        this.nametagDecorator = new NametagDecorator(settingsService);
        this.chatNameDecorator = new ChatNameDecorator(settingsService);

        plugin.getServer().getOnlinePlayers().forEach(this::track);

        registerPacketAdapters();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
        settingsService.loadSettings(event.getPlayer().getUniqueId())
            .thenRun(() -> refreshView(event.getPlayer()))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to warm username view for " + event.getPlayer().getUniqueId(), throwable);
                return null;
            });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        settingsService.evictCachedSettings(playerId);
        linkedAccountService.evict(playerId);
        vanillaNames.remove(playerId);
        recentHealthRefresh.remove(playerId);
        viewerNametagStates.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        refreshHealthIfPeaceful(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        refreshHealthIfPeaceful(event.getEntity());
    }

    public void track(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        vanillaNames.put(playerId, player.getName());
        linkedAccountService.refresh(playerId);
    }

    public Component displayComponent(UUID viewerId, Player target, TextColor color) {
        TextColor resolvedColor = color == null ? NamedTextColor.WHITE : color;
        UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(viewerId, target.getUniqueId(), target.getName());
        return chatNameDecorator.decorateForChat(target.getUniqueId(), baseName.component(), resolvedColor);
    }

    public void refreshView(Player viewer) {
        if (viewer == null) {
            return;
        }
        UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sendTabUpdate(viewer, preference);
            refreshNametagView(viewer, preference);
        });
    }

    private void registerPacketAdapters() {
        PacketEvents.getAPI().getEventManager().registerListener(new PlayerInfoPacketListener());
        PacketEvents.getAPI().getEventManager().registerListener(new TeamsPacketListener());
        PacketEvents.getAPI().getEventManager().registerListener(new SpawnPlayerPacketListener());
        PacketEvents.getAPI().getEventManager().registerListener(new DestroyEntitiesPacketListener());
        PacketEvents.getAPI().getEventManager().registerListener(new MovementPacketListener());
    }

    private void sendTabUpdate(Player viewer, UsernameView preference) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = plugin.getServer().getOnlinePlayers().stream()
            .filter(target -> target.equals(viewer) || viewer.canSee(target))
            .map(target -> {
                UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(preference, target.getUniqueId(), target.getName());
                Component decorated = tabNameDecorator.decorateForTab(target.getUniqueId(), target, baseName.component());
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(target.getUniqueId());
                info.setDisplayName(decorated);
                return info;
            })
            .toList();
        if (entries.isEmpty()) {
            return;
        }
        var packet = new WrapperPlayServerPlayerInfoUpdate(
            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
            entries
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void refreshNametagView(Player viewer, UsernameView preference) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        ViewerNametagState state = viewerNametagStates.computeIfAbsent(viewer.getUniqueId(), ViewerNametagState::new);
        for (Map.Entry<Integer, TrackedPlayer> entry : state.trackedPlayers.entrySet()) {
            int targetEntityId = entry.getKey();
            TrackedPlayer tracked = entry.getValue();
            if (tracked == null) {
                continue;
            }
            applyViewerNametag(viewer, state, targetEntityId, tracked, preference);
        }
    }

    private void applyViewerNametag(
        Player viewer,
        ViewerNametagState state,
        int targetEntityId,
        TrackedPlayer tracked,
        UsernameView preference
    ) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = tracked.playerId();

        if (targetId == null || targetId.equals(viewerId)) {
            destroyCarrier(viewer, state, targetEntityId);
            return;
        }

        String vanillaName = tracked.vanillaName();
        if (vanillaName == null || vanillaName.isBlank()) {
            vanillaName = vanillaNames.get(targetId);
        }
        if (vanillaName == null || vanillaName.isBlank()) {
            destroyCarrier(viewer, state, targetEntityId);
            return;
        }

        boolean hasDecorations = nametagDecorator.hasNametagDecorations(targetId);
        UsernameView resolvedView = preference == null ? UsernameView.MINECRAFT : preference;
        UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(resolvedView, targetId, vanillaName);
        boolean aliasDiffers = !Objects.equals(baseName.value(), vanillaName);

        boolean shouldOverride = resolvedView == UsernameView.MINECRAFT
            ? hasDecorations
            : hasDecorations || aliasDiffers;

        if (!shouldOverride) {
            destroyCarrier(viewer, state, targetEntityId);
            return;
        }

        Vector3d position = tracked.position();
        if (position == null) {
            return;
        }

        Component decorated = nametagDecorator.decorateForNametag(targetId, baseName.component());
        ensureCarrier(viewer, state, targetEntityId, vanillaName, position, decorated);
    }

    private void ensureCarrier(
        Player viewer,
        ViewerNametagState state,
        int targetEntityId,
        String targetEntryName,
        Vector3d targetPosition,
        Component displayName
    ) {
        CarrierEntity existing = state.carriers.get(targetEntityId);
        if (existing == null) {
            ensureHiddenNametag(viewer, state, targetEntryName);
            int carrierEntityId = nextCarrierEntityId.getAndDecrement();
            UUID carrierId = UUID.randomUUID();
            Vector3d position = targetPosition.add(0.0D, CARRIER_Y_OFFSET, 0.0D);
            var spawn = new WrapperPlayServerSpawnEntity(
                carrierEntityId,
                Optional.of(carrierId),
                EntityTypes.INTERACTION,
                position,
                0.0F,
                0.0F,
                0.0F,
                0,
                Optional.empty()
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);
            sendCarrierMetadata(viewer, carrierEntityId, displayName);
            state.carriers.put(targetEntityId, new CarrierEntity(carrierEntityId, targetEntryName, displayName));
            return;
        }

        if (!Objects.equals(existing.lastDisplayName(), displayName)) {
            sendCarrierMetadata(viewer, existing.entityId(), displayName);
            state.carriers.put(targetEntityId, new CarrierEntity(existing.entityId(), existing.targetEntryName(), displayName));
        }
    }

    private void destroyCarrier(Player viewer, ViewerNametagState state, int targetEntityId) {
        CarrierEntity carrier = state.carriers.remove(targetEntityId);
        if (carrier == null) {
            return;
        }
        releaseHiddenNametag(viewer, state, carrier.targetEntryName());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(carrier.entityId()));
    }

    private void sendCarrierMetadata(Player viewer, int carrierEntityId, Component displayName) {
        List<EntityData<?>> metadata = List.of(
            new EntityData<>(CUSTOM_NAME_INDEX, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.ofNullable(displayName)),
            new EntityData<>(CUSTOM_NAME_VISIBILITY_INDEX, EntityDataTypes.BOOLEAN, true),
            new EntityData<>(NO_GRAVITY_INDEX, EntityDataTypes.BOOLEAN, true)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(carrierEntityId, metadata));
    }

    private void ensureHiddenNametag(Player viewer, ViewerNametagState state, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return;
        }
        if (!state.hiddenEntries.add(entryName)) {
            return;
        }
        ensureHideTeam(viewer, state);
        var packet = new WrapperPlayServerTeams(
            state.hideTeamName,
            WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
            Optional.empty(),
            List.of(entryName)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void releaseHiddenNametag(Player viewer, ViewerNametagState state, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return;
        }
        if (!state.hiddenEntries.remove(entryName)) {
            return;
        }
        var packet = new WrapperPlayServerTeams(
            state.hideTeamName,
            WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
            Optional.empty(),
            List.of(entryName)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void ensureHideTeam(Player viewer, ViewerNametagState state) {
        if (!state.hideTeamCreated.compareAndSet(false, true)) {
            return;
        }
        var info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.empty(),
            Component.empty(),
            Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.NEVER,
            WrapperPlayServerTeams.CollisionRule.ALWAYS,
            NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.NONE
        );
        var packet = new WrapperPlayServerTeams(
            state.hideTeamName,
            WrapperPlayServerTeams.TeamMode.CREATE,
            info,
            List.of()
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void refreshHealthIfPeaceful(Entity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (settingsService.cachedPvpEnabled(playerId)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = recentHealthRefresh.get(playerId);
        if (last != null && now - last < HEALTH_REFRESH_INTERVAL_MILLIS) {
            return;
        }
        recentHealthRefresh.put(playerId, now);
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getServer().getOnlinePlayers().forEach(this::refreshView));
    }

    private void debug(Supplier<String> message) {
        if (!debug) {
            return;
        }
        try {
            logger.info("[username-debug] " + message.get());
        } catch (RuntimeException runtimeException) {
            logger.warning("Failed to render debug log: " + runtimeException.getMessage());
        }
    }

    private final class PlayerInfoPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            Object handle = event.getPlayer();
            if (!(handle instanceof Player viewer)) {
                return;
            }

            UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
            if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
                handleInfoUpdate(event, viewer, preference);
            } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
                handleLegacyInfo(event, viewer, preference);
            }
        }

        private void handleInfoUpdate(PacketSendEvent event, Player viewer, UsernameView preference) {
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = packet.getEntries();
            if (entries == null || entries.isEmpty()) {
                return;
            }

            boolean mutated = false;
            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo data : entries) {
                if (data == null) {
                    continue;
                }
                UUID targetId = data.getProfileId();
                if (targetId == null && data.getGameProfile() != null) {
                    targetId = data.getGameProfile().getUUID();
                }
                Player target = targetId == null ? null : plugin.getServer().getPlayer(targetId);
                String vanillaName = resolveVanillaName(data, targetId, target);

                if (preference == UsernameView.MINECRAFT && !tabNameDecorator.hasTabDecorations(targetId, target)) {
                    continue;
                }

                UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(preference, targetId, vanillaName);
                if (preference == UsernameView.MINECRAFT
                    && Objects.equals(baseName.value(), vanillaName)
                    && !tabNameDecorator.hasTabDecorations(targetId, target)) {
                    continue;
                }

                Component decorated = tabNameDecorator.decorateForTab(targetId, target, baseName.component());
                data.setDisplayName(decorated);
                mutated = true;
            }

            if (mutated) {
                EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = packet.getActions();
                if (actions == null || !actions.contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)) {
                    EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> updatedActions = (actions == null || actions.isEmpty())
                        ? EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)
                        : EnumSet.copyOf(actions);
                    updatedActions.add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
                    packet.setActions(updatedActions);
                }
                packet.setEntries(entries);
                debug(() -> "Rewrote player info for viewer " + viewer.getUniqueId() + " (" + entries.size() + " entries)");
            }
        }

        private void handleLegacyInfo(PacketSendEvent event, Player viewer, UsernameView preference) {
            WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo(event);
            List<WrapperPlayServerPlayerInfo.PlayerData> entries = packet.getPlayerDataList();
            if (entries == null || entries.isEmpty()) {
                return;
            }

            boolean mutated = false;
            for (WrapperPlayServerPlayerInfo.PlayerData data : entries) {
                if (data == null || data.getUserProfile() == null) {
                    continue;
                }
                UUID targetId = data.getUserProfile().getUUID();
                Player target = targetId == null ? null : plugin.getServer().getPlayer(targetId);
                String vanillaName = target != null ? target.getName() : data.getUserProfile().getName();

                if (preference == UsernameView.MINECRAFT && !tabNameDecorator.hasTabDecorations(targetId, target)) {
                    continue;
                }

                UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(preference, targetId, vanillaName);
                if (preference == UsernameView.MINECRAFT
                    && Objects.equals(baseName.value(), vanillaName)
                    && !tabNameDecorator.hasTabDecorations(targetId, target)) {
                    continue;
                }

                Component decorated = tabNameDecorator.decorateForTab(targetId, target, baseName.component());
                data.setDisplayName(decorated);
                mutated = true;
            }

            if (mutated) {
                packet.setPlayerDataList(entries);
                debug(() -> "Rewrote legacy player info for viewer " + viewer.getUniqueId() + " (" + entries.size() + " entries)");
            }
        }

        private String resolveVanillaName(
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo data,
            UUID targetId,
            Player target
        ) {
            if (target != null) {
                return target.getName();
            }
            if (data.getGameProfile() != null
                && data.getGameProfile().getName() != null
                && !data.getGameProfile().getName().isBlank()) {
                return data.getGameProfile().getName();
            }
            if (targetId != null) {
                String cached = vanillaNames.get(targetId);
                if (cached != null && !cached.isBlank()) {
                    return cached;
                }
            }
            return null;
        }
    }

    private static final class ViewerNametagState {
        private final String hideTeamName;
        private final AtomicBoolean hideTeamCreated = new AtomicBoolean();
        private final Map<Integer, TrackedPlayer> trackedPlayers = new ConcurrentHashMap<>();
        private final Map<Integer, CarrierEntity> carriers = new ConcurrentHashMap<>();
        private final Set<String> hiddenEntries = ConcurrentHashMap.newKeySet();

        private ViewerNametagState(UUID viewerId) {
            String compact = viewerId == null ? "" : viewerId.toString().replace("-", "");
            this.hideTeamName = "flc_ud_" + (compact.length() >= 8 ? compact.substring(0, 8) : Integer.toHexString(Objects.hashCode(viewerId)));
        }
    }

    private record TrackedPlayer(UUID playerId, String vanillaName, Vector3d position) {
    }

    private record CarrierEntity(int entityId, String targetEntryName, Component lastDisplayName) {
    }

    private final class TeamsPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.TEAMS) {
                return;
            }
            Object handle = event.getPlayer();
            if (!(handle instanceof Player viewer)) {
                return;
            }

            ViewerNametagState state = viewerNametagStates.get(viewer.getUniqueId());
            if (state == null || state.hiddenEntries.isEmpty()) {
                return;
            }

            WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
            String teamName = packet.getTeamName();
            if (teamName == null || teamName.equals(state.hideTeamName)) {
                return;
            }

            Set<String> hidden = state.hiddenEntries;
            var players = packet.getPlayers();
            if (players == null || players.isEmpty()) {
                return;
            }

            List<String> filtered = players.stream()
                .filter(entryName -> !hidden.contains(entryName))
                .toList();
            if (filtered.size() == players.size()) {
                return;
            }
            packet.setPlayers(filtered);
        }
    }

    private final class SpawnPlayerPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.SPAWN_PLAYER) {
                return;
            }
            Object handle = event.getPlayer();
            if (!(handle instanceof Player viewer)) {
                return;
            }

            WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
            int entityId = packet.getEntityId();
            UUID targetId = packet.getUUID();
            Vector3d position = packet.getPosition();
            String vanillaName = targetId == null ? null : vanillaNames.get(targetId);

            ViewerNametagState state = viewerNametagStates.computeIfAbsent(viewer.getUniqueId(), ViewerNametagState::new);
            TrackedPlayer tracked = new TrackedPlayer(targetId, vanillaName, position);
            state.trackedPlayers.put(entityId, tracked);

            UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
            applyViewerNametag(viewer, state, entityId, tracked, preference);
        }
    }

    private final class DestroyEntitiesPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.DESTROY_ENTITIES) {
                return;
            }
            Object handle = event.getPlayer();
            if (!(handle instanceof Player viewer)) {
                return;
            }

            ViewerNametagState state = viewerNametagStates.get(viewer.getUniqueId());
            if (state == null) {
                return;
            }

            WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
            int[] ids = packet.getEntityIds();
            if (ids == null || ids.length == 0) {
                return;
            }

            for (int entityId : ids) {
                state.trackedPlayers.remove(entityId);
                destroyCarrier(viewer, state, entityId);
            }
        }
    }

    private final class MovementPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            var packetType = event.getPacketType();
            if (packetType != PacketType.Play.Server.ENTITY_RELATIVE_MOVE
                && packetType != PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION
                && packetType != PacketType.Play.Server.ENTITY_TELEPORT) {
                return;
            }

            Object handle = event.getPlayer();
            if (!(handle instanceof Player viewer)) {
                return;
            }

            ViewerNametagState state = viewerNametagStates.get(viewer.getUniqueId());
            if (state == null || state.trackedPlayers.isEmpty()) {
                return;
            }

            if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                handleRelativeMove(event, viewer, state);
            } else if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                handleRelativeMoveAndRotation(event, viewer, state);
            } else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
                handleTeleport(event, viewer, state);
            }
        }

        private void handleRelativeMove(PacketSendEvent event, Player viewer, ViewerNametagState state) {
            WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
            int entityId = packet.getEntityId();
            TrackedPlayer tracked = state.trackedPlayers.get(entityId);
            if (tracked != null && tracked.position() != null) {
                Vector3d updated = tracked.position().add(packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
                state.trackedPlayers.put(entityId, new TrackedPlayer(tracked.playerId(), tracked.vanillaName(), updated));
            }

            CarrierEntity carrier = state.carriers.get(entityId);
            if (carrier == null) {
                return;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerEntityRelativeMove(
                    carrier.entityId(),
                    packet.getDeltaX(),
                    packet.getDeltaY(),
                    packet.getDeltaZ(),
                    packet.isOnGround()
                )
            );
        }

        private void handleRelativeMoveAndRotation(PacketSendEvent event, Player viewer, ViewerNametagState state) {
            WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            int entityId = packet.getEntityId();
            TrackedPlayer tracked = state.trackedPlayers.get(entityId);
            if (tracked != null && tracked.position() != null) {
                Vector3d updated = tracked.position().add(packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
                state.trackedPlayers.put(entityId, new TrackedPlayer(tracked.playerId(), tracked.vanillaName(), updated));
            }

            CarrierEntity carrier = state.carriers.get(entityId);
            if (carrier == null) {
                return;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerEntityRelativeMoveAndRotation(
                    carrier.entityId(),
                    packet.getDeltaX(),
                    packet.getDeltaY(),
                    packet.getDeltaZ(),
                    packet.getYaw(),
                    packet.getPitch(),
                    packet.isOnGround()
                )
            );
        }

        private void handleTeleport(PacketSendEvent event, Player viewer, ViewerNametagState state) {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
            int entityId = packet.getEntityId();
            Vector3d position = packet.getPosition();
            TrackedPlayer tracked = state.trackedPlayers.get(entityId);
            if (tracked != null) {
                state.trackedPlayers.put(entityId, new TrackedPlayer(tracked.playerId(), tracked.vanillaName(), position));
            }

            CarrierEntity carrier = state.carriers.get(entityId);
            if (carrier == null || position == null) {
                return;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerEntityTeleport(
                    carrier.entityId(),
                    position.add(0.0D, CARRIER_Y_OFFSET, 0.0D),
                    packet.getYaw(),
                    packet.getPitch(),
                    packet.isOnGround()
                )
            );
        }
    }
}

package sh.harold.fulcrum.plugin.playerdata;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UsernameDisplayService implements Listener {

    private static final int CUSTOM_NAME_INDEX = 2;
    private static final int CUSTOM_NAME_VISIBILITY_INDEX = 3;
    private static final long HEALTH_REFRESH_INTERVAL_MILLIS = 500L;

    private final Plugin plugin;
    private final Logger logger;
    private final PlayerSettingsService settingsService;
    private final LinkedAccountService linkedAccountService;
    private final UsernameBaseNameResolver baseNameResolver;
    private final TabNameDecorator tabNameDecorator;
    private final NametagDecorator nametagDecorator;
    private final ChatNameDecorator chatNameDecorator;
    private final ProtocolManager protocolManager;
    private final PacketType playerInfoPacketType;
    private final GsonComponentSerializer gson = GsonComponentSerializer.gson();
    private final WrappedDataWatcher.Serializer nameSerializer;
    private final WrappedDataWatcher.Serializer booleanSerializer;
    private final Map<Integer, UUID> entityToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> vanillaNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentHealthRefresh = new ConcurrentHashMap<>();
    private final boolean debug = false;
    private boolean loggedMissingProtocolLib;

    public UsernameDisplayService(Plugin plugin, DataApi dataApi, PlayerSettingsService settingsService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.linkedAccountService = new LinkedAccountService(Objects.requireNonNull(dataApi, "dataApi"), logger);
        this.baseNameResolver = new UsernameBaseNameResolver(settingsService, linkedAccountService);
        this.tabNameDecorator = new TabNameDecorator(settingsService);
        this.nametagDecorator = new NametagDecorator(settingsService);
        this.chatNameDecorator = new ChatNameDecorator(settingsService);
        this.protocolManager = plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")
            ? ProtocolLibrary.getProtocolManager()
            : null;
        this.playerInfoPacketType = resolvePlayerInfoUpdate();
        this.nameSerializer = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
        this.booleanSerializer = WrappedDataWatcher.Registry.get(Boolean.class);

        plugin.getServer().getOnlinePlayers().forEach(this::track);

        if (protocolManager != null) {
            registerPacketAdapters();
        } else {
            logger.warning("ProtocolLib not detected; username masking will only apply in chat and viewer-aware messages. Tab and nametags will stay vanilla.");
        }
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
        entityToPlayer.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
        recentHealthRefresh.remove(playerId);
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
        entityToPlayer.put(player.getEntityId(), playerId);
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
        if (protocolManager == null) {
            if (!loggedMissingProtocolLib) {
                loggedMissingProtocolLib = true;
                logger.warning("ProtocolLib missing; username view changes will not affect tab or nametags.");
            }
            return;
        }
        UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            fallbackResendTab(viewer);
            sendNametagUpdate(viewer, preference);
        });
    }

    private void registerPacketAdapters() {
        protocolManager.addPacketListener(new PlayerInfoPacketAdapter(plugin));
        protocolManager.addPacketListener(new EntityMetadataPacketAdapter(plugin));
        if (PacketType.Play.Server.ENTITY_DESTROY.isSupported()) {
            protocolManager.addPacketListener(new EntityDestroyAdapter(plugin));
        }
    }

    private PlayerInfoData buildInfoData(Player target, Component display) {
        return PlayerInfoDataCloner.buildFromPlayer(target, display, gson, logger);
    }

    private void sendNametagUpdate(Player viewer, UsernameView preference) {
        if (protocolManager == null) {
            return;
        }
        if (preference == UsernameView.MINECRAFT) {
            boolean anyDecorations = plugin.getServer().getOnlinePlayers().stream()
                .anyMatch(player -> nametagDecorator.hasNametagDecorations(player.getUniqueId()));
            if (!anyDecorations) {
                return;
            }
        }
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(preference, target.getUniqueId(), target.getName());
            Component decorated = nametagDecorator.decorateForNametag(target.getUniqueId(), baseName.component());
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().writeSafely(0, target.getEntityId());
            applyNametag(packet, decorated);
            try {
                protocolManager.sendServerPacket(viewer, packet);
            } catch (RuntimeException runtimeException) {
                logger.log(Level.WARNING, "Failed to send nametag update for " + target.getUniqueId(), runtimeException);
            }
        }
    }

    private void fallbackResendTab(Player viewer) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (target.equals(viewer)) {
                    continue;
                }
                viewer.hidePlayer(plugin, target);
                viewer.showPlayer(plugin, target);
            }
        });
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

    private PacketType resolvePlayerInfoUpdate() {
        try {
            var field = PacketType.Play.Server.class.getField("PLAYER_INFO_UPDATE");
            Object value = field.get(null);
            if (value instanceof PacketType type) {
                return type;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return PacketType.Play.Server.PLAYER_INFO;
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

    private final class PlayerInfoPacketAdapter extends PacketAdapter {
        PlayerInfoPacketAdapter(Plugin ownerPlugin) {
            super(ownerPlugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_INFO, playerInfoPacketType);
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            Player viewer = event.getPlayer();
            if (viewer == null) {
                return;
            }
            UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
            var dataLists = event.getPacket().getPlayerInfoDataLists();
            if (dataLists.size() == 0) {
                return;
            }
            List<PlayerInfoData> originals = dataLists.readSafely(0);
            if (originals == null || originals.isEmpty()) {
                return;
            }
            List<PlayerInfoData> updated = new ArrayList<>(originals.size());
            boolean mutated = false;
            for (PlayerInfoData data : originals) {
                if (data == null) {
                    updated.add(null);
                    continue;
                }
                UUID targetId = resolveTargetId(data);
                Player target = targetId == null ? null : plugin.getServer().getPlayer(targetId);
                String vanillaName = resolveVanillaName(data, targetId, target);

                if (preference == UsernameView.MINECRAFT && !tabNameDecorator.hasTabDecorations(targetId, target)) {
                    updated.add(data);
                    continue;
                }

                UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(preference, targetId, vanillaName);
                if (preference == UsernameView.MINECRAFT
                    && baseName.value().equals(vanillaName)
                    && !tabNameDecorator.hasTabDecorations(targetId, target)) {
                    updated.add(data);
                    continue;
                }

                Component decorated = tabNameDecorator.decorateForTab(targetId, target, baseName.component());
                PlayerInfoData replacement = cloneWithDisplayName(data, decorated);
                updated.add(replacement);
                mutated = mutated || replacement != data;
            }
            if (mutated) {
                dataLists.writeSafely(0, updated);
                if (actionsHandle != null && actionsHandle.size() > 0) {
                    EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.copyOf(actionsHandle.readSafely(0));
                    actions.add(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
                    actionsHandle.write(0, actions);
                }
                debug(() -> "Rewrote player info for viewer " + viewer.getUniqueId() + " (" + updated.size() + " entries)");
            }
        }

        private UUID resolveTargetId(PlayerInfoData data) {
            UUID targetId = data.getProfileId();
            if (targetId == null && data.getProfile() != null) {
                targetId = data.getProfile().getUUID();
            }
            return targetId;
        }

        private String resolveVanillaName(PlayerInfoData data, UUID targetId, Player target) {
            if (target != null) {
                return target.getName();
            }
            if (data.getProfile() != null && data.getProfile().getName() != null && !data.getProfile().getName().isBlank()) {
                return data.getProfile().getName();
            }
            if (targetId != null) {
                String cached = vanillaNames.get(targetId);
                if (cached != null && !cached.isBlank()) {
                    return cached;
                }
            }
            return null;
        }

        private PlayerInfoData cloneWithDisplayName(PlayerInfoData source, Component display) {
            return PlayerInfoDataCloner.cloneWithDisplayName(source, display, gson, logger);
        }
    }

    private final class EntityMetadataPacketAdapter extends PacketAdapter {
        EntityMetadataPacketAdapter(Plugin ownerPlugin) {
            super(ownerPlugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA);
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            Player viewer = event.getPlayer();
            if (viewer == null) {
                return;
            }
            PacketContainer packet = event.getPacket();
            Integer entityId = packet.getIntegers().readSafely(0);
            Entity entity = packet.getEntityModifier(viewer.getWorld()).readSafely(0);
            Player targetPlayer = entity instanceof Player player ? player : null;
            UUID targetId = targetPlayer != null ? targetPlayer.getUniqueId() : entityToPlayer.get(entityId);
            if (targetPlayer != null && entityId != null) {
                entityToPlayer.put(entityId, targetPlayer.getUniqueId());
            }
            if (targetId == null) {
                return;
            }
            UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
            if (preference == UsernameView.MINECRAFT && !nametagDecorator.hasNametagDecorations(targetId)) {
                return;
            }
            String vanillaName = targetPlayer != null ? targetPlayer.getName() : vanillaNames.get(targetId);
            UsernameBaseNameResolver.BaseName baseName = baseNameResolver.resolve(preference, targetId, vanillaName);
            Component decorated = nametagDecorator.decorateForNametag(targetId, baseName.component());
            applyNametag(packet, decorated);
        }
    }

    private final class EntityDestroyAdapter extends PacketAdapter {
        EntityDestroyAdapter(Plugin ownerPlugin) {
            super(ownerPlugin, ListenerPriority.MONITOR, PacketType.Play.Server.ENTITY_DESTROY);
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            PacketContainer packet = event.getPacket();
            List<Integer> ids = packet.getIntLists().size() > 0 ? packet.getIntLists().readSafely(0) : List.of();
            if (ids.isEmpty() && packet.getIntegerArrays().size() > 0) {
                int[] array = packet.getIntegerArrays().readSafely(0);
                ids = array == null ? List.of() : Arrays.stream(array).boxed().toList();
            }
            if (!ids.isEmpty()) {
                ids.forEach(entityToPlayer::remove);
            }
        }
    }

    private void applyNametag(PacketContainer packet, Component nameComponent) {
        var dataValues = packet.getDataValueCollectionModifier();
        List<WrappedDataValue> values = dataValues.size() == 0 ? null : dataValues.readSafely(0);
        values = values == null ? new ArrayList<>() : new ArrayList<>(values);
        boolean updatedName = false;
        boolean updatedVisibility = false;

        WrappedChatComponent serialized = WrappedChatComponent.fromJson(gson.serialize(nameComponent));
        Optional<?> namePayload = Optional.ofNullable(serialized.getHandle());

        for (int i = 0; i < values.size(); i++) {
            WrappedDataValue value = values.get(i);
            if (value.getIndex() == CUSTOM_NAME_INDEX) {
                values.set(i, new WrappedDataValue(CUSTOM_NAME_INDEX, nameSerializer, namePayload));
                updatedName = true;
            } else if (value.getIndex() == CUSTOM_NAME_VISIBILITY_INDEX) {
                values.set(i, new WrappedDataValue(CUSTOM_NAME_VISIBILITY_INDEX, booleanSerializer, true));
                updatedVisibility = true;
            }
        }

        if (!updatedName) {
            values.add(new WrappedDataValue(CUSTOM_NAME_INDEX, nameSerializer, namePayload));
        }
        if (!updatedVisibility) {
            values.add(new WrappedDataValue(CUSTOM_NAME_VISIBILITY_INDEX, booleanSerializer, true));
        }

        dataValues.writeSafely(0, values);
        debug(() -> "Applied nametag for entity " + packet.getIntegers().readSafely(0));
    }
}

package sh.harold.fulcrum.plugin.playerdata;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
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
    private final GsonComponentSerializer gson = GsonComponentSerializer.gson();
    private final Map<Integer, UUID> entityToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> vanillaNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentHealthRefresh = new ConcurrentHashMap<>();
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
        UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            fallbackResendTab(viewer);
            sendNametagUpdate(viewer, preference);
        });
    }

    private void registerPacketAdapters() {
        PacketEvents.getAPI().getEventManager().registerListener(new PlayerInfoPacketListener());
        PacketEvents.getAPI().getEventManager().registerListener(new EntityMetadataPacketListener());
        PacketEvents.getAPI().getEventManager().registerListener(new EntityDestroyPacketListener());
    }

    private void sendNametagUpdate(Player viewer, UsernameView preference) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        boolean clearNames = false;
        if (preference == UsernameView.MINECRAFT) {
            boolean anyDecorations = plugin.getServer().getOnlinePlayers().stream()
                .anyMatch(player -> nametagDecorator.hasNametagDecorations(player.getUniqueId()));
            clearNames = !anyDecorations;
        }
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            try {
                List<EntityData<?>> values = clearNames
                    ? List.of(
                        new EntityData<>(CUSTOM_NAME_INDEX, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.empty()),
                        new EntityData<>(CUSTOM_NAME_VISIBILITY_INDEX, EntityDataTypes.BOOLEAN, false)
                    )
                    : nametagData(
                        nametagDecorator.decorateForNametag(
                            target.getUniqueId(),
                            baseNameResolver.resolve(preference, target.getUniqueId(), target.getName()).component()
                        )
                    );
                var packet = new WrapperPlayServerEntityMetadata(target.getEntityId(), values);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
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
                boolean initiallyVisible = viewer.canSee(target);
                viewer.hidePlayer(plugin, target);
                if (initiallyVisible) {
                    viewer.showPlayer(plugin, target);
                }
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

    private final class EntityMetadataPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
                return;
            }
            Object handle = event.getPlayer();
            if (!(handle instanceof Player viewer)) {
                return;
            }

            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            int entityId = packet.getEntityId();

            Player targetPlayer = null;
            UUID targetId = entityToPlayer.get(entityId);
            for (Player candidate : plugin.getServer().getOnlinePlayers()) {
                if (candidate.getEntityId() == entityId) {
                    targetPlayer = candidate;
                    targetId = candidate.getUniqueId();
                    entityToPlayer.put(entityId, targetId);
                    break;
                }
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

            List<EntityData<?>> values = applyNametag(packet.getEntityMetadata(), decorated);
            packet.setEntityMetadata(values);
        }
    }

    private final class EntityDestroyPacketListener extends PacketListenerAbstract {
        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.DESTROY_ENTITIES) {
                return;
            }
            WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
            int[] ids = packet.getEntityIds();
            if (ids == null || ids.length == 0) {
                return;
            }
            for (int id : ids) {
                entityToPlayer.remove(id);
            }
        }
    }

    private List<EntityData<?>> nametagData(Component nameComponent) {
        return applyNametag(List.of(), nameComponent);
    }

    private List<EntityData<?>> applyNametag(List<EntityData<?>> values, Component nameComponent) {
        List<EntityData<?>> updated = values == null ? new ArrayList<>() : new ArrayList<>(values);
        boolean updatedName = false;
        boolean updatedVisibility = false;

        for (int i = 0; i < updated.size(); i++) {
            EntityData<?> value = updated.get(i);
            int index = value.getIndex();
            if (index == CUSTOM_NAME_INDEX) {
                updated.set(i, new EntityData<>(CUSTOM_NAME_INDEX, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.ofNullable(nameComponent)));
                updatedName = true;
            } else if (index == CUSTOM_NAME_VISIBILITY_INDEX) {
                updated.set(i, new EntityData<>(CUSTOM_NAME_VISIBILITY_INDEX, EntityDataTypes.BOOLEAN, true));
                updatedVisibility = true;
            }
        }

        if (!updatedName) {
            updated.add(new EntityData<>(CUSTOM_NAME_INDEX, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.ofNullable(nameComponent)));
        }
        if (!updatedVisibility) {
            updated.add(new EntityData<>(CUSTOM_NAME_VISIBILITY_INDEX, EntityDataTypes.BOOLEAN, true));
        }

        return updated;
    }
}

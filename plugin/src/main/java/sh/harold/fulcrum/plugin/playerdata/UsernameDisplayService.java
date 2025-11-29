package sh.harold.fulcrum.plugin.playerdata;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UsernameDisplayService implements Listener {

    private static final int CUSTOM_NAME_INDEX = 2;
    private static final int CUSTOM_NAME_VISIBILITY_INDEX = 3;

    private final Plugin plugin;
    private final Logger logger;
    private final DocumentCollection players;
    private final PlayerSettingsService settingsService;
    private final ProtocolManager protocolManager;
    private final Map<UUID, PlayerIdentity> identities = new ConcurrentHashMap<>();
    private final GsonComponentSerializer gson = GsonComponentSerializer.gson();
    private final WrappedDataWatcher.Serializer nameSerializer;
    private final WrappedDataWatcher.Serializer booleanSerializer;

    public UsernameDisplayService(Plugin plugin, DataApi dataApi, PlayerSettingsService settingsService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.protocolManager = plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")
            ? ProtocolLibrary.getProtocolManager()
            : null;
        this.nameSerializer = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
        this.booleanSerializer = WrappedDataWatcher.Registry.get(Boolean.class);

        plugin.getServer().getOnlinePlayers().forEach(this::track);

        if (protocolManager != null) {
            registerPacketAdapters();
        } else {
            logger.warning("ProtocolLib not detected; username masking will only apply in chat.");
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
        identities.remove(playerId);
        settingsService.evictCachedSettings(playerId);
    }

    public void track(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        identities.put(playerId, new PlayerIdentity(playerId, player.getName(), null, null));
        loadExternalUsernames(player);
    }

    public String displayName(UUID viewerId, Player target) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(target, "target");
        UsernameView preference = settingsService.cachedUsernameView(viewerId);
        return displayName(preference, target);
    }

    public Component displayComponent(UUID viewerId, Player target, TextColor color) {
        TextColor resolvedColor = color == null ? NamedTextColor.WHITE : color;
        String text = displayName(viewerId, target);
        return Component.text(text, resolvedColor).decoration(TextDecoration.ITALIC, false);
    }

    public void refreshView(Player viewer) {
        if (protocolManager == null || viewer == null) {
            return;
        }
        UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> sendTabUpdate(viewer, preference));
    }

    private String displayName(UsernameView preference, Player target) {
        PlayerIdentity identity = identities.get(target.getUniqueId());
        if (identity == null) {
            return target.getName();
        }
        if (preference == UsernameView.OSU && identity.osuUsername() != null) {
            return identity.osuUsername();
        }
        if (preference == UsernameView.DISCORD && identity.discordDisplayName() != null) {
            return identity.discordDisplayName();
        }
        return identity.minecraftUsername();
    }

    private String displayName(UsernameView preference, UUID targetId, String profileName) {
        PlayerIdentity identity = targetId == null ? null : identities.get(targetId);
        if (identity != null) {
            if (preference == UsernameView.OSU && identity.osuUsername() != null) {
                return identity.osuUsername();
            }
            if (preference == UsernameView.DISCORD && identity.discordDisplayName() != null) {
                return identity.discordDisplayName();
            }
            return identity.minecraftUsername();
        }
        if (profileName != null && !profileName.isBlank()) {
            return profileName;
        }
        return targetId != null ? targetId.toString().substring(0, 8) : "Player";
    }

    private void loadExternalUsernames(Player player) {
        UUID playerId = player.getUniqueId();
        players.load(playerId.toString())
            .thenApply(document -> {
                String osuUsername = document.get("linking.osu.username", String.class)
                    .filter(value -> !value.isBlank())
                    .orElseGet(() -> document.get("osu.username", String.class)
                        .filter(value -> !value.isBlank())
                        .orElse(null));

                String discordDisplay = resolveDiscordDisplay(document);
                return new ExternalNames(osuUsername, discordDisplay);
            })
            .thenAccept(names -> identities.compute(playerId, (id, existing) -> {
                String minecraftUsername = existing != null ? existing.minecraftUsername() : player.getName();
                String osuUsername = names.osuUsername();
                String discordDisplay = names.discordDisplayName();
                return new PlayerIdentity(id, minecraftUsername, osuUsername, discordDisplay);
            }))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to load osu! username for " + playerId, throwable);
                return null;
            });
    }

    private String resolveDiscordDisplay(sh.harold.fulcrum.common.data.Document document) {
        return document.get("linking.discord.globalName", String.class)
            .filter(value -> !value.isBlank())
            .orElseGet(() -> document.get("linking.discord.username", String.class)
                .filter(value -> !value.isBlank())
                .orElse(null));
    }

    private void registerPacketAdapters() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();
                if (viewer == null) {
                    return;
                }
                UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
                if (preference == UsernameView.MINECRAFT) {
                    return;
                }
                var dataLists = event.getPacket().getPlayerInfoDataLists();
                if (dataLists.size() == 0) {
                    return;
                }
                List<PlayerInfoData> originals = dataLists.readSafely(0);
                if (originals == null || originals.isEmpty()) {
                    return;
                }
                List<PlayerInfoData> updated = new ArrayList<>(originals);
                boolean mutated = false;
                for (int i = 0; i < updated.size(); i++) {
                    PlayerInfoData data = updated.get(i);
                    if (data == null) {
                        continue;
                    }
                    UUID targetId = data.getProfileId();
                    if (targetId == null && data.getProfile() != null) {
                        targetId = data.getProfile().getUUID();
                    }
                    if (targetId == null) {
                        continue;
                    }
                    Player target = plugin.getServer().getPlayer(targetId);
                    if (target == null && preference == UsernameView.MINECRAFT) {
                        continue;
                    }
                    String displayName = target != null
                        ? displayName(preference, target)
                        : displayName(preference, targetId, data.getProfile() == null ? null : data.getProfile().getName());
                    Component display = Component.text(displayName, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false);
                    PlayerInfoData replacement = cloneWithDisplayName(data, display);
                    updated.set(i, replacement);
                    mutated = true;
                }
                if (mutated) {
                    dataLists.writeSafely(0, updated);
                }
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();
                if (viewer == null) {
                    return;
                }
                UsernameView preference = settingsService.cachedUsernameView(viewer.getUniqueId());
                if (preference == UsernameView.MINECRAFT) {
                    return;
                }
                PacketContainer packet = event.getPacket();
                Entity entity = packet.getEntityModifier(viewer.getWorld()).readSafely(0);
                if (!(entity instanceof Player target)) {
                    return;
                }
                applyNametag(packet, target, preference);
            }
        });
    }

    private PlayerInfoData cloneWithDisplayName(PlayerInfoData source, Component display) {
        WrappedChatComponent chatComponent = WrappedChatComponent.fromJson(gson.serialize(display));
        return new PlayerInfoData(
            source.getProfileId(),
            source.getLatency(),
            source.isListed(),
            source.getGameMode(),
            source.getProfile(),
            chatComponent,
            source.isShowHat(),
            source.getListOrder(),
            source.getRemoteChatSessionData()
        );
    }

    private void applyNametag(PacketContainer packet, Player target, UsernameView preference) {
        var dataValues = packet.getDataValueCollectionModifier();
        if (dataValues.size() == 0) {
            return;
        }
        List<WrappedDataValue> values = dataValues.readSafely(0);
        if (values == null) {
            values = new ArrayList<>();
        } else {
            values = new ArrayList<>(values);
        }
        boolean updatedName = false;
        boolean updatedVisibility = false;

        Component nameComponent = Component.text(displayName(preference, target), NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false);
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
    }

    private void sendTabUpdate(Player viewer, UsernameView preference) {
        if (protocolManager == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (target.equals(viewer)) {
                    continue;
                }
                viewer.hidePlayer(plugin, target);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Player target : plugin.getServer().getOnlinePlayers()) {
                    if (target.equals(viewer)) {
                        continue;
                    }
                    viewer.showPlayer(plugin, target);
                }
                sendNametagUpdate(viewer, preference);
            }, 2L);
        });
    }

    private PlayerInfoData buildInfoData(Player target, UsernameView preference) {
        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(target);
        WrappedRemoteChatSessionData chatSession = WrappedRemoteChatSessionData.fromPlayer(target);
        Component display = preference == UsernameView.MINECRAFT
            ? null
            : Component.text(displayName(preference, target), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
        WrappedChatComponent chatComponent = display == null ? null : WrappedChatComponent.fromJson(gson.serialize(display));

        return new PlayerInfoData(
            target.getUniqueId(),
            target.getPing(),
            true,
            EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
            profile,
            chatComponent,
            true,
            0,
            chatSession
        );
    }

    private void sendNametagUpdate(Player viewer, UsernameView preference) {
        if (protocolManager == null) {
            return;
        }
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().writeSafely(0, target.getEntityId());
            List<WrappedDataValue> values = new ArrayList<>();
            if (preference == UsernameView.OSU) {
                Component nameComponent = Component.text(displayName(preference, target), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false);
                WrappedChatComponent serialized = WrappedChatComponent.fromJson(gson.serialize(nameComponent));
                values.add(new WrappedDataValue(CUSTOM_NAME_INDEX, nameSerializer, Optional.ofNullable(serialized.getHandle())));
                values.add(new WrappedDataValue(CUSTOM_NAME_VISIBILITY_INDEX, booleanSerializer, true));
            } else {
                values.add(new WrappedDataValue(CUSTOM_NAME_INDEX, nameSerializer, Optional.empty()));
                values.add(new WrappedDataValue(CUSTOM_NAME_VISIBILITY_INDEX, booleanSerializer, false));
            }
            packet.getDataValueCollectionModifier().writeSafely(0, values);
            protocolManager.sendServerPacket(viewer, packet);
        }
    }

    private record PlayerIdentity(UUID playerId, String minecraftUsername, String osuUsername, String discordDisplayName) {
    }

    private record ExternalNames(String osuUsername, String discordDisplayName) {
    }
}

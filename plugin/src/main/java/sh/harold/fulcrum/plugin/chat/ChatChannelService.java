package sh.harold.fulcrum.plugin.chat;

import sh.harold.fulcrum.common.permissions.StaffService;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class ChatChannelService {

    private final Supplier<Optional<StaffService>> staffService;
    private final Map<UUID, ChatChannel> channels = new ConcurrentHashMap<>();
    private final Collection<ChannelListener> channelListeners = new CopyOnWriteArrayList<>();

    public ChatChannelService(Supplier<Optional<StaffService>> staffService) {
        this.staffService = Objects.requireNonNull(staffService, "staffService");
    }

    public ChatChannel channel(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return channels.getOrDefault(playerId, ChatChannel.all());
    }

    public void setAll(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ChatChannel previous = channels.remove(playerId);
        if (previous != null) {
            notifyChannelChange(playerId, ChatChannel.all());
        }
    }

    public boolean setStaff(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!isStaff(playerId)) {
            return false;
        }
        ChatChannel staffChannel = ChatChannel.staff();
        ChatChannel previous = channels.put(playerId, staffChannel);
        if (!staffChannel.equals(previous)) {
            notifyChannelChange(playerId, staffChannel);
        }
        return true;
    }

    public void setDirect(UUID playerId, UUID targetId, String targetName) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(targetId, "targetId");
        ChatChannel directChannel = ChatChannel.direct(targetId, targetName);
        ChatChannel previous = channels.put(playerId, directChannel);
        if (!directChannel.equals(previous)) {
            notifyChannelChange(playerId, directChannel);
        }
    }

    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ChatChannel previous = channels.remove(playerId);
        if (previous != null) {
            notifyChannelChange(playerId, ChatChannel.all());
        }
    }

    public boolean isStaff(UUID playerId) {
        return staffService.get()
            .map(service -> service.isStaff(playerId).toCompletableFuture().join())
            .orElse(false);
    }

    public void addListener(ChannelListener listener) {
        channelListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void notifyChannelChange(UUID playerId, ChatChannel channel) {
        for (ChannelListener listener : channelListeners) {
            listener.onChannelChange(playerId, channel);
        }
    }

    @FunctionalInterface
    public interface ChannelListener {
        void onChannelChange(UUID playerId, ChatChannel channel);
    }

    public record ChatChannel(ChatChannelType type, UUID directTarget, String directTargetName) {

        public static ChatChannel all() {
            return new ChatChannel(ChatChannelType.ALL, null, null);
        }

        public static ChatChannel staff() {
            return new ChatChannel(ChatChannelType.STAFF, null, null);
        }

        public static ChatChannel direct(UUID targetId, String targetName) {
            return new ChatChannel(ChatChannelType.DIRECT, targetId, targetName);
        }

        public boolean isAll() {
            return type == ChatChannelType.ALL;
        }

        public boolean isStaff() {
            return type == ChatChannelType.STAFF;
        }

        public boolean isDirect() {
            return type == ChatChannelType.DIRECT;
        }
    }

    public enum ChatChannelType {
        ALL,
        STAFF,
        DIRECT
    }
}

package sh.harold.fulcrum.plugin.playerdata;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedProfilePublicKey;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerInfoDataCloner {

    private static final Field PROFILE_KEY_FIELD = resolveProfileKeyField();

    private PlayerInfoDataCloner() {
    }

    static PlayerInfoData cloneWithDisplayName(PlayerInfoData source, Component display, GsonComponentSerializer gson, Logger logger) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(gson, "gson");
        WrappedChatComponent chatComponent = serializeDisplay(display, gson, logger);
        PlayerInfoData clone = new PlayerInfoData(
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
        copyProfileKey(clone, source.getProfileKeyData(), logger);
        return clone;
    }

    static PlayerInfoData buildFromPlayer(Player target, Component display, GsonComponentSerializer gson, Logger logger) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(gson, "gson");
        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(target);
        WrappedRemoteChatSessionData chatSession = WrappedRemoteChatSessionData.fromPlayer(target);
        WrappedChatComponent chatComponent = serializeDisplay(display, gson, logger);
        PlayerInfoData data = new PlayerInfoData(
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
        WrappedProfilePublicKey publicKey = WrappedProfilePublicKey.ofPlayer(target);
        WrappedProfilePublicKey.WrappedProfileKeyData keyData = publicKey == null ? null : publicKey.getKeyData();
        copyProfileKey(data, keyData, logger);
        return data;
    }

    private static void copyProfileKey(PlayerInfoData data, WrappedProfilePublicKey.WrappedProfileKeyData keyData, Logger logger) {
        if (data == null || keyData == null || PROFILE_KEY_FIELD == null) {
            return;
        }
        try {
            PROFILE_KEY_FIELD.set(data, keyData);
        } catch (IllegalAccessException illegalAccessException) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to copy profile key data for " + data.getProfileId(), illegalAccessException);
            }
        }
    }

    private static WrappedChatComponent serializeDisplay(Component display, GsonComponentSerializer gson, Logger logger) {
        if (display == null) {
            return null;
        }
        try {
            return WrappedChatComponent.fromJson(gson.serialize(display));
        } catch (RuntimeException runtimeException) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to serialize display component; falling back to null.", runtimeException);
            }
            return null;
        }
    }

    private static Field resolveProfileKeyField() {
        try {
            Field field = PlayerInfoData.class.getDeclaredField("profileKeyData");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

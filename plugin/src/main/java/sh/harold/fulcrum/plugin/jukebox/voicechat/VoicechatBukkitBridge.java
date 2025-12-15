package sh.harold.fulcrum.plugin.jukebox.voicechat;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.Objects;

public final class VoicechatBukkitBridge {

    private VoicechatBukkitBridge() {
    }

    public static ServerLevel serverLevel(VoicechatServerApi api, World world) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(world, "world");

        try {
            ServerLevel direct = api.fromServerLevel(world);
            if (direct != null) {
                return direct;
            }
        } catch (Throwable ignored) {
        }

        Object handle = nmsWorldHandle(world);
        return api.fromServerLevel(handle);
    }

    private static Object nmsWorldHandle(World world) {
        try {
            Method getHandle = world.getClass().getMethod("getHandle");
            return getHandle.invoke(world);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to adapt Bukkit world to voice chat server level", exception);
        }
    }
}


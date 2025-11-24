package sh.harold.fulcrum.plugin.version;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public record PluginVersionService(JavaPlugin plugin) implements VersionService {

    public PluginVersionService {
        Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String version() {
        String value = plugin.getPluginMeta().getVersion();
        if (value == null || value.isBlank() || value.contains("${")) {
            return "dev";
        }
        return value;
    }
}

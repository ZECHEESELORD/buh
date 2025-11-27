package sh.harold.fulcrum.plugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import sh.harold.fulcrum.plugin.accountlink.AccountLinkModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;

final class AddInviteSourceCommand {

    private final BuhPlugin plugin;
    private final AccountLinkModule accountLinkModule;

    AddInviteSourceCommand(BuhPlugin plugin, AccountLinkModule accountLinkModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.accountLinkModule = Objects.requireNonNull(accountLinkModule, "accountLinkModule");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return literal("addinvitesource")
            .requires(this::isStaff)
            .then(argument("name", greedyString())
                .then(argument("roleId", longArg(1))
                    .executes(this::addSource)))
            .build();
    }

    private int addSource(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = context.getArgument("name", String.class);
        long roleId = context.getArgument("roleId", Long.class);
        Path configPath = plugin.getDataFolder().toPath().resolve("config/account-link/sources/config.yml");
        try {
            Files.createDirectories(configPath.getParent());
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configPath.toFile());
            List<Map<?, ?>> existing = configuration.getMapList("sources");
            if (existing.isEmpty()) {
                List<String> legacy = configuration.getStringList("sources");
                for (String value : legacy) {
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", value);
                    existing.add(entry);
                }
            }
            List<Map<String, Object>> updated = new ArrayList<>();
            boolean replaced = false;
            for (Map<?, ?> raw : existing) {
                Object rawName = raw.get("name");
                if (!(rawName instanceof String currentName)) {
                    continue;
                }
                if (currentName.equalsIgnoreCase(name)) {
                    LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", name);
                    entry.put("role-id", roleId);
                    updated.add(entry);
                    replaced = true;
                } else {
                    LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", currentName);
                    Object maybeRole = raw.get("role-id");
                    if (maybeRole != null) {
                        entry.put("role-id", maybeRole);
                    }
                    updated.add(entry);
                }
            }
            if (!replaced) {
                LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("role-id", roleId);
                updated.add(entry);
            }
            configuration.set("sources", updated);
            configuration.save(configPath.toFile());
            accountLinkModule.reloadSourcesConfig();
            sender.sendMessage(Component.text("Added invite source '" + name + "' with role " + roleId + ".", NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save invite source config", exception);
            sender.sendMessage(Component.text("Failed to add invite source; check logs.", NamedTextColor.RED));
            return 0;
        }
    }

    private boolean isStaff(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            return sender.hasPermission("buh.addinvitesource");
        }
        return plugin.staffService()
            .map(service -> service.isStaff(player.getUniqueId()).toCompletableFuture().join())
            .orElse(false);
    }
}

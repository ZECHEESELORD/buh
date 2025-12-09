package sh.harold.fulcrum.plugin.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.ItemEngine;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.plugin.item.enchant.EnchantService;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class EnchantCommand {

    private final EnchantRegistry registry;
    private final EnchantService enchantService;
    private final ItemResolver resolver;
    private final StaffGuard staffGuard;

    public EnchantCommand(ItemEngine engine, StaffGuard staffGuard) {
        this.registry = engine.enchantService() == null ? null : engine.enchantService().registry();
        this.enchantService = engine.enchantService();
        this.resolver = engine.resolver();
        this.staffGuard = staffGuard;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("enchant")
            .requires(source -> staffGuard.isStaff(source))
            .then(Commands.argument("enchant", StringArgumentType.word())
                .suggests((context, builder) -> {
                    try {
                        registry.ids().forEach(id -> {
                            int colon = id.indexOf(':');
                            builder.suggest(colon == -1 ? id : id.substring(colon + 1));
                        });
                    } catch (Exception ignored) {
                    }
                    return builder.buildFuture();
                })
                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                    .executes(context -> apply(context.getSource(), StringArgumentType.getString(context, "enchant"), IntegerArgumentType.getInteger(context, "level"), EquipmentSlot.HAND))
                    .then(Commands.literal("offhand").executes(context -> apply(context.getSource(), StringArgumentType.getString(context, "enchant"), IntegerArgumentType.getInteger(context, "level"), EquipmentSlot.OFF_HAND)))
                    .then(Commands.literal("mainhand").executes(context -> apply(context.getSource(), StringArgumentType.getString(context, "enchant"), IntegerArgumentType.getInteger(context, "level"), EquipmentSlot.HAND)))
                )
            );
        return root.build();
    }

    private int apply(CommandSourceStack source, String rawId, int level, EquipmentSlot slot) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        if (registry == null || enchantService == null) {
            player.sendMessage(Component.text("Enchant system is not available.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        String enchantId = resolveId(rawId);
        if (enchantId == null) {
            player.sendMessage(Component.text("Unknown enchant '" + rawId + "'.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Optional<EnchantDefinition> definition = registry.get(enchantId);
        if (definition.isEmpty()) {
            player.sendMessage(Component.text("Unknown enchant '" + rawId + "'.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        ItemStack stack = slot == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir()) {
            player.sendMessage(Component.text("Hold an item first.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        ItemStack target = resolver.resolve(stack).map(ItemInstance::stack).orElse(stack);
        Enchantment vanillaEnchant = resolveVanillaEnchant(enchantId);
        ItemStack enchanted = enchantService.applyEnchant(target, enchantId, level);
        enchanted = writeVanillaEnchant(enchanted, vanillaEnchant, level);
        if (slot == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(enchanted);
        } else {
            player.getInventory().setItemInMainHand(enchanted);
        }
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(definition.get().displayName());
        player.sendMessage(Component.text("Applied " + name + " " + roman(level) + ".", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private String resolveId(String input) {
        if (registry == null) {
            return null;
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            if (registry.ids().contains(normalized)) {
                return normalized;
            }
            String suffix = normalized.substring(normalized.lastIndexOf(':') + 1);
            return registry.ids().stream().filter(id -> id.endsWith(":" + suffix)).findFirst().orElse(null);
        }
        String candidate = "fulcrum:" + normalized;
        if (registry.ids().contains(candidate)) {
            return candidate;
        }
        return registry.ids().stream().filter(id -> id.endsWith(":" + normalized)).findFirst().orElse(null);
    }

    private Enchantment resolveVanillaEnchant(String enchantId) {
        if (enchantId == null) {
            return null;
        }
        String key = enchantId.contains(":")
            ? enchantId.substring(enchantId.indexOf(':') + 1)
            : enchantId;
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    private ItemStack writeVanillaEnchant(ItemStack stack, Enchantment enchantment, int level) {
        if (stack == null || enchantment == null) {
            return stack;
        }
        var meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.addStoredEnchant(enchantment, level, true);
            stack.setItemMeta(storage);
            return stack;
        }
        meta.addEnchant(enchantment, level, true);
        stack.setItemMeta(meta);
        return stack;
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(value);
        };
    }
}

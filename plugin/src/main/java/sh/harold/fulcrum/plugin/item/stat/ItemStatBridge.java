package sh.harold.fulcrum.plugin.item.stat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sh.harold.fulcrum.plugin.item.model.SlotGroup;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer;
import sh.harold.fulcrum.stats.core.ModifierOp;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatModifier;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;
import sh.harold.fulcrum.plugin.item.runtime.StatContribution;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;
import sh.harold.fulcrum.plugin.item.model.ItemRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;
import java.util.Objects;

public final class ItemStatBridge {

    private final ItemResolver resolver;
    private final StatService statService;
    private final StatSourceContextRegistry contextRegistry;

    public ItemStatBridge(ItemResolver resolver, StatService statService, StatSourceContextRegistry contextRegistry) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.statService = Objects.requireNonNull(statService, "statService");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry");
    }

    public void refreshPlayer(Player player) {
        EntityKey key = EntityKey.fromUuid(player.getUniqueId());
        StatContainer container = statService.getContainer(key);
        apply(key, container, SlotGroup.MAIN_HAND, player.getInventory().getItemInMainHand());
        apply(key, container, SlotGroup.OFF_HAND, player.getInventory().getItemInOffHand());
        apply(key, container, SlotGroup.HELMET, player.getInventory().getHelmet());
        apply(key, container, SlotGroup.CHESTPLATE, player.getInventory().getChestplate());
        apply(key, container, SlotGroup.LEGGINGS, player.getInventory().getLeggings());
        apply(key, container, SlotGroup.BOOTS, player.getInventory().getBoots());
    }

    private void apply(EntityKey key, StatContainer container, SlotGroup slot, ItemStack stack) {
        String slotPrefix = "item:" + slot.name().toLowerCase();
        clearSlotSources(container, slotPrefix);
        contextRegistry.clearPrefix(key, slotPrefix);
        if (slot == SlotGroup.MAIN_HAND && (stack == null || stack.getType().isAir())) {
            StatSourceId sourceId = new StatSourceId(slotPrefix + ":empty");
            container.addModifier(new StatModifier(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED, sourceId, ModifierOp.FLAT, 4.0));
            contextRegistry.put(key, sourceId, new StatSourceContext(
                "Default Attack Speed",
                "&8Default",
                "Fallback attack speed when no item is equipped.",
                new ItemStack(org.bukkit.Material.BARRIER),
                SourceCategory.DEFAULT,
                slot.name()
            ));
            return;
        }
        resolver.resolve(stack).ifPresent(instance -> {
            boolean defunct = instance.durability().map(sh.harold.fulcrum.plugin.item.runtime.DurabilityState::defunct).orElse(false);
            if (defunct) {
                return;
            }
            if (instance.definition().category().defaultSlot() != slot) {
                if (slot == SlotGroup.MAIN_HAND) {
                    StatSourceId sourceId = new StatSourceId(slotPrefix + ":default");
                    container.addModifier(new StatModifier(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED, sourceId, ModifierOp.FLAT, 4.0));
                    contextRegistry.put(key, sourceId, new StatSourceContext(
                        "Default Attack Speed",
                        "&8Default",
                        "Fallback attack speed when item is not for main hand.",
                        new ItemStack(org.bukkit.Material.BARRIER),
                        SourceCategory.DEFAULT,
                        slot.name()
                    ));
                }
                return;
            }
            final boolean[] hasAttackSpeed = {false};
            instance.statSources().forEach((source, values) -> {
                StatSourceId sourceId = new StatSourceId(slotPrefix + ":" + source);
                for (Map.Entry<StatId, StatContribution> entry : values.entrySet()) {
                    double contribution = entry.getValue().value();
                    if (entry.getKey().equals(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED) && Double.compare(contribution, 0.0) != 0) {
                        hasAttackSpeed[0] = true;
                    }
                    container.addModifier(new StatModifier(entry.getKey(), sourceId, ModifierOp.FLAT, contribution, 0, entry.getValue().condition()));
                }
                contextRegistry.put(key, sourceId, buildContext(source, slot, instance, stack));
            });
            if (slot == SlotGroup.MAIN_HAND && !hasAttackSpeed[0]) {
                StatSourceId sourceId = new StatSourceId(slotPrefix + ":default");
                container.addModifier(new StatModifier(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED, sourceId, ModifierOp.FLAT, 4.0));
                contextRegistry.put(key, sourceId, new StatSourceContext(
                    "Default Attack Speed",
                    "&8Default",
                    "Fallback attack speed when no attack speed is provided.",
                    new ItemStack(org.bukkit.Material.BARRIER),
                    SourceCategory.DEFAULT,
                    slot.name()
                ));
            }
        });
    }

    private void clearSlotSources(StatContainer container, String slotPrefix) {
        container.debugView().forEach(snapshot -> snapshot.modifiers().forEach((op, bySource) -> {
            bySource.keySet().stream()
                .filter(sourceId -> sourceId.value().startsWith(slotPrefix))
                .forEach(container::clearSource);
        }));
    }

    private StatSourceContext buildContext(String sourceKey, SlotGroup slot, ItemInstance instance, ItemStack rawStack) {
        ItemStack display = rawStack == null ? new ItemStack(org.bukkit.Material.BARRIER) : ItemSanitizer.normalize(rawStack.clone());
        display = colorizeDisplay(instance, display);
        if ("base".equalsIgnoreCase(sourceKey)) {
            return new StatSourceContext(
                "Base Value",
                "&8Basic",
                "Base stat provided by this item.",
                display,
                SourceCategory.BASE,
                slot.name()
            );
        }
        if (sourceKey.startsWith("enchant:")) {
            String id = sourceKey.substring("enchant:".length());
            int level = instance.enchants().getOrDefault(id, 1);
            String humanName = humanizeId(id);
            String levelLabel = roman(level);
            return new StatSourceContext(
                humanName + " " + levelLabel,
                "&8Enchantment",
                "Stats coming from enchantments on the item.",
                display,
                SourceCategory.ENCHANT,
                slot.name()
            );
        }
        return new StatSourceContext(
            humanizeId(sourceKey),
            "&8Source",
            "Contribution from this source.",
            display,
            SourceCategory.UNKNOWN,
            slot.name()
        );
    }

    private ItemStack colorizeDisplay(ItemInstance instance, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean hasColor = meta.hasDisplayName() && meta.displayName() != null && meta.displayName().color() != null;
        VisualComponent visual = instance.definition().component(ComponentType.VISUAL, VisualComponent.class).orElse(null);
        if (hasColor || visual == null) {
            return stack;
        }
        Component baseName = meta.hasDisplayName() && meta.displayName() != null
            ? meta.displayName()
            : Component.translatable(instance.definition().material().translationKey());
        NamedTextColor color = rarityColor(visual.rarity());
        meta.displayName(baseName.color(color).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private NamedTextColor rarityColor(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.BLUE;
            case EPIC -> NamedTextColor.DARK_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }

    private String humanizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Source";
        }
        String trimmed = raw.contains(":") ? raw.substring(raw.lastIndexOf(':') + 1) : raw;
        String[] tokens = trimmed.split("[_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append(token.substring(0, 1).toUpperCase()).append(token.substring(1).toLowerCase());
        }
        return builder.isEmpty() ? trimmed : builder.toString();
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

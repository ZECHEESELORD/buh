package sh.harold.fulcrum.plugin.item.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.common.data.ledger.item.ItemInstanceRecord;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BlockedItemMasker {

    private static final List<String> BLOCKED_MARKERS = List.of("mobcaptains", "stellarity");
    private static final String MASK_ITEM_ID = "fulcrum:null_item";
    private static final Material FALLBACK_MASK_MATERIAL = Material.STRUCTURE_VOID;

    private final ItemRegistry registry;
    private final ItemPdc itemPdc;
    private final ItemLedgerRepository itemLedgerRepository;
    private final Logger logger;

    public BlockedItemMasker(ItemRegistry registry, ItemPdc itemPdc, ItemLedgerRepository itemLedgerRepository, Logger logger) {
        this.registry = registry;
        this.itemPdc = itemPdc;
        this.itemLedgerRepository = itemLedgerRepository;
        this.logger = logger != null ? logger : Logger.getLogger(BlockedItemMasker.class.getName());
    }

    public boolean isBlocked(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (matches(meta.displayName())) {
            return true;
        }
        if (meta.hasLore() && meta.getLore() != null) {
            for (Component line : meta.lore()) {
                if (matches(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ItemStack mask(ItemStack original) {
        ItemStack masked = registry.get(MASK_ITEM_ID)
            .map(this::createCustomMask)
            .orElseGet(this::createFallbackMask);
        UUID instanceId = itemPdc.readInstanceId(masked).orElse(null);
        if (instanceId == null) {
            masked = itemPdc.ensureInstanceId(masked);
            instanceId = itemPdc.readInstanceId(masked).orElse(null);
        }
        masked = itemPdc.ensureProvenance(masked, ItemCreationSource.MIGRATION, Instant.now());
        logMask(masked, instanceId);
        return masked;
    }

    private ItemStack createCustomMask(CustomItem definition) {
        ItemStack base = new ItemStack(definition.material());
        ItemStack withId = itemPdc.setId(base, definition.id());
        return itemPdc.ensureInstanceId(withId);
    }

    private ItemStack createFallbackMask() {
        ItemStack masked = new ItemStack(FALLBACK_MASK_MATERIAL);
        masked.setAmount(1);
        ItemMeta meta = masked.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Null Item", NamedTextColor.DARK_GRAY));
            meta.lore(List.of(
                Component.text("Gone. Reduced to null.", NamedTextColor.GRAY),
                Component.text("A small price to pay for balance.", NamedTextColor.GRAY)
            ));
            meta.addItemFlags(
                org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ARMOR_TRIM
            );
            masked.setItemMeta(meta);
        }
        masked = itemPdc.setId(masked, MASK_ITEM_ID);
        masked = itemPdc.ensureInstanceId(masked);
        return itemPdc.ensureProvenance(masked, ItemCreationSource.MIGRATION, Instant.now());
    }

    private boolean matches(Component component) {
        if (component == null) {
            return false;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
        return matches(plain);
    }

    private boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return BLOCKED_MARKERS.stream().anyMatch(lower::contains);
    }

    private void logMask(ItemStack masked, UUID instanceId) {
        if (itemLedgerRepository == null || instanceId == null) {
            return;
        }
        String itemId = itemPdc.readId(masked).orElse(MASK_ITEM_ID);
        ItemInstanceRecord record = new ItemInstanceRecord(instanceId, itemId, ItemCreationSource.SYSTEM, null, Instant.now());
        itemLedgerRepository.append(record).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to ledger-log masked item " + instanceId + " (" + itemId + ")", throwable);
            return null;
        });
    }
}

package sh.harold.fulcrum.plugin.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettingsService;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

final class DamageMarkerRenderer {

    private static final ThreadLocal<DecimalFormat> DAMAGE_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat format = new DecimalFormat("0.##");
        format.setGroupingUsed(false);
        return format;
    });
    private static final long LIFETIME_TICKS = 24L;
    private static final double HORIZONTAL_SPREAD = 0.45;
    private static final double VERTICAL_OFFSET_MIN = 0.35;
    private static final double VERTICAL_OFFSET_MAX = 0.85;
    private static final double VELOCITY_SPREAD = 0.03;
    private static final double BASE_RISE = 0.08;

    private final Plugin plugin;
    private final PlayerSettingsService settingsService;

    DamageMarkerRenderer(Plugin plugin, PlayerSettingsService settingsService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
    }

    void render(Player attacker, LivingEntity defender, double damage, boolean critical) {
        if (damage <= 0.0 || attacker == null || defender == null) {
            return;
        }
        if (!settingsService.cachedDamageMarkersEnabled(attacker.getUniqueId())) {
            return;
        }
        World world = defender.getWorld();
        Location spawnLocation = offset(defender);
        Component text = buildText(damage, critical);
        TextDisplay display = world.spawn(spawnLocation, TextDisplay.class, spawned -> {
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setShadowed(false);
            spawned.setSeeThrough(false);
            spawned.setViewRange(24.0f);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.text(text);
            spawned.setGravity(false);
            spawned.setVelocity(randomVelocity());
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, display::remove, LIFETIME_TICKS);
    }

    private Location offset(LivingEntity defender) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double xOffset = (random.nextDouble() * 2.0 - 1.0) * HORIZONTAL_SPREAD;
        double zOffset = (random.nextDouble() * 2.0 - 1.0) * HORIZONTAL_SPREAD;
        double vertical = defender.getHeight() * 0.45 + VERTICAL_OFFSET_MIN
            + random.nextDouble() * (VERTICAL_OFFSET_MAX - VERTICAL_OFFSET_MIN);
        return defender.getLocation().clone().add(xOffset, vertical, zOffset);
    }

    private Component buildText(double damage, boolean critical) {
        String formatted = DAMAGE_FORMAT.get().format(damage);
        Component base = Component.text(
            critical ? formatted + " \u2620" : formatted,
            critical ? NamedTextColor.RED : NamedTextColor.GRAY
        );
        return base.decoration(TextDecoration.ITALIC, false);
    }

    private Vector randomVelocity() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = (random.nextDouble() - 0.5) * VELOCITY_SPREAD;
        double z = (random.nextDouble() - 0.5) * VELOCITY_SPREAD;
        double y = BASE_RISE + random.nextDouble() * VELOCITY_SPREAD;
        return new Vector(x, y, z);
    }
}

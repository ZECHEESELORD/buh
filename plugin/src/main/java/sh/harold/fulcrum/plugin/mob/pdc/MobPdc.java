package sh.harold.fulcrum.plugin.mob.pdc;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.mob.MobNameMode;
import sh.harold.fulcrum.plugin.mob.MobTier;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MobPdc {

    private final MobDataKeys keys;
    private final MobStatPdcCodec statCodec = new MobStatPdcCodec();

    public MobPdc(Plugin plugin) {
        this.keys = new MobDataKeys(Objects.requireNonNull(plugin, "plugin"));
    }

    public MobDataKeys keys() {
        return keys;
    }

    public Optional<String> readId(LivingEntity entity) {
        return read(entity, keys.id(), PersistentDataType.STRING);
    }

    public void writeId(LivingEntity entity, String id) {
        write(entity, keys.id(), PersistentDataType.STRING, id);
    }

    public Optional<Integer> readVersion(LivingEntity entity) {
        return read(entity, keys.version(), PersistentDataType.INTEGER);
    }

    public void writeVersion(LivingEntity entity, int version) {
        write(entity, keys.version(), PersistentDataType.INTEGER, version);
    }

    public Optional<MobTier> readTier(LivingEntity entity) {
        return read(entity, keys.tier(), PersistentDataType.STRING).flatMap(raw -> {
            try {
                return Optional.of(MobTier.valueOf(raw.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
    }

    public void writeTier(LivingEntity entity, MobTier tier) {
        Objects.requireNonNull(tier, "tier");
        write(entity, keys.tier(), PersistentDataType.STRING, tier.name().toLowerCase());
    }

    public Optional<Map<StatId, Double>> readStatBases(LivingEntity entity) {
        return read(entity, keys.statBases(), PersistentDataType.STRING).map(statCodec::decode);
    }

    public void writeStatBases(LivingEntity entity, Map<StatId, Double> bases) {
        Objects.requireNonNull(bases, "bases");
        write(entity, keys.statBases(), PersistentDataType.STRING, statCodec.encode(bases));
    }

    public Optional<String> readNameBase(LivingEntity entity) {
        return read(entity, keys.nameBase(), PersistentDataType.STRING);
    }

    public void writeNameBase(LivingEntity entity, String name) {
        write(entity, keys.nameBase(), PersistentDataType.STRING, name);
    }

    public Optional<Long> readSeed(LivingEntity entity) {
        return read(entity, keys.seed(), PersistentDataType.LONG);
    }

    public void writeSeed(LivingEntity entity, long seed) {
        write(entity, keys.seed(), PersistentDataType.LONG, seed);
    }

    public Optional<MobNameMode> readNameMode(LivingEntity entity) {
        return read(entity, keys.nameMode(), PersistentDataType.STRING).flatMap(raw -> {
            try {
                return Optional.of(MobNameMode.valueOf(raw.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
    }

    public void writeNameMode(LivingEntity entity, MobNameMode mode) {
        Objects.requireNonNull(mode, "mode");
        write(entity, keys.nameMode(), PersistentDataType.STRING, mode.name().toLowerCase());
    }

    private <T, Z> Optional<Z> read(LivingEntity entity, org.bukkit.NamespacedKey key, PersistentDataType<T, Z> type) {
        if (entity == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (container == null || key == null || type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(container.get(key, type));
    }

    private <T, Z> void write(LivingEntity entity, org.bukkit.NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (entity == null || key == null || type == null) {
            return;
        }
        if (value == null) {
            entity.getPersistentDataContainer().remove(key);
            return;
        }
        entity.getPersistentDataContainer().set(key, type, value);
    }
}

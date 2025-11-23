package sh.harold.fulcrum.stats.service;

import sh.harold.fulcrum.stats.core.StatId;

public record StatChange(EntityKey entity, StatId statId, double oldValue, double newValue) {
}

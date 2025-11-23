package sh.harold.fulcrum.stats.binding;

import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.service.StatChange;

public interface StatBinding {

    StatId getStatId();

    void onStatChanged(StatChange change);
}

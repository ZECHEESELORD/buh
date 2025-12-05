package sh.harold.fulcrum.plugin.playermenu;

enum StatGrouping {
    OFF,
    ON
}

enum StatFlatten {
    ON,
    OFF
}

enum StatFilter {
    ALL,
    POSITIVE
}

record StatViewState(StatGrouping grouping, StatFlatten flatten, StatFilter filter) {
    static StatViewState defaultState() {
        return new StatViewState(StatGrouping.OFF, StatFlatten.ON, StatFilter.ALL);
    }

    StatViewState toggleGrouping() {
        return new StatViewState(
            grouping == StatGrouping.OFF ? StatGrouping.ON : StatGrouping.OFF,
            flatten,
            filter
        );
    }

    StatViewState toggleFlatten() {
        return new StatViewState(
            grouping,
            flatten == StatFlatten.ON ? StatFlatten.OFF : StatFlatten.ON,
            filter
        );
    }

    StatViewState toggleFilter() {
        return new StatViewState(
            grouping,
            flatten,
            filter == StatFilter.ALL ? StatFilter.POSITIVE : StatFilter.ALL
        );
    }

    boolean groupingEnabled() {
        return grouping == StatGrouping.ON;
    }

    boolean flattenEnabled() {
        return flatten == StatFlatten.ON;
    }

    boolean positiveOnly() {
        return filter == StatFilter.POSITIVE;
    }
}

package sh.harold.fulcrum.plugin.item.registry;

import sh.harold.fulcrum.plugin.item.model.CustomItem;

import java.util.List;

public interface ItemDefinitionProvider {
    List<CustomItem> definitions();
}

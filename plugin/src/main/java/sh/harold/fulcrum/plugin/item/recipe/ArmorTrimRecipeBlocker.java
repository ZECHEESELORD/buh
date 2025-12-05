package sh.harold.fulcrum.plugin.item.recipe;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArmorTrimRecipeBlocker implements Listener {

    private final Plugin plugin;

    public ArmorTrimRecipeBlocker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        removeArmorTrimDuplicationRecipes();
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        removeArmorTrimDuplicationRecipes();
    }

    private void removeArmorTrimDuplicationRecipes() {
        Server server = plugin.getServer();
        List<NamespacedKey> recipesToRemove = new ArrayList<>();
        server.recipeIterator().forEachRemaining(recipe -> {
            if (!(recipe instanceof Keyed keyed)) {
                return;
            }
            NamespacedKey key = keyed.getKey();
            if (isArmorTrimDuplicationRecipe(key)) {
                recipesToRemove.add(key);
            }
        });
        recipesToRemove.forEach(server::removeRecipe);
        if (!recipesToRemove.isEmpty()) {
            String removedList = recipesToRemove.stream()
                .map(NamespacedKey::asString)
                .toList()
                .toString();
            plugin.getLogger().info("Removed armor trim duplication recipes: " + removedList);
        }
    }

    private boolean isArmorTrimDuplicationRecipe(NamespacedKey key) {
        return Objects.equals(key.getNamespace(), NamespacedKey.MINECRAFT)
            && key.getKey().endsWith("_armor_trim_smithing_template");
    }
}

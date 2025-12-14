package sh.harold.fulcrum.plugin.unlockable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class CrawlCommand {

    private final UnlockableService unlockableService;
    private final CrawlManager crawlManager;

    public CrawlCommand(UnlockableService unlockableService, CrawlManager crawlManager) {
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.crawlManager = Objects.requireNonNull(crawlManager, "crawlManager");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("crawl")
            .executes(this::execute)
            .build();
    }


    private int execute(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can crawl.", NamedTextColor.RED));
            return 0;
        }

        UUID playerId = player.getUniqueId();
        boolean hasCrawl = unlockableService.cachedState(playerId)
            .map(state -> state.equippedCosmetics(CosmeticSection.ACTIONS).contains(UnlockableCatalog.CRAWL_ACTION))
            .orElse(false);

        boolean crawling = crawlManager.isCrawling(playerId);
        if (!hasCrawl && !crawling) {
            player.sendMessage(Component.text("You need the Crawl action equipped to use this.", NamedTextColor.RED));
            return 0;
        }

        CrawlManager.CrawlToggleResult result = crawlManager.toggle(player);
        if (result == CrawlManager.CrawlToggleResult.FAILED) {
            player.sendMessage(Component.text("Crawl fizzled; try again in a moment.", NamedTextColor.RED));
            return 0;
        }
        boolean target = result == CrawlManager.CrawlToggleResult.STARTED;
        player.sendMessage(target
            ? Component.text("You drop into a crawl.", NamedTextColor.GREEN)
            : Component.text("You stand back up.", NamedTextColor.YELLOW));
        context.getSource().getSender().getServer().getLogger().info("Crawl command toggled swimming=" + target + " for " + player.getUniqueId());
        return Command.SINGLE_SUCCESS;
    }
}

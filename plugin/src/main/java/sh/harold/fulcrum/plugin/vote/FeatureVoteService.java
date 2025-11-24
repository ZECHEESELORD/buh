package sh.harold.fulcrum.plugin.vote;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.CustomMenuBuilder;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.message.Message;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FeatureVoteService {

    private static final int MENU_ROWS = 6;
    private static final int BAR_HEIGHT = MENU_ROWS - 1;
    private static final Material BACKGROUND = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material TRACK_MATERIAL = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private static final int LORE_WRAP_WORDS = 5;
    private static final String SELECTION_PATH = "selection";

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final DocumentCollection ballots;
    private final Logger logger;

    public FeatureVoteService(JavaPlugin plugin, DataApi dataApi, MenuService menuService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.ballots = Objects.requireNonNull(dataApi, "dataApi").collection("feature_votes");
        this.logger = plugin.getLogger();
    }

    public CompletionStage<Void> openMenu(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        return computeState(playerId)
            .thenCompose(state -> renderMenu(player, state))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open feature vote menu for " + playerId, throwable);
                player.sendMessage(Component.text("The voting board is snoozing; try again in a moment.", NamedTextColor.RED));
                return null;
            });
    }

    public CompletionStage<Boolean> castVote(UUID playerId, FeatureVoteOption option) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(option, "option");

        return ballots.load(playerId.toString())
            .thenCompose(document -> {
                FeatureVoteOption current = readSelection(document);
                if (option.equals(current)) {
                    return CompletableFuture.completedFuture(false);
                }
                return document.set(SELECTION_PATH, option.id())
                    .thenApply(ignored -> true);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to store vote for " + playerId, throwable);
            });
    }

    private CompletionStage<FeatureVoteState> computeState(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        return ballots.all()
            .thenApply(documents -> {
                Map<FeatureVoteOption, Integer> counts = new EnumMap<>(FeatureVoteOption.class);
                FeatureVoteOption selected = null;

                for (FeatureVoteOption option : FeatureVoteOption.values()) {
                    counts.put(option, 0);
                }

                for (Document document : documents) {
                    FeatureVoteOption option = readSelection(document);
                    if (option == null) {
                        continue;
                    }
                    counts.merge(option, 1, Integer::sum);
                    if (document.key().id().equalsIgnoreCase(playerId.toString())) {
                        selected = option;
                    }
                }

                int total = counts.values().stream().mapToInt(Integer::intValue).sum();
                return new FeatureVoteState(counts, total, selected);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to compute feature vote state", throwable);
            });
    }

    private CompletionStage<Void> renderMenu(Player player, FeatureVoteState state) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                CustomMenuBuilder builder = menuService.createMenuBuilder()
                    .title(Component.text("Feature Votes", NamedTextColor.GOLD))
                    .rows(MENU_ROWS)
                    .autoCloseButton(false)
                    .fillEmpty(BACKGROUND);

                drawColumns(builder, state);
                addOptionButtons(builder, state);

                builder.buildAsync(player)
                    .whenComplete((menu, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(null);
                        }
                    });
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private void drawColumns(CustomMenuBuilder builder, FeatureVoteState state) {
        for (FeatureVoteOption option : FeatureVoteOption.values()) {
            drawColumn(builder, option, state);
        }
    }

    private void drawColumn(CustomMenuBuilder builder, FeatureVoteOption option, FeatureVoteState state) {
        for (int row = 0; row < BAR_HEIGHT; row++) {
            builder.addItem(trackItem(option), row, option.zeroBasedColumn());
        }

        int filled = state.filledSegments(option, BAR_HEIGHT);
        for (int row = BAR_HEIGHT - filled; row < BAR_HEIGHT; row++) {
            builder.addItem(barItem(option, state), row, option.zeroBasedColumn());
        }
    }

    private MenuDisplayItem trackItem(FeatureVoteOption option) {
        Component name = Component.text(option.displayName(), NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false);
        return MenuDisplayItem.builder(TRACK_MATERIAL)
            .name(name)
            .build();
    }

    private MenuDisplayItem barItem(FeatureVoteOption option, FeatureVoteState state) {
        String percent = percentLabel(state.percentage(option));
        Component name = Component.text(percent + " of " + option.displayName(), option.color())
            .decoration(TextDecoration.ITALIC, false);
        return MenuDisplayItem.builder(option.barMaterial())
            .name(name)
            .description("Click the matching pad to pledge your vote.")
            .build();
    }

    private void addOptionButtons(CustomMenuBuilder builder, FeatureVoteState state) {
        int row = MENU_ROWS - 1;
        for (FeatureVoteOption option : FeatureVoteOption.values()) {
            int slot = row * 9 + option.zeroBasedColumn();
            MenuButton button = buildVoteButton(option, state, slot);
            builder.addButton(button);
        }
    }

    private MenuButton buildVoteButton(FeatureVoteOption option, FeatureVoteState state, int slot) {
        boolean selected = option.equals(state.selectedOption());
        int votes = state.tallies().getOrDefault(option, 0);

        MenuButton button = MenuButton.builder(option.material())
            .name(optionTitle(option))
            .lore(buildVoteLore(option, votes, selected).toArray(Component[]::new))
            .onClick(player -> handleVote(player, option))
            .slot(slot)
            .skipClickPrompt()
            .build();

        if (selected) {
            ItemStack display = button.getDisplayItem();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                display.setItemMeta(meta);
                button.setDisplayItem(display);
            }
        }

        return button;
    }

    private List<Component> buildVoteLore(FeatureVoteOption option, int votes, boolean selected) {
        List<Component> lore = new ArrayList<>();
        lore.add(voteStatusLine(option, selected));
        lore.add(Component.text("Gameplay Feature", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.empty());
        lore.add(Component.text(voteCountLabel(votes), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.empty());
        lore.add(Component.text(option.displayName(), option.color()).decoration(TextDecoration.ITALIC, false));
        lore.addAll(wrapText(option.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.addAll(wrapText("You may change your vote at any time until the votes end!", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Click to vote!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    private Component voteStatusLine(FeatureVoteOption option, boolean selected) {
        Component name = Component.text(option.displayName(), option.color())
            .decoration(TextDecoration.ITALIC, false);
        if (!selected) {
            return name;
        }
        Component voted = Component.text("VOTED", NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false);
        return name.append(Component.space()).append(voted);
    }

    private String voteCountLabel(int votes) {
        return votes == 1 ? "1 Vote" : votes + " Votes";
    }

    private List<Component> wrapText(String text, NamedTextColor color) {
        List<Component> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        int count = 0;

        for (String word : words) {
            if (count == LORE_WRAP_WORDS) {
                lines.add(coloredLine(current.toString(), color));
                current = new StringBuilder();
                count = 0;
            }
            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(word);
            count++;
        }

        if (current.length() > 0) {
            lines.add(coloredLine(current.toString(), color));
        }

        return lines;
    }

    private Component coloredLine(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component optionTitle(FeatureVoteOption option) {
        return Component.text(option.displayName(), option.color())
            .decoration(TextDecoration.ITALIC, false);
    }

    private String percentLabel(double percent) {
        return String.format(Locale.US, "%.0f%%", percent);
    }

    private void handleVote(Player player, FeatureVoteOption option) {
        UUID playerId = player.getUniqueId();
        castVote(playerId, option)
            .thenCompose(changed -> {
                if (changed) {
                    Message.success("Vote saved: {0}.", option.displayName()).send(player);
                } else {
                    Message.info("You already back {0}; no changes.", option.displayName()).send(player);
                }
                return openMenu(player);
            })
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to record vote for " + playerId, throwable);
                player.sendMessage(Component.text("Could not record that vote right now; try again shortly.", NamedTextColor.RED));
                return null;
            });
    }

    private FeatureVoteOption readSelection(Document document) {
        return document.get(SELECTION_PATH, String.class)
            .flatMap(FeatureVoteOption::fromId)
            .orElse(null);
    }
}

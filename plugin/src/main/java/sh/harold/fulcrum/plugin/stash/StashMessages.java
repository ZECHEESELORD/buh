package sh.harold.fulcrum.plugin.stash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class StashMessages {

    private static final String PICKUP_COMMAND = "/pickupstash";
    private static final int CENTER_WIDTH = 56;

    private StashMessages() {
    }

    static Component reminderTitle(int totalItems) {
        String line = "You have " + totalItems + ' ' + plural(totalItems) + " stashed away!";
        return centered(line, NamedTextColor.GOLD);
    }

    static Component reminderAction(int totalItems) {
        String line = ">>> CLICK HERE to pick " + pronoun(totalItems) + " up! <<<";
        Component action = centered(line, NamedTextColor.AQUA)
            .hoverEvent(HoverEvent.showText(Component.text("Click to withdraw your stash", NamedTextColor.YELLOW)))
            .clickEvent(ClickEvent.runCommand(PICKUP_COMMAND));
        return action;
    }

    static Component stashDepositNotice(String stashLabel) {
        Component intro = Component.text("One or more items didn't fit into your inventory and were added to your " + stashLabel + " stash! ", NamedTextColor.YELLOW);
        Component callToAction = Component.text("Click Here", NamedTextColor.GOLD);
        Component outro = Component.text(" to pick them up!", NamedTextColor.YELLOW);

        return Component.text()
            .append(intro)
            .append(callToAction)
            .append(outro)
            .hoverEvent(HoverEvent.showText(Component.text("Withdraw now", NamedTextColor.YELLOW)))
            .clickEvent(ClickEvent.runCommand(PICKUP_COMMAND))
            .build();
    }

    private static Component centered(String content, NamedTextColor color) {
        String padding = computePadding(content);
        return Component.text(padding + content, color)
            .decoration(TextDecoration.BOLD, true);
    }

    private static String computePadding(String content) {
        int padding = Math.max((CENTER_WIDTH - content.length()) / 2, 0);
        if (padding <= 0) {
            return "";
        }
        return " ".repeat(padding);
    }

    private static String pronoun(int totalItems) {
        return totalItems == 1 ? "it" : "them";
    }

    private static String plural(int totalItems) {
        return totalItems == 1 ? "item" : "items";
    }
}

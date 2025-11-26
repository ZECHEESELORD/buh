package sh.harold.fulcrum.plugin.discordbot;

import net.dv8tion.jda.api.JDA;

public interface DiscordFeature {

    void register(JDA jda);
}

package sh.harold.fulcrum.plugin.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import sh.harold.fulcrum.plugin.accountlink.AccountLinkConfig;
import sh.harold.fulcrum.plugin.accountlink.StateCodec;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplate;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplateLoader;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplateMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DiscordBotService implements AutoCloseable {

    private static final String RULES_STATE_KEY = "rules-message-id";
    private static final String LINK_STATE_KEY = "link-message-id";

    private final Logger logger;
    private final DiscordBotConfig config;
    private final AccountLinkConfig linkConfig;
    private final Path configRoot;
    private final SourcesConfig sourcesConfig;
    private final sh.harold.fulcrum.common.data.DataApi dataApi;
    private final java.util.Set<Long> acceptedUsers = ConcurrentHashMap.newKeySet();
    private JDA jda;
    private MessageStateStore stateStore;

    public DiscordBotService(Logger logger, DiscordBotConfig config, AccountLinkConfig linkConfig, Path configRoot, SourcesConfig sourcesConfig, sh.harold.fulcrum.common.data.DataApi dataApi) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.config = Objects.requireNonNull(config, "config");
        this.linkConfig = Objects.requireNonNull(linkConfig, "linkConfig");
        this.configRoot = Objects.requireNonNull(configRoot, "configRoot");
        this.sourcesConfig = Objects.requireNonNull(sourcesConfig, "sourcesConfig");
        this.dataApi = Objects.requireNonNull(dataApi, "dataApi");
    }

    public CompletionStage<Void> start() {
        if (!config.enabled()) {
            logger.warning("Discord bot token not configured; bot features are disabled.");
            return CompletableFuture.completedFuture(null);
        }
        if (!linkConfig.hasSecret()) {
            logger.warning("Account link secret missing; bot cannot produce valid state.");
            return CompletableFuture.completedFuture(null);
        }
        try {
            JDABuilder builder = JDABuilder.createDefault(config.token())
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .enableIntents(Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .setAutoReconnect(true);
            jda = builder.build();
            jda.awaitReady();

            StateCodec stateCodec = new StateCodec(linkConfig.hmacSecret(), linkConfig.secretVersion());
            MojangClient mojangClient = new MojangClient(logger);
            MessageTemplate whitelistTemplate = MessageTemplateLoader.load(
                configRoot.resolve(config.whitelistTemplate()),
                Defaults.whitelistTemplate(),
                logger
            );
            MessageTemplate reviewTemplate = MessageTemplateLoader.load(
                configRoot.resolve(config.reviewTemplate()),
                Defaults.reviewTemplate(),
                logger
            );
            MessageTemplate decisionTemplate = MessageTemplateLoader.load(
                configRoot.resolve(config.decisionTemplate()),
                Defaults.decisionTemplate(),
                logger
            );
            MessageTemplate sponsorDmTemplate = MessageTemplateLoader.load(
                configRoot.resolve(config.sponsorDmTemplate()),
                Defaults.sponsorDmTemplate(),
                logger
            );
            MessageTemplate sponsorPingTemplate = MessageTemplateLoader.load(
                configRoot.resolve(config.sponsorPingTemplate()),
                Defaults.sponsorPingTemplate(),
                logger
            );

            RequestStore requestStore = new RequestStore(dataApi);
            SponsorRequestStore sponsorRequestStore = new SponsorRequestStore(dataApi);
            OsuLookupService osuLookupService = new OsuLookupService(dataApi);

            LinkDiscordFeature linkFeature = new LinkDiscordFeature(
                stateCodec,
                linkConfig,
                mojangClient,
                logger,
                acceptedUsers,
                () -> sourcesConfig.sources(),
                whitelistTemplate,
                reviewTemplate,
                decisionTemplate,
                sponsorDmTemplate,
                sponsorPingTemplate,
                dataApi,
                requestStore,
                sponsorRequestStore,
                osuLookupService,
                config
            );
            linkFeature.register(jda);
            linkFeature.restoreSponsorRequests();

            stateStore = new MessageStateStore(configRoot.resolve(config.stateFile()), logger);
            ensurePersistentMessages(linkFeature);

            logger.info("Discord bot connected.");
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed to start Discord bot", exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public void close() {
        if (jda != null) {
            jda.shutdown();
            try {
                jda.awaitShutdown();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            jda = null;
        }
    }

    private void ensurePersistentMessages(LinkDiscordFeature linkFeature) {
        if (!config.hasChannels()) {
            logger.warning("Discord bot channels are not configured; skipping persistent message setup.");
            return;
        }
        MessageTemplate rulesTemplate = MessageTemplateLoader.load(
            configRoot.resolve(config.rulesTemplate()),
            Defaults.rulesTemplate(),
            logger
        );
        MessageTemplate linkTemplate = MessageTemplateLoader.load(
            configRoot.resolve(config.linkTemplate()),
            Defaults.linkTemplate(),
            logger
        );
        MessageTemplate reviewTemplate = MessageTemplateLoader.load(
            configRoot.resolve(config.reviewTemplate()),
            Defaults.reviewTemplate(),
            logger
        );

        long rulesMessageId = ensureMessage(
            config.rulesChannelId(),
            rulesTemplate,
            linkFeature.rulesComponents(),
            RULES_STATE_KEY
        );
        long linkMessageId = ensureMessage(
            config.linkChannelId(),
            linkTemplate,
            linkFeature.linkComponents(),
            LINK_STATE_KEY
        );
        linkFeature.setAnchorMessages(rulesMessageId, linkMessageId);
    }

    private long ensureMessage(long channelId, MessageTemplate template, java.util.List<net.dv8tion.jda.api.interactions.components.LayoutComponent> components, String stateKey) {
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            throw new IllegalStateException("Channel " + channelId + " not found for Discord bot message " + stateKey);
        }
        MessageTemplate nonNullTemplate = template != null ? template : Defaults.linkTemplate();
        MessageCreateData messageData = MessageTemplateMapper.toMessage(nonNullTemplate);

        // Try existing
        Message existing = stateStore.messageId(stateKey)
            .map(id -> retrieveMessage(channel, id))
            .orElse(null);

        if (existing != null) {
            return existing.getIdLong();
        }

        Message sent = channel.sendMessage(messageData)
            .setComponents(components)
            .complete();
        stateStore.put(stateKey, sent.getIdLong());
        return sent.getIdLong();
    }

    private Message retrieveMessage(MessageChannel channel, long messageId) {
        try {
            return channel.retrieveMessageById(messageId).complete();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static DiscordBotService withLinkFeature(Logger logger, DiscordBotConfig config, AccountLinkConfig linkConfig, Path configRoot, SourcesConfig sourcesConfig, sh.harold.fulcrum.common.data.DataApi dataApi) {
        return new DiscordBotService(logger, config, linkConfig, configRoot, sourcesConfig, dataApi);
    }

    private static final class Defaults {
        private static MessageTemplate rulesTemplate() {
            return new MessageTemplate(
                "Please accept the rules before linking.",
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "Server Rules",
                    "Click the button below to accept the rules.",
                    null,
                    0x00AAAA,
                    java.util.List.of(),
                    null,
                    null,
                    null
                ))
            );
        }

        private static MessageTemplate linkTemplate() {
            return new MessageTemplate(
                null,
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "Complete *after* agreeing to rules.",
                    "# Whitelist Request\n-# Click \"Start Whitelist Process\" to start!",
                    null,
                    0xFFFFFF,
                    java.util.List.of(
                        new MessageTemplate.FieldTemplate("Required:", "Where did you find us?", true),
                        new MessageTemplate.FieldTemplate("Required:", "Minecraft Username", true),
                        new MessageTemplate.FieldTemplate("Required:", "osu! Account Link", true)
                    ),
                    null,
                    null,
                    null
                ))
            );
        }

        private static MessageTemplate whitelistTemplate() {
            return new MessageTemplate(
                null,
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "Complete Whitelist Process Here!",
                    "-# *In a realm whose cartography scholars rejected for excessive stupidity, there loomed a kingdom named Tenderia, where all citizens were chicken nuggets. Not chickens that became nuggets. Nuggets that… spontaneously existed. Philosophers argued about this until they got hungry and ate each other, which was considered both rude and a valid form of debate.*\n## Link Steps: `{completed}`/`{total}`",
                    null,
                    0xD6006B,
                    java.util.List.of(
                        new MessageTemplate.FieldTemplate("❌ **Where did you come from?**", "> `Waiting for response...`", false),
                        new MessageTemplate.FieldTemplate("❌ Minecraft Username", "> `Waiting for response...`", true),
                        new MessageTemplate.FieldTemplate("❌ osu! Account Link", "> `Waiting...`", true)
                    ),
                    null,
                    null,
                    null
                ))
            );
        }

        private static MessageTemplate reviewTemplate() {
            return new MessageTemplate(
                null,
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "Whitelist Request!",
                    "# <@{discordId}> ({createdAt})\n# From: `{source}`",
                    null,
                    0xFFBB04,
                    java.util.List.of(
                        new MessageTemplate.FieldTemplate("osu!username", "`{osuUsername}`", true),
                        new MessageTemplate.FieldTemplate("osu!rank", "`{osuRankFormatted}`", true),
                        new MessageTemplate.FieldTemplate("osu!country", "`{osuCountry}` :flag_{osuCountryLower}:", true),
                        new MessageTemplate.FieldTemplate("Minecraft Username:", "`{minecraftUsername}`", false)
                    ),
                    null,
                    null,
                    null
                ))
            );
        }

        private static MessageTemplate decisionTemplate() {
            return new MessageTemplate(
                null,
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "{title}",
                    "## <@{discordId}> ({createdAt})\n## From: `{source}`",
                    null,
                    0x4D9B40,
                    java.util.List.of(
                        new MessageTemplate.FieldTemplate("osu!username", "`{osuUsername}`", true),
                        new MessageTemplate.FieldTemplate("osu!rank", "`{osuRankFormatted}`", true),
                        new MessageTemplate.FieldTemplate("osu!country", "`{osuCountry}` :flag_{osuCountryLower}:", true),
                        new MessageTemplate.FieldTemplate("Minecraft Username:", "`{minecraftUsername}`", false)
                    ),
                    new MessageTemplate.FooterTemplate("Approved by {moderator}", null),
                    null,
                    null
                ))
            );
        }

        private static MessageTemplate sponsorDmTemplate() {
            return new MessageTemplate(
                null,
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "🎟️ Vouch Request: `[Player Name]`",
                    "<@{discordId}> has applied to join the server and listed you as their sponsor.\n-# Since we operate on trust, we need you to confirm that you actually know this person and invited them.",
                    null,
                    0xFF7775,
                    java.util.List.of(
                        new MessageTemplate.FieldTemplate("Info:", "> osu! Username: `{osuUsername}` \n> Minecraft Username: `{minecraftUsername}`\n> Discord Username: <@{discordId}>", false),
                        new MessageTemplate.FieldTemplate("⚠️ **Read Before Confirming**", "By vouching for this player, **you are accepting responsibility for their actions.**\n\nIf they grief, cheat, or break the rules, **your ability to invite future players may be revoked.** Do not vouch for them if you don't trust them not to blow up spawn.", false)
                    ),
                    null,
                    null,
                    null
                ))
            );
        }

        private static MessageTemplate sponsorPingTemplate() {
            return new MessageTemplate(
                "Hey <@{sponsorId}>, you were listed as a sponsor. Click below to respond.",
                java.util.List.of(new MessageTemplate.EmbedTemplate(
                    "Sponsor Confirmation Needed",
                    "We could not DM you; please confirm or deny the request via the buttons.",
                    null,
                    0xFF7775,
                    java.util.List.of(),
                    null,
                    null,
                    null
                ))
            );
        }
    }
}

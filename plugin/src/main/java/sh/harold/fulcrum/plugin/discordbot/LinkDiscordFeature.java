package sh.harold.fulcrum.plugin.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.plugin.accountlink.AccountLinkConfig;
import sh.harold.fulcrum.plugin.accountlink.LinkState;
import sh.harold.fulcrum.plugin.accountlink.StateCodec;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplate;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplateMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LinkDiscordFeature extends ListenerAdapter implements DiscordFeature {

    private static final String COMMAND_NAME = "link";
    private static final String MODAL_ID = "link-modal";
    private static final String USERNAME_INPUT_ID = "minecraft_username";
    private static final String INVITER_MODAL_ID = "inviter-modal";
    private static final String INVITER_INPUT_ID = "inviter_username";
    private static final String START_WHITELIST_BUTTON_ID = "whitelist-start";
    private static final String ACCEPT_RULES_BUTTON_ID = "rules-accept";
    private static final String SOURCE_SELECT_ID = "source-select";
    private static final String INVITER_BUTTON_ID = "inviter-button";
    private static final String USERNAME_BUTTON_ID = "username-button";
    private static final String OSU_BUTTON_ID = "osu-button";
    private static final String SUBMIT_BUTTON_ID = "submit-button";
    private static final String REVIEW_APPROVE_ID = "review-approve";
    private static final String REVIEW_DENY_ID = "review-deny";
    private static final String SPONSOR_YES_ID = "sponsor-yes";
    private static final String SPONSOR_NO_ID = "sponsor-no";
    private static final String INVITED_BY_FRIEND = "Invited By Friend";
    private static final String SPONSOR_FLAG_PATH = "meta.cannotInviteOthers";
    private static final Duration SPONSOR_TIMEOUT = Duration.ofHours(24);

    private final StateCodec stateCodec;
    private final AccountLinkConfig linkConfig;
    private final MojangClient mojangClient;
    private final Logger logger;
    private final java.util.Set<Long> acceptedUsers;
    private final Supplier<List<String>> sourcesSupplier;
    private final MessageTemplate whitelistTemplate;
    private final MessageTemplate reviewTemplate;
    private final MessageTemplate decisionTemplate;
    private final MessageTemplate sponsorDmTemplate;
    private final MessageTemplate sponsorPingTemplate;
    private final DataApi dataApi;
    private final RequestStore requestStore;
    private final SponsorRequestStore sponsorRequestStore;
    private final OsuLookupService osuLookupService;
    private final DiscordBotConfig botConfig;
    private final Map<Long, Session> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<Long, Session> sessionsByReviewMessage = new ConcurrentHashMap<>();
    private final Map<Long, Session> sessionsBySponsorMessage = new ConcurrentHashMap<>();
    private JDA jda;

    public LinkDiscordFeature(
        StateCodec stateCodec,
        AccountLinkConfig linkConfig,
        MojangClient mojangClient,
        Logger logger,
        java.util.Set<Long> acceptedUsers,
        Supplier<List<String>> sourcesSupplier,
        MessageTemplate whitelistTemplate,
        MessageTemplate reviewTemplate,
        MessageTemplate decisionTemplate,
        MessageTemplate sponsorDmTemplate,
        MessageTemplate sponsorPingTemplate,
        DataApi dataApi,
        RequestStore requestStore,
        SponsorRequestStore sponsorRequestStore,
        OsuLookupService osuLookupService,
        DiscordBotConfig botConfig
    ) {
        this.stateCodec = stateCodec;
        this.linkConfig = linkConfig;
        this.mojangClient = mojangClient;
        this.logger = logger;
        this.acceptedUsers = acceptedUsers;
        this.sourcesSupplier = sourcesSupplier;
        this.whitelistTemplate = whitelistTemplate;
        this.reviewTemplate = reviewTemplate;
        this.decisionTemplate = decisionTemplate;
        this.sponsorDmTemplate = sponsorDmTemplate;
        this.sponsorPingTemplate = sponsorPingTemplate;
        this.dataApi = dataApi;
        this.requestStore = requestStore;
        this.sponsorRequestStore = sponsorRequestStore;
        this.osuLookupService = osuLookupService;
        this.botConfig = botConfig;
    }

    @Override
    public void register(JDA jda) {
        this.jda = jda;
        jda.updateCommands().addCommands(
            Commands.slash(COMMAND_NAME, "Link your Minecraft account with osu")
        ).queue();
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!COMMAND_NAME.equals(event.getName())) {
            return;
        }
        if (!acceptedUsers.contains(event.getUser().getIdLong())) {
            event.reply("Please accept the rules above first.").setEphemeral(true).queue();
            return;
        }
        Session session = sessionsByUser.computeIfAbsent(event.getUser().getIdLong(), ignored -> Session.forUser(event.getUser().getIdLong(), event.getUser().getName()));
        sendWhitelistPrompt(event, session);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        long userId = event.getUser().getIdLong();
        if (ACCEPT_RULES_BUTTON_ID.equals(id)) {
            acceptedUsers.add(userId);
            event.reply("Thanks! You can now start the whitelist process.").setEphemeral(true).queue();
            return;
        }
        if (REVIEW_APPROVE_ID.equals(id) || REVIEW_DENY_ID.equals(id)) {
            handleStaffDecision(event, REVIEW_APPROVE_ID.equals(id));
            return;
        }
        if (SPONSOR_YES_ID.equals(id) || SPONSOR_NO_ID.equals(id)) {
            handleSponsorDecision(event, SPONSOR_YES_ID.equals(id));
            return;
        }

        Session session = sessionsByUser.computeIfAbsent(userId, ignored -> Session.forUser(userId, event.getUser().getName()));

        switch (id) {
            case START_WHITELIST_BUTTON_ID -> {
                if (!acceptedUsers.contains(userId)) {
                    event.reply("Please accept the rules above first.").setEphemeral(true).queue();
                    return;
                }
                sendWhitelistPrompt(event, session);
            }
            case INVITER_BUTTON_ID -> openInviterModal(event);
            case USERNAME_BUTTON_ID -> openUsernameModal(event);
            case OSU_BUTTON_ID -> handleOsuLink(event, session);
            case SUBMIT_BUTTON_ID -> handleSubmit(event, session);
            default -> {
            }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        long userId = event.getUser().getIdLong();
        Session session = sessionsByUser.computeIfAbsent(userId, ignored -> Session.forUser(userId, event.getUser().getName()));
        if (MODAL_ID.equals(modalId)) {
            String username = optionalValue(event, USERNAME_INPUT_ID);
            if (username == null || username.isBlank()) {
                event.reply("Give me a Minecraft username and try again.").setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue();
            mojangClient.lookup(username)
                .toCompletableFuture()
                .whenComplete((profile, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.SEVERE, "Mojang lookup failed for " + username, throwable);
                        event.getHook().sendMessage("Lookup failed; please try again in a moment.").setEphemeral(true).queue();
                        return;
                    }
                    Optional<MojangClient.MojangProfile> mojangProfile = profile;
                    if (mojangProfile.isEmpty()) {
                        event.getHook().sendMessage("Account not found. Double check the username.").setEphemeral(true).queue();
                        return;
                    }
                    UUID uuid = parseDashed(mojangProfile.get().id());
                    session.minecraftUsername = mojangProfile.get().name();
                    session.minecraftId = uuid;
                    event.getHook().sendMessage("Got it! Username set to **" + session.minecraftUsername + "**.").setEphemeral(true).queue();
                });
            return;
        }
        if (INVITER_MODAL_ID.equals(modalId)) {
            String inviter = optionalValue(event, INVITER_INPUT_ID);
            if (inviter == null || inviter.isBlank()) {
                event.reply("Please provide the inviter's username.").setEphemeral(true).queue();
                return;
            }
            session.invitedBy = inviter.trim();
            event.reply("Inviter set to **" + session.invitedBy + "**.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!SOURCE_SELECT_ID.equals(event.getComponentId())) {
            return;
        }
        long userId = event.getUser().getIdLong();
        Session session = sessionsByUser.computeIfAbsent(userId, ignored -> Session.forUser(userId, event.getUser().getName()));
        String chosen = event.getValues().isEmpty() ? null : event.getValues().get(0);
        session.source = chosen;
        event.reply("Source set to **" + chosen + "**.").setEphemeral(true).queue();
    }

    private void openInviterModal(ButtonInteractionEvent event) {
        Modal modal = Modal.create(INVITER_MODAL_ID, "Who invited you?")
            .addActionRow(TextInput.create(INVITER_INPUT_ID, "Inviter Username", TextInputStyle.SHORT).setRequired(true).build())
            .build();
        event.replyModal(modal).queue();
    }

    private void openUsernameModal(ButtonInteractionEvent event) {
        TextInput usernameInput = TextInput.create(USERNAME_INPUT_ID, "Minecraft Username", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("Not your email; use in-game name")
            .build();
        Modal modal = Modal.create(MODAL_ID, "Link your account")
            .addActionRow(usernameInput)
            .build();
        event.replyModal(modal).queue();
    }

    public List<LayoutComponent> rulesComponents() {
        return List.of(
            ActionRow.of(
                Button.success(ACCEPT_RULES_BUTTON_ID, "Accept rules")
            )
        );
    }

    public List<LayoutComponent> linkComponents() {
        return List.of(
            ActionRow.of(
                Button.primary(START_WHITELIST_BUTTON_ID, "Start Whitelist Process")
            )
        );
    }

    public void setAnchorMessages(long rulesMessageId, long linkMessageId) {
        // anchors are tracked via MessageStateStore in the service; no-op here
    }

    private void sendWhitelistPrompt(SlashCommandInteractionEvent event, Session session) {
        MessageCreateData data = buildWhitelistMessage(session);
        event.reply(data)
            .setEphemeral(true)
            .setComponents(buildComponents(session))
            .queue();
    }

    private void sendWhitelistPrompt(ButtonInteractionEvent event, Session session) {
        MessageCreateData data = buildWhitelistMessage(session);
        event.reply(data)
            .setEphemeral(true)
            .setComponents(buildComponents(session))
            .queue();
    }

    private MessageCreateData buildWhitelistMessage(Session session) {
        MessageCreateBuilder builder = new MessageCreateBuilder();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Complete Whitelist Process Here!");
        embedBuilder.setDescription("-# *In a realm whose cartography scholars rejected for excessive stupidity, there loomed a kingdom named Tenderia, where all citizens were chicken nuggets. Not chickens that became nuggets. Nuggets that… spontaneously existed. Philosophers argued about this until they got hungry and ate each other, which was considered both rude and a valid form of debate.*\n## Link Steps: `{completed}`/`{total}`");
        embedBuilder.setColor(0xD6006B);

        int total = 3;
        int completed = 0;

        boolean sourceDone = session.source != null && (!INVITED_BY_FRIEND.equals(session.source) || (session.invitedBy != null && !session.invitedBy.isBlank()));
        boolean usernameDone = session.minecraftId != null && session.minecraftUsername != null;
        boolean osuDone = session.osuInfo != null;
        completed += sourceDone ? 1 : 0;
        completed += usernameDone ? 1 : 0;
        completed += osuDone ? 1 : 0;

        String description = embedBuilder.getDescriptionBuilder().toString()
            .replace("{completed}", Integer.toString(completed))
            .replace("{total}", Integer.toString(total));
        embedBuilder.setDescription(description);

        embedBuilder.addField(
            (sourceDone ? "✅" : "❌") + " Where did you come from?",
            sourceDone
                ? "> `" + session.source + (INVITED_BY_FRIEND.equals(session.source) && session.invitedBy != null ? " (Invited by " + session.invitedBy + ")" : "") + "`"
                : "> `Waiting for response...`",
            false
        );
        embedBuilder.addField(
            (usernameDone ? "✅" : "❌") + " Minecraft Username",
            usernameDone ? "> `" + session.minecraftUsername + "`" : "> `Waiting for response...`",
            true
        );
        embedBuilder.addField(
            (osuDone ? "✅" : "❌") + " osu! Account Link",
            osuDone ? "> `Linked via osu!`" : "> `Waiting...`",
            true
        );
        builder.addEmbeds(embedBuilder.build());
        return builder.build();
    }

    private List<LayoutComponent> buildComponents(Session session) {
        List<String> sources = sourcesSupplier.get();
        if (sources == null || sources.isEmpty()) {
            sources = List.of("Other");
        }
        List<SelectOption> options = sources.stream()
            .map(source -> {
                SelectOption option = SelectOption.of(source, source);
                if (session.source != null && session.source.equals(source)) {
                    option = option.withDefault(true);
                }
                return option;
            })
            .toList();
        StringSelectMenu select = StringSelectMenu.create(SOURCE_SELECT_ID)
            .setPlaceholder("Where did you find us?")
            .addOptions(options)
            .build();
        ActionRow selectRow = ActionRow.of(select);

        Button inviterButton = Button.secondary(
            INVITER_BUTTON_ID,
            session.invitedBy != null && !session.invitedBy.isBlank()
                ? "Inviter: " + session.invitedBy
                : "Input Inviter Username"
        );
        Button usernameButton = Button.primary(
            USERNAME_BUTTON_ID,
            session.minecraftUsername != null ? "IGN: " + session.minecraftUsername : "Set Minecraft Username"
        );
        Button osuButton = Button.primary(OSU_BUTTON_ID, "Link osu! Account");
        boolean ready = session.source != null && session.minecraftId != null && session.minecraftUsername != null && session.osuInfo != null;
        Button submitButton = ready
            ? Button.success(SUBMIT_BUTTON_ID, "Submit Request").withEmoji(Emoji.fromUnicode("✅"))
            : Button.secondary(SUBMIT_BUTTON_ID, "Submit Request").asDisabled();

        return List.of(
            selectRow,
            ActionRow.of(inviterButton, usernameButton),
            ActionRow.of(osuButton, submitButton)
        );
    }

    private void handleOsuLink(ButtonInteractionEvent event, Session session) {
        if (session.minecraftId == null || session.minecraftUsername == null) {
            event.reply("Set your Minecraft username first.").setEphemeral(true).queue();
            return;
        }
        if (INVITED_BY_FRIEND.equals(session.source) && (session.invitedBy == null || session.invitedBy.isBlank())) {
            event.reply("Please input the inviter's username.").setEphemeral(true).queue();
            return;
        }
        LinkState state = new LinkState(
            event.getUser().getIdLong(),
            session.minecraftId,
            session.minecraftUsername,
            Instant.now()
        );
        String token = stateCodec.encode(state);
        String authUrl = buildAuthUrl(token);
        event.reply("Click to link via osu: " + authUrl).setEphemeral(true).queue();
    }

    private void handleSubmit(ButtonInteractionEvent event, Session session) {
        if (session.minecraftId == null || session.minecraftUsername == null) {
            event.reply("Set your Minecraft username first.").setEphemeral(true).queue();
            return;
        }
        if (session.source == null || session.source.isBlank()) {
            event.reply("Please select where you found us.").setEphemeral(true).queue();
            return;
        }
        if (INVITED_BY_FRIEND.equals(session.source) && (session.invitedBy == null || session.invitedBy.isBlank())) {
            event.reply("Please input the inviter's username.").setEphemeral(true).queue();
            return;
        }
        if (INVITED_BY_FRIEND.equals(session.source) && session.sponsorId == null) {
            // resolve sponsor by username in player docs
            resolveSponsorId(session.invitedBy)
                .thenAccept(optSponsor -> {
                    if (optSponsor.isEmpty()) {
                        event.getHook().sendMessage("Could not find that sponsor in player records.").setEphemeral(true).queue();
                        return;
                    }
                    session.sponsorId = optSponsor.get();
                    proceedSubmit(event, session);
                })
                .exceptionally(throwable -> {
                    event.getHook().sendMessage("Failed to resolve sponsor; try again.").setEphemeral(true).queue();
                    return null;
                });
            event.deferReply(true).queue();
            return;
        }
        proceedSubmit(event, session);
    }

    private void proceedSubmit(ButtonInteractionEvent event, Session session) {
        event.deferReply(true).queue();
        resolveOsu(session.minecraftId)
            .thenCompose(osuInfo -> {
                if (osuInfo.isEmpty()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("osu link not completed yet. Finish the osu link step first."));
                }
                Session enriched = session.withOsu(osuInfo.get());
                enriched.createdAt = Instant.now();
                enriched.discordId = event.getUser().getIdLong();
                if (INVITED_BY_FRIEND.equals(enriched.source)) {
                    return sendSponsorRequest(event, enriched);
                }
                return sendStaffReview(event, enriched);
            })
            .exceptionally(throwable -> {
                event.getHook().sendMessage(throwable.getMessage() != null ? throwable.getMessage() : "Submission failed; try again.").setEphemeral(true).queue();
                return null;
            });
    }

    private CompletionStage<Optional<Long>> resolveSponsorId(String inviterUsername) {
        return dataApi.collection("players").all()
            .thenApply(list -> list.stream()
                .filter(doc -> inviterUsername.equalsIgnoreCase(doc.get("meta.username", String.class).orElse("")))
                .map(doc -> doc.get("discordId", Number.class).map(Number::longValue).orElse(null))
                .filter(id -> id != null)
                .findFirst());
    }

    private CompletionStage<Optional<OsuLookupService.OsuInfo>> resolveOsu(UUID playerId) {
        return osuLookupService.fromTicket(playerId)
            .thenCompose(osu -> {
                if (osu.isPresent()) {
                    return CompletableFuture.completedFuture(osu);
                }
                return osuLookupService.fromPlayer(playerId);
            });
    }

    private CompletionStage<Void> sendStaffReview(ButtonInteractionEvent event, Session session) {
        MessageTemplate rendered = render(reviewTemplate, session, event.getUser().getIdLong());
        MessageCreateData reviewMessage = MessageTemplateMapper.toMessage(rendered);
        MessageChannel channel = jda.getChannelById(MessageChannel.class, botConfig.reviewChannelId());
        if (channel == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Review channel not configured or not found."));
        }
        return channel.sendMessage(reviewMessage)
            .setActionRow(
                Button.success(REVIEW_APPROVE_ID, "Approve").withEmoji(Emoji.fromUnicode("✅")),
                Button.danger(REVIEW_DENY_ID, "Deny").withEmoji(Emoji.fromUnicode("❌"))
            )
            .submit()
            .thenAccept(message -> {
                session.requestMessageId = message.getIdLong();
                session.requestCreatedAt = Instant.now();
                requestStore.save(session.toRequest()).toCompletableFuture().join();
                sessionsByReviewMessage.put(message.getIdLong(), session);
                event.getHook().sendMessage("Submitted for staff review.").setEphemeral(true).queue();
            });
    }

    private CompletionStage<Void> sendSponsorRequest(ButtonInteractionEvent event, Session session) {
        if (session.sponsorId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Sponsor not set."));
        }
        return validateSponsor(session.sponsorId)
            .thenCompose(valid -> {
                if (!valid) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Sponsor is not eligible to invite."));
                }
                return dispatchSponsorDm(event, session);
            })
            .exceptionally(throwable -> {
                if (botConfig.generalChannelId() > 0) {
                    MessageChannel general = jda.getChannelById(MessageChannel.class, botConfig.generalChannelId());
                    if (general != null) {
                        MessageTemplate ping = render(sponsorPingTemplate, session, session.sponsorId);
                        general.sendMessage(MessageTemplateMapper.toMessage(ping))
                            .setActionRow(
                                Button.success(SPONSOR_YES_ID, "Yes I know this player"),
                                Button.danger(SPONSOR_NO_ID, "No I don't know this player")
                            ).queue();
                    }
                }
                event.getHook().sendMessage(throwable.getMessage() != null ? throwable.getMessage() : "Sponsor confirmation failed.").setEphemeral(true).queue();
                return null;
            });
    }

    private CompletionStage<Void> dispatchSponsorDm(ButtonInteractionEvent event, Session session) {
        return jda.retrieveUserById(session.sponsorId)
            .submit()
            .thenCompose(user -> user.openPrivateChannel().submit()
                .thenCompose(dm -> dm.sendMessage(MessageTemplateMapper.toMessage(render(sponsorDmTemplate, session, session.sponsorId)))
                    .setActionRow(
                        Button.success(SPONSOR_YES_ID, "Yes I know this player"),
                        Button.danger(SPONSOR_NO_ID, "No I don't know this player")
                    )
                    .submit()
                )
            )
            .thenAccept(message -> {
                session.sponsorRequestExpiresAt = Instant.now().plus(SPONSOR_TIMEOUT);
                sessionsBySponsorMessage.put(message.getIdLong(), session);
                persistSponsorRequest(message.getIdLong(), session);
                scheduleSponsorTimeout(message.getIdLong());
                event.getHook().sendMessage("Sent sponsor request. Waiting for confirmation.").setEphemeral(true).queue();
            });
    }

    public void restoreSponsorRequests() {
        sponsorRequestStore.loadPending()
            .thenAccept(list -> list.forEach(this::restoreSponsorSession))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to restore sponsor requests", throwable);
                return null;
            });
    }

    private void restoreSponsorSession(SponsorRequestStore.SponsorRequest request) {
        Session session = Session.forUser(request.discordId(), request.discordUsername());
        session.source = request.source();
        session.invitedBy = request.invitedBy();
        session.minecraftId = request.minecraftId();
        session.minecraftUsername = request.minecraftUsername();
        session.osuInfo = new OsuLookupService.OsuInfo(
            request.osuUserId(),
            request.osuUsername(),
            request.osuRank(),
            request.osuCountry()
        );
        session.sponsorId = request.sponsorId();
        session.createdAt = request.createdAt();
        session.sponsorRequestExpiresAt = request.sponsorExpiresAt();
        sessionsBySponsorMessage.put(request.messageId(), session);
        if (Instant.now().isAfter(request.sponsorExpiresAt())) {
            expireSponsorRequest(request.messageId());
        } else {
            long delay = Duration.between(Instant.now(), request.sponsorExpiresAt()).toSeconds();
            CompletableFuture.runAsync(
                () -> expireSponsorRequest(request.messageId()),
                CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS)
            );
        }
    }

    private void persistSponsorRequest(long sponsorMessageId, Session session) {
        if (session.discordId == null || session.sponsorId == null || session.minecraftId == null || session.osuInfo == null) {
            return;
        }
        SponsorRequestStore.SponsorRequest request = new SponsorRequestStore.SponsorRequest(
            sponsorMessageId,
            session.discordId,
            session.discordUsername != null ? session.discordUsername : "",
            session.source != null ? session.source : "",
            session.invitedBy,
            session.minecraftId,
            session.minecraftUsername != null ? session.minecraftUsername : "",
            session.osuInfo.userId(),
            session.osuInfo.username(),
            session.osuInfo.rank(),
            session.osuInfo.country(),
            session.sponsorId,
            session.createdAt != null ? session.createdAt : Instant.now(),
            session.sponsorRequestExpiresAt != null ? session.sponsorRequestExpiresAt : Instant.now().plus(SPONSOR_TIMEOUT)
        );
        sponsorRequestStore.save(request).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to persist sponsor request " + sponsorMessageId, throwable);
            return null;
        });
    }

    private void scheduleSponsorTimeout(long sponsorMessageId) {
        CompletableFuture.runAsync(
            () -> expireSponsorRequest(sponsorMessageId),
            CompletableFuture.delayedExecutor(SPONSOR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
        );
    }

    private void expireSponsorRequest(long sponsorMessageId) {
        Session session = sessionsBySponsorMessage.get(sponsorMessageId);
        if (session == null) {
            session = sponsorRequestStore.load(sponsorMessageId)
                .toCompletableFuture()
                .join()
                .map(this::sessionFromStoredRequest)
                .orElse(null);
            if (session != null) {
                sessionsBySponsorMessage.put(sponsorMessageId, session);
            }
        }
        if (session == null || session.sponsorResolved) {
            return;
        }
        if (session.sponsorRequestExpiresAt != null && Instant.now().isBefore(session.sponsorRequestExpiresAt)) {
            return;
        }
        sessionsBySponsorMessage.remove(sponsorMessageId);
        session.sponsorResolved = true;
        sponsorRequestStore.delete(sponsorMessageId);
        logger.info("Sponsor request timed out for applicant " + session.discordId + " with sponsor " + session.sponsorId);
        notifyApplicantOfTimeout(session);
    }

    private Session sessionFromStoredRequest(SponsorRequestStore.SponsorRequest request) {
        Session session = Session.forUser(request.discordId(), request.discordUsername());
        session.source = request.source();
        session.invitedBy = request.invitedBy();
        session.minecraftId = request.minecraftId();
        session.minecraftUsername = request.minecraftUsername();
        session.osuInfo = new OsuLookupService.OsuInfo(
            request.osuUserId(),
            request.osuUsername(),
            request.osuRank(),
            request.osuCountry()
        );
        session.sponsorId = request.sponsorId();
        session.createdAt = request.createdAt();
        session.sponsorRequestExpiresAt = request.sponsorExpiresAt();
        return session;
    }

    private void notifyApplicantOfTimeout(Session session) {
        if (session.discordId == null || jda == null) {
            return;
        }
        jda.retrieveUserById(session.discordId)
            .queue(
                user -> user.openPrivateChannel().queue(
                    dm -> dm.sendMessage("Your sponsor did not respond within 24 hours. Please restart the whitelist flow and try again.").queue(),
                    throwable -> logger.log(Level.WARNING, "Failed to DM applicant about sponsor timeout", throwable)
                ),
                throwable -> logger.log(Level.WARNING, "Failed to resolve applicant for sponsor timeout", throwable)
            );
    }

    private CompletionStage<Boolean> validateSponsor(Long sponsorId) {
        return dataApi.collection("players").load(sponsorId.toString())
            .thenApply(document -> {
                if (document == null || !document.exists()) {
                    return false;
                }
                boolean hasDiscord = document.get("discordId", Number.class).isPresent();
                boolean hasOsu = document.get("osu.userId", Number.class).isPresent();
                boolean hasMc = document.get("meta.username", String.class).isPresent();
                boolean banned = document.get(SPONSOR_FLAG_PATH, Boolean.class).orElse(false);
                return hasDiscord && hasOsu && hasMc && !banned;
            });
    }

    private void handleStaffDecision(ButtonInteractionEvent event, boolean approve) {
        Session session = sessionsByReviewMessage.get(event.getMessageIdLong());
        if (session == null || session.osuInfo == null || session.minecraftId == null) {
            event.reply("Request data missing; cannot process.").setEphemeral(true).queue();
            return;
        }
        MessageTemplate decision = renderDecision(decisionTemplate, session, event.getUser().getIdLong(), approve);
        MessageChannel decisionChannel = botConfig.decisionChannelId() > 0
            ? jda.getChannelById(MessageChannel.class, botConfig.decisionChannelId())
            : null;
        if (decisionChannel != null) {
            decisionChannel.sendMessage(MessageTemplateMapper.toMessage(decision)).queue();
        }
        event.reply(approve ? "Request approved." : "Request denied.").setEphemeral(true).queue();
    }

    private void handleSponsorDecision(ButtonInteractionEvent event, boolean approve) {
        Session session = sessionsBySponsorMessage.remove(event.getMessageIdLong());
        if (session == null) {
            event.reply("No pending sponsor request found.").setEphemeral(true).queue();
            return;
        }
        session.sponsorResolved = true;
        sponsorRequestStore.delete(event.getMessageIdLong());
        if (session.sponsorRequestExpiresAt != null && Instant.now().isAfter(session.sponsorRequestExpiresAt)) {
            event.reply("Sponsor request timed out.").setEphemeral(true).queue();
            return;
        }
        if (!approve) {
            event.reply("You denied the request.").setEphemeral(true).queue();
            return;
        }
        event.reply("Thanks! We sent this to staff for final approval.").setEphemeral(true).queue();
        sendStaffReview(event, session);
    }

    private MessageTemplate render(MessageTemplate template, Session session, long actorDiscordId) {
        if (template == null || template.embeds().isEmpty()) {
            return template;
        }
        MessageTemplate.EmbedTemplate embed = template.embeds().getFirst();
        String description = embed.description();
        if (description != null) {
            description = description
                .replace("{discordId}", session.discordId != null ? Long.toString(session.discordId) : "unknown")
                .replace("{createdAt}", session.createdAt != null ? session.createdAt.toString() : Instant.now().toString())
                .replace("{source}", session.source != null ? session.source : "unknown")
                .replace("{osuUsername}", session.osuInfo != null ? session.osuInfo.username() : "unknown")
                .replace("{osuRankFormatted}", session.osuInfo != null ? "#" + session.osuInfo.rank() : "unknown")
                .replace("{osuCountry}", session.osuInfo != null ? session.osuInfo.country() : "??")
                .replace("{osuCountryLower}", session.osuInfo != null ? session.osuInfo.country().toLowerCase() : "??")
                .replace("{minecraftUsername}", session.minecraftUsername != null ? session.minecraftUsername : "unknown")
                .replace("{moderator}", "<@" + actorDiscordId + ">")
                .replace("{sponsorId}", session.sponsorId != null ? Long.toString(session.sponsorId) : "");
        }
        List<MessageTemplate.FieldTemplate> mappedFields = embed.fields().stream()
            .map(field -> new MessageTemplate.FieldTemplate(
                field.name(),
                field.value()
                    .replace("{osuUsername}", session.osuInfo != null ? session.osuInfo.username() : "unknown")
                    .replace("{osuRankFormatted}", session.osuInfo != null ? "#" + session.osuInfo.rank() : "unknown")
                    .replace("{osuCountry}", session.osuInfo != null ? session.osuInfo.country() : "??")
                    .replace("{osuCountryLower}", session.osuInfo != null ? session.osuInfo.country().toLowerCase() : "??")
                    .replace("{minecraftUsername}", session.minecraftUsername != null ? session.minecraftUsername : "unknown"),
                field.inline()
            ))
            .toList();

        MessageTemplate.EmbedTemplate rendered = new MessageTemplate.EmbedTemplate(
            embed.title(),
            description,
            embed.url(),
            embed.color(),
            mappedFields,
            embed.footer(),
            embed.image(),
            embed.thumbnail()
        );
        return new MessageTemplate(template.content(), List.of(rendered));
    }

    private MessageTemplate renderDecision(MessageTemplate template, Session session, long actorDiscordId, boolean approve) {
        MessageTemplate base = render(template, session, actorDiscordId);
        MessageTemplate.EmbedTemplate embed = base.embeds().getFirst();
        String title = approve ? "✅ Request Approved!" : "❌ Request Denied";
        Integer color = approve ? 0x4D9B40 : 0xD6006B;
        MessageTemplate.EmbedTemplate updated = new MessageTemplate.EmbedTemplate(
            title,
            embed.description(),
            embed.url(),
            color,
            embed.fields(),
            embed.footer(),
            embed.image(),
            embed.thumbnail()
        );
        return new MessageTemplate(base.content(), List.of(updated));
    }

    private String buildAuthUrl(String stateToken) {
        String encodedRedirect = URLEncoder.encode(linkConfig.publicCallbackUrl(), StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(stateToken, StandardCharsets.UTF_8);
        return linkConfig.osuAuthorizeUrl()
            + "?response_type=code"
            + "&client_id=" + linkConfig.osuClientId()
            + "&redirect_uri=" + encodedRedirect
            + "&scope=public"
            + "&state=" + encodedState;
    }

    private String optionalValue(ModalInteractionEvent modalInteraction, String id) {
        return modalInteraction.getValue(id) != null ? modalInteraction.getValue(id).getAsString() : null;
    }

    private UUID parseDashed(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String dashed = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
            );
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static final class Session {
        private String source;
        private String invitedBy;
        private String minecraftUsername;
        private UUID minecraftId;
        private OsuLookupService.OsuInfo osuInfo;
        private Long sponsorId;
        private Instant sponsorRequestExpiresAt;
        private Long requestMessageId;
        private Instant requestCreatedAt;
        private Long discordId;
        private Instant createdAt;
        private String discordUsername;
        private boolean sponsorResolved;

        static Session forUser(long discordId, String discordUsername) {
            Session session = new Session();
            session.discordId = discordId;
            session.discordUsername = discordUsername;
            return session;
        }

        Session withOsu(OsuLookupService.OsuInfo info) {
            this.osuInfo = info;
            return this;
        }

        Session withSponsorConfirmed() {
            this.sponsorResolved = true;
            return this;
        }

        RequestStore.Request toRequest() {
            return new RequestStore.Request(
                discordId != null ? discordId : 0L,
                discordUsername != null ? discordUsername : "",
                source != null ? source : "",
                invitedBy,
                minecraftId != null ? minecraftId : UUID.randomUUID(),
                minecraftUsername != null ? minecraftUsername : "",
                osuInfo != null ? osuInfo.userId() : 0L,
                osuInfo != null ? osuInfo.username() : "",
                osuInfo != null ? osuInfo.rank() : 0,
                osuInfo != null ? osuInfo.country() : "",
                createdAt != null ? createdAt : Instant.now(),
                RequestStore.RequestState.PENDING,
                Optional.ofNullable(sponsorId)
            );
        }
    }
}

package sh.harold.fulcrum.plugin.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
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
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.plugin.accountlink.AccountLinkConfig;
import sh.harold.fulcrum.plugin.accountlink.SourcesConfig;
import sh.harold.fulcrum.plugin.accountlink.LinkState;
import sh.harold.fulcrum.plugin.accountlink.StateCodec;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplate;
import sh.harold.fulcrum.plugin.discordbot.template.MessageTemplateMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
    private static final String SOURCE_PLACEHOLDER_ID = "source-placeholder";
    private static final String INVITER_BUTTON_ID = "inviter-button";
    private static final String USERNAME_BUTTON_ID = "username-button";
    private static final String OSU_BUTTON_ID = "osu-button";
    private static final String NO_OSU_BUTTON_ID = "no-osu-button";
    private static final String SUBMIT_BUTTON_ID = "submit-button";
    private static final String CANCEL_BUTTON_ID = "whitelist-cancel";
    private static final String REVIEW_APPROVE_ID = "review-approve";
    private static final String REVIEW_DENY_ID = "review-deny";
    private static final String REVIEW_DENY_MODAL_ID = "review-deny-modal";
    private static final String REVIEW_DENY_REASON_ID = "review-deny-reason";
    private static final String REVIEW_REAPPLY_SELECT_ID = "review-reapply-select";
    private static final String SPONSOR_YES_ID = "sponsor-yes";
    private static final String SPONSOR_NO_ID = "sponsor-no";
    private static final String INVITED_BY_FRIEND = "Invited By Friend";
    private static final String SPONSOR_FLAG_PATH = "meta.cannotInviteOthers";
    private static final Duration SPONSOR_TIMEOUT = Duration.ofHours(24);
    private static final Duration WHITELIST_TIMEOUT = Duration.ofMinutes(5);
    private static final OsuLookupService.OsuInfo NO_OSU_INFO = new OsuLookupService.OsuInfo(0L, "No osu account", 0, "XX");

    private final StateCodec stateCodec;
    private final AccountLinkConfig linkConfig;
    private final MojangClient mojangClient;
    private final Logger logger;
    private final java.util.Set<Long> acceptedUsers;
    private final java.util.function.Supplier<SourcesConfig> sourcesSupplier;
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
    private Guild cachedGuild;

    public LinkDiscordFeature(
        StateCodec stateCodec,
        AccountLinkConfig linkConfig,
        MojangClient mojangClient,
        Logger logger,
        java.util.Set<Long> acceptedUsers,
        java.util.function.Supplier<SourcesConfig> sourcesSupplier,
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
        if (hasBlockingRequest(event.getUser().getIdLong())) {
            replyBlocked(event);
            return;
        }
        Session session = sessionsByUser.computeIfAbsent(event.getUser().getIdLong(), ignored -> Session.forUser(event.getUser().getIdLong(), event.getUser().getName()));
        session.flowStartedAt = Instant.now();
        session.reset();
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
        if (REVIEW_APPROVE_ID.equals(id)) {
            handleStaffDecision(event, true, null);
            return;
        }
        if (REVIEW_DENY_ID.equals(id)) {
            openDenyReasonModal(event);
            return;
        }
        if (SPONSOR_YES_ID.equals(id) || SPONSOR_NO_ID.equals(id)) {
            handleSponsorDecision(event, SPONSOR_YES_ID.equals(id));
            return;
        }

        Session session = sessionForUser(userId, event.getUser().getName());
        if (isUserFlow(id) && hasBlockingRequest(userId)) {
            replyBlocked(event);
            return;
        }
        if (isUserFlow(id) && !START_WHITELIST_BUTTON_ID.equals(id) && session.requestMessageId != null) {
            event.reply("You already submitted this request. Click **Start Whitelist Process** to begin again.").setEphemeral(true).queue();
            return;
        }

        switch (id) {
            case START_WHITELIST_BUTTON_ID -> {
                if (!acceptedUsers.contains(userId)) {
                    event.reply("Please accept the rules above first.").setEphemeral(true).queue();
                    return;
                }
                session.reset();
                session.flowStartedAt = Instant.now();
                sendWhitelistPrompt(event, session);
            }
            case NO_OSU_BUTTON_ID -> {
                if (!acceptedUsers.contains(userId)) {
                    event.reply("Please accept the rules above first.").setEphemeral(true).queue();
                    return;
                }
                session.reset();
                session.flowStartedAt = Instant.now();
                session.osuSkipped = true;
                session.osuInfo = NO_OSU_INFO;
                session.source = INVITED_BY_FRIEND;
                session.invitedBy = null;
                session.sponsorId = null;
                sendWhitelistPrompt(event, session);
            }
            case INVITER_BUTTON_ID -> {
                if (handleExpired(event, session)) {
                    return;
                }
                openInviterModal(event);
            }
            case USERNAME_BUTTON_ID -> {
                if (handleExpired(event, session)) {
                    return;
                }
                openUsernameModal(event);
            }
            case OSU_BUTTON_ID -> {
                if (handleExpired(event, session)) {
                    return;
                }
                handleOsuLink(event, session);
            }
            case SUBMIT_BUTTON_ID -> {
                if (handleExpired(event, session)) {
                    return;
                }
                handleSubmit(event, session);
            }
            case CANCEL_BUTTON_ID -> handleCancel(event, session);
            default -> {
            }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        long userId = event.getUser().getIdLong();
        Session session = sessionForUser(userId, event.getUser().getName());
        if (MODAL_ID.equals(modalId) || INVITER_MODAL_ID.equals(modalId)) {
            if (hasBlockingRequest(userId)) {
                replyBlocked(event);
                return;
            }
            if (session.requestMessageId != null) {
                event.reply("You already submitted this request. Click **Start Whitelist Process** to begin again.").setEphemeral(true).queue();
                return;
            }
        }
        if (MODAL_ID.equals(modalId)) {
            String username = optionalValue(event, USERNAME_INPUT_ID);
            if (username == null || username.isBlank()) {
                event.reply("Give me a Minecraft username and try again.").setEphemeral(true).queue();
                return;
            }
            username = username.trim();
            if (!isValidMinecraftUsername(username)) {
                event.reply("That doesn't look like a valid Minecraft username (3-16 letters, numbers, or underscores).").setEphemeral(true).queue();
                return;
            }
            if (handleExpired(event, session)) {
                return;
            }
            event.deferReply(true).queue();
            mojangClient.lookup(username)
                .toCompletableFuture()
                .whenComplete((profile, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.SEVERE, "Mojang lookup failed for " + username, throwable);
                        event.getHook().editOriginal("Lookup failed; please try again in a moment.").queue();
                        return;
                    }
                    Optional<MojangClient.MojangProfile> mojangProfile = profile;
                    if (mojangProfile.isEmpty()) {
                        event.getHook().editOriginal("Account not found. Double check the username.").queue();
                        return;
                    }
                    UUID uuid = parseDashed(mojangProfile.get().id());
                    session.minecraftUsername = mojangProfile.get().name();
                    session.minecraftId = uuid;
                    event.getHook().editOriginal(MessageEditData.fromCreateData(buildWhitelistMessage(session)))
                        .setComponents(buildComponents(session))
                        .queue();
                });
            return;
        }
        if (INVITER_MODAL_ID.equals(modalId)) {
            String inviter = optionalValue(event, INVITER_INPUT_ID);
            if (inviter == null || inviter.isBlank()) {
                event.reply("Please provide the inviter's username.").setEphemeral(true).queue();
                return;
            }
            if (handleExpired(event, session)) {
                return;
            }
            if (!playerExists(inviter.trim())) {
                event.reply("That sponsor username was not found; sponsors must be existing players with a Discord link.").setEphemeral(true).queue();
                return;
            }
            session.invitedBy = inviter.trim();
            event.deferReply(true).queue();
            event.getHook().editOriginal(MessageEditData.fromCreateData(buildWhitelistMessage(session)))
                .setComponents(buildComponents(session))
                .queue();
            return;
        }
        if (modalId.startsWith(REVIEW_DENY_MODAL_ID)) {
            String reason = optionalValue(event, REVIEW_DENY_REASON_ID);
            if (reason == null || reason.isBlank()) {
                event.reply("Please provide a denial reason.").setEphemeral(true).queue();
                return;
            }
            Long messageId = parseMessageIdFromModal(modalId);
            handleDenyDecision(event, messageId, reason.trim());
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (SOURCE_SELECT_ID.equals(id)) {
            long userId = event.getUser().getIdLong();
            Session session = sessionForUser(userId, event.getUser().getName());
            if (handleExpired(event, session)) {
                return;
            }
            String chosen = event.getValues().isEmpty() ? null : event.getValues().getFirst();
            session.source = chosen;
            if (!INVITED_BY_FRIEND.equalsIgnoreCase(chosen)) {
                session.invitedBy = null;
            }
            event.editMessageEmbeds(buildWhitelistEmbed(session))
                .setComponents(buildComponents(session))
                .queue();
            return;
        }
        if (REVIEW_REAPPLY_SELECT_ID.equals(id)) {
            boolean allowReapply = !event.getValues().isEmpty() && "allow".equalsIgnoreCase(event.getValues().getFirst());
            Session session = sessionsByReviewMessage.get(event.getMessageIdLong());
            if (session == null) {
                session = restoreSessionFromRequest(event.getMessageIdLong());
                if (session != null) {
                    sessionsByReviewMessage.put(event.getMessageIdLong(), session);
                }
            }
            if (session != null) {
                session.allowReapply = allowReapply;
            }
            requestStore.updateCanReapplyByMessageId(event.getMessageIdLong(), allowReapply);
            event.deferEdit().queue();
        }
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

    private void openDenyReasonModal(ButtonInteractionEvent event) {
        TextInput reasonInput = TextInput.create(REVIEW_DENY_REASON_ID, "Reason for denial", TextInputStyle.PARAGRAPH)
            .setRequired(true)
            .setRequiredRange(5, 500)
            .build();
        String modalKey = REVIEW_DENY_MODAL_ID + ":" + event.getMessageId();
        Modal modal = Modal.create(modalKey, "Deny whitelist request")
            .addActionRow(reasonInput)
            .build();
        event.replyModal(modal).queue();
    }

    private void hydrateOsu(Session session) {
        if (session.osuInfo != null || session.minecraftId == null) {
            return;
        }
        try {
            Optional<OsuLookupService.OsuInfo> fromTicket = osuLookupService.fromTicket(session.minecraftId).toCompletableFuture().join();
            if (fromTicket.isPresent()) {
                session.osuInfo = fromTicket.get();
                return;
            }
            Optional<OsuLookupService.OsuInfo> fromPlayer = osuLookupService.fromPlayer(session.minecraftId).toCompletableFuture().join();
            fromPlayer.ifPresent(info -> session.osuInfo = info);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to hydrate osu info for " + session.minecraftId, exception);
        }
    }

    public void onLinkCompleted(sh.harold.fulcrum.plugin.accountlink.AccountLinkService.LinkTicket ticket, JDA jda) {
        Session session = sessionsByUser.computeIfAbsent(ticket.discordId(), id -> Session.forUser(id, ""));
        session.minecraftId = ticket.uuid();
        session.minecraftUsername = ticket.username();
        session.osuInfo = new OsuLookupService.OsuInfo(
            ticket.osu().userId(),
            ticket.osu().username(),
            ticket.osu().rank(),
            ticket.osu().country()
        );
        session.source = ticket.source();
        session.invitedBy = ticket.invitedBy();
        session.sponsorId = ticket.sponsorId();
        session.flowStartedAt = session.flowStartedAt == null ? Instant.now() : session.flowStartedAt;
        sendLinkUpdatedMessage(ticket.discordId(), jda, session);
    }

    private void sendLinkUpdatedMessage(long discordId, JDA jda, Session session) {
        jda.retrieveUserById(discordId)
            .queue(
                user -> user.openPrivateChannel().queue(
                    dm -> dm.sendMessage(buildWhitelistMessage(session))
                        .setComponents(buildComponents(session))
                        .queue(),
                    throwable -> logger.log(Level.WARNING, "Failed to open DM for link completion update", throwable)
                ),
                throwable -> logger.log(Level.WARNING, "Failed to resolve user for link completion update", throwable)
            );
    }

    private boolean handleExpired(ButtonInteractionEvent event, Session session) {
        if (session.isExpired()) {
            session.reset();
            event.reply("Whitelist session expired. Click Start Whitelist Process to begin again.").setEphemeral(true).queue();
            return true;
        }
        return false;
    }

    private boolean handleExpired(ModalInteractionEvent event, Session session) {
        if (session.isExpired()) {
            session.reset();
            event.reply("Whitelist session expired. Click Start Whitelist Process to begin again.").setEphemeral(true).queue();
            return true;
        }
        return false;
    }

    private Session sessionForUser(long userId, String username) {
        return sessionsByUser.computeIfAbsent(userId, ignored -> Session.forUser(userId, username));
    }

    private boolean handleExpired(StringSelectInteractionEvent event, Session session) {
        if (session.isExpired()) {
            session.reset();
            event.reply("Whitelist session expired. Click Start Whitelist Process to begin again.").setEphemeral(true).queue();
            return true;
        }
        return false;
    }

    private Session restoreSessionFromRequest(long messageId) {
        try {
            Optional<RequestStore.Request> stored = requestStore.findByMessageId(messageId).toCompletableFuture().join();
            if (stored.isEmpty()) {
                return null;
            }
            RequestStore.Request request = stored.get();
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
        session.createdAt = request.createdAt();
        session.sponsorId = request.sponsorId().orElse(null);
        session.requestMessageId = request.requestMessageId().orElse(null);
        session.allowReapply = request.canReapply();
        session.decisionReason = request.decisionReason().orElse(null);
        return session;
    } catch (Exception exception) {
        logger.log(Level.WARNING, "Failed to restore session for review message " + messageId, exception);
        return null;
    }
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
                Button.primary(START_WHITELIST_BUTTON_ID, "Start Whitelist Process"),
                Button.secondary(NO_OSU_BUTTON_ID, "No osu! Account")
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
        hydrateOsu(session);
        MessageCreateBuilder builder = new MessageCreateBuilder();
            builder.addEmbeds(buildWhitelistEmbed(session));
        if (session.flowStartedAt != null) {
            long remainingSeconds = Math.max(0, WHITELIST_TIMEOUT.minus(Duration.between(session.flowStartedAt, Instant.now())).toSeconds());
            builder.setContent("Session expires in " + remainingSeconds + "s.");
        }
        return builder.build();
    }

    private net.dv8tion.jda.api.entities.MessageEmbed buildWhitelistEmbed(Session session) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Complete Whitelist Process Here!");
        embedBuilder.setDescription("-# *In a realm whose cartography scholars rejected for excessive stupidity, there loomed a kingdom named Tenderia, where all citizens were chicken nuggets. Not chickens that became nuggets. Nuggets that… spontaneously existed. Philosophers argued about this until they got hungry and ate each other, which was considered both rude and a valid form of debate.*\n## Link Steps: `{completed}`/`{total}`");
        embedBuilder.setColor(0xD6006B);

        int total = 3;
        int completed = 0;

        boolean sourceDone = session.sourceComplete();
        boolean usernameDone = session.minecraftId != null && session.minecraftUsername != null;
        boolean osuDone = session.osuSkipped || session.osuInfo != null;
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
            osuDone
                ? (session.osuSkipped ? "> `Skipped (sponsor required)`" : "> `Linked via osu!`")
                : "> `Waiting...`",
            true
        );
        return embedBuilder.build();
    }

    private List<LayoutComponent> buildComponents(Session session) {
        Button inviterButton;
        if (!INVITED_BY_FRIEND.equals(session.source) && !session.osuSkipped) {
            inviterButton = Button.secondary(INVITER_BUTTON_ID, "No Inviter").asDisabled();
        } else {
            boolean hasInviter = session.invitedBy != null && !session.invitedBy.isBlank();
            inviterButton = hasInviter
                ? Button.success(INVITER_BUTTON_ID, "Inviter: " + session.invitedBy)
                : Button.danger(INVITER_BUTTON_ID, "Add Inviter Name");
        }

        Button usernameButton = styleButton(
            USERNAME_BUTTON_ID,
            session.minecraftUsername != null ? "IGN: " + session.minecraftUsername : "Set Minecraft Username",
            session.minecraftUsername != null
        );

        Button osuButton = session.osuSkipped
            ? Button.secondary(OSU_BUTTON_ID, "osu! link skipped").asDisabled()
            : styleButton(OSU_BUTTON_ID, "Link osu! Account", session.osuInfo != null);
        boolean ready = session.sourceComplete()
            && session.minecraftId != null
            && session.minecraftUsername != null
            && (session.osuSkipped || session.osuInfo != null);
        Button submitButton = ready
            ? Button.success(SUBMIT_BUTTON_ID, "Submit Request").withEmoji(Emoji.fromUnicode("✅"))
            : Button.secondary(SUBMIT_BUTTON_ID, "Submit Request").asDisabled();

        List<LayoutComponent> rows = new java.util.ArrayList<>();
        if (session.osuSkipped) {
            rows.add(ActionRow.of(Button.secondary(SOURCE_PLACEHOLDER_ID, "Source: Invited by friend").asDisabled()));
        } else {
            List<String> sources = currentSources().sourceNames();
            if (sources == null || sources.isEmpty()) {
                sources = List.of("Other", INVITED_BY_FRIEND);
            } else if (sources.stream().noneMatch(name -> name.equalsIgnoreCase(INVITED_BY_FRIEND))) {
                sources = new java.util.ArrayList<>(sources);
                sources.add(INVITED_BY_FRIEND);
            }
            List<SelectOption> options = sources.stream()
                .map(source -> {
                    SelectOption option = SelectOption.of(source, source);
                    if (session.source != null && session.source.equalsIgnoreCase(source)) {
                        option = option.withDefault(true);
                    }
                    return option;
                })
                .toList();
            StringSelectMenu select = StringSelectMenu.create(SOURCE_SELECT_ID)
                .setPlaceholder("Where did you find us?")
                .addOptions(options)
                .build();
            rows.add(ActionRow.of(select));
        }
        rows.add(ActionRow.of(inviterButton, usernameButton));
        rows.add(ActionRow.of(osuButton, submitButton));
        rows.add(ActionRow.of(Button.danger(CANCEL_BUTTON_ID, "Cancel")));
        return rows;
    }

    private ButtonStyle inviterButtonStyle(Session session) {
        if (!INVITED_BY_FRIEND.equals(session.source)) {
            return ButtonStyle.SECONDARY;
        }
        boolean filled = session.invitedBy != null && !session.invitedBy.isBlank();
        return filled ? ButtonStyle.SUCCESS : ButtonStyle.DANGER;
    }

    private Button styleButton(String id, String label, boolean complete) {
        return complete ? Button.success(id, label) : Button.danger(id, label);
    }

    private void handleOsuLink(ButtonInteractionEvent event, Session session) {
        if (handleExpired(event, session)) {
            return;
        }
        if (session.minecraftId == null || session.minecraftUsername == null) {
            event.reply("Set your Minecraft username first.").setEphemeral(true).queue();
            return;
        }
        if (INVITED_BY_FRIEND.equals(session.source) && (session.invitedBy == null || session.invitedBy.isBlank())) {
            event.reply("Please input the inviter's username.").setEphemeral(true).queue();
            return;
        }
        if (session.osuSkipped) {
            event.reply("osu! linking skipped; sponsor required.").setEphemeral(true).queue();
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
        Button linkButton = Button.link(authUrl, "Link via osu!");
        event.reply("Click below to link via osu!")
            .addActionRow(linkButton)
            .setEphemeral(true)
            .queue(hook -> pollForOsu(hook, session));
    }

    private void pollForOsu(InteractionHook hook, Session session) {
        if (session.minecraftId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            Instant start = Instant.now();
            while (!session.isExpired() && Duration.between(start, Instant.now()).compareTo(WHITELIST_TIMEOUT) < 0) {
                try {
                    Optional<OsuLookupService.OsuInfo> found = osuLookupService.fromTicket(session.minecraftId)
                        .thenCompose(osu -> osu.isPresent() ? CompletableFuture.completedFuture(osu) : osuLookupService.fromPlayer(session.minecraftId))
                        .toCompletableFuture()
                        .join();
                    if (found.isPresent()) {
                        session.withOsu(found.get());
                        hook.editOriginal(MessageEditData.fromCreateData(buildWhitelistMessage(session)))
                            .setComponents(buildComponents(session))
                            .queue();
                        return;
                    }
                    Thread.sleep(2000);
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    private void handleSubmit(ButtonInteractionEvent event, Session session) {
        if (handleExpired(event, session)) {
            return;
        }
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
            if (!event.isAcknowledged()) {
                event.deferReply(true).queue();
            }
            return;
        }
        disableUserSubmissionControls(event);
        proceedSubmit(event, session);
    }

    private void proceedSubmit(ButtonInteractionEvent event, Session session) {
        if (!event.isAcknowledged()) {
            event.deferReply(true).queue();
        }
        CompletionStage<Session> prepared = session.osuSkipped
            ? CompletableFuture.completedFuture(session)
            : resolveOsu(session.minecraftId)
                .thenApply(osuInfo -> {
                    if (osuInfo.isEmpty()) {
                        throw new IllegalStateException("osu link not completed yet. Finish the osu link step first.");
                    }
                    return session.withOsu(osuInfo.get());
                });

        prepared
            .thenCompose(enriched -> {
                if (enriched.osuSkipped && enriched.osuInfo == null) {
                    enriched.osuInfo = NO_OSU_INFO;
                }
                enriched.createdAt = Instant.now();
                enriched.discordId = event.getUser().getIdLong();
                return validateOwnership(enriched, event.getUser().getIdLong())
                    .thenApply(ignored -> enriched);
            })
            .thenCompose(enriched -> {
                if (enriched.osuSkipped || INVITED_BY_FRIEND.equals(enriched.source)) {
                    return sendSponsorRequest(event, enriched);
                }
                return sendStaffReview(event, enriched);
            })
            .exceptionally(throwable -> {
                Throwable root = throwable.getCause() != null ? throwable.getCause() : throwable;
                logger.log(Level.WARNING, "Submit failed for " + session.minecraftId + " / " + session.discordId, root);
                restoreUserSubmissionControls(event, session);
                event.getHook().sendMessage(root.getMessage() != null ? root.getMessage() : "Submission failed; try again.").setEphemeral(true).queue();
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
        if (reviewTemplate == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Review template not configured."));
        }
        if (hasBlockingRequest(event.getUser().getIdLong())) {
            replyBlocked(event);
            return CompletableFuture.completedFuture(null);
        }
        MessageTemplate rendered = render(reviewTemplate, session, event.getUser().getIdLong());
        MessageCreateData reviewMessage = MessageTemplateMapper.toMessage(rendered);
        MessageChannel channel = jda.getChannelById(MessageChannel.class, botConfig.reviewChannelId());
        if (channel == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Review channel not configured or not found."));
        }
        StringSelectMenu reapplySelect = StringSelectMenu.create(REVIEW_REAPPLY_SELECT_ID)
            .setPlaceholder("Can this applicant reapply?")
            .addOptions(
                SelectOption.of("Allow reapply", "allow").withDefault(session.allowReapply),
                SelectOption.of("Do not allow reapply", "deny").withDefault(!session.allowReapply)
            )
            .build();
        return channel.sendMessage(reviewMessage)
            .setComponents(
                ActionRow.of(reapplySelect),
                ActionRow.of(
                    Button.success(REVIEW_APPROVE_ID, "Approve").withEmoji(Emoji.fromUnicode("✅")),
                    Button.danger(REVIEW_DENY_ID, "Deny").withEmoji(Emoji.fromUnicode("❌"))
                )
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
                return ensureSponsorHasOsu(session.sponsorId)
                    .thenCompose(ignored -> dispatchSponsorDm(event, session));
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
                notifySponsorInGame(session);
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
        return dataApi.collection("players").all()
            .thenApply(list -> list.stream()
                .filter(Document::exists)
                .filter(doc -> doc.get("discordId", Number.class).map(Number::longValue).orElse(0L) == sponsorId)
                .noneMatch(doc -> doc.get(SPONSOR_FLAG_PATH, Boolean.class).orElse(false)));
    }

    private void handleStaffDecision(ButtonInteractionEvent event, boolean approve, String denyReason) {
        Session session = sessionsByReviewMessage.get(event.getMessageIdLong());
        if (session == null) {
            session = restoreSessionFromRequest(event.getMessageIdLong());
        }
        if (session == null || session.osuInfo == null || session.minecraftId == null) {
            event.reply("Request data missing; cannot process.").setEphemeral(true).queue();
            return;
        }
        requestStore.updateDecisionByMessageId(
            event.getMessageIdLong(),
            approve ? RequestStore.RequestState.APPROVED : RequestStore.RequestState.DENIED,
            session.allowReapply,
            Optional.ofNullable(denyReason)
        );
        disableReviewButtons(event);
        MessageTemplate decision = renderDecision(decisionTemplate, session, event.getUser().getIdLong(), approve, Optional.ofNullable(denyReason));
        MessageChannel decisionChannel = botConfig.decisionChannelId() > 0
            ? jda.getChannelById(MessageChannel.class, botConfig.decisionChannelId())
            : null;
        if (decisionChannel != null) {
            decisionChannel.sendMessage(MessageTemplateMapper.toMessage(decision)).queue();
        }
        notifyApplicant(session.discordId, approve, denyReason);
        if (approve) {
            applyRoles(session, event.getGuild());
        }
        event.reply(approve ? "Request approved." : "Request denied.").setEphemeral(true).queue();
    }

    private void handleDenyDecision(ModalInteractionEvent event, Long messageIdOverride, String denyReason) {
        long messageId = messageIdOverride != null ? messageIdOverride : 0L;
        if (messageId == 0L) {
            event.reply("Request reference missing; cannot process denial.").setEphemeral(true).queue();
            return;
        }
        Session session = sessionsByReviewMessage.get(messageId);
        if (session == null) {
            session = restoreSessionFromRequest(messageId);
        }
        if (session == null || session.osuInfo == null || session.minecraftId == null) {
            event.reply("Request data missing; cannot process.").setEphemeral(true).queue();
            return;
        }
        requestStore.updateDecisionByMessageId(messageId, RequestStore.RequestState.DENIED, session.allowReapply, Optional.of(denyReason));
        disableReviewButtons(messageId);
        MessageTemplate decision = renderDecision(decisionTemplate, session, event.getUser().getIdLong(), false, Optional.of(denyReason));
        MessageChannel decisionChannel = botConfig.decisionChannelId() > 0
            ? jda.getChannelById(MessageChannel.class, botConfig.decisionChannelId())
            : null;
        if (decisionChannel != null) {
            decisionChannel.sendMessage(MessageTemplateMapper.toMessage(decision)).queue();
        }
        notifyApplicant(session.discordId, false, denyReason);
        event.reply("Request denied.").setEphemeral(true).queue();
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
        if (session.osuInfo == null || session.minecraftId == null || session.discordId == null) {
            event.reply("Request data missing; please ask the applicant to resubmit.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        session.createdAt = session.createdAt != null ? session.createdAt : Instant.now();
        requestStore.save(session.toRequest(RequestStore.RequestState.APPROVED))
            .thenCompose(ignored -> applyRoles(session, event.getGuild()))
            .thenAccept(ignored -> {
                MessageTemplate decision = renderDecision(decisionTemplate, session, event.getUser().getIdLong(), true, Optional.empty());
                MessageChannel decisionChannel = botConfig.decisionChannelId() > 0
                    ? jda.getChannelById(MessageChannel.class, botConfig.decisionChannelId())
                    : null;
                if (decisionChannel != null) {
                    decisionChannel.sendMessage(MessageTemplateMapper.toMessage(decision)).queue();
                }
                notifyApplicant(session.discordId, true, null);
                event.getHook().sendMessage("Sponsor approval recorded. You are all set!").queue();
            })
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to auto-approve sponsor flow for " + session.minecraftId, throwable);
                event.getHook().sendMessage("Could not finalize the request. Please ask staff to review manually.").queue();
                return null;
            });
    }

    private void disableReviewButtons(ButtonInteractionEvent event) {
        Message message = event.getMessage();
        if (message == null) {
            return;
        }
        List<ActionRow> disabledRows = disableComponents(message.getActionRows());
        if (!disabledRows.isEmpty()) {
            message.editMessageComponents(disabledRows).queue(
                ignored -> {},
                throwable -> logger.log(Level.WARNING, "Failed to disable review buttons for " + message.getIdLong(), throwable)
            );
        }
    }

    private void disableReviewButtons(long messageId) {
        if (botConfig.reviewChannelId() <= 0) {
            return;
        }
        MessageChannel channel = jda.getChannelById(MessageChannel.class, botConfig.reviewChannelId());
        if (channel == null) {
            return;
        }
        channel.retrieveMessageById(messageId)
            .queue(
                message -> {
                    List<ActionRow> disabledRows = disableComponents(message.getActionRows());
                    message.editMessageComponents(disabledRows).queue(
                        ignored -> {},
                        throwable -> logger.log(Level.WARNING, "Failed to disable review buttons for " + messageId, throwable)
                    );
                },
                throwable -> logger.log(Level.WARNING, "Failed to fetch review message " + messageId + " to disable buttons", throwable)
            );
    }

    private List<ActionRow> disableComponents(List<ActionRow> rows) {
        return rows.stream()
            .map(row -> row.getComponents().stream().map(component -> {
                if (component instanceof Button button) {
                    return button.asDisabled();
                }
                if (component instanceof StringSelectMenu menu) {
                    return menu.asDisabled();
                }
                return component;
            }).toList())
            .filter(components -> !components.isEmpty())
            .map(ActionRow::of)
            .toList();
    }

    private void disableUserSubmissionControls(ButtonInteractionEvent event) {
        Message message = event.getMessage();
        if (message == null) {
            return;
        }
        List<ActionRow> disabledRows = disableComponents(message.getActionRows());
        message.editMessageComponents(disabledRows).queue(
            ignored -> {},
            throwable -> logger.log(Level.WARNING, "Failed to disable submit controls for " + event.getUser().getIdLong(), throwable)
        );
    }

    private void restoreUserSubmissionControls(ButtonInteractionEvent event, Session session) {
        Message message = event.getMessage();
        if (message == null) {
            return;
        }
        MessageEditData data = MessageEditData.fromCreateData(buildWhitelistMessage(session));
        message.editMessage(data).setComponents(buildComponents(session)).queue(
            ignored -> {},
            throwable -> logger.log(Level.WARNING, "Failed to restore submit controls for " + event.getUser().getIdLong(), throwable)
        );
    }

    private MessageTemplate render(MessageTemplate template, Session session, long actorDiscordId) {
        if (template == null || template.embeds().isEmpty()) {
            return template;
        }
        MessageTemplate.EmbedTemplate embed = template.embeds().getFirst();
        String description = applyPlaceholders(embed.description(), session, actorDiscordId);
        List<MessageTemplate.FieldTemplate> mappedFields = embed.fields().stream()
            .map(field -> new MessageTemplate.FieldTemplate(
                field.name(),
                applyPlaceholders(field.value(), session, actorDiscordId),
                field.inline()
            ))
            .toList();
        MessageTemplate.FooterTemplate mappedFooter = embed.footer() != null
            ? new MessageTemplate.FooterTemplate(
                applyPlaceholders(embed.footer().text(), session, actorDiscordId),
                embed.footer().iconUrl()
            )
            : null;

        MessageTemplate.EmbedTemplate rendered = new MessageTemplate.EmbedTemplate(
            applyPlaceholders(embed.title(), session, actorDiscordId),
            description,
            embed.url(),
            embed.color(),
            mappedFields,
            mappedFooter,
            embed.image(),
            embed.thumbnail()
        );
        return new MessageTemplate(template.content(), List.of(rendered));
    }

    private MessageTemplate renderDecision(MessageTemplate template, Session session, long actorDiscordId, boolean approve, Optional<String> denyReason) {
        MessageTemplate base = render(template, session, actorDiscordId);
        MessageTemplate.EmbedTemplate embed = base.embeds().getFirst();
        String title = approve ? "✅ Request Approved!" : "❌ Request Denied";
        Integer color = approve ? 0x4D9B40 : 0xD6006B;
        List<MessageTemplate.FieldTemplate> fields = new java.util.ArrayList<>(embed.fields());
        denyReason.filter(reason -> !reason.isBlank()).ifPresent(reason -> fields.add(
            new MessageTemplate.FieldTemplate("Denial reason", reason, false)
        ));
        fields.add(new MessageTemplate.FieldTemplate("Can reapply?", session.allowReapply ? "Yes" : "No", true));
        MessageTemplate.EmbedTemplate updated = new MessageTemplate.EmbedTemplate(
            title,
            embed.description(),
            embed.url(),
            color,
            fields,
            embed.footer(),
            embed.image(),
            embed.thumbnail()
        );
        return new MessageTemplate(base.content(), List.of(updated));
    }

    private String applyPlaceholders(String value, Session session, long actorDiscordId) {
        if (value == null) {
            return null;
        }
        return value
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

    private Long parseMessageIdFromModal(String modalId) {
        int idx = modalId.indexOf(':');
        if (idx < 0 || idx + 1 >= modalId.length()) {
            return null;
        }
        try {
            return Long.parseLong(modalId.substring(idx + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void notifyApplicant(Long discordId, boolean approved, String denyReason) {
        if (discordId == null || jda == null) {
            return;
        }
        String message = approved
            ? "Your whitelist application was approved! You can join the server now."
            : "Your whitelist application was denied." + (denyReason != null && !denyReason.isBlank() ? " Reason: " + denyReason : "");
        jda.retrieveUserById(discordId)
            .queue(
                user -> user.openPrivateChannel().queue(
                    dm -> dm.sendMessage(message).queue(),
                    throwable -> logger.log(Level.WARNING, "Failed to open DM for applicant " + discordId, throwable)
                ),
                throwable -> logger.log(Level.WARNING, "Failed to resolve applicant " + discordId + " for decision DM", throwable)
            );
    }

    private CompletionStage<Void> applyRoles(Session session, Guild preferredGuild) {
        if (jda == null || session.discordId == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<Long> roleIds = new ArrayList<>();
        SourcesConfig currentSources = currentSources();
        currentSources.linkedRoleId().ifPresent(roleIds::add);
        currentSources.roleIdFor(session.source).ifPresent(roleIds::add);
        if (roleIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Guild guild = preferredGuild != null ? preferredGuild : resolveGuild();
        if (guild == null) {
            logger.warning("Could not resolve guild for role assignment; skipping roles for " + session.discordId);
            return CompletableFuture.completedFuture(null);
        }
        return guild.retrieveMemberById(session.discordId)
            .submit()
            .thenCompose(member -> addRoles(guild, member, roleIds))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to assign roles for " + session.discordId, throwable);
                return null;
            });
    }

    private CompletionStage<Void> addRoles(Guild guild, Member member, List<Long> roleIds) {
        List<CompletableFuture<?>> actions = new ArrayList<>();
        for (Long roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                continue;
            }
            actions.add((CompletableFuture<?>) guild.addRoleToMember(member, role).submit());
        }
        if (actions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = actions.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    private Guild resolveGuild() {
        if (cachedGuild != null) {
            return cachedGuild;
        }
        long[] candidateChannels = new long[] {
            botConfig.reviewChannelId(),
            botConfig.linkChannelId(),
            botConfig.rulesChannelId(),
            botConfig.decisionChannelId(),
            botConfig.generalChannelId()
        };
        for (long channelId : candidateChannels) {
            if (channelId <= 0) {
                continue;
            }
            GuildChannel channel = jda.getGuildChannelById(channelId);
            if (channel != null) {
                cachedGuild = channel.getGuild();
                break;
            }
        }
        return cachedGuild;
    }

    private SourcesConfig currentSources() {
        SourcesConfig config = sourcesSupplier.get();
        if (config == null) {
            return new SourcesConfig(List.of(), Optional.empty());
        }
        return config;
    }

    private void notifySponsorInGame(Session session) {
        if (session.sponsorId == null) {
            return;
        }
        dataApi.collection("players").all()
            .thenAccept(players -> players.stream()
                .filter(Document::exists)
                .filter(doc -> doc.get("discordId", Number.class).map(Number::longValue).orElse(0L) == session.sponsorId)
                .findFirst()
                .ifPresent(doc -> {
                    UUID uuid = parseDashed(doc.key().id());
                    if (uuid == null) {
                        return;
                    }
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) {
                        return;
                    }
                    String message = ChatColor.GREEN + "" + ChatColor.BOLD + "POKE! " + ChatColor.RESET + ChatColor.YELLOW
                        + (session.minecraftUsername != null ? session.minecraftUsername : "Applicant")
                        + ChatColor.GRAY + " (" + (session.osuInfo != null ? session.osuInfo.username() : "osu user") + ") has applied to join the server and listed you as their sponsor!\n"
                        + ChatColor.GREEN + "" + ChatColor.BOLD + "[ACCEPT] " + ChatColor.DARK_GRAY + "- "
                        + ChatColor.RED + "" + ChatColor.BOLD + "[DENY] " + ChatColor.DARK_GRAY + "- "
                        + ChatColor.GRAY + "" + ChatColor.BOLD + "[IGNORE]";
                    player.sendMessage(message);
                }))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to notify sponsor in-game for " + session.sponsorId, throwable);
                return null;
            });
    }

    private boolean playerExists(String username) {
        try {
            return dataApi.collection("players").all()
                .toCompletableFuture()
                .join()
                .stream()
                .filter(Document::exists)
                .anyMatch(doc -> username.equalsIgnoreCase(doc.get("meta.username", String.class).orElse("")));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to check player existence for " + username, exception);
            return false;
        }
    }

    private boolean hasBlockingRequest(long discordId) {
        try {
            Optional<RequestStore.Request> request = requestStore.findLatestByDiscordId(discordId).toCompletableFuture().join();
            if (request.isEmpty()) {
                return false;
            }
            RequestStore.Request req = request.get();
            boolean pendingOrUnconsumedApproval = req.status() == RequestStore.RequestState.PENDING
                || (req.status() == RequestStore.RequestState.APPROVED && !req.consumed());
            boolean deniedAndBlocked = req.status() == RequestStore.RequestState.DENIED && !req.canReapply();
            boolean blocked = pendingOrUnconsumedApproval || deniedAndBlocked;
            return blocked;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to check blocking request for " + discordId, exception);
            return false;
        }
    }

    private void replyBlocked(ButtonInteractionEvent event) {
        event.reply(blockedMessage()).setEphemeral(true).queue();
    }

    private void replyBlocked(SlashCommandInteractionEvent event) {
        event.reply(blockedMessage()).setEphemeral(true).queue();
    }

    private void replyBlocked(ModalInteractionEvent event) {
        event.reply(blockedMessage()).setEphemeral(true).queue();
    }

    private String blockedMessage() {
        return "Your application was denied previously- please contact a staff member!";
    }

    private boolean isUserFlow(String id) {
        return switch (id) {
            case START_WHITELIST_BUTTON_ID, NO_OSU_BUTTON_ID, INVITER_BUTTON_ID, USERNAME_BUTTON_ID, OSU_BUTTON_ID, SUBMIT_BUTTON_ID, CANCEL_BUTTON_ID -> true;
            default -> false;
        };
    }

    private void handleCancel(ButtonInteractionEvent event, Session session) {
        session.reset();
        event.deferEdit().queue();
        Message message = event.getMessage();
        if (message != null) {
            message.editMessage("Whitelist flow canceled. Click Start Whitelist Process to begin again.")
                .setComponents(List.of())
                .queue(
                    ignored -> {},
                    throwable -> logger.log(Level.WARNING, "Failed to clear canceled whitelist message for " + event.getUser().getIdLong(), throwable)
                );
        }
        event.getHook().sendMessage("Canceled your whitelist session. Click **Start Whitelist Process** to begin again.").setEphemeral(true).queue();
    }

    private CompletionStage<Void> validateOwnership(Session session, long discordId) {
        CompletionStage<Void> minecraftCheck = ensureMinecraftNotTaken(session.minecraftId, discordId);
        long osuUserId = session.osuInfo != null ? session.osuInfo.userId() : 0L;
        CompletionStage<Void> osuCheck = osuUserId > 0 ? ensureOsuNotTaken(osuUserId, discordId) : CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(minecraftCheck.toCompletableFuture(), osuCheck.toCompletableFuture());
    }

    private CompletionStage<Void> ensureSponsorHasOsu(Long sponsorId) {
        if (sponsorId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Sponsor not set."));
        }
        return dataApi.collection("players").all()
            .thenCompose(list -> {
                boolean missingOsu = list.stream()
                    .filter(Document::exists)
                    .filter(doc -> doc.get("discordId", Number.class).map(Number::longValue).orElse(0L) == sponsorId)
                    .noneMatch(doc -> doc.get("osu.userId", Number.class).isPresent());
                if (missingOsu) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Sponsors must have a linked osu! account to vouch for others."));
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    private CompletionStage<Void> ensureMinecraftNotTaken(UUID minecraftId, long discordId) {
        if (minecraftId == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> playerCheck = dataApi.collection("players").load(minecraftId.toString())
            .thenCompose(document -> {
                if (document != null && document.exists()) {
                    Optional<Long> owner = document.get("discordId", Number.class).map(Number::longValue);
                    if (owner.isPresent() && owner.get() != discordId) {
                        return CompletableFuture.failedFuture(new IllegalStateException("That Minecraft account is already linked to another Discord user. If this seems wrong, contact staff."));
                    }
                    Optional<Long> linkingOwner = document.get("linking.discordId", Number.class).map(Number::longValue);
                    if (linkingOwner.isPresent() && linkingOwner.get() != discordId) {
                        return CompletableFuture.failedFuture(new IllegalStateException("That Minecraft account already has a pending link with another Discord user."));
                    }
                }
                return CompletableFuture.completedFuture(null);
            });

        CompletionStage<Void> requestCheck = dataApi.collection("link_requests").all()
            .thenCompose(list -> {
                boolean conflict = list.stream()
                    .filter(Document::exists)
                    .anyMatch(doc -> {
                        String docUuid = doc.get("minecraft.uuid", String.class).orElse(null);
                        if (docUuid == null && doc.key() != null) {
                            docUuid = doc.key().id();
                        }
                        if (docUuid == null || !minecraftId.toString().equalsIgnoreCase(docUuid)) {
                            return false;
                        }
                        long requestDiscord = doc.get("userId", Number.class).map(Number::longValue).orElse(0L);
                        return requestDiscord != 0L && requestDiscord != discordId && isActiveRequest(doc);
                    });
                if (conflict) {
                    return CompletableFuture.failedFuture(new IllegalStateException("That Minecraft account already has a pending or approved request from another Discord user."));
                }
                return CompletableFuture.completedFuture(null);
            });
        return playerCheck.thenCompose(ignored -> requestCheck);
    }

    private CompletionStage<Void> ensureOsuNotTaken(long osuUserId, long discordId) {
        CompletionStage<Void> playerCheck = dataApi.collection("players").all()
            .thenCompose(list -> {
                boolean conflict = list.stream()
                    .filter(Document::exists)
                    .anyMatch(doc -> {
                        long linkedOsu = doc.get("osu.userId", Number.class).map(Number::longValue).orElse(0L);
                        long linkingOsu = doc.get("linking.osu.userId", Number.class).map(Number::longValue).orElse(0L);
                        long effectiveOsu = linkedOsu != 0L ? linkedOsu : linkingOsu;
                        if (effectiveOsu != osuUserId) {
                            return false;
                        }
                        long ownerDiscord = doc.get("discordId", Number.class)
                            .or(() -> doc.get("linking.discordId", Number.class))
                            .map(Number::longValue)
                            .orElse(0L);
                        return ownerDiscord != 0L && ownerDiscord != discordId;
                    });
                if (conflict) {
                    return CompletableFuture.failedFuture(new IllegalStateException("That osu! account is already linked to another Discord user."));
                }
                return CompletableFuture.completedFuture(null);
            });

        CompletionStage<Void> requestCheck = dataApi.collection("link_requests").all()
            .thenCompose(list -> {
                boolean conflict = list.stream()
                    .filter(Document::exists)
                    .anyMatch(doc -> {
                        long requestOsu = doc.get("osu.userId", Number.class).map(Number::longValue).orElse(0L);
                        if (requestOsu != osuUserId) {
                            return false;
                        }
                        long requestDiscord = doc.get("userId", Number.class).map(Number::longValue).orElse(0L);
                        return requestDiscord != 0L && requestDiscord != discordId && isActiveRequest(doc);
                    });
                if (conflict) {
                    return CompletableFuture.failedFuture(new IllegalStateException("That osu! account already has a pending or approved link with another Discord user."));
                }
                return CompletableFuture.completedFuture(null);
            });
        return playerCheck.thenCompose(ignored -> requestCheck);
    }

    private boolean isActiveRequest(Document document) {
        String status = document.get("status", String.class).map(String::toUpperCase).orElse("PENDING");
        return !"DENIED".equals(status);
    }

    private boolean isValidMinecraftUsername(String username) {
        if (username == null) {
            return false;
        }
        int length = username.length();
        return length >= 3 && length <= 16 && username.matches("^[A-Za-z0-9_]+$");
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
        private boolean osuSkipped;
        private Long sponsorId;
        private Instant sponsorRequestExpiresAt;
        private Long requestMessageId;
        private Instant requestCreatedAt;
        private Long discordId;
        private Instant createdAt;
        private String discordUsername;
        private boolean sponsorResolved;
        private boolean allowReapply = true;
        private String decisionReason;

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

        boolean isExpired() {
            return flowStartedAt != null && Instant.now().isAfter(flowStartedAt.plus(WHITELIST_TIMEOUT));
        }

        void reset() {
            source = null;
            invitedBy = null;
            minecraftUsername = null;
            minecraftId = null;
            osuInfo = null;
            sponsorId = null;
            sponsorRequestExpiresAt = null;
            requestMessageId = null;
            requestCreatedAt = null;
            createdAt = null;
            sponsorResolved = false;
            flowStartedAt = null;
            allowReapply = true;
            decisionReason = null;
            osuSkipped = false;
        }

        RequestStore.Request toRequest() {
            return toRequest(RequestStore.RequestState.PENDING);
        }

        RequestStore.Request toRequest(RequestStore.RequestState status) {
            RequestStore.RequestState effectiveStatus = status != null ? status : RequestStore.RequestState.PENDING;
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
                effectiveStatus,
                Optional.ofNullable(sponsorId),
                Optional.ofNullable(requestMessageId),
                allowReapply,
                false,
                Optional.ofNullable(decisionReason)
            );
        }

        boolean sourceComplete() {
            return source != null && (!INVITED_BY_FRIEND.equals(source) || (invitedBy != null && !invitedBy.isBlank()));
        }
        private Instant flowStartedAt;
    }
}

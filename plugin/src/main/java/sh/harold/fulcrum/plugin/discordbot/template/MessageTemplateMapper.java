package sh.harold.fulcrum.plugin.discordbot.template;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.Color;
import java.util.Objects;

public final class MessageTemplateMapper {

    private MessageTemplateMapper() {
    }

    public static MessageCreateData toMessage(MessageTemplate template) {
        Objects.requireNonNull(template, "template");
        MessageCreateBuilder builder = new MessageCreateBuilder();
        if (template.content() != null && !template.content().isBlank()) {
            builder.setContent(template.content());
        }
        for (MessageTemplate.EmbedTemplate embed : template.embeds()) {
            builder.addEmbeds(toEmbed(embed));
        }
        return builder.build();
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed toEmbed(MessageTemplate.EmbedTemplate template) {
        EmbedBuilder builder = new EmbedBuilder();
        if (template.title() != null) {
            builder.setTitle(template.title(), template.url());
        }
        if (template.description() != null) {
            builder.setDescription(template.description());
        }
        if (template.color() != null) {
            builder.setColor(new Color(template.color()));
        }
        if (template.footer() != null) {
            builder.setFooter(template.footer().text(), template.footer().iconUrl());
        }
        if (template.thumbnail() != null && template.thumbnail().url() != null) {
            builder.setThumbnail(template.thumbnail().url());
        }
        if (template.image() != null && template.image().url() != null) {
            builder.setImage(template.image().url());
        }
        if (template.fields() != null) {
            for (MessageTemplate.FieldTemplate field : template.fields()) {
                if (field.name() != null && field.value() != null) {
                    builder.addField(field.name(), field.value(), field.inline());
                }
            }
        }
        return builder.build();
    }
}

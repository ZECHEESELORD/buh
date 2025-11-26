package sh.harold.fulcrum.plugin.discordbot.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageTemplate(
    String content,
    List<EmbedTemplate> embeds
) {

    public MessageTemplate {
        embeds = embeds == null ? List.of() : List.copyOf(embeds);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbedTemplate(
        String title,
        String description,
        String url,
        Integer color,
        List<FieldTemplate> fields,
        FooterTemplate footer,
        ImageTemplate image,
        ImageTemplate thumbnail
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldTemplate(String name, String value, @JsonProperty("inline") boolean inline) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FooterTemplate(String text, @JsonProperty("icon_url") String iconUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageTemplate(String url) {
    }
}

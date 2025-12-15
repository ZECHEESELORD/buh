package sh.harold.fulcrum.plugin.jukebox.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import java.util.Objects;

public final class JukeboxVoicechatPlugin implements VoicechatPlugin {

    private final JukeboxVoicechatApi voicechatApi;

    public JukeboxVoicechatPlugin(JukeboxVoicechatApi voicechatApi) {
        this.voicechatApi = Objects.requireNonNull(voicechatApi, "voicechatApi");
    }

    @Override
    public String getPluginId() {
        return "buh_jukebox";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            voicechatApi.setServerApi(serverApi);
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> voicechatApi.setServerApi(event.getVoicechat()));
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> voicechatApi.clear());
    }
}

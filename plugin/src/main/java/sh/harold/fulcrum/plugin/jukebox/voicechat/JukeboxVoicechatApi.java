package sh.harold.fulcrum.plugin.jukebox.voicechat;

import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class JukeboxVoicechatApi {

    private final AtomicReference<VoicechatServerApi> serverApi;

    public JukeboxVoicechatApi() {
        this.serverApi = new AtomicReference<>();
    }

    public Optional<VoicechatServerApi> serverApi() {
        return Optional.ofNullable(serverApi.get());
    }

    public void setServerApi(VoicechatServerApi api) {
        serverApi.set(api);
    }

    public void clear() {
        serverApi.set(null);
    }

    public boolean isReady() {
        return serverApi.get() != null;
    }
}

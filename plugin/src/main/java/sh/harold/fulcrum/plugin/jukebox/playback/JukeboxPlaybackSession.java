package sh.harold.fulcrum.plugin.jukebox.playback;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.Objects;

public record JukeboxPlaybackSession(
    String trackId,
    AudioChannel channel,
    AudioPlayer player,
    OpusEncoder encoder,
    PcmTrackStream stream
) {

    public JukeboxPlaybackSession {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(stream, "stream");
    }
}


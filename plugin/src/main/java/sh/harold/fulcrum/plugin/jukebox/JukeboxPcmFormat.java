package sh.harold.fulcrum.plugin.jukebox;

public final class JukeboxPcmFormat {

    public static final int SAMPLE_RATE_HZ = 48_000;
    public static final int CHANNELS = 1;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final String SAMPLE_FORMAT = "s16le";
    public static final int FRAME_SAMPLES_PER_CHANNEL = 960;

    public static final int FRAME_BYTES = FRAME_SAMPLES_PER_CHANNEL * CHANNELS * BYTES_PER_SAMPLE;

    private JukeboxPcmFormat() {
    }

    public static long expectedFileByteLength(long samplesPerChannel) {
        return samplesPerChannel * (long) CHANNELS * BYTES_PER_SAMPLE;
    }
}


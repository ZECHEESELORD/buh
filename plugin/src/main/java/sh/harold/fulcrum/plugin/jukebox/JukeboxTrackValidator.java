package sh.harold.fulcrum.plugin.jukebox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class JukeboxTrackValidator {

    private JukeboxTrackValidator() {
    }

    public static JukeboxTrackValidation validateReady(JukeboxTrackMetadata metadata, JukeboxConfig config, Path pcmPath) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(pcmPath, "pcmPath");

        if (metadata.status() != JukeboxTrackStatus.READY) {
            return JukeboxTrackValidation.invalid("Track status is " + metadata.status() + ".");
        }

        if (metadata.sampleRateHz() != JukeboxPcmFormat.SAMPLE_RATE_HZ) {
            return JukeboxTrackValidation.invalid("Unsupported sample rate: " + metadata.sampleRateHz() + " Hz.");
        }
        if (metadata.channels() != JukeboxPcmFormat.CHANNELS) {
            return JukeboxTrackValidation.invalid("Unsupported channel count: " + metadata.channels() + ".");
        }
        String sampleFormat = metadata.sampleFormat();
        if (sampleFormat == null || !JukeboxPcmFormat.SAMPLE_FORMAT.equalsIgnoreCase(sampleFormat)) {
            return JukeboxTrackValidation.invalid("Unsupported sample format: " + sampleFormat + ".");
        }
        if (metadata.bytesPerSample() != JukeboxPcmFormat.BYTES_PER_SAMPLE) {
            return JukeboxTrackValidation.invalid("Unsupported bytes per sample: " + metadata.bytesPerSample() + ".");
        }
        if (metadata.frameSamplesPerChannel() != JukeboxPcmFormat.FRAME_SAMPLES_PER_CHANNEL) {
            return JukeboxTrackValidation.invalid("Unsupported frame size: " + metadata.frameSamplesPerChannel() + " samples.");
        }
        if (metadata.frameBytes() != JukeboxPcmFormat.FRAME_BYTES) {
            return JukeboxTrackValidation.invalid("Unsupported frame bytes: " + metadata.frameBytes() + ".");
        }

        if (metadata.durationSeconds() > config.maxDuration().toSeconds()) {
            return JukeboxTrackValidation.invalid("Track is too long.");
        }

        long expectedLength = JukeboxPcmFormat.expectedFileByteLength(metadata.samplesPerChannel());
        if (metadata.fileByteLength() != expectedLength) {
            return JukeboxTrackValidation.invalid("Metadata length mismatch.");
        }

        if (!Files.exists(pcmPath)) {
            return JukeboxTrackValidation.invalid("PCM file missing.");
        }
        try {
            long actualLength = Files.size(pcmPath);
            if (actualLength != metadata.fileByteLength()) {
                return JukeboxTrackValidation.invalid("PCM file length mismatch.");
            }
        } catch (IOException exception) {
            return JukeboxTrackValidation.invalid("PCM file unreadable.");
        }

        return JukeboxTrackValidation.ok();
    }
}

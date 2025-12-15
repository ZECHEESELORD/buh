package sh.harold.fulcrum.plugin.jukebox.playback;

import sh.harold.fulcrum.plugin.jukebox.JukeboxPcmFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class PcmTrackStream implements Supplier<short[]>, AutoCloseable {

    private static final int FRAME_SAMPLES = JukeboxPcmFormat.FRAME_SAMPLES_PER_CHANNEL * JukeboxPcmFormat.CHANNELS;
    private static final short[] SILENCE_FRAME = new short[FRAME_SAMPLES];

    private final Path pcmPath;
    private final BlockingQueue<short[]> frames;
    private final AtomicBoolean finished;
    private final AtomicBoolean closed;
    private final AtomicReference<Throwable> error;
    private final Duration fadeIn;

    private volatile long framesSupplied;
    private Thread readerThread;

    public PcmTrackStream(Path pcmPath, int bufferFrames, Duration fadeIn) {
        this.pcmPath = Objects.requireNonNull(pcmPath, "pcmPath");
        if (bufferFrames < 1) {
            throw new IllegalArgumentException("bufferFrames must be >= 1");
        }
        this.frames = new ArrayBlockingQueue<>(bufferFrames);
        this.fadeIn = Objects.requireNonNull(fadeIn, "fadeIn");
        this.finished = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.error = new AtomicReference<>();
    }

    public void start() {
        if (readerThread != null) {
            throw new IllegalStateException("Stream already started");
        }
        readerThread = Thread.startVirtualThread(this::readLoop);
    }

    public boolean isFinished() {
        return finished.get() && frames.isEmpty();
    }

    public Optional<Throwable> error() {
        return Optional.ofNullable(error.get());
    }

    @Override
    public short[] get() {
        if (closed.get()) {
            return null;
        }
        short[] frame = frames.poll();
        if (frame == null) {
            return finished.get() ? null : SILENCE_FRAME.clone();
        }
        applyFadeIn(frame);
        framesSupplied++;
        return frame;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        frames.clear();
    }

    private void readLoop() {
        if (!Files.exists(pcmPath)) {
            error.compareAndSet(null, new IOException("PCM file does not exist: " + pcmPath));
            finished.set(true);
            return;
        }
        try (FileChannel channel = FileChannel.open(pcmPath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(JukeboxPcmFormat.FRAME_BYTES);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            while (!closed.get()) {
                buffer.clear();
                int bytesRead = channel.read(buffer);
                if (bytesRead < 0) {
                    break;
                }
                short[] frame = decodeFrame(buffer, bytesRead);
                while (!closed.get()) {
                    if (frames.offer(frame, 50, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            error.compareAndSet(null, throwable);
        } finally {
            finished.set(true);
        }
    }

    private short[] decodeFrame(ByteBuffer buffer, int bytesRead) {
        int safeBytesRead = Math.max(0, Math.min(bytesRead, JukeboxPcmFormat.FRAME_BYTES));
        int safeShortsRead = safeBytesRead / JukeboxPcmFormat.BYTES_PER_SAMPLE;

        buffer.flip();
        short[] frame = new short[FRAME_SAMPLES];
        buffer.asShortBuffer().get(frame, 0, Math.min(safeShortsRead, frame.length));
        return frame;
    }

    private void applyFadeIn(short[] frame) {
        long fadeInMillis = fadeIn.toMillis();
        if (fadeInMillis <= 0) {
            return;
        }
        long fadeInFrames = Math.max(1, fadeInMillis / 20L);
        long progressFrame = framesSupplied;
        if (progressFrame >= fadeInFrames) {
            return;
        }
        double volume = (progressFrame + 1D) / (double) fadeInFrames;
        for (int index = 0; index < frame.length; index++) {
            frame[index] = (short) Math.round(frame[index] * volume);
        }
    }
}

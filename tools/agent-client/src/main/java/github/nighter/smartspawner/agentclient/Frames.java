package github.nighter.smartspawner.agentclient;

import github.nighter.smartspawner.agentclient.core.AgentException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Screenshots of the framebuffer. A capture is not finished when the call returns: from 1.21.11 the
 * pixels are read back from the GPU asynchronously and the PNG is written on a worker thread, so a
 * capture is a {@link Capture} the caller waits on across ticks.
 */
final class Frames {
    private static int counter;
    /** How the last capture is found again by {@code client.pixels} when it is not asked to take one. */
    private static Path lastFile;
    private static BufferedImage cached;
    private static Path cachedFile;

    private Frames() { }

    record Capture(Path file, AtomicReference<String> message, AtomicReference<String> failure) {
        boolean finished() {
            return message.get() != null || failure.get() != null;
        }
    }

    /**
     * Starts a capture into {@code <queue>/screenshots/<name>.png}. Vanilla builds that path itself from
     * the directory it is given, so the queue's own directory is handed over.
     */
    static Capture capture(Minecraft minecraft, Path queue, String name) {
        String file = name == null || name.isBlank() ? String.format("frame-%04d", ++counter) : name.strip();
        if (!file.endsWith(".png")) file = file + ".png";
        Path target = queue.resolve(Screenshot.SCREENSHOT_DIR).resolve(file);
        AtomicReference<String> message = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        try {
            Files.createDirectories(target.getParent());
            // Retaking a shot under the same name has to replace it, not sit beside a stale one.
            Files.deleteIfExists(target);
            Screenshot.grab(queue.toFile(), file, minecraft.gameRenderer.mainRenderTarget(), 1,
                    component -> message.set(component.getString()));
        } catch (IOException | RuntimeException e) {
            failure.set(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        lastFile = target;
        cached = null;
        cachedFile = null;
        return new Capture(target, message, failure);
    }

    /** The file the last capture wrote, or null when nothing has been captured yet. */
    static Path lastFile() {
        return lastFile;
    }

    /**
     * The image a capture wrote, read back with {@code javax.imageio} rather than from the GPU, which
     * keeps the readback out of this and leaves the file as evidence. One image is kept decoded, since a
     * probe usually samples several points of the same frame.
     */
    static BufferedImage image(Path file) {
        if (file == null) {
            throw new AgentException("no frame has been captured yet; run client.capture first, "
                    + "or ask client.pixels to take one with \"capture\": true");
        }
        if (file.equals(cachedFile) && cached != null) return cached;
        if (!Files.isRegularFile(file)) {
            throw new AgentException("the frame " + file + " is not there; the capture may still be running");
        }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) throw new AgentException("the frame " + file + " is not a readable image");
            cached = image;
            cachedFile = file;
            return image;
        } catch (IOException e) {
            throw new AgentException("could not read the frame " + file + ": " + e.getMessage());
        }
    }

    /** {@code #AARRGGBB}. */
    static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}

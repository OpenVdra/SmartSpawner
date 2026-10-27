package github.nighter.smartspawner.updates;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Copies {@code language/} and {@code gui_layouts/} to {@code language.backup-<yyyy-MM-dd>/} before a
 * migration rewrites or deletes anything in them. An existing backup is never overwritten; a second one
 * on the same day gets a {@code -2}, {@code -3}, ... suffix.
 */
final class LanguageBackup {
    private static final List<String> FOLDERS = List.of("language", "gui_layouts");

    private LanguageBackup() {}

    /**
     * A backup made the first time it is asked for, then reused, so every migration of one startup
     * shares a single folder. The result, a folder name or {@code null}, is remembered either way.
     */
    static Supplier<String> once(File dataFolder, LocalDate today, Logger log) {
        return new Supplier<>() {
            private boolean done;
            private String name;

            @Override
            public synchronized String get() {
                if (!done) {
                    name = create(dataFolder, today, log);
                    done = true;
                }
                return name;
            }
        };
    }

    /**
     * @return the backup folder's name, or {@code null} if it could not be written, in which case the
     *         caller must not touch the originals
     */
    static String create(File dataFolder, LocalDate today, Logger log) {
        String baseName = "language.backup-" + today;
        File target = new File(dataFolder, baseName);
        for (int i = 2; target.exists(); i++) {
            target = new File(dataFolder, baseName + "-" + i);
        }

        try {
            for (String folder : FOLDERS) {
                Path source = new File(dataFolder, folder).toPath();
                if (Files.isDirectory(source)) {
                    copy(source, target.toPath().resolve(folder));
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to back up the language files to " + target.getName(), e);
            return null;
        }
        return target.getName();
    }

    private static void copy(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}

package dev.noir.basehunt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.function.BiConsumer;

/**
 * Appends finds to a local, tab-separated file: {@code config/noir/logs/<module>.log}.
 * Columns: ISO time, server, dimension, x, y, z, details. Nothing leaves the machine.
 */
public final class FindLog {
    private final Path dir;
    private final BiConsumer<String, Throwable> errors;

    public FindLog(Path dir, BiConsumer<String, Throwable> errors) {
        this.dir = dir;
        this.errors = errors;
    }

    public Path file(String module) {
        return dir.resolve(module.toLowerCase().replaceAll("[^a-z0-9_-]", "") + ".log");
    }

    public synchronized void append(String module, String server, String dimension, int x, int y, int z, String details) {
        String line = Instant.now() + "\t" + server + "\t" + dimension + "\t" + x + "\t" + y + "\t" + z + "\t"
            + details.replace('\t', ' ').replace('\n', ' ') + System.lineSeparator();
        try {
            Files.createDirectories(dir);
            Files.writeString(file(module), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            errors.accept("Failed to write " + module + " log", e);
        }
    }
}

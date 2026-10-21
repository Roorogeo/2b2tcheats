package dev.noir.basehunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Local waypoints, one file per server: {@code config/noir/waypoints/<server>.json}.
 */
public final class WaypointStore {
    public record Waypoint(String name, String type, String dimension, int x, int y, int z, int color, long createdMs) {
        public double distanceTo(double px, double py, double pz) {
            double dx = x + 0.5 - px, dy = y - py, dz = z + 0.5 - pz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private final Path dir;
    private final BiConsumer<String, Throwable> errors;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Waypoint> waypoints = new ArrayList<>();
    private String server = "";

    public WaypointStore(Path dir, BiConsumer<String, Throwable> errors) {
        this.dir = dir;
        this.errors = errors;
    }

    public static String fileName(String server) {
        String s = server.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return (s.isEmpty() ? "unknown" : s) + ".json";
    }

    /** Switches to the waypoints of {@code server} (on join). */
    public synchronized void load(String server) {
        this.server = server;
        waypoints.clear();
        Path f = dir.resolve(fileName(server));
        if (!Files.isRegularFile(f)) return;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return;
            for (JsonElement e : root.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                try {
                    waypoints.add(new Waypoint(o.get("name").getAsString(), o.get("type").getAsString(), o.get("dimension").getAsString(),
                        o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(), (int) Long.parseLong(o.get("color").getAsString(), 16),
                        o.get("created").getAsLong()));
                } catch (RuntimeException ignored) {
                    // skip malformed entry
                }
            }
        } catch (Exception e) {
            errors.accept("Ignoring unreadable waypoint file " + f.getFileName(), e);
        }
    }

    public synchronized void save() {
        JsonArray a = new JsonArray();
        for (Waypoint w : waypoints) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("type", w.type());
            o.addProperty("dimension", w.dimension());
            o.addProperty("x", w.x());
            o.addProperty("y", w.y());
            o.addProperty("z", w.z());
            o.addProperty("color", Integer.toHexString(w.color()));
            o.addProperty("created", w.createdMs());
            a.add(o);
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName(server)), gson.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            errors.accept("Failed to save waypoints", e);
        }
    }

    /** Adds a waypoint unless one of the same type already exists within {@code mergeRadius} blocks. */
    public synchronized boolean add(Waypoint w, double mergeRadius) {
        for (Waypoint o : waypoints) {
            if (o.dimension().equals(w.dimension()) && o.type().equals(w.type())
                && o.distanceTo(w.x() + 0.5, w.y(), w.z() + 0.5) <= mergeRadius) return false;
        }
        waypoints.add(w);
        save();
        return true;
    }

    public synchronized boolean remove(String name) {
        boolean r = waypoints.removeIf(w -> w.name().equalsIgnoreCase(name));
        if (r) save();
        return r;
    }

    public synchronized void clear() {
        waypoints.clear();
        save();
    }

    public synchronized List<Waypoint> all() {
        return Collections.unmodifiableList(new ArrayList<>(waypoints));
    }

    public synchronized List<Waypoint> in(String dimension) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : waypoints) if (w.dimension().equals(dimension)) out.add(w);
        return out;
    }

    /** Expands {type}, {x}, {y}, {z}, {dim}, {n} in a name format. */
    public static String format(String fmt, String type, String dim, int x, int y, int z, int n) {
        return fmt.replace("{type}", type).replace("{x}", Integer.toString(x)).replace("{y}", Integer.toString(y))
            .replace("{z}", Integer.toString(z)).replace("{dim}", dim).replace("{n}", Integer.toString(n));
    }
}

package dev.noir.basehunt;

import dev.noir.NoirCore;
import dev.noir.event.Events;
import dev.noir.module.Category;
import dev.noir.module.Module;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Shared services for the Base Hunting modules: the chunk scanner and cache, new/old
 * chunk tracking, local waypoints, find logs and local alerts. Scanning only runs while
 * at least one Base Hunting module is enabled. Everything here is read-only and local.
 */
public final class BaseHunt {
    /** Implemented by modules that want block positions from scans. */
    public interface BlockInterest {
        Set<String> interestingBlocks();
    }

    /** Posted on the game thread when a chunk scan finishes. */
    public record ScanDone(ChunkScan scan) {}

    private final NoirCore core;
    private final ScanService scans;
    private final NewChunkTracker newChunks = new NewChunkTracker();
    private final WaypointStore waypoints;
    private final FindLog log;
    private String dimension = "overworld";

    public BaseHunt(NoirCore core, Executor executor) {
        this.core = core;
        this.scans = executor == null
            ? new ScanService(() -> core.platform().clock().millis())
            : new ScanService(executor, () -> core.platform().clock().millis());
        this.waypoints = new WaypointStore(core.platform().configDir().resolve("waypoints"), core.platform()::logError);
        this.log = new FindLog(core.platform().configDir().resolve("logs"), core.platform()::logError);
        scans.setInteresting(this::interesting);
        scans.addListener(newChunks::onScan);
        scans.addListener(scan -> core.events().post(new ScanDone(scan)));

        core.events().subscribe(Events.ChunkLoad.class, e -> {
            if (e.view() == null) return;
            dimension = e.view().dimension();
            newChunks.onChunkLoad(e.view().key(), now());
            if (active()) scans.submit(e.view());
        });
        core.events().subscribe(Events.BlockChange.class, e -> {
            ChunkKey key = new ChunkKey(e.dimension(), e.x() >> 4, e.z() >> 4);
            if (e.flowingFluid()) newChunks.onFlowingUpdate(key, now());
            if (active() && scans.get(key) != null) scans.invalidate(key);
        });
        core.events().subscribe(Events.TickPost.class, e -> scans.tick());
        core.events().subscribe(Events.WorldChange.class, e -> {
            if (e.joined()) {
                waypoints.load(core.platform().serverId());
            } else {
                scans.clear();
                newChunks.clear();
            }
        });
        core.events().subscribe(Events.ModuleToggle.class, e -> {
            if (e.enabled() && e.module().category() == Category.BASE_HUNTING) scans.rescanLoaded();
        });
    }

    private long now() {
        return core.platform().clock().millis();
    }

    /** True while any Base Hunting module is enabled. */
    public boolean active() {
        for (Module m : core.modules().byCategory(Category.BASE_HUNTING)) if (m.isEnabled()) return true;
        return false;
    }

    private Set<String> interesting() {
        Set<String> ids = new HashSet<>();
        for (Module m : core.modules().all()) {
            if (m.isEnabled() && m instanceof BlockInterest bi) ids.addAll(bi.interestingBlocks());
        }
        return ids;
    }

    public ScanService scans() {
        return scans;
    }

    public NewChunkTracker newChunks() {
        return newChunks;
    }

    public WaypointStore waypoints() {
        return waypoints;
    }

    public FindLog log() {
        return log;
    }

    public String dimension() {
        return scans.source() != ChunkSource.NONE ? scans.source().dimension() : dimension;
    }

    public void setSource(ChunkSource source) {
        scans.setSource(source);
    }

    /** Local-only alert: a client chat line and/or a toast. */
    public void alert(String module, String message, boolean chat, boolean toast) {
        if (chat) core.platform().chat(module + ": " + message);
        if (toast) core.hud().toast(module, message);
    }

    public void logFind(String module, int x, int y, int z, String details) {
        log.append(module, core.platform().serverId(), dimension(), x, y, z, details);
    }

    public void shutdown() {
        scans.shutdown();
    }
}

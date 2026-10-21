package dev.noir.basehunt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Scans chunks on a background thread and caches the results so rendering never
 * touches chunk data. Results are handed to listeners on the game thread from
 * {@link #tick()}. Read-only: only reads chunks the server already sent.
 */
public final class ScanService {
    private static final int MAX_CACHE = 8192;

    private final Executor executor;
    private final LongSupplier clock;
    private final Map<ChunkKey, ChunkScan> cache = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Long> order = new LinkedHashMap<>();
    private final Set<ChunkKey> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<ChunkKey> dirty = new HashSet<>();
    private final ConcurrentLinkedQueue<ChunkScan> done = new ConcurrentLinkedQueue<>();
    private final List<Consumer<ChunkScan>> listeners = new ArrayList<>();
    private final AtomicLong generation = new AtomicLong();
    private Supplier<Set<String>> interesting = Set::of;
    private ChunkSource source = ChunkSource.NONE;

    public ScanService(LongSupplier clock) {
        this(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Noir chunk scanner");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }), clock);
    }

    /** Runs scans on {@code executor}; tests pass {@code Runnable::run} to scan synchronously. */
    public ScanService(Executor executor, LongSupplier clock) {
        this.executor = executor;
        this.clock = clock;
    }

    public void setSource(ChunkSource source) {
        this.source = source;
    }

    public ChunkSource source() {
        return source;
    }

    /** Union of block ids any enabled module wants positions for. */
    public void setInteresting(Supplier<Set<String>> interesting) {
        this.interesting = interesting;
    }

    public void addListener(Consumer<ChunkScan> listener) {
        listeners.add(listener);
    }

    /** Queues a scan of a loaded chunk. */
    public void submit(ChunkView view) {
        ChunkKey key = view.key();
        if (!inFlight.add(key)) {
            synchronized (dirty) {
                dirty.add(key);
            }
            return;
        }
        long gen = generation.get();
        Set<String> wanted = interesting.get();
        executor.execute(() -> {
            try {
                ChunkScan scan = ChunkScanner.scan(view, wanted, clock.getAsLong());
                if (generation.get() == gen) done.add(scan);
            } catch (RuntimeException ignored) {
                // Chunk unloaded or changed under us; it is rescanned on its next update.
            } finally {
                inFlight.remove(key);
            }
        });
    }

    /** Marks a chunk for rescan (block changed). Coalesced and resubmitted on the next tick. */
    public void invalidate(ChunkKey key) {
        synchronized (dirty) {
            dirty.add(key);
        }
    }

    /** Rescans every loaded chunk (e.g. the Search list changed). */
    public void rescanLoaded() {
        for (ChunkView v : source.loaded()) submit(v);
    }

    /** Drops all cached results and pending work. */
    public void clear() {
        generation.incrementAndGet();
        cache.clear();
        synchronized (order) {
            order.clear();
        }
        done.clear();
        synchronized (dirty) {
            dirty.clear();
        }
    }

    /** Game thread: publish finished scans and resubmit dirty chunks. */
    public void tick() {
        ChunkScan s;
        while ((s = done.poll()) != null) {
            cache.put(s.key, s);
            synchronized (order) {
                order.remove(s.key);
                order.put(s.key, s.scannedAtMs);
                while (order.size() > MAX_CACHE) {
                    ChunkKey eldest = order.keySet().iterator().next();
                    order.remove(eldest);
                    cache.remove(eldest);
                }
            }
            for (Consumer<ChunkScan> l : listeners) l.accept(s);
        }
        List<ChunkKey> resubmit;
        synchronized (dirty) {
            resubmit = new ArrayList<>(dirty);
            dirty.clear();
        }
        for (ChunkKey k : resubmit) {
            if (inFlight.contains(k)) {
                invalidate(k);
                continue;
            }
            if (!k.dimension().equals(source.dimension())) continue;
            ChunkView v = source.get(k.x(), k.z());
            if (v != null) submit(v);
        }
    }

    public ChunkScan get(ChunkKey key) {
        return cache.get(key);
    }

    public Collection<ChunkScan> all() {
        return cache.values();
    }

    public int size() {
        return cache.size();
    }

    public boolean busy() {
        return !inFlight.isEmpty() || !done.isEmpty();
    }

    public void shutdown() {
        if (executor instanceof ExecutorService es) es.shutdownNow();
    }
}

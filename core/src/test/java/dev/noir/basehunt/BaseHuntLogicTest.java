package dev.noir.basehunt;

import dev.noir.testutil.FakeChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseHuntLogicTest {
    private static SusScorer.Config config() {
        return new SusScorer.Config(-64, 320,
            true, 2, 2,
            true, 1, 16, Set.of(Blocks.defaultPlacedBlocks()).stream().map(s -> "minecraft:" + s).collect(java.util.stream.Collectors.toSet()),
            true, 0.5, 4,
            true, 1, 96, 0.35,
            true, 5);
    }

    private static void sprinkleOres(FakeChunk c) {
        // ~4% iron ore through the stone.
        for (int y = 0; y < 60; y += 2) for (int z = (y / 2) % 5; z < 16; z += 5) for (int x = (y / 2) % 5; x < 16; x += 5) c.set(x, y, z, "iron_ore");
    }

    @Test
    void scannerCountsFindsAndExposure() {
        FakeChunk c = FakeChunk.overworld(2, -3);
        c.set(1, 70, 1, "chest").set(2, 70, 1, "chest").set(3, 10, 3, "diamond_ore");
        c.fill(0, 20, 7, 15, 21, 8, "air"); // tunnel
        c.blockEntity("minecraft:chest").blockEntity("minecraft:chest");
        ChunkScan s = ChunkScanner.scan(c, Set.of("minecraft:chest"), 0);
        assertEquals(2, s.count("minecraft:chest"));
        assertEquals(2, s.finds.size());
        assertEquals(java.util.Set.of(33, 34), java.util.Set.of(s.finds.get(0).x(), s.finds.get(1).x()), "finds use world coords");
        assertEquals(-48 + 1, s.finds.get(0).z(), "chunk z -3 starts at block -48");
        assertEquals(2, s.count("minecraft:chest", 64, 80));
        assertEquals(0, s.count("minecraft:chest", 0, 40));
        assertTrue(s.sum(s.exposedStone, 16, 31) > 50, "tunnel walls are exposed stone");
        assertEquals(2, s.blockEntityTotal());
    }

    @Test
    void naturalChunkScoresLow() {
        FakeChunk c = FakeChunk.overworld(0, 0);
        sprinkleOres(c);
        c.set(5, 63, 5, "chest"); // a lone dungeon-style chest
        SusScorer.Result r = SusScorer.score(ChunkScanner.scan(c, Set.of(), 0), config());
        assertTrue(r.score() < 10, "natural chunk scored " + r.score() + " " + r.reasons());
    }

    @Test
    void baseChunkScoresHigh() {
        FakeChunk c = FakeChunk.overworld(0, 0);
        sprinkleOres(c);
        c.fill(0, 63, 0, 5, 63, 0, "chest");
        c.set(8, 63, 8, "white_bed").set(9, 63, 8, "crafting_table").set(10, 63, 8, "furnace").fill(0, 63, 4, 6, 64, 4, "obsidian");
        for (int i = 0; i < 9; i++) c.blockEntity("minecraft:chest");
        SusScorer.Result r = SusScorer.score(ChunkScanner.scan(c, Set.of(), 0), config());
        assertTrue(r.score() >= 10, "base chunk scored " + r.score());
        assertTrue(r.summary().contains("storage"));
        assertTrue(r.summary().contains("placed"));
    }

    @Test
    void stripMinedWallsAreSuspicious() {
        FakeChunk c = FakeChunk.overworld(0, 0);
        sprinkleOres(c);
        // A 2x2 tunnel grid with every ore along the walls removed (mined out).
        for (int z = 1; z < 16; z += 4) {
            c.fill(0, 20, z, 15, 21, z + 1, "air");
            c.fill(0, 19, z - 1, 15, 22, z - 1, "stone");
            if (z + 2 < 16) c.fill(0, 19, z + 2, 15, 22, z + 2, "stone");
            c.fill(0, 19, z, 15, 19, z + 1, "stone");
            c.fill(0, 22, z, 15, 22, z + 1, "stone");
        }
        SusScorer.Config cfg = config();
        SusScorer.Result r = SusScorer.score(ChunkScanner.scan(c, Set.of(), 0), cfg);
        assertTrue(r.summary().contains("mined walls"), r.reasons().toString());
    }

    @Test
    void portalCounts() {
        FakeChunk c = FakeChunk.overworld(0, 0);
        c.fill(4, 64, 4, 5, 66, 4, "nether_portal");
        SusScorer.Result r = SusScorer.score(ChunkScanner.scan(c, Set.of(), 0), config());
        assertTrue(r.summary().contains("6 portal blocks"));
    }

    @Test
    void yRangeLimitsCounts() {
        FakeChunk c = FakeChunk.overworld(0, 0);
        c.fill(0, 100, 0, 9, 100, 0, "chest");
        SusScorer.Config cfg = config();
        SusScorer.Config low = new SusScorer.Config(-64, 60, cfg.storage(), cfg.storageWeight(), cfg.storageAllowance(), cfg.placed(),
            cfg.placedWeight(), cfg.placedCap(), cfg.placedBlocks(), cfg.blockEntities(), cfg.blockEntityWeight(), cfg.blockEntityAllowance(),
            cfg.ores(), cfg.oreWeight(), cfg.minExposedStone(), cfg.oreRatioFactor(), cfg.portals(), cfg.portalWeight());
        ChunkScan s = ChunkScanner.scan(c, Set.of(), 0);
        assertTrue(SusScorer.score(s, cfg).summary().contains("10 storage"));
        assertFalse(SusScorer.score(s, low).summary().contains("storage"));
    }

    @Test
    void newChunksByLiquidFlowAndVersion() {
        NewChunkTracker t = new NewChunkTracker();
        ChunkKey a = new ChunkKey("overworld", 0, 0), b = new ChunkKey("overworld", 1, 0);
        t.onChunkLoad(a, 1000);
        t.onFlowingUpdate(a, 2500);
        assertEquals(NewChunkTracker.State.NEW, t.verdict(a).state());

        t.onChunkLoad(b, 1000);
        t.onFlowingUpdate(b, 9000); // too late
        assertEquals(NewChunkTracker.State.UNKNOWN, t.verdict(b).state());
        FakeChunk settled = FakeChunk.overworld(1, 0).flowing(3, 64, 3);
        t.onScan(ChunkScanner.scan(settled, Set.of(), 0));
        assertEquals(NewChunkTracker.State.OLD, t.verdict(b).state());
        assertEquals(NewChunkTracker.Reason.SETTLED_LIQUID, t.verdict(b).reason());

        // Liquid NEW is never overridden by a later scan.
        t.onScan(ChunkScanner.scan(FakeChunk.overworld(0, 0).flowing(1, 64, 1), Set.of(), 0));
        assertEquals(NewChunkTracker.State.NEW, t.verdict(a).state());
    }

    @Test
    void pre118OverworldIsOld() {
        FakeChunk old = FakeChunk.overworld(5, 5);
        old.fill(0, -64, 0, 15, -1, 15, "stone");
        assertTrue(NewChunkTracker.isOldVersion(ChunkScanner.scan(old, Set.of(), 0)));
        assertFalse(NewChunkTracker.isOldVersion(ChunkScanner.scan(FakeChunk.overworld(5, 5), Set.of(), 0)));
        FakeChunk nether = new FakeChunk("the_nether", 0, 0, 0, 128, 100, "minecraft:netherrack");
        assertTrue(NewChunkTracker.isOldVersion(ChunkScanner.scan(nether, Set.of(), 0)));
        nether.set(1, 10, 1, "ancient_debris");
        assertFalse(NewChunkTracker.isOldVersion(ChunkScanner.scan(nether, Set.of(), 0)));
    }

    @Test
    void trailIsALongThinLineOfOldChunks() {
        Set<ChunkKey> old = new HashSet<>(), neu = new HashSet<>();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) neu.add(new ChunkKey("overworld", x, z));
        for (int i = -8; i <= 8; i++) {
            ChunkKey k = new ChunkKey("overworld", i, i / 3);
            neu.remove(k);
            old.add(k);
        }
        // A 4x4 blob of old chunks: not a trail.
        Set<ChunkKey> blob = new HashSet<>();
        for (int x = 20; x < 24; x++) for (int z = 20; z < 24; z++) blob.add(new ChunkKey("overworld", x, z));
        old.addAll(blob);
        List<TrailDetector.Trail> trails = TrailDetector.detect(old, neu, 6, 3, 0.5);
        assertEquals(1, trails.size());
        assertTrue(trails.get(0).length() >= 15, "length " + trails.get(0).length());
        assertFalse(trails.get(0).chunks().stream().anyMatch(blob::contains));
    }

    @Test
    void waypointsPersistAndMerge(@TempDir Path dir) {
        WaypointStore s = new WaypointStore(dir, (m, t) -> {});
        s.load("play.example.org");
        assertTrue(s.add(new WaypointStore.Waypoint("Stash 1", "Stash", "overworld", 100, 64, 200, 0xFFFFFFFF, 1), 32));
        assertFalse(s.add(new WaypointStore.Waypoint("Stash 2", "Stash", "overworld", 110, 64, 205, 0xFFFFFFFF, 2), 32), "merged");
        assertTrue(s.add(new WaypointStore.Waypoint("Portal", "Portal", "overworld", 110, 64, 205, 0xFFFFFFFF, 3), 32), "different type");
        WaypointStore t = new WaypointStore(dir, (m, e) -> {});
        t.load("play.example.org");
        assertEquals(2, t.all().size());
        t.load("other.server");
        assertEquals(0, t.all().size());
        assertEquals("Stash 100 200", WaypointStore.format("{type} {x} {z}", "Stash", "overworld", 100, 64, 200, 1));
    }

    @Test
    void portalsCluster() {
        List<ChunkScan.Find> finds = List.of(
            new ChunkScan.Find(0, 64, 0, "minecraft:nether_portal"), new ChunkScan.Find(1, 64, 0, "minecraft:nether_portal"),
            new ChunkScan.Find(0, 65, 0, "minecraft:nether_portal"), new ChunkScan.Find(1, 65, 0, "minecraft:nether_portal"),
            new ChunkScan.Find(10, 64, 10, "minecraft:nether_portal"));
        var portals = dev.noir.module.basehunting.PortalESP.cluster(finds, Set.of("minecraft:nether_portal"));
        assertEquals(2, portals.size());
    }
}

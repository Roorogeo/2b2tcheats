package dev.noir.basehunt;

import dev.noir.NoirCore;
import dev.noir.event.Events;
import dev.noir.module.basehunting.NewChunks;
import dev.noir.module.basehunting.PortalESP;
import dev.noir.module.basehunting.Search;
import dev.noir.module.basehunting.StashFinder;
import dev.noir.module.basehunting.SusChunk;
import dev.noir.module.basehunting.Trails;
import dev.noir.module.basehunting.Waypoints;
import dev.noir.render.CameraInfo;
import dev.noir.render.WorldCanvas;
import dev.noir.sim.SimulatedClient;
import dev.noir.testutil.FakeChunk;
import dev.noir.testutil.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real Base Hunting modules end to end, with scans running synchronously. */
class BaseHuntModulesTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    NoirCore core;
    SimulatedClient sim;
    final Map<Long, FakeChunk> loaded = new HashMap<>();

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        core = new NoirCore(platform, Runnable::run);
        core.register(new SusChunk(), new NewChunks(), new Trails(), new Search(), new PortalESP(), new StashFinder(), new Waypoints());
        core.start();
        core.baseHunt().setSource(new ChunkSource() {
            @Override public String dimension() { return "overworld"; }
            @Override public List<ChunkView> loaded() { return new ArrayList<>(loaded.values()); }
            @Override public ChunkView get(int x, int z) { return loaded.get(((long) x << 32) ^ (z & 0xFFFFFFFFL)); }
        });
        core.events().post(new Events.WorldChange(true));
        sim = new SimulatedClient(core);
    }

    private void load(FakeChunk c) {
        loaded.put(((long) c.chunkX() << 32) ^ (c.chunkZ() & 0xFFFFFFFFL), c);
        core.events().post(new Events.ChunkLoad(c.chunkX(), c.chunkZ(), c, c));
    }

    private int quads() {
        int[] n = {0};
        WorldCanvas canvas = new WorldCanvas() {
            @Override public CameraInfo camera() { return new CameraInfo(8, 80, 8, 0, 30, 70, 1920, 1080); }
            @Override public void quad(double a, double b, double c, double d, double e, double f, double g, double h, double i,
                                       double j, double k, double l, int argb) { n[0]++; }
        };
        core.events().post(new Events.RenderWorld3D(canvas, 0));
        return n[0];
    }

    private static FakeChunk stash(int cx, int cz) {
        FakeChunk c = FakeChunk.overworld(cx, cz);
        c.fill(0, 63, 0, 11, 63, 0, "chest").set(4, 64, 4, "white_bed").set(5, 64, 4, "ender_chest");
        for (int i = 0; i < 12; i++) c.blockEntity("minecraft:chest");
        return c;
    }

    @Test
    void stashChunkIsFoundScoredAlertedLoggedAndWaypointed() throws Exception {
        core.modules().find("Waypoints").setEnabled(true);
        core.modules().find("SusChunk").setEnabled(true);
        core.modules().find("StashFinder").setEnabled(true);
        load(FakeChunk.overworld(0, 0));
        load(stash(1, 0));
        sim.run(3);

        SusChunk sus = core.modules().get(SusChunk.class);
        assertEquals(1, sus.hits());
        assertTrue(sus.result(new ChunkKey("overworld", 1, 0)).score() >= 10);
        assertEquals(1, core.modules().get(StashFinder.class).stashes().size());
        assertTrue(platform.chat.stream().anyMatch(s -> s.startsWith("SusChunk: Suspicious chunk")), platform.chat.toString());
        assertTrue(platform.chat.stream().anyMatch(s -> s.startsWith("StashFinder: Stash")));
        assertTrue(Files.readString(dir.resolve("logs/suschunk.log")).contains("\t24\t64\t8\t"));
        assertEquals(1, core.baseHunt().waypoints().all().size(), "StashFinder auto-waypoint (SusChunk's is off by default)");
        assertEquals(1, core.hud().counters().get("SusChunk"));
        assertTrue(quads() > 0);

        // Alerts fire once per chunk, even when the chunk is rescanned.
        int alerts = platform.chat.size();
        load(stash(1, 0));
        sim.run(3);
        assertEquals(alerts, platform.chat.size());
    }

    @Test
    void changingWeightsRescoresWithoutRescanning() {
        core.modules().find("SusChunk").setEnabled(true);
        load(stash(1, 0));
        sim.run(2);
        SusChunk sus = core.modules().get(SusChunk.class);
        assertEquals(1, sus.hits());
        sus.setting("scorethreshold").parse("200");
        sim.run(2);
        assertEquals(0, sus.hits());
    }

    @Test
    void searchFindsListedBlocksAndRescansWhenTheListChanges() {
        Search search = core.modules().get(Search.class);
        search.setEnabled(true);
        FakeChunk c = FakeChunk.overworld(0, 0).set(3, 40, 3, "spawner").set(5, 41, 5, "diamond_ore");
        load(c);
        sim.run(2);
        CameraInfo cam = new CameraInfo(8, 64, 8, 0, 0, 70, 1920, 1080);
        assertEquals(1, search.nearest(cam).size());
        search.setting("blocks").parse("add diamond_ore #FF00FFFF");
        sim.run(2);
        assertEquals(2, search.nearest(cam).size());
        assertTrue(quads() > 0);
    }

    @Test
    void portalsAreClusteredAndAlertedOnce() {
        core.modules().find("PortalESP").setEnabled(true);
        load(FakeChunk.overworld(0, 0).fill(4, 64, 4, 5, 66, 4, "nether_portal"));
        sim.run(2);
        assertEquals(1, platform.chat.stream().filter(s -> s.startsWith("PortalESP:")).count());
    }

    @Test
    void newChunksFromLiquidUpdates() {
        core.modules().find("NewChunks").setEnabled(true);
        FakeChunk fresh = FakeChunk.overworld(3, 3);
        load(fresh);
        core.events().post(new Events.BlockChange("overworld", 3 * 16 + 2, 64, 3 * 16 + 2, "minecraft:air", "minecraft:water", true));
        sim.run(2);
        assertEquals(NewChunkTracker.State.NEW, core.baseHunt().newChunks().verdict(new ChunkKey("overworld", 3, 3)).state());
        assertTrue(quads() > 0);
    }

    @Test
    void nothingScansWhileAllBaseHuntingModulesAreOff() {
        load(stash(1, 0));
        sim.run(2);
        assertEquals(0, core.baseHunt().scans().size());
    }

    @Test
    void baseHuntingModulesSendNoPackets() {
        sim.targets.add(new SimulatedClient.Target(1, 1.5, 64, 2));
        sim.playerInput = c -> c.x += (c.tick / 25) % 2 == 0 ? 0.05 : -0.05;
        core.modules().byCategory(dev.noir.module.Category.BASE_HUNTING).forEach(m -> m.setEnabled(true));
        for (int i = 0; i < 5; i++) load(stash(i, 2));
        sim.run(200);
        assertTrue(core.packets().violations().isEmpty(), core.packets().violations().toString());
        assertTrue(sim.wire.stream().allMatch(w -> w.source().equals("vanilla")), "only vanilla packets on the wire");
        assertEquals(List.of(), dev.noir.sim.VanillaBudget.check(sim.wire));
    }

    @Test
    void waypointCommand() {
        core.modules().find("Waypoints").setEnabled(true);
        core.commands().handle(".waypoint add home 10 70 -20");
        assertEquals(1, core.baseHunt().waypoints().all().size());
        core.commands().handle(".wp remove home");
        assertFalse(core.baseHunt().waypoints().all().stream().anyMatch(w -> w.name().equals("home")));
    }
}

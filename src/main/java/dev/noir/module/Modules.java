package dev.noir.module;

import dev.noir.module.basehunting.NewChunks;
import dev.noir.module.basehunting.PortalESP;
import dev.noir.module.basehunting.Search;
import dev.noir.module.basehunting.StashFinder;
import dev.noir.module.basehunting.SusChunk;
import dev.noir.module.basehunting.Trails;
import dev.noir.module.basehunting.Waypoints;

/**
 * Every module in the client. Base Hunting modules are platform-independent (core/);
 * Combat, Movement and Render modules (stage 3) are added here.
 */
public final class Modules {
    private Modules() {}

    public static Module[] create() {
        return new Module[] {
            new SusChunk(), new NewChunks(), new Trails(), new Search(), new PortalESP(), new StashFinder(), new Waypoints(),
        };
    }
}

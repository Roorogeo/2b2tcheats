package dev.noir.event;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** World render pass (end of the main level pass). Render modules draw ESP/tracers here. */
public record RenderWorldEvent(LevelRenderContext context) {}

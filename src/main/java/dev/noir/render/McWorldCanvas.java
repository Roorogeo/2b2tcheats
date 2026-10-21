package dev.noir.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * {@link WorldCanvas} for one frame. Quads are collected in camera-relative floats, then
 * submitted once as debug filled-box geometry (position + color quads), the way Fabric's
 * own level render tests draw boxes on 26.2.
 */
public final class McWorldCanvas implements WorldCanvas {
    private final CameraInfo camera;
    private float[] verts = new float[4096];
    private int[] colors = new int[256];
    private int quads;

    public McWorldCanvas(CameraInfo camera) {
        this.camera = camera;
    }

    @Override
    public CameraInfo camera() {
        return camera;
    }

    @Override
    public void quad(double x0, double y0, double z0, double x1, double y1, double z1,
                     double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        if (verts.length < (quads + 1) * 12) verts = Arrays.copyOf(verts, verts.length * 2);
        if (colors.length < quads + 1) colors = Arrays.copyOf(colors, colors.length * 2);
        int i = quads * 12;
        double cx = camera.x(), cy = camera.y(), cz = camera.z();
        verts[i] = (float) (x0 - cx); verts[i + 1] = (float) (y0 - cy); verts[i + 2] = (float) (z0 - cz);
        verts[i + 3] = (float) (x1 - cx); verts[i + 4] = (float) (y1 - cy); verts[i + 5] = (float) (z1 - cz);
        verts[i + 6] = (float) (x2 - cx); verts[i + 7] = (float) (y2 - cy); verts[i + 8] = (float) (z2 - cz);
        verts[i + 9] = (float) (x3 - cx); verts[i + 10] = (float) (y3 - cy); verts[i + 11] = (float) (z3 - cz);
        colors[quads++] = argb;
    }

    public int quadCount() {
        return quads;
    }

    /** Submits everything collected this frame. Coordinates are already camera-relative. */
    public void submit(LevelRenderContext context) {
        if (quads == 0) return;
        PoseStack pose = context.poseStack();
        int n = quads;
        float[] v = verts;
        int[] c = colors;
        context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (entry, buffer) -> {
            for (int q = 0; q < n; q++) {
                int i = q * 12;
                for (int k = 0; k < 4; k++) {
                    buffer.addVertex(entry, v[i + k * 3], v[i + k * 3 + 1], v[i + k * 3 + 2]).setColor(c[q]);
                }
            }
        });
    }

    public static Vec3 cameraPos(LevelRenderContext context) {
        return context.levelState().cameraRenderState.pos;
    }
}

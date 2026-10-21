package dev.noir.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderMathTest {
    static final class Recorder implements WorldCanvas {
        final CameraInfo cam;
        final List<double[]> quads = new ArrayList<>();

        Recorder(CameraInfo cam) {
            this.cam = cam;
        }

        @Override public CameraInfo camera() { return cam; }

        @Override
        public void quad(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
                         double x3, double y3, double z3, int argb) {
            quads.add(new double[] {x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3});
        }
    }

    private static final CameraInfo CAM = new CameraInfo(0, 64, 0, 0, 0, 70, 1920, 1080);

    @Test
    void projectionCenterAndSides() {
        double[] center = CAM.project(0, 64, 10);
        assertEquals(960, center[0], 0.5);
        assertEquals(540, center[1], 0.5);
        // Facing +Z (yaw 0), +X is to the left on screen.
        assertTrue(CAM.project(3, 64, 10)[0] < 960);
        assertTrue(CAM.project(0, 70, 10)[1] < 540, "higher is up on screen");
        assertNull(CAM.project(0, 64, -10), "behind the camera");
        CameraInfo east = new CameraInfo(0, 64, 0, -90, 0, 70, 1920, 1080);
        assertEquals(960, east.project(10, 64, 0)[0], 0.5, "yaw -90 looks +X");
    }

    @Test
    void boxIsSixDoubleSidedFaces() {
        Recorder r = new Recorder(CAM);
        Shapes.fillBox(r, 0, 0, 0, 1, 1, 1, 0x80FFFFFF);
        assertEquals(12, r.quads.size());
    }

    @Test
    void outlineWidthScalesWithDistance() {
        Recorder near = new Recorder(CAM), far = new Recorder(CAM);
        Shapes.line(near, -1, 64, 5, 1, 64, 5, 0xFFFFFFFF, 2);
        Shapes.line(far, -1, 64, 50, 1, 64, 50, 0xFFFFFFFF, 2);
        double nearW = Math.abs(near.quads.get(0)[1] - near.quads.get(0)[10]);
        double farW = Math.abs(far.quads.get(0)[1] - far.quads.get(0)[10]);
        assertTrue(farW > nearW * 8, "far " + farW + " near " + nearW);
        double expected = CAM.worldPerPixel(CAM.distanceTo(-1, 64, 5)) * 2;
        assertEquals(expected, nearW, expected * 0.01);
    }

    @Test
    void tracerIsThinAtTheCameraEnd() {
        Recorder r = new Recorder(CAM);
        Shapes.line(r, 1, 64, 0.5, 1, 64, 200, 0xFFFFFFFF, 2);
        double[] q = r.quads.get(0);
        double nearWidth = Math.hypot(q[0] - q[9], q[1] - q[10]);
        double farWidth = Math.hypot(q[3] - q[6], q[4] - q[7]);
        assertTrue(nearWidth < 0.01, "near end " + nearWidth);
        assertTrue(farWidth > nearWidth * 100, "far end " + farWidth);
    }

    @Test
    void transparentShapesEmitNothing() {
        Recorder r = new Recorder(CAM);
        Shapes.fillBox(r, 0, 0, 0, 1, 1, 1, 0x00FFFFFF);
        Shapes.outlineBox(r, 0, 0, 0, 1, 1, 1, 0x00FFFFFF, 2);
        assertEquals(0, r.quads.size());
    }
}

package dev.noir.testutil;

import dev.noir.hud.HudData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FakeHudData implements HudData {
    public int fps = 144;
    public int ping = 38;
    public double tps = 19.8;
    public double x = 12_480.5, y = 64, z = -3_312.2;
    public String dimension = "overworld";
    public int totems = 6;
    public Map<String, Integer> hits = new LinkedHashMap<>();
    public dev.noir.render.CameraInfo camera = dev.noir.render.CameraInfo.NONE;
    public List<ArmorPiece> armor = List.of(
        new ArmorPiece("helmet", "Netherite Helmet", 40, 407),
        new ArmorPiece("chestplate", "Netherite Chestplate", 120, 592),
        new ArmorPiece("leggings", "Netherite Leggings", 10, 555),
        new ArmorPiece("boots", "Netherite Boots", 400, 481));

    @Override public int fps() { return fps; }
    @Override public int ping() { return ping; }
    @Override public double tps() { return tps; }
    @Override public double x() { return x; }
    @Override public double y() { return y; }
    @Override public double z() { return z; }
    @Override public String dimension() { return dimension; }
    @Override public List<ArmorPiece> armor() { return armor; }
    @Override public int totems() { return totems; }
    @Override public Map<String, Integer> hitCounters() { return hits; }
    @Override public dev.noir.render.CameraInfo camera() { return camera; }
}

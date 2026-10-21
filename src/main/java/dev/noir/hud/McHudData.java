package dev.noir.hud;

import dev.noir.NoirCore;
import dev.noir.network.TpsTracker;
import dev.noir.render.CameraInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HUD data read from the client. Read-only. */
public final class McHudData implements HudData {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Minecraft mc;
    private final NoirCore core;
    private final TpsTracker tps;

    public McHudData(Minecraft mc, NoirCore core, TpsTracker tps) {
        this.mc = mc;
        this.core = core;
        this.tps = tps;
    }

    private LocalPlayer player() {
        return mc.player;
    }

    @Override
    public int fps() {
        return mc.getFps();
    }

    @Override
    public int ping() {
        if (mc.getConnection() == null || player() == null) return -1;
        PlayerInfo info = mc.getConnection().getPlayerInfo(player().getUUID());
        return info == null ? -1 : info.getLatency();
    }

    @Override
    public double tps() {
        return tps.tps();
    }

    @Override public double x() { return player() == null ? 0 : player().getX(); }
    @Override public double y() { return player() == null ? 0 : player().getY(); }
    @Override public double z() { return player() == null ? 0 : player().getZ(); }

    @Override
    public String dimension() {
        if (mc.level == null) return "overworld";
        if (mc.level.dimension() == Level.NETHER) return "the_nether";
        if (mc.level.dimension() == Level.END) return "the_end";
        if (mc.level.dimension() == Level.OVERWORLD) return "overworld";
        return "custom";
    }

    @Override
    public List<ArmorPiece> armor() {
        List<ArmorPiece> out = new ArrayList<>(4);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack s = player() == null ? ItemStack.EMPTY : player().getItemBySlot(slot);
            if (s.isEmpty()) out.add(new ArmorPiece(null, "", 0, 0));
            else out.add(new ArmorPiece(s, s.getHoverName().getString(), s.getDamageValue(), s.isDamageableItem() ? s.getMaxDamage() : 0));
        }
        return out;
    }

    @Override
    public int totems() {
        if (player() == null) return 0;
        Inventory inv = player().getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        return n;
    }

    @Override
    public CameraInfo camera() {
        return camera(mc.gameRenderer.mainCamera().position());
    }

    /** Camera at {@code pos} with the main camera's rotation and the FOV option. */
    public CameraInfo camera(Vec3 pos) {
        var cam = mc.gameRenderer.mainCamera();
        return new CameraInfo(pos.x, pos.y, pos.z, cam.yRot(), cam.xRot(), mc.options.fov().get(),
            mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    @Override
    public Map<String, Integer> hitCounters() {
        return core.hud().counters();
    }
}

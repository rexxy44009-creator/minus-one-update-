package com.example.godzilla.client;

import com.example.godzilla.entity.GodzillaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client-only reactions to server events. Kept out of GodzillaEntity so a dedicated server never loads client classes. */
public final class GodzillaClientHooks {
    private GodzillaClientHooks() {}

    private static float falloff(GodzillaEntity g, float range) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) return 0.0F;
        return Math.max(0.0F, 1.0F - (float) me.distanceTo(g) / range);
    }

    public static void onStomp(GodzillaEntity g) {
        CameraShake.add(0.5F * falloff(g, 80.0F), 0.6F);
    }

    public static void onRoar(GodzillaEntity g) {
        CameraShake.add(0.9F * falloff(g, 130.0F), 0.9F);
    }

    public static void onBlast(GodzillaEntity g) {
        CameraShake.add(1.0F * falloff(g, 220.0F), 1.0F);   // the mushroom-cloud blast: the biggest shake in the mod
    }

    /** Called every client tick for every Godzilla in range. */
    public static void tick(GodzillaEntity g) {
        switch (g.getAction()) {
            case GodzillaEntity.ACT_ROAR -> CameraShake.add(0.05F * falloff(g, 130.0F), 0.5F);
            case GodzillaEntity.ACT_CHARGE -> CameraShake.add(0.03F * falloff(g, 100.0F) * ((float) g.getActionTicks() / 80.0F), 0.4F);
            case GodzillaEntity.ACT_FIRE -> CameraShake.add(0.08F * falloff(g, 160.0F), 0.7F);
            case GodzillaEntity.ACT_DEATH -> CameraShake.add(0.05F * falloff(g, 120.0F), 0.5F);
            default -> { }
        }
    }
}

package com.example.godzilla.client;

import com.example.godzilla.GodzillaMod;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** "Trauma"-style screen shake: intensity decays every tick, shake strength = trauma^2. */
@EventBusSubscriber(modid = GodzillaMod.MODID, value = Dist.CLIENT)
public final class CameraShake {
    private static float trauma;
    private static final float DECAY_PER_TICK = 0.025F;

    private CameraShake() {}

    /** Raise trauma by `amount`, but never above `cap` (so continuous sources don't stack forever). */
    public static void add(float amount, float cap) {
        trauma = Math.max(trauma, Math.min(cap, trauma + amount));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        trauma = Math.max(0.0F, trauma - DECAY_PER_TICK);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0.0F) return;
        float s = trauma * trauma;
        double t = System.nanoTime() / 1.0E9;
        event.setPitch(event.getPitch() + (float) Math.sin(t * 47.0) * 3.0F * s);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 39.0 + 1.0) * 3.0F * s);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 53.0 + 2.0) * 4.0F * s);
    }
}

package com.example.godzilla.ai;

import com.example.godzilla.GodzillaMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Drives running AtomicBlasts once per level tick and forgets them when the server stops. */
@EventBusSubscriber(modid = GodzillaMod.MODID)
public final class AtomicBlastEvents {
    private AtomicBlastEvents() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AtomicBlast.tickAll(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AtomicBlast.clearAll();
    }
}

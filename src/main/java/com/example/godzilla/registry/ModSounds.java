package com.example.godzilla.registry;

import com.example.godzilla.GodzillaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Event ids match assets/godzilla_mod/sounds.json (which currently points at vanilla sounds as placeholders). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, GodzillaMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR   = register("godzilla.roar", 220f);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = register("godzilla.charge", 160f);
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE  = register("godzilla.plate", 64f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM   = register("godzilla.beam", 220f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST  = register("godzilla.blast", 300f);
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP   = register("godzilla.step", 120f);
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE   = register("godzilla.bite", 64f);
    public static final DeferredHolder<SoundEvent, SoundEvent> TAIL   = register("godzilla.tail", 64f);
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT   = register("godzilla.hurt", 64f);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH  = register("godzilla.death", 220f);

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(GodzillaMod.MODID, name), range));
    }

    private ModSounds() {}
}

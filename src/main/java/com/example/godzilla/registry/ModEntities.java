package com.example.godzilla.registry;

import com.example.godzilla.GodzillaMod;
import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.entity.GodzillaEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, GodzillaMod.MODID);

    /** Entity ID: godzilla_mod:godzilla_minus_one  ->  /summon godzilla_mod:godzilla_minus_one */
    public static final DeferredHolder<EntityType<?>, EntityType<GodzillaEntity>> GODZILLA_MINUS_ONE =
            ENTITY_TYPES.register("godzilla_minus_one", () -> EntityType.Builder.of(GodzillaEntity::new, MobCategory.MONSTER)
                    .sized(GodzillaTuning.HITBOX_WIDTH, GodzillaTuning.HITBOX_HEIGHT)
                    .eyeHeight(GodzillaTuning.EYE_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("godzilla_minus_one"));

    private ModEntities() {}
}

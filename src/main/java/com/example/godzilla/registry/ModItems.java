package com.example.godzilla.registry;

import com.example.godzilla.GodzillaMod;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GodzillaMod.MODID);

    public static final DeferredItem<DeferredSpawnEggItem> GODZILLA_SPAWN_EGG = ITEMS.register("godzilla_minus_one_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GODZILLA_MINUS_ONE, 0x2B2B2E, 0x33B8FF, new Item.Properties()));

    private ModItems() {}
}

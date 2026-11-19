package com.example.godzilla;

import com.example.godzilla.entity.GodzillaEntity;
import com.example.godzilla.registry.ModEntities;
import com.example.godzilla.registry.ModItems;
import com.example.godzilla.registry.ModSounds;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(GodzillaMod.MODID)
public class GodzillaMod {
    public static final String MODID = "godzilla_mod";

    public GodzillaMod(IEventBus modBus) {
        ModEntities.ENTITY_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::addToCreativeTab);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GODZILLA_MINUS_ONE.get(), GodzillaEntity.createAttributes().build());
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GODZILLA_SPAWN_EGG.get());
        }
    }
}

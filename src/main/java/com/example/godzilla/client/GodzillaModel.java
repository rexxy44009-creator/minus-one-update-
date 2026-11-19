package com.example.godzilla.client;

import com.example.godzilla.GodzillaMod;
import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.entity.GodzillaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

public class GodzillaModel extends GeoModel<GodzillaEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(GodzillaMod.MODID, "geo/godzilla_minus_one.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(GodzillaMod.MODID, "textures/entity/godzilla_minus_one.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(GodzillaMod.MODID, "animations/godzilla_minus_one.animation.json");

    @Override public ResourceLocation getModelResource(GodzillaEntity animatable) { return MODEL; }
    @Override public ResourceLocation getTextureResource(GodzillaEntity animatable) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(GodzillaEntity animatable) { return ANIMATION; }

    /**
     * Runs every frame after the keyframe animation.
     *  1) head follows where the entity is looking
     *  2) glow overlay bones plate_glow_00..11 are shown/hidden so the plates light up one by one, tail -> head
     */
    @Override
    public void setCustomAnimations(GodzillaEntity g, long instanceId, AnimationState<GodzillaEntity> state) {
        var head = getAnimationProcessor().getBone("head");
        var look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && look != null) {
            head.setRotX(head.getRotX() + look.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(head.getRotY() + look.netHeadYaw() * Mth.DEG_TO_RAD);
        }

        float pt = state.getPartialTick();
        for (int i = 0; i < GodzillaTuning.PLATE_COUNT; i++) {
            var glow = getAnimationProcessor().getBone(String.format("plate_glow_%02d", i));
            if (glow != null) glow.setHidden(!g.isPlateLit(i, pt));
        }
        var mouth = getAnimationProcessor().getBone("mouth_glow");
        if (mouth != null) mouth.setHidden(!g.isMouthGlowing(pt));
    }
}

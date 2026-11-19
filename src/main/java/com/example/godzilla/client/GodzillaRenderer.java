package com.example.godzilla.client;

import com.example.godzilla.entity.GodzillaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GodzillaRenderer extends GeoEntityRenderer<GodzillaEntity> {
    public GodzillaRenderer(EntityRendererProvider.Context context) {
        super(context, new GodzillaModel());
        // Emissive layer: anything painted in textures/entity/godzilla_minus_one_glowmask.png renders full-bright.
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 5.0F;
    }

    @Override
    protected float getDeathMaxRotation(GodzillaEntity animatable) {
        return 0.0F;   // the death animation does the falling; don't let vanilla tip the model over as well
    }

    @Override
    public void render(GodzillaEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);

        float length = entity.getBeamLength();
        if (!entity.isFiring() || length < 1.0F) return;

        // The poseStack is at the entity's feet. Move to the mouth, then aim along the synced yaw/pitch.
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        Vec3 mouth = entity.getMouthOffset(bodyYaw);
        poseStack.pushPose();
        poseStack.translate(mouth.x, mouth.y, mouth.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getBeamYaw()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getBeamPitch()));
        // local +Z is now the beam direction

        float pulse = 1.0F + 0.12F * Mth.sin((entity.tickCount + partialTick) * 1.7F);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());   // additive, unlit: glows
        Matrix4f m = poseStack.last().pose();
        drawBeamLayer(vc, m, length, 0.9F * pulse, 255, 255, 255, 255);   // white-hot core
        drawBeamLayer(vc, m, length, 1.8F * pulse, 90, 190, 255, 120);
        drawBeamLayer(vc, m, length, 3.0F * pulse, 30, 110, 255, 55);     // wide blue halo
        poseStack.popPose();
    }

    /** An 8-sided tube along +Z from 0 to length. */
    private static void drawBeamLayer(VertexConsumer vc, Matrix4f m, float length, float radius, int r, int g, int b, int a) {
        final int sides = 8;
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / sides), a1 = (float) (Math.PI * 2.0 * (i + 1) / sides);
            float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
            vc.addVertex(m, x0, y0, 0.0F).setColor(r, g, b, a);
            vc.addVertex(m, x1, y1, 0.0F).setColor(r, g, b, a);
            vc.addVertex(m, x1, y1, length).setColor(r, g, b, 0);   // fades out toward the far end
            vc.addVertex(m, x0, y0, length).setColor(r, g, b, 0);
        }
    }
}

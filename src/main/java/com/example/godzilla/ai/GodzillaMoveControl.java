package com.example.godzilla.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Pathfinding is useless for a 7x21 block monster, so Godzilla walks in a straight line at the wanted position,
 * turning slowly (a real lumbering turn radius). Terrain is handled by STEP_HEIGHT and by smashing blocks.
 * The wanted position is consumed every tick, so when no goal is steering him he stops.
 */
public class GodzillaMoveControl extends MoveControl {
    private static final float TURN_DEGREES_PER_TICK = 3.0F;

    public GodzillaMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setZza(0.0F);
            return;
        }
        this.operation = Operation.WAIT;   // goals must re-issue every tick

        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        if (dx * dx + dz * dz < 1.0) {
            this.mob.setZza(0.0F);
            return;
        }

        float desiredYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = this.rotlerp(this.mob.getYRot(), desiredYaw, TURN_DEGREES_PER_TICK);
        this.mob.setYRot(yaw);
        this.mob.yBodyRot = yaw;

        this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        // Only walk forward once roughly facing the target; otherwise pivot in place.
        this.mob.setZza(Math.abs(Mth.wrapDegrees(desiredYaw - yaw)) < 50.0F ? 1.0F : 0.0F);
    }
}

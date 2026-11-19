package com.example.godzilla.ai;

import com.example.godzilla.entity.GodzillaEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Lumber straight toward the current target until the melee goal takes over. */
public class GodzillaRampageMoveGoal extends Goal {
    private static final double STOP_DISTANCE = 8.0;
    private final GodzillaEntity godzilla;

    public GodzillaRampageMoveGoal(GodzillaEntity godzilla) {
        this.godzilla = godzilla;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() {
        LivingEntity t = godzilla.getTarget();
        return t != null && t.isAlive() && godzilla.getAction() == GodzillaEntity.ACT_IDLE;
    }

    @Override public boolean canContinueToUse() { return canUse(); }

    /** GoalSelector only ticks goals every 2nd tick unless they ask otherwise; MoveControl needs every tick. */
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        LivingEntity t = godzilla.getTarget();
        if (t == null) return;
        godzilla.getLookControl().setLookAt(t, 10.0F, 10.0F);
        double dx = t.getX() - godzilla.getX();
        double dz = t.getZ() - godzilla.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > STOP_DISTANCE) {
            godzilla.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
        }
    }
}

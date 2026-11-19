package com.example.godzilla.ai;

import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.entity.GodzillaEntity;
import com.example.godzilla.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Jaw bite for targets in front, tail swipe for targets behind / to the flanks.
 * Both are wind-up -> strike -> recover, with the strike tick aligned to the animation (see GodzillaTuning).
 */
public class GodzillaMeleeGoal extends Goal {
    private static final int NONE = 0, BITE = 1, TAIL = 2;

    private final GodzillaEntity g;
    private int attack = NONE;
    private int tick;
    private long nextAttackTime;

    public GodzillaMeleeGoal(GodzillaEntity g) {
        this.g = g;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));   // plants his feet while attacking
    }

    @Override public boolean canUse() {
        LivingEntity t = g.getTarget();
        if (t == null || !t.isAlive() || g.getAction() != GodzillaEntity.ACT_IDLE) return false;
        if (g.level().getGameTime() < nextAttackTime) return false;
        double dx = t.getX() - g.getX(), dz = t.getZ() - g.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > GodzillaTuning.MELEE_REACH) return false;
        return t.getY() > g.getY() - 4.0 && t.getY() < g.getY() + 24.0;
    }

    @Override public boolean canContinueToUse() {
        int total = attack == BITE ? GodzillaTuning.BITE_TICKS : GodzillaTuning.TAIL_TICKS;
        return attack != NONE && g.isAlive() && tick < total;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        LivingEntity t = g.getTarget();
        float yawToTarget = t == null ? g.getYRot() : GodzillaEntity.yawTo(g.position(), t.position());
        float rel = Mth.wrapDegrees(yawToTarget - g.getYRot());
        attack = Math.abs(rel) > 100.0F ? TAIL : BITE;
        tick = 0;
        g.setAction(attack == BITE ? GodzillaEntity.ACT_BITE : GodzillaEntity.ACT_TAIL);
        g.getNavigation().stop();
    }

    @Override public void stop() {
        if (g.isAlive()) g.setAction(GodzillaEntity.ACT_IDLE);
        nextAttackTime = g.level().getGameTime() + GodzillaTuning.MELEE_COOLDOWN;
        attack = NONE;
    }

    @Override public void tick() {
        tick++;
        LivingEntity t = g.getTarget();
        if (attack == BITE && tick < GodzillaTuning.BITE_STRIKE_TICK && t != null) {
            g.turnBodyTowards(GodzillaEntity.yawTo(g.position(), t.position()), 12.0F);
        }
        if (!(g.level() instanceof ServerLevel level)) return;
        if (attack == BITE && tick == GodzillaTuning.BITE_STRIKE_TICK) strikeBite(level);
        if (attack == TAIL && tick == GodzillaTuning.TAIL_STRIKE_TICK) strikeTail(level);
    }

    private void strikeBite(ServerLevel level) {
        Vec3 mouth = g.getMouthPosition();
        Vec3 fwd = g.getForwardHoriz();
        g.playSound(ModSounds.BITE.get(), 6.0F, 0.7F);
        AtomicFx.send(level, ParticleTypes.EXPLOSION, mouth.x + fwd.x * 2, mouth.y - 2, mouth.z + fwd.z * 2, 2, 1, 1, 1, 0);
        AABB zone = new AABB(mouth, mouth).inflate(9.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, g::isValidVictim)) {
            Vec3 c = e.getBoundingBox().getCenter();
            if (c.distanceTo(mouth) > 8.0 + e.getBbWidth() / 2.0) continue;
            Vec3 flat = c.subtract(g.position()).multiply(1, 0, 1);
            if (flat.lengthSqr() > 1.0E-4 && fwd.dot(flat.normalize()) < 0.2) continue;   // must be in front
            e.hurt(g.damageSources().mobAttack(g), GodzillaTuning.BITE_DAMAGE);
            g.launch(e, g.position(), 2.2, 1.5);
        }
    }

    private void strikeTail(ServerLevel level) {
        Vec3 fwd = g.getForwardHoriz();
        g.playSound(ModSounds.TAIL.get(), 6.0F, 0.6F);
        // visible shock ring behind him
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI * (0.55 + 0.9 * i / 9.0);
            double r = 7.0 + (i % 3) * 3.0;
            Vec3 dir = rotateY(fwd, ang);
            AtomicFx.send(level, ParticleTypes.EXPLOSION, g.getX() + dir.x * r, g.getY() + 1.5, g.getZ() + dir.z * r, 1, 0, 0, 0, 0);
        }
        double reach = GodzillaTuning.TAIL_RADIUS;
        AABB zone = g.getBoundingBox().inflate(reach, 3.0, reach);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, g::isValidVictim)) {
            Vec3 flat = e.position().subtract(g.position()).multiply(1, 0, 1);
            double dist = flat.length();
            if (dist > reach + e.getBbWidth() / 2.0) continue;
            if (dist > 1.0E-4 && fwd.dot(flat.normalize()) > 0.3) continue;   // rear and flanks only
            e.hurt(g.damageSources().mobAttack(g), GodzillaTuning.TAIL_DAMAGE);
            g.launch(e, g.position(), 3.0, 1.7);
        }
    }

    private static Vec3 rotateY(Vec3 v, double radians) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return new Vec3(v.x * c - v.z * s, 0.0, v.x * s + v.z * c);
    }
}

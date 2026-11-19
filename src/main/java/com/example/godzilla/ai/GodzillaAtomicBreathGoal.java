package com.example.godzilla.ai;

import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.entity.GodzillaEntity;
import com.example.godzilla.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The multi-stage atomic breath:
 *
 *   ROAR   (50t)  plants his feet, turns to face the target, deep roar + camera shake
 *   CHARGE (80t)  dorsal plates ignite one by one tail -> head (sparks, flashes, rising chime); mouth glow builds
 *   FIRE   (100t) sweeping ray: carves terrain, craters, burns, radiation clouds
 *   VENT   (40t)  steam, plates cool tail -> head
 *
 * The stage is mirrored to the client through GodzillaEntity#setAction, which drives animations and the glowing
 * plate overlay. This goal holds MOVE and LOOK so nothing else can steer him while it runs.
 */
public class GodzillaAtomicBreathGoal extends Goal {
    private enum Stage { ROAR, CHARGE, FIRE, VENT }

    private final GodzillaEntity g;
    @Nullable private Stage stage;
    private int stageTick;
    private int litSoFar;
    private Vec3 aim = Vec3.ZERO;
    @Nullable private LivingEntity victim;
    @Nullable private Vec3 impact;   // where the ray meets the ground; the mushroom cloud erupts here

    public GodzillaAtomicBreathGoal(GodzillaEntity g) {
        this.g = g;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    // ------------------------------------------------------------------ lifecycle
    @Override public boolean canUse() {
        LivingEntity t = g.getTarget();
        if (t == null || !t.isAlive() || g.getAction() != GodzillaEntity.ACT_IDLE) return false;
        if (g.level().getGameTime() < g.getBreathReadyTime()) return false;
        double dx = t.getX() - g.getX(), dz = t.getZ() - g.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return dist >= GodzillaTuning.BREATH_MIN_RANGE && dist <= GodzillaTuning.BREATH_MAX_RANGE;
    }

    @Override public boolean canContinueToUse() { return stage != null && g.isAlive(); }
    @Override public boolean isInterruptable() { return false; }          // once he inhales, nothing stops it
    @Override public boolean requiresUpdateEveryTick() { return true; }   // stage timers are in ticks

    @Override public void start() {
        stage = Stage.ROAR;
        stageTick = 0;
        litSoFar = 0;
        impact = null;
        victim = g.getTarget();
        g.getNavigation().stop();
        g.setAction(GodzillaEntity.ACT_ROAR);
        g.playSound(ModSounds.ROAR.get(), 10.0F, 1.0F);
        g.level().broadcastEntityEvent(g, GodzillaEntity.EVENT_ROAR);
    }

    @Override public void stop() {
        stage = null;
        victim = null;
        g.setBeamLength(0.0F);
        if (g.isAlive()) g.setAction(GodzillaEntity.ACT_IDLE);
        boolean enraged = g.getHealth() < g.getMaxHealth() * 0.5F;
        g.setBreathReadyTime(g.level().getGameTime()
                + (enraged ? GodzillaTuning.BREATH_COOLDOWN_ENRAGED : GodzillaTuning.BREATH_COOLDOWN));
    }

    @Override public void tick() {
        if (stage == null || !(g.level() instanceof ServerLevel level)) return;
        stageTick++;
        if (victim == null || !victim.isAlive()) victim = g.getTarget();   // may become null: we keep firing ahead

        switch (stage) {
            case ROAR -> tickRoar();
            case CHARGE -> tickCharge(level);
            case FIRE -> tickFire(level);
            case VENT -> tickVent(level);
        }
    }

    // ------------------------------------------------------------------ stage 1: roar
    private void tickRoar() {
        faceVictim(5.0F);
        lookAhead(20.0);
        if (stageTick >= GodzillaTuning.ROAR_TICKS) {
            stage = Stage.CHARGE;
            stageTick = 0;
            g.setAction(GodzillaEntity.ACT_CHARGE);
            g.playSound(ModSounds.CHARGE.get(), 10.0F, 0.8F);
        }
    }

    // ------------------------------------------------------------------ stage 2: plates ignite tail -> head
    private void tickCharge(ServerLevel level) {
        faceVictim(2.5F);
        lookAhead(20.0);

        float progress = (float) stageTick / GodzillaTuning.CHARGE_TICKS;
        int n = GodzillaTuning.PLATE_COUNT;
        int shouldBeLit = Math.min(n, (int) (progress * n) + 1);

        while (litSoFar < shouldBeLit) {   // a new plate just lit: burst + chime (pitch climbs as the charge builds)
            Vec3 p = g.getPlateWorldPos(litSoFar);
            AtomicFx.send(level, ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.0, p.z, 30, 1.2, 1.5, 1.2, 0.5);
            AtomicFx.send(level, AtomicFx.BLUE_GLOW, p.x, p.y + 1.0, p.z, 14, 0.8, 1.4, 0.8, 0.0);
            g.playSound(ModSounds.PLATE.get(), 4.0F, 0.5F + progress);
            litSoFar++;
        }

        if (stageTick % 2 == 0) {          // shimmer on every lit plate
            for (int i = 0; i < litSoFar; i++) {
                Vec3 p = g.getPlateWorldPos(i);
                AtomicFx.send(level, AtomicFx.BLUE_GLOW, p.x, p.y + 1.5, p.z, 2, 0.5, 1.0, 0.5, 0.0);
            }
        }
        if (stageTick % 8 == 0 && litSoFar > 0) {   // flashing lights
            Vec3 p = g.getPlateWorldPos(g.getRandom().nextInt(litSoFar));
            AtomicFx.send(level, ParticleTypes.FLASH, p.x, p.y + 1.5, p.z, 1, 0, 0, 0, 0);
        }
        if (progress > 0.5F) {              // energy gathering at the mouth
            Vec3 m = g.getMouthPosition();
            AtomicFx.send(level, ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 4, 2.0, 2.0, 2.0, 0.2);
            AtomicFx.send(level, AtomicFx.WHITE_CORE, m.x, m.y, m.z, 1, 0.3, 0.3, 0.3, 0.0);
        }

        if (stageTick >= GodzillaTuning.CHARGE_TICKS) {
            Vec3 mouth = g.getMouthPosition();
            Vec3 want = victim != null ? victim.getBoundingBox().getCenter().subtract(mouth) : g.getForwardHoriz();
            aim = safeNormalize(want);
            stage = Stage.FIRE;
            stageTick = 0;
            g.setAction(GodzillaEntity.ACT_FIRE);
        }
    }

    // ------------------------------------------------------------------ stage 3: the ray
    private void tickFire(ServerLevel level) {
        Vec3 mouth = g.getMouthPosition();
        Vec3 want = victim != null ? victim.getBoundingBox().getCenter().subtract(mouth) : g.getForwardHoriz();
        // The ray swings toward the target slowly, so a moving player can outrun the sweep.
        aim = safeNormalize(aim.lerp(safeNormalize(want), GodzillaTuning.BEAM_TRACKING));
        float yaw = yawOf(aim);
        float pitch = Mth.clamp(pitchOf(aim), -25.0F, 75.0F);
        aim = fromYawPitch(yaw, pitch);

        g.turnBodyTowards(yaw, 3.0F);
        g.getLookControl().setLookAt(mouth.add(aim.scale(40.0)));
        mouth = g.getMouthPosition();   // the head moved when the body turned

        double len = Math.min(GodzillaTuning.BEAM_RANGE, stageTick * 14.0);            // ray shoots out quickly...
        int remaining = GodzillaTuning.FIRE_TICKS - stageTick;
        if (remaining < 8) len *= remaining / 8.0;                                       // ...and retracts at the end
        g.setBeam(yaw, pitch, (float) len);

        Vec3 hit = findGroundImpact(level, mouth, aim);
        if (hit != null) impact = hit;
        AtomicBeam.tick(g, level, mouth, aim, stageTick, len);
        if (stageTick == GodzillaTuning.FIRE_TICKS - 6 && impact != null) {
            AtomicBlast.spawn(level, impact, g);   // the ray collapses into the atomic blast + mushroom cloud
        }
        if (stageTick % 10 == 1) {
            g.playSound(ModSounds.BEAM.get(), 10.0F, 0.85F + g.getRandom().nextFloat() * 0.2F);
        }

        if (stageTick >= GodzillaTuning.FIRE_TICKS) {
            stage = Stage.VENT;
            stageTick = 0;
            g.setBeamLength(0.0F);
            g.setAction(GodzillaEntity.ACT_VENT);
        }
    }

    // ------------------------------------------------------------------ stage 4: cool-down
    private void tickVent(ServerLevel level) {
        lookAhead(30.0);
        Vec3 mouth = g.getMouthPosition();
        if (stageTick % 2 == 0) {
            AtomicFx.send(level, ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 8, 0.6, 0.6, 0.6, 0.05);
        }
        if (stageTick % 3 == 0) {
            int cooled = (int) ((float) stageTick / GodzillaTuning.VENT_TICKS * GodzillaTuning.PLATE_COUNT);
            for (int i = cooled; i < GodzillaTuning.PLATE_COUNT; i++) {
                Vec3 p = g.getPlateWorldPos(i);
                AtomicFx.send(level, ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.5, p.z, 1, 0.4, 0.6, 0.4, 0.02);
            }
        }
        if (stageTick >= GodzillaTuning.VENT_TICKS) {
            stage = null;   // canContinueToUse() turns false -> stop() sets IDLE and starts the cooldown
        }
    }

    // ------------------------------------------------------------------ helpers
    private void faceVictim(float maxStep) {
        if (victim != null) g.turnBodyTowards(GodzillaEntity.yawTo(g.position(), victim.position()), maxStep);
    }

    private void lookAhead(double distance) {
        g.getLookControl().setLookAt(g.getMouthPosition().add(g.getForwardHoriz().scale(distance)));
    }

    /** First point where the ray goes below the terrain surface (null if it never does). */
    @Nullable
    private static Vec3 findGroundImpact(ServerLevel level, Vec3 from, Vec3 dir) {
        for (double d = 4.0; d <= GodzillaTuning.BEAM_RANGE; d += 2.0) {
            Vec3 p = from.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(p);
            if (!level.hasChunkAt(bp)) return null;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
            if (p.y <= top) return new Vec3(p.x, top, p.z);
        }
        return null;
    }

    private static Vec3 safeNormalize(Vec3 v) {
        return v.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : v.normalize();
    }

    /** Minecraft convention: yaw 0 = +Z (south), positive pitch = looking down. */
    private static float yawOf(Vec3 d) { return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG); }

    private static float pitchOf(Vec3 d) {
        return (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
    }

    private static Vec3 fromYawPitch(float yawDeg, float pitchDeg) {
        float y = yawDeg * Mth.DEG_TO_RAD, p = pitchDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }
}

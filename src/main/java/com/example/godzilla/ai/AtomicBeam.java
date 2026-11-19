package com.example.godzilla.ai;

import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.entity.GodzillaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side effects of one tick of the atomic ray: carve terrain, damage everything inside it,
 * drop craters on the ground under it, leave radiation clouds in the craters. (The glowing beam itself is
 * drawn on the client from the synced yaw/pitch/length; see client/GodzillaRenderer.)
 */
public final class AtomicBeam {
    private AtomicBeam() {}

    public static void tick(GodzillaEntity g, ServerLevel level, Vec3 origin, Vec3 dir, int fireTick, double length) {
        if (length < 2.0) return;
        boolean grief = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

        if (grief) carve(level, origin, dir, length);
        damage(g, level, origin, dir, length, fireTick);
        if (grief && fireTick % GodzillaTuning.CRATER_INTERVAL_TICKS == 0) craters(g, level, origin, dir, length);

        // light show: white-hot core at the mouth, sparks all along the ray
        AtomicFx.send(level, AtomicFx.WHITE_CORE, origin.x, origin.y, origin.z, 6, 0.8, 0.8, 0.8, 0.0);
        if (fireTick % 3 == 0) {
            for (double d = 5.0; d <= length; d += 6.0) {
                Vec3 p = origin.add(dir.scale(d));
                AtomicFx.send(level, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 6, 2.5, 2.5, 2.5, 0.3);
            }
        }
    }

    // ---------------------------------------------------------------- terrain carving
    private static void carve(ServerLevel level, Vec3 origin, Vec3 dir, double length) {
        int budget = GodzillaTuning.BEAM_BLOCKS_PER_TICK;
        double r = GodzillaTuning.BEAM_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double d = 3.0; d <= length && budget > 0; d += r * 0.75) {
            budget -= carveSphere(level, pos, origin.add(dir.scale(d)), r, budget);
        }
    }

    private static int carveSphere(ServerLevel level, BlockPos.MutableBlockPos pos, Vec3 c, double r, int budget) {
        int broken = 0;
        int ri = (int) Math.ceil(r);
        int cx = Mth.floor(c.x), cy = Mth.floor(c.y), cz = Mth.floor(c.z);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    pos.set(cx + dx, cy + dy, cz + dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;   // bedrock survives
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);  // no neighbour updates = no lag spike
                    if (++broken >= budget) return broken;
                }
            }
        }
        return broken;
    }

    // ---------------------------------------------------------------- damage
    private static void damage(GodzillaEntity g, ServerLevel level, Vec3 origin, Vec3 dir, double length, int fireTick) {
        double r = GodzillaTuning.BEAM_RADIUS;
        AABB sweep = new AABB(origin, origin.add(dir.scale(length))).inflate(r + 1.5);
        boolean hitTick = fireTick % 5 == 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, g::isValidVictim)) {
            Vec3 p = e.getBoundingBox().getCenter();
            double along = Mth.clamp(p.subtract(origin).dot(dir), 0.0, length);
            double reach = r + e.getBbWidth() * 0.5 + 0.5;
            if (origin.add(dir.scale(along)).distanceToSqr(p) > reach * reach) continue;
            e.setRemainingFireTicks(200);
            if (hitTick) {
                e.invulnerableTime = 0;   // let the ray hit every 5 ticks instead of every 10
                e.hurt(level.damageSources().explosion(g, g), GodzillaTuning.BEAM_DAMAGE);
            }
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.3, 0.05, dir.z * 0.3));
            e.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- craters + radiation
    private static void craters(GodzillaEntity g, ServerLevel level, Vec3 origin, Vec3 dir, double length) {
        if (length < 12.0) return;
        boolean placedCloud = false;
        for (int n = 0; n < 2; n++) {
            double d = 8.0 + level.random.nextDouble() * (length - 8.0);
            Vec3 c = origin.add(dir.scale(d));
            Vec3 ground = surfaceBelow(level, g, c.add(0.0, 30.0, 0.0), 40.0);
            if (ground == null || Math.abs(c.y - ground.y) > 8.0) continue;   // beam isn't touching ground here

            level.explode(g, ground.x, ground.y + 0.5, ground.z, GodzillaTuning.CRATER_POWER, false, Level.ExplosionInteraction.MOB);

            if (!placedCloud) {   // one hazard zone per volley, sitting in the freshly made crater
                Vec3 floor = surfaceBelow(level, g, new Vec3(ground.x, ground.y + 6.0, ground.z), 18.0);
                spawnRadiation(level, floor != null ? floor : ground);
                placedCloud = true;
            }
        }
    }

    private static Vec3 surfaceBelow(ServerLevel level, GodzillaEntity g, Vec3 from, double depth) {
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0.0, -depth, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, g));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    /** Lingering "Radiation" hazard: Wither II + Slowness III to anything standing in it. */
    private static void spawnRadiation(ServerLevel level, Vec3 at) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y + 0.1, at.z);
        cloud.setRadius(GodzillaTuning.RADIATION_RADIUS);
        cloud.setDuration(GodzillaTuning.RADIATION_DURATION_TICKS);
        cloud.setRadiusPerTick(-GodzillaTuning.RADIATION_RADIUS * 0.5F / GodzillaTuning.RADIATION_DURATION_TICKS); // shrinks to half
        cloud.setWaitTime(0);
        cloud.setParticle(AtomicFx.RADIATION);
        // AreaEffectCloud applies a quarter of the listed duration, so 400 here = 5 s of effect per application.
        cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 2));
        level.addFreshEntity(cloud);
    }
}

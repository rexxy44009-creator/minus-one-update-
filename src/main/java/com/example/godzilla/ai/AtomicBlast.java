package com.example.godzilla.ai;

import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.entity.GodzillaEntity;
import com.example.godzilla.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The atomic blast at the end of the ray: white flash, big explosion + crater, a ground shockwave that throws
 * and damages everything, then a rising fire-and-smoke stem topped by a rolling mushroom cap.
 * Pure server-side particles (sent with count 0 = "these numbers are a velocity"), ticked by AtomicBlastEvents.
 */
public final class AtomicBlast {
    private static final List<AtomicBlast> ACTIVE = new ArrayList<>();
    private static final double TAU = Math.PI * 2.0;

    private final ServerLevel level;
    private final Vec3 center;
    private final UUID ownerId;
    private final Set<UUID> alreadyHit = new HashSet<>();
    private int age;

    private AtomicBlast(ServerLevel level, Vec3 center, UUID ownerId) {
        this.level = level;
        this.center = center;
        this.ownerId = ownerId;
    }

    public static void spawn(ServerLevel level, Vec3 center, GodzillaEntity owner) {
        AtomicBlast blast = new AtomicBlast(level, center, owner.getUUID());
        ACTIVE.add(blast);
        blast.ignite(owner);
    }

    static void tickAll(ServerLevel level) {
        Iterator<AtomicBlast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            AtomicBlast b = it.next();
            if (b.level == level && !b.tick()) it.remove();
        }
    }

    static void clearAll() { ACTIVE.clear(); }

    // ---------------------------------------------------------------- t = 0: flash + bang
    private void ignite(GodzillaEntity owner) {
        double cx = center.x, cy = center.y, cz = center.z;
        level.explode(owner, cx, cy + 1.0, cz, GodzillaTuning.BLAST_POWER, true, Level.ExplosionInteraction.MOB);

        AtomicFx.send(level, ParticleTypes.FLASH, cx, cy + 6.0, cz, 10, 5.0, 5.0, 5.0, 0.0);
        AtomicFx.send(level, ParticleTypes.END_ROD, cx, cy + 4.0, cz, 260, 0.1, 0.1, 0.1, 1.5);           // white fireball
        AtomicFx.send(level, ParticleTypes.EXPLOSION_EMITTER, cx, cy + 3.0, cz, 6, 6.0, 3.0, 6.0, 0.0);
        AtomicFx.send(level, ParticleTypes.SOUL_FIRE_FLAME, cx, cy + 3.0, cz, 120, 0.3, 0.3, 0.3, 0.9);    // blue-white heart

        level.playSound(null, cx, cy, cz, ModSounds.BLAST.get(), SoundSource.HOSTILE, 60.0F, 0.9F);
        level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 60.0F, 0.5F);
        owner.level().broadcastEntityEvent(owner, GodzillaEntity.EVENT_BLAST);   // client: biggest screen shake
    }

    // ---------------------------------------------------------------- per tick
    private boolean tick() {
        if (++age > GodzillaTuning.BLAST_TICKS) return false;
        Entity owner = level.getEntity(ownerId);
        shockwave(owner);
        stem();
        cap();
        return true;
    }

    private double surfaceY(double x, double z) {
        BlockPos bp = BlockPos.containing(x, center.y, z);
        if (!level.hasChunkAt(bp)) return center.y;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
    }

    // ---- expanding ground ring: dust wall + damage/launch -------------------------------------------------------
    private void shockwave(Entity owner) {
        if (age > 22) return;
        double r = age * 2.2;
        int n = Math.max(8, Math.min(28, (int) (TAU * r / 2.2)));
        for (int k = 0; k < n; k++) {
            double ang = TAU * k / n + age * 0.37;
            double dx = Math.cos(ang), dz = Math.sin(ang);
            double x = center.x + dx * r, z = center.z + dz * r, y = surfaceY(x, z) + 0.3;
            AtomicFx.send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, dx * 0.14, 0.04, dz * 0.14, 1.0);
            AtomicFx.send(level, ParticleTypes.POOF, x, y + 0.6, z, 3, 0.8, 0.5, 0.8, 0.15);
        }

        double reach = Math.min(r + 2.0, GodzillaTuning.BLAST_SHOCK_RADIUS);
        AABB zone = new AABB(center, center).inflate(reach, 40.0, reach);
        DamageSource src = level.damageSources().explosion(owner, owner);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, this::canHurt)) {
            if (alreadyHit.contains(e.getUUID())) continue;
            double ex = e.getX() - center.x, ez = e.getZ() - center.z;
            double dist = Math.sqrt(ex * ex + ez * ez);
            if (dist > reach) continue;
            alreadyHit.add(e.getUUID());
            float scale = (float) Math.max(0.0, 1.0 - dist / GodzillaTuning.BLAST_SHOCK_RADIUS);
            e.hurt(src, GodzillaTuning.BLAST_DAMAGE * scale);
            double inv = dist < 1.0E-3 ? 0.0 : 1.0 / dist;
            e.setDeltaMovement(ex * inv * 1.8 * scale, 0.5 + 0.9 * scale, ez * inv * 1.8 * scale);
            e.hurtMarked = true;
        }
    }

    private boolean canHurt(LivingEntity e) {
        if (e instanceof GodzillaEntity || !e.isAlive()) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---- the stem: a thick column of fire and smoke, widest at the base ------------------------------------------
    private double stemHeight(int t) {
        return GodzillaTuning.BLAST_STEM_HEIGHT * (1.0 - Math.exp(-t / 24.0));
    }

    private void stem() {
        if (age > 95) return;
        double h = stemHeight(age);
        for (int i = 0; i < 8; i++) {
            double frac = level.random.nextDouble();
            double y = center.y + 1.0 + frac * h;
            double radius = (2.0 + 4.5 * (1.0 - frac) * (1.0 - frac)) * level.random.nextDouble();
            double ang = level.random.nextDouble() * TAU;
            double x = center.x + Math.cos(ang) * radius, z = center.z + Math.sin(ang) * radius;
            double up = 0.12 + level.random.nextDouble() * 0.12;
            double roll = level.random.nextDouble();
            if (age < 45 && roll < 0.40) {
                AtomicFx.send(level, ParticleTypes.FLAME, x, y, z, 0, 0.0, up * 1.2, 0.0, 1.0);
            } else if (roll < 0.80) {
                AtomicFx.send(level, ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.0, up, 0.0, 1.0);
            } else {
                AtomicFx.send(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, 0.0, up * 0.6, 0.0, 1.0);
            }
        }
        if (age < 25) {   // blue-white atomic glow at the base
            AtomicFx.send(level, ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 2.0, center.z, 8, 2.5, 1.5, 2.5, 0.05);
        }
    }

    // ---- the cap: a rolling torus that rides the top of the stem (rises in the middle, spreads on top, falls outside) ---
    private void cap() {
        if (age < 6 || age > 115) return;
        double R = GodzillaTuning.BLAST_CAP_RADIUS * (1.0 - Math.exp(-(age - 5) / 28.0));
        double m = 0.35 * R + 1.5;                                  // tube radius
        double cy = center.y + stemHeight(age) + 3.0;
        for (int i = 0; i < 16; i++) {
            double theta = level.random.nextDouble() * TAU;         // around the ring
            double phi = level.random.nextDouble() * TAU;           // around the tube
            double rx = Math.cos(theta), rz = Math.sin(theta);
            double ring = R + m * Math.cos(phi);
            double x = center.x + rx * ring, z = center.z + rz * ring, y = cy + m * Math.sin(phi) * 0.8;
            double out = Math.sin(phi) * 0.14;                      // top: outward, bottom: inward
            double vy = -Math.cos(phi) * 0.14;                      // outer edge: down, inner edge: up
            if (Math.sin(phi) < 0.0 && age < 70 && level.random.nextFloat() < 0.4F) {
                AtomicFx.send(level, ParticleTypes.FLAME, x, y, z, 0, rx * out, vy, rz * out, 1.0);   // glowing underside
            } else if (level.random.nextFloat() < 0.7F) {
                AtomicFx.send(level, ParticleTypes.LARGE_SMOKE, x, y, z, 0, rx * out, vy, rz * out, 1.0);
            } else {
                AtomicFx.send(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 0, rx * out, vy * 0.5, rz * out, 1.0);
            }
        }
    }
}

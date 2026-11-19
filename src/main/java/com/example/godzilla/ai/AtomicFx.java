package com.example.godzilla.ai;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

/** Particle helpers. ServerLevel#sendParticles(...) only reaches ~32 blocks; this reaches far-away spectators too. */
public final class AtomicFx {
    private AtomicFx() {}

    public static final DustParticleOptions BLUE_GLOW = new DustParticleOptions(new Vector3f(0.25F, 0.65F, 1.0F), 2.2F);
    public static final DustParticleOptions WHITE_CORE = new DustParticleOptions(new Vector3f(0.9F, 0.97F, 1.0F), 3.0F);
    public static final DustParticleOptions RADIATION = new DustParticleOptions(new Vector3f(0.25F, 1.0F, 0.35F), 2.0F);

    public static <T extends ParticleOptions> void send(ServerLevel level, T particle, double x, double y, double z,
                                                         int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < 192.0 * 192.0) {
                level.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }
}

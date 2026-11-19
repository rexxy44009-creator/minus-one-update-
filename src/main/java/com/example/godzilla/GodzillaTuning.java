package com.example.godzilla;

/** Every balance number in one place. 20 ticks = 1 second. */
public final class GodzillaTuning {
    private GodzillaTuning() {}

    // ---- body ----
    public static final double MAX_HEALTH = 5000.0;
    public static final double ARMOR = 30.0;             // vanilla attribute cap
    public static final double ARMOR_TOUGHNESS = 20.0;   // vanilla attribute cap
    public static final double MOVE_SPEED = 0.16;        // zombie = 0.23. Slow and lumbering.
    public static final double FOLLOW_RANGE = 64.0;      // aggro radius (players, villagers, iron golems)
    public static final double STEP_HEIGHT = 3.0;
    public static final float HITBOX_WIDTH = 7.0F;
    public static final float HITBOX_HEIGHT = 21.0F;
    public static final float EYE_HEIGHT = 18.0F;
    public static final int XP_REWARD = 2000;

    // ---- mob griefing by walking ----
    public static final int GRIEF_INTERVAL_TICKS = 3;
    public static final int GRIEF_MAX_BLOCKS_PER_PASS = 300;
    public static final double GRIEF_HORIZONTAL_MARGIN = 1.25;

    // ---- footsteps ----
    public static final int STEP_INTERVAL_TICKS = 20;    // matches the walk animation (one foot plant per second)
    public static final float STOMP_DAMAGE = 10.0F;
    public static final double STOMP_RADIUS = 5.0;

    // ---- melee ----
    public static final double MELEE_REACH = 11.0;       // horizontal, from the body centre
    public static final float BITE_DAMAGE = 60.0F;
    public static final float TAIL_DAMAGE = 50.0F;
    public static final int BITE_TICKS = 30;
    public static final int BITE_STRIKE_TICK = 14;
    public static final int TAIL_TICKS = 44;
    public static final int TAIL_STRIKE_TICK = 22;
    public static final double TAIL_RADIUS = 15.0;
    public static final int MELEE_COOLDOWN = 25;

    // ---- atomic breath ----
    public static final double BREATH_MIN_RANGE = 12.0;  // closer than this he bites/tail-swipes instead
    public static final double BREATH_MAX_RANGE = 64.0;
    public static final int BREATH_COOLDOWN = 400;
    public static final int BREATH_COOLDOWN_ENRAGED = 220; // below 50% health
    public static final int ROAR_TICKS = 50;
    public static final int CHARGE_TICKS = 80;
    public static final int FIRE_TICKS = 100;
    public static final int VENT_TICKS = 40;
    public static final int PLATE_COUNT = 14;   // must equal PlateProfile.FORWARD.length (generated with the model)
    public static final double BEAM_RANGE = 90.0;
    public static final double BEAM_RADIUS = 3.0;
    public static final double BEAM_TRACKING = 0.045;    // 0..1 per tick: how fast the beam swings after the target
    public static final float BEAM_DAMAGE = 35.0F;       // applied every 5 ticks to everything in the beam
    public static final int BEAM_BLOCKS_PER_TICK = 600;  // carve budget per tick (server-lag safety valve)
    public static final int CRATER_INTERVAL_TICKS = 6;
    public static final float CRATER_POWER = 5.0F;       // TNT = 4
    public static final int RADIATION_DURATION_TICKS = 20 * 60;
    public static final float RADIATION_RADIUS = 6.0F;

    // ---- mushroom-cloud blast (erupts where the ray meets the ground as it ends) ----
    public static final float BLAST_POWER = 10.0F;          // vanilla explosion power at ground zero (TNT = 4)
    public static final double BLAST_SHOCK_RADIUS = 45.0;   // ground shockwave reach
    public static final float BLAST_DAMAGE = 60.0F;         // at ground zero, falls off linearly to 0 at the edge
    public static final int BLAST_TICKS = 140;              // how long the cloud keeps forming
    public static final double BLAST_STEM_HEIGHT = 55.0;
    public static final double BLAST_CAP_RADIUS = 24.0;
}

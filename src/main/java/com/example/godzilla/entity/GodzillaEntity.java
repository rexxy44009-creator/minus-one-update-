package com.example.godzilla.entity;

import com.example.godzilla.GodzillaTuning;
import com.example.godzilla.ai.GodzillaAtomicBreathGoal;
import com.example.godzilla.ai.GodzillaMeleeGoal;
import com.example.godzilla.ai.GodzillaMoveControl;
import com.example.godzilla.ai.GodzillaRampageMoveGoal;
import com.example.godzilla.client.GodzillaClientHooks;
import com.example.godzilla.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Godzilla Minus One boss mob.
 *
 * Server owns all behaviour; the client only receives DATA_ACTION (which animation / which plates glow)
 * and the beam yaw/pitch/length (so the client can render the beam). Everything else is server-side.
 */
public class GodzillaEntity extends Monster implements GeoEntity {

    // ------------------------------------------------------------------ action state (synced)
    public static final int ACT_IDLE = 0;
    public static final int ACT_ROAR = 1;
    public static final int ACT_CHARGE = 2;
    public static final int ACT_FIRE = 3;
    public static final int ACT_VENT = 4;
    public static final int ACT_BITE = 5;
    public static final int ACT_TAIL = 6;
    public static final int ACT_DEATH = 7;

    public static final byte EVENT_STOMP = 110;
    public static final byte EVENT_ROAR = 111;
    public static final byte EVENT_BLAST = 112;

    public static final int DEATH_TICKS = 100;
    /** Where the mouth is relative to the entity, in blocks (matches the placeholder model; tune for yours). */
    public static final double MOUTH_FORWARD = PlateProfile.MOUTH_FORWARD;
    public static final double MOUTH_HEIGHT = PlateProfile.MOUTH_UP;

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BEAM_YAW = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_PITCH = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_LENGTH = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);

    // ------------------------------------------------------------------ GeckoLib
    private static final String ANIM = "animation.godzilla_minus_one.";
    private static final RawAnimation A_IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation A_WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation A_ROAR = RawAnimation.begin().thenPlayAndHold(ANIM + "roar");
    private static final RawAnimation A_CHARGE = RawAnimation.begin().thenPlayAndHold(ANIM + "charge");
    private static final RawAnimation A_FIRE = RawAnimation.begin().thenLoop(ANIM + "fire");
    private static final RawAnimation A_VENT = RawAnimation.begin().thenPlayAndHold(ANIM + "vent");
    private static final RawAnimation A_BITE = RawAnimation.begin().thenPlayAndHold(ANIM + "bite");
    private static final RawAnimation A_TAIL = RawAnimation.begin().thenPlayAndHold(ANIM + "tail_swipe");
    private static final RawAnimation A_DEATH = RawAnimation.begin().thenPlayAndHold(ANIM + "death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent =
            new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);

    private int actionStartTick;   // tickCount when DATA_ACTION last changed (both sides)
    private int stepTimer;
    private boolean leftFoot;
    private long breathReadyTime;  // game time when the atomic breath is available again (server)

    public GodzillaEntity(EntityType<? extends GodzillaEntity> type, Level level) {
        super(type, level);
        this.xpReward = GodzillaTuning.XP_REWARD;
        this.moveControl = new GodzillaMoveControl(this);
        this.setPersistenceRequired();
    }

    // ------------------------------------------------------------------ attributes / data / goals
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, GodzillaTuning.MAX_HEALTH)
                .add(Attributes.ARMOR, GodzillaTuning.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, GodzillaTuning.ARMOR_TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, GodzillaTuning.MOVE_SPEED)
                .add(Attributes.FOLLOW_RANGE, GodzillaTuning.FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, GodzillaTuning.BITE_DAMAGE)
                .add(Attributes.STEP_HEIGHT, GodzillaTuning.STEP_HEIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, ACT_IDLE);
        builder.define(DATA_BEAM_YAW, 0.0F);
        builder.define(DATA_BEAM_PITCH, 0.0F);
        builder.define(DATA_BEAM_LENGTH, 0.0F);
    }

    @Override
    protected void registerGoals() {
        // Priority: lower number wins when goals share a flag (MOVE/LOOK).
        this.goalSelector.addGoal(1, new GodzillaAtomicBreathGoal(this)); // roar -> charge -> fire -> vent
        this.goalSelector.addGoal(2, new GodzillaMeleeGoal(this));        // jaw bite / tail swipe
        this.goalSelector.addGoal(3, new GodzillaRampageMoveGoal(this));  // lumber toward the target
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // FOLLOW_RANGE (64) is the acquisition radius; mustSee=false so he hunts you through terrain.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, 10, false, false, null));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, 10, false, false, null));
    }

    // ------------------------------------------------------------------ action / beam accessors
    public int getAction() { return this.entityData.get(DATA_ACTION); }

    /** Server only. Resets the action clock on both sides via onSyncedDataUpdated. */
    public void setAction(int action) { this.entityData.set(DATA_ACTION, action); }

    public int getActionTicks() { return Math.max(0, this.tickCount - this.actionStartTick); }

    public boolean isFiring() { return getAction() == ACT_FIRE; }

    public float getBeamYaw() { return this.entityData.get(DATA_BEAM_YAW); }
    public float getBeamPitch() { return this.entityData.get(DATA_BEAM_PITCH); }
    public float getBeamLength() { return this.entityData.get(DATA_BEAM_LENGTH); }

    public void setBeam(float yaw, float pitch, float length) {
        this.entityData.set(DATA_BEAM_YAW, yaw);
        this.entityData.set(DATA_BEAM_PITCH, pitch);
        this.entityData.set(DATA_BEAM_LENGTH, length);
    }

    public void setBeamLength(float length) { this.entityData.set(DATA_BEAM_LENGTH, length); }

    public long getBreathReadyTime() { return breathReadyTime; }
    public void setBreathReadyTime(long gameTime) { this.breathReadyTime = gameTime; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key)) {
            this.actionStartTick = this.tickCount;
        }
    }

    // ------------------------------------------------------------------ geometry helpers
    public Vec3 getForwardHoriz() {
        float r = this.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    public Vec3 getMouthOffset(float bodyYawDeg) {
        float r = bodyYawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r) * MOUTH_FORWARD, MOUTH_HEIGHT, Mth.cos(r) * MOUTH_FORWARD);
    }

    public Vec3 getMouthPosition() { return this.position().add(getMouthOffset(this.yBodyRot)); }

    /** World position of dorsal plate i (0 = tail tip ... 11 = neck). */
    public Vec3 getPlateWorldPos(int index) {
        int i = Mth.clamp(index, 0, PlateProfile.FORWARD.length - 1);
        return this.position().add(getForwardHoriz().scale(PlateProfile.FORWARD[i])).add(0.0, PlateProfile.UP[i], 0.0);
    }

    /** Turn the whole body (not just the head) toward a yaw, at most maxStep degrees per tick. */
    public void turnBodyTowards(float targetYaw, float maxStep) {
        float y = Mth.approachDegrees(this.getYRot(), targetYaw, maxStep);
        this.setYRot(y);
        this.yBodyRot = y;
        this.yHeadRot = y;
    }

    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Which dorsal plates are glowing right now (called by the client model every frame). */
    public boolean isPlateLit(int index, float partialTick) {
        int n = GodzillaTuning.PLATE_COUNT;
        float t = getActionTicks() + partialTick;
        switch (getAction()) {
            case ACT_CHARGE: {   // lights up one by one, tail (0) -> head (n-1)
                float p = Mth.clamp(t / GodzillaTuning.CHARGE_TICKS, 0.0F, 1.0F);
                return index < Math.min(n, (int) (p * n) + 1);
            }
            case ACT_FIRE:
                return true;
            case ACT_VENT: {     // cools down tail -> head
                float p = Mth.clamp(t / GodzillaTuning.VENT_TICKS, 0.0F, 1.0F);
                return index >= (int) (p * n);
            }
            default:
                return false;
        }
    }

    public boolean isMouthGlowing(float partialTick) {
        float t = getActionTicks() + partialTick;
        switch (getAction()) {
            case ACT_CHARGE: return t / GodzillaTuning.CHARGE_TICKS > 0.5F;
            case ACT_FIRE: return true;
            case ACT_VENT: return t < 15.0F;
            default: return false;
        }
    }

    public boolean isValidVictim(LivingEntity e) {
        if (e == this || e instanceof GodzillaEntity || !e.isAlive()) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Throw a living entity away from `from`. Works on players (hurtMarked syncs the velocity). */
    public void launch(LivingEntity target, Vec3 from, double horizontal, double vertical) {
        Vec3 away = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1.0E-4 ? getForwardHoriz() : away.normalize();
        target.setDeltaMovement(away.x * horizontal, vertical, away.z * horizontal);
        target.hurtMarked = true;
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            GodzillaClientHooks.tick(this);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || this.isDeadOrDying() || !(this.level() instanceof ServerLevel level)) return;

        // Mob griefing: everything inside (and just in front of) the body is flattened.
        if (this.tickCount % GodzillaTuning.GRIEF_INTERVAL_TICKS == 0
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            smashBlocksInBody(level);
        }

        // Ground-shaking footsteps.
        if (this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() > 6.0E-4) {
            if (--this.stepTimer <= 0) {
                this.stepTimer = GodzillaTuning.STEP_INTERVAL_TICKS;
                stomp(level);
            }
        } else {
            this.stepTimer = Math.min(this.stepTimer, 5);
        }
    }

    private void smashBlocksInBody(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(GodzillaTuning.GRIEF_HORIZONTAL_MARGIN, 0.0, GodzillaTuning.GRIEF_HORIZONTAL_MARGIN);
        int minY = Mth.floor(box.minY + 0.01);   // never touch the ground he is standing on
        int maxY = Mth.floor(box.maxY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    pos.set(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    if (state.getDestroySpeed(level, pos) < 0.0F) continue;           // bedrock etc.
                    if ((broken & 3) == 0) level.levelEvent(2001, pos, Block.getId(state)); // break particles + sound (1 in 4)
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (++broken >= GodzillaTuning.GRIEF_MAX_BLOCKS_PER_PASS) return;
                }
            }
        }
    }

    private void stomp(ServerLevel level) {
        this.leftFoot = !this.leftFoot;
        float r = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
        Vec3 side = new Vec3(Mth.cos(r), 0.0, Mth.sin(r));
        Vec3 foot = this.position().add(fwd.scale(1.5)).add(side.scale(this.leftFoot ? 2.0 : -2.0));

        this.playSound(ModSounds.STEP.get(), 8.0F, 0.85F + this.random.nextFloat() * 0.2F);
        level.broadcastEntityEvent(this, EVENT_STOMP);   // client: camera shake

        BlockState ground = level.getBlockState(BlockPos.containing(foot.x, foot.y - 0.5, foot.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                    foot.x, foot.y + 0.2, foot.z, 40, 1.8, 0.3, 1.8, 0.15);
        }

        AABB zone = new AABB(foot, foot).inflate(GodzillaTuning.STOMP_RADIUS, 2.0, GodzillaTuning.STOMP_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, this::isValidVictim)) {
            if (!e.onGround()) continue;                 // jump to dodge the shockwave
            e.hurt(this.damageSources().mobAttack(this), GodzillaTuning.STOMP_DAMAGE);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.6, 0.0));
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ client events
    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_STOMP) {
            GodzillaClientHooks.onStomp(this);
        } else if (id == EVENT_ROAR) {
            GodzillaClientHooks.onRoar(this);
        } else if (id == EVENT_BLAST) {
            GodzillaClientHooks.onBlast(this);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // Tail sticks out of the hitbox; while firing, the beam does too (so it doesn't vanish when he's off-screen).
        return isFiring() ? this.getBoundingBox().inflate(95.0, 30.0, 95.0) : this.getBoundingBox().inflate(22.0, 2.0, 22.0);
    }

    // ------------------------------------------------------------------ immunities / physics
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;   // /kill still works
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)) return true;
        if (source.getEntity() == this) return true;                             // own beam / craters
        Entity direct = source.getDirectEntity();
        return direct instanceof Arrow || direct instanceof SpectralArrow;       // "normal arrows"
    }

    @Override public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.getEffect().is(MobEffects.WITHER) && super.canBeAffected(effect);
    }
    @Override public boolean ignoreExplosion(Explosion explosion) { return true; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { /* custom footsteps in stomp() */ }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.DEATH.get(); }

    // ------------------------------------------------------------------ death
    @Override
    public void die(DamageSource cause) {
        this.entityData.set(DATA_ACTION, ACT_DEATH);
        this.entityData.set(DATA_BEAM_LENGTH, 0.0F);
        super.die(cause);
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level && this.deathTime % 5 == 0 && this.deathTime < DEATH_TICKS) {
            double x = this.getX() + (this.random.nextDouble() - 0.5) * 8.0;
            double y = this.getY() + this.random.nextDouble() * 12.0;
            double z = this.getZ() + (this.random.nextDouble() - 0.5) * 8.0;
            level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 3, 1.0, 1.0, 1.0, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 2.0, 2.0, 2.0, 0.4);
        }
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);   // vanilla poof
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ boss bar
    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }
    @Override public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<GodzillaEntity>(this, "main", 4, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<GodzillaEntity> state) {
        RawAnimation anim = switch (getAction()) {
            case ACT_ROAR -> A_ROAR;
            case ACT_CHARGE -> A_CHARGE;
            case ACT_FIRE -> A_FIRE;
            case ACT_VENT -> A_VENT;
            case ACT_BITE -> A_BITE;
            case ACT_TAIL -> A_TAIL;
            case ACT_DEATH -> A_DEATH;
            default -> state.isMoving() ? A_WALK : A_IDLE;
        };
        return state.setAndContinue(anim);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
}

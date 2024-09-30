package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import java.util.ArrayList;
import java.util.List;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
// CraftBukkit end

public class Slime extends Mob implements Enemy {

    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Slime.class, EntityDataSerializers.INT);
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 127;
    public static final int MAX_NATURAL_SIZE = 4;
    public float targetSquish;
    public float squish;
    public float oSquish;
    private boolean wasOnGround;

    public Slime(EntityType<? extends Slime> type, Level world) {
        super(type, world);
        this.fixupDimensions();
        this.moveControl = new Slime.SlimeMoveControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Slime.SlimeFloatGoal(this));
        this.goalSelector.addGoal(2, new Slime.SlimeAttackGoal(this));
        this.goalSelector.addGoal(3, new Slime.SlimeRandomDirectionGoal(this));
        this.goalSelector.addGoal(5, new Slime.SlimeKeepOnJumpingGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (entityliving) -> {
            return Math.abs(entityliving.getY() - this.getY()) <= 4.0D;
        }));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Slime.ID_SIZE, 1);
    }

    @VisibleForTesting
    public void setSize(int size, boolean heal) {
        int j = Mth.clamp(size, 1, 127);

        this.entityData.set(Slime.ID_SIZE, j);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double) (j * j));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) (0.2F + 0.1F * (float) j));
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double) j);
        if (heal) {
            this.setHealth(this.getMaxHealth());
        }

        this.xpReward = j;
    }

    public int getSize() {
        return (Integer) this.entityData.get(Slime.ID_SIZE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Paper.canWander", this.canWander); // Paper
        nbt.putInt("Size", this.getSize() - 1);
        nbt.putBoolean("wasOnGround", this.wasOnGround);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.setSize(nbt.getInt("Size") + 1, false);
        super.readAdditionalSaveData(nbt);
        // Paper start
        if (nbt.contains("Paper.canWander")) {
            this.canWander = nbt.getBoolean("Paper.canWander");
        }
        // Paper end
        this.wasOnGround = nbt.getBoolean("wasOnGround");
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected ParticleOptions getParticleType() {
        return ParticleTypes.ITEM_SLIME;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return this.getSize() > 0;
    }

    @Override
    public void tick() {
        this.squish += (this.targetSquish - this.squish) * 0.5F;
        this.oSquish = this.squish;
        super.tick();
        if (this.onGround() && !this.wasOnGround) {
            float f = this.getDimensions(this.getPose()).width() * 2.0F;
            float f1 = f / 2.0F;

            for (int i = 0; (float) i < f * 16.0F; ++i) {
                float f2 = this.random.nextFloat() * 6.2831855F;
                float f3 = this.random.nextFloat() * 0.5F + 0.5F;
                float f4 = Mth.sin(f2) * f1 * f3;
                float f5 = Mth.cos(f2) * f1 * f3;

                this.level().addParticle(this.getParticleType(), this.getX() + (double) f4, this.getY(), this.getZ() + (double) f5, 0.0D, 0.0D, 0.0D);
            }

            this.playSound(this.getSquishSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            this.targetSquish = -0.5F;
        } else if (!this.onGround() && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }

        this.wasOnGround = this.onGround();
        this.decreaseSquish();
    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Slime.ID_SIZE.equals(data)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    public EntityType<? extends Slime> getType() {
        return (EntityType<? extends Slime>) super.getType(); // CraftBukkit - decompile error
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        // CraftBukkit end
        int i = this.getSize();

        if (!this.level().isClientSide && i > 1 && this.isDeadOrDying()) {
            Component ichatbasecomponent = this.getCustomName();
            boolean flag = this.isNoAi();
            float f = this.getDimensions(this.getPose()).width();
            float f1 = f / 2.0F;
            int j = i / 2;
            int k = 2 + this.random.nextInt(3);

            // CraftBukkit start
            SlimeSplitEvent event = new SlimeSplitEvent((org.bukkit.entity.Slime) this.getBukkitEntity(), k);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled() && event.getCount() > 0) {
                k = event.getCount();
            } else {
                super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
                return;
            }
            List<LivingEntity> slimes = new ArrayList<>(j);
            // CraftBukkit end

            for (int l = 0; l < k; ++l) {
                float f2 = ((float) (l % 2) - 0.5F) * f1;
                float f3 = ((float) (l / 2) - 0.5F) * f1;
                Slime entityslime = (Slime) this.getType().create(this.level());

                if (entityslime != null) {
                    if (this.isPersistenceRequired()) {
                        entityslime.setPersistenceRequired();
                    }

                    entityslime.aware = this.aware; // Paper - Fix nerfed slime when splitting
                    entityslime.setCustomName(ichatbasecomponent);
                    entityslime.setNoAi(flag);
                    entityslime.setInvulnerable(this.isInvulnerable());
                    entityslime.setSize(j, true);
                    entityslime.moveTo(this.getX() + (double) f2, this.getY() + 0.5D, this.getZ() + (double) f3, this.random.nextFloat() * 360.0F, 0.0F);
                    slimes.add(entityslime); // CraftBukkit
                }
            }
            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, slimes, EntityTransformEvent.TransformReason.SPLIT).isCancelled()) {
                super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
                return;
            }
            for (LivingEntity living : slimes) {
                this.level().addFreshEntity(living, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT); // CraftBukkit - SpawnReason
            }
            // CraftBukkit end
        }

        super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem && this.isDealsDamage()) {
            this.dealDamage((LivingEntity) entity);
        }

    }

    @Override
    public void playerTouch(Player player) {
        if (this.isDealsDamage()) {
            this.dealDamage(player);
        }

    }

    protected void dealDamage(LivingEntity target) {
        if (this.isAlive()) {
            int i = this.getSize();

            if (this.distanceToSqr((Entity) target) < 0.6D * (double) i * 0.6D * (double) i && this.hasLineOfSight(target) && target.hurt(this.damageSources().mobAttack(this), this.getAttackDamage())) {
                this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.doEnchantDamageEffects(this, target);
            }
        }

    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return new Vec3(0.0D, (double) dimensions.height() - 0.015625D * (double) this.getSize() * (double) scaleFactor, 0.0D);
    }

    protected boolean isDealsDamage() {
        return !this.isTiny() && this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isTiny() ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isTiny() ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
    }

    protected SoundEvent getSquishSound() {
        return this.isTiny() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH;
    }

    public static boolean checkSlimeSpawnRules(EntityType<Slime> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        if (MobSpawnType.isSpawner(spawnReason)) {
            return checkMobSpawnRules(type, world, spawnReason, pos, random);
        } else {
            if (world.getDifficulty() != Difficulty.PEACEFUL) {
                if (spawnReason == MobSpawnType.SPAWNER) {
                    return checkMobSpawnRules(type, world, spawnReason, pos, random);
                }

                // Paper start - Replace rules for Height in Swamp Biome
                final double maxHeightSwamp = world.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.maximum;
                final double minHeightSwamp = world.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.minimum;
                if (world.getBiome(pos).is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS) && pos.getY() > minHeightSwamp && pos.getY() < maxHeightSwamp && random.nextFloat() < 0.5F && random.nextFloat() < world.getMoonBrightness() && world.getMaxLocalRawBrightness(pos) <= random.nextInt(8)) {
                // Paper end - Replace rules for Height in Swamp Biome
                    return checkMobSpawnRules(type, world, spawnReason, pos, random);
                }

                if (!(world instanceof WorldGenLevel)) {
                    return false;
                }

                ChunkPos chunkcoordintpair = new ChunkPos(pos);
                boolean flag = world.getMinecraftWorld().paperConfig().entities.spawning.allChunksAreSlimeChunks || WorldgenRandom.seedSlimeChunk(chunkcoordintpair.x, chunkcoordintpair.z, ((WorldGenLevel) world).getSeed(), world.getMinecraftWorld().spigotConfig.slimeSeed).nextInt(10) == 0; // Spigot // Paper

                // Paper start - Replace rules for Height in Slime Chunks
                final double maxHeightSlimeChunk = world.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.slimeChunk.maximum;
                if (random.nextInt(10) == 0 && flag && pos.getY() < maxHeightSlimeChunk) {
                // Paper end - Replace rules for Height in Slime Chunks
                    return checkMobSpawnRules(type, world, spawnReason, pos, random);
                }
            }

            return false;
        }
    }

    @Override
    public float getSoundVolume() {
        return 0.4F * (float) this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Override
    protected void jumpFromGround() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x, (double) this.getJumpPower(), vec3d.z);
        this.hasImpulse = true;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        RandomSource randomsource = world.getRandom();
        int i = randomsource.nextInt(3);

        if (i < 2 && randomsource.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) {
            ++i;
        }

        int j = 1 << i;

        this.setSize(j, true);
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }

    float getSoundPitch() {
        float f = this.isTiny() ? 1.4F : 0.8F;

        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * f;
    }

    protected SoundEvent getJumpSound() {
        return this.isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale((float) this.getSize());
    }

    private static class SlimeMoveControl extends MoveControl {

        private float yRot;
        private int jumpDelay;
        private final Slime slime;
        private boolean isAggressive;

        public SlimeMoveControl(Slime slime) {
            super(slime);
            this.slime = slime;
            this.yRot = 180.0F * slime.getYRot() / 3.1415927F;
        }

        public void setDirection(float targetYaw, boolean jumpOften) {
            this.yRot = targetYaw;
            this.isAggressive = jumpOften;
        }

        public void setWantedMovement(double speed) {
            this.speedModifier = speed;
            this.operation = MoveControl.Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.yBodyRot = this.mob.getYRot();
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                this.mob.setZza(0.0F);
            } else {
                this.operation = MoveControl.Operation.WAIT;
                if (this.mob.onGround()) {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                    if (this.jumpDelay-- <= 0) {
                        this.jumpDelay = this.slime.getJumpDelay();
                        if (this.isAggressive) {
                            this.jumpDelay /= 3;
                        }

                        this.slime.getJumpControl().jump();
                        if (this.slime.doPlayJumpSound()) {
                            this.slime.playSound(this.slime.getJumpSound(), this.slime.getSoundVolume(), this.slime.getSoundPitch());
                        }
                    } else {
                        this.slime.xxa = 0.0F;
                        this.slime.zza = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                }

            }
        }
    }

    private static class SlimeFloatGoal extends Goal {

        private final Slime slime;

        public SlimeFloatGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
            slime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (this.slime.isInWater() || this.slime.isInLava()) && this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeSwimEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity()).callEvent(); // Paper - Slime pathfinder events
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().jump();
            }

            MoveControl controllermove = this.slime.getMoveControl();

            if (controllermove instanceof Slime.SlimeMoveControl entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setWantedMovement(1.2D);
            }

        }
    }

    private static class SlimeAttackGoal extends Goal {

        private final Slime slime;
        private int growTiredTimer;

        public SlimeAttackGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity entityliving = this.slime.getTarget();

            // Paper start - Slime pathfinder events
            if (entityliving == null || !entityliving.isAlive()) {
                return false;
            }
            if (!this.slime.canAttack(entityliving)) {
                return false;
            }
            return this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), (org.bukkit.entity.LivingEntity) entityliving.getBukkitEntity()).callEvent();
            // Paper end - Slime pathfinder events
        }

        @Override
        public void start() {
            this.growTiredTimer = reducedTickDelay(300);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = this.slime.getTarget();

            // Paper start - Slime pathfinder events
            if (entityliving == null || !entityliving.isAlive()) {
                return false;
            }
            if (!this.slime.canAttack(entityliving)) {
                return false;
            }
            return --this.growTiredTimer > 0 && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), (org.bukkit.entity.LivingEntity) entityliving.getBukkitEntity()).callEvent();
            // Paper end - Slime pathfinder events
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity entityliving = this.slime.getTarget();

            if (entityliving != null) {
                this.slime.lookAt(entityliving, 10.0F, 10.0F);
            }

            MoveControl controllermove = this.slime.getMoveControl();

            if (controllermove instanceof Slime.SlimeMoveControl entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setDirection(this.slime.getYRot(), this.slime.isDealsDamage());
            }

        }

        // Paper start - Slime pathfinder events; clear timer and target when goal resets
        public void stop() {
            this.growTiredTimer = 0;
            this.slime.setTarget(null);
        }
        // Paper end - Slime pathfinder events
    }

    private static class SlimeRandomDirectionGoal extends Goal {

        private final Slime slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        public SlimeRandomDirectionGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.slime.getTarget() == null && (this.slime.onGround() || this.slime.isInWater() || this.slime.isInLava() || this.slime.hasEffect(MobEffects.LEVITATION)) && this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander; // Paper - Slime pathfinder events
        }

        @Override
        public void tick() {
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = this.adjustedTickDelay(40 + this.slime.getRandom().nextInt(60));
                this.chosenDegrees = (float) this.slime.getRandom().nextInt(360);
                // Paper start - Slime pathfinder events
                com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent event = new com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), this.chosenDegrees);
                if (!this.slime.canWander || !event.callEvent()) return;
                this.chosenDegrees = event.getNewYaw();
                // Paper end - Slime pathfinder events
            }

            MoveControl controllermove = this.slime.getMoveControl();

            if (controllermove instanceof Slime.SlimeMoveControl entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setDirection(this.chosenDegrees, false);
            }

        }
    }

    private static class SlimeKeepOnJumpingGoal extends Goal {

        private final Slime slime;

        public SlimeKeepOnJumpingGoal(Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.slime.isPassenger() && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeWanderEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity()).callEvent(); // Paper - Slime pathfinder events
        }

        @Override
        public void tick() {
            MoveControl controllermove = this.slime.getMoveControl();

            if (controllermove instanceof Slime.SlimeMoveControl entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setWantedMovement(1.0D);
            }

        }
    }

    // Paper start - Slime pathfinder events
    private boolean canWander = true;
    public boolean canWander() {
        return canWander;
    }

    public void setWander(boolean canWander) {
        this.canWander = canWander;
    }
    // Paper end - Slime pathfinder events
}

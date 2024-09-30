package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class Spider extends Monster {

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(Spider.class, EntityDataSerializers.BYTE);
    private static final float SPIDER_SPECIAL_EFFECT_CHANCE = 0.1F;

    public Spider(EntityType<? extends Spider> type, Level world) {
        super(type, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Armadillo.class, 6.0F, 1.0D, 1.2D, (entityliving) -> {
            return !((Armadillo) entityliving).isScared();
        }));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new Spider.SpiderAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(2, new Spider.SpiderTargetGoal<>(this, Player.class));
        this.targetSelector.addGoal(3, new Spider.SpiderTargetGoal<>(this, IronGolem.class));
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new WallClimberNavigation(this, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Spider.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.setClimbing(this.horizontalCollision && (this.level().paperConfig().entities.behavior.allowSpiderWorldBorderClimbing || !io.papermc.paper.util.CollisionUtil.isCollidingWithBorder(this.level().getWorldBorder(), this.getBoundingBox().inflate(io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)))); // Paper - Add config option for spider worldborder climbing & Inflate by +EPSILON as collision will just barely place us outside border
        }

    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0D).add(Attributes.MOVEMENT_SPEED, 0.30000001192092896D);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, multiplier);
        }

    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.is(MobEffects.POISON) && this.level().paperConfig().entities.mobEffects.spidersImmuneToPoisonEffect ? false : super.canBeAffected(effect); // Paper - Add config for mobs immune to default effects
    }

    public boolean isClimbing() {
        return ((Byte) this.entityData.get(Spider.DATA_FLAGS_ID) & 1) != 0;
    }

    public void setClimbing(boolean climbing) {
        byte b0 = (Byte) this.entityData.get(Spider.DATA_FLAGS_ID);

        if (climbing) {
            b0 = (byte) (b0 | 1);
        } else {
            b0 &= -2;
        }

        this.entityData.set(Spider.DATA_FLAGS_ID, b0);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        Object object = super.finalizeSpawn(world, difficulty, spawnReason, entityData);
        RandomSource randomsource = world.getRandom();

        if (randomsource.nextInt(100) == 0) {
            Skeleton entityskeleton = (Skeleton) EntityType.SKELETON.create(this.level());

            if (entityskeleton != null) {
                entityskeleton.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                entityskeleton.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) null);
                entityskeleton.startRiding(this);
            }
        }

        if (object == null) {
            object = new Spider.SpiderEffectsGroupData();
            if (world.getDifficulty() == Difficulty.HARD && randomsource.nextFloat() < 0.1F * difficulty.getSpecialMultiplier()) {
                ((Spider.SpiderEffectsGroupData) object).setRandomEffect(randomsource);
            }
        }

        if (object instanceof Spider.SpiderEffectsGroupData entityspider_groupdataspider) {
            Holder<MobEffect> holder = entityspider_groupdataspider.effect;

            if (holder != null) {
                this.addEffect(new MobEffectInstance(holder, -1), null, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.SPIDER_SPAWN, world instanceof net.minecraft.server.level.ServerLevel); // CraftBukkit
            }
        }

        return (SpawnGroupData) object;
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        return vehicle.getBbWidth() <= this.getBbWidth() ? new Vec3(0.0D, 0.3125D * (double) this.getScale(), 0.0D) : super.getVehicleAttachmentPoint(vehicle);
    }

    private static class SpiderAttackGoal extends MeleeAttackGoal {

        public SpiderAttackGoal(Spider spider) {
            super(spider, 1.0D, true);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.mob.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            float f = this.mob.getLightLevelDependentMagicValue();

            if (f >= 0.5F && this.mob.getRandom().nextInt(100) == 0) {
                this.mob.setTarget((LivingEntity) null);
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }

    private static class SpiderTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {

        public SpiderTargetGoal(Spider spider, Class<T> targetEntityClass) {
            super(spider, targetEntityClass, true);
        }

        @Override
        public boolean canUse() {
            float f = this.mob.getLightLevelDependentMagicValue();

            return f >= 0.5F ? false : super.canUse();
        }
    }

    public static class SpiderEffectsGroupData implements SpawnGroupData {

        @Nullable
        public Holder<MobEffect> effect;

        public SpiderEffectsGroupData() {}

        public void setRandomEffect(RandomSource random) {
            int i = random.nextInt(5);

            if (i <= 1) {
                this.effect = MobEffects.MOVEMENT_SPEED;
            } else if (i <= 2) {
                this.effect = MobEffects.DAMAGE_BOOST;
            } else if (i <= 3) {
                this.effect = MobEffects.REGENERATION;
            } else if (i <= 4) {
                this.effect = MobEffects.INVISIBILITY;
            }

        }
    }
}

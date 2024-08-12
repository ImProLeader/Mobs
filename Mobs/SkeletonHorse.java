package net.minecraft.world.entity.animal.horse;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class SkeletonHorse extends AbstractHorse {

    private final SkeletonTrapGoal skeletonTrapGoal = new SkeletonTrapGoal(this);
    private static final int TRAP_MAX_LIFE = 18000;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.SKELETON_HORSE.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.SKELETON_HORSE.getHeight() - 0.03125F, 0.0F)).scale(0.5F);
    private boolean isTrap;
    public int trapTime;

    public SkeletonHorse(EntityType<? extends SkeletonHorse> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 15.0D).add(Attributes.MOVEMENT_SPEED, 0.20000000298023224D);
    }

    public static boolean checkSkeletonHorseSpawnRules(EntityType<? extends Animal> type, LevelAccessor world, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return !MobSpawnType.isSpawner(reason) ? Animal.checkAnimalSpawnRules(type, world, reason, pos, random) : MobSpawnType.ignoresLightRequirements(reason) || isBrightEnoughToSpawn(world, pos);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.JUMP_STRENGTH);

        Objects.requireNonNull(random);
        attributemodifiable.setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    protected void addBehaviourGoals() {}

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isEyeInFluid(FluidTags.WATER) ? SoundEvents.SKELETON_HORSE_AMBIENT_WATER : SoundEvents.SKELETON_HORSE_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_HORSE_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HORSE_HURT;
    }

    @Override
    protected SoundEvent getSwimSound() {
        if (this.onGround()) {
            if (!this.isVehicle()) {
                return SoundEvents.SKELETON_HORSE_STEP_WATER;
            }

            ++this.gallopSoundCounter;
            if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                return SoundEvents.SKELETON_HORSE_GALLOP_WATER;
            }

            if (this.gallopSoundCounter <= 5) {
                return SoundEvents.SKELETON_HORSE_STEP_WATER;
            }
        }

        return SoundEvents.SKELETON_HORSE_SWIM;
    }

    @Override
    protected void playSwimSound(float volume) {
        if (this.onGround()) {
            super.playSwimSound(0.3F);
        } else {
            super.playSwimSound(Math.min(0.1F, volume * 25.0F));
        }

    }

    @Override
    protected void playJumpSound() {
        if (this.isInWater()) {
            this.playSound(SoundEvents.SKELETON_HORSE_JUMP_WATER, 0.4F, 1.0F);
        } else {
            super.playJumpSound();
        }

    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? SkeletonHorse.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isTrap() && this.trapTime++ >= 18000) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("SkeletonTrap", this.isTrap());
        nbt.putInt("SkeletonTrapTime", this.trapTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setTrap(nbt.getBoolean("SkeletonTrap"));
        this.trapTime = nbt.getInt("SkeletonTrapTime");
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.96F;
    }

    public boolean isTrap() {
        return this.isTrap;
    }

    public void setTrap(boolean trapped) {
        if (trapped != this.isTrap) {
            this.isTrap = trapped;
            if (trapped) {
                this.goalSelector.addGoal(1, this.skeletonTrapGoal);
            } else {
                this.goalSelector.removeGoal(this.skeletonTrapGoal);
            }

        }
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity) {
        return (AgeableMob) EntityType.SKELETON_HORSE.create(world);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return !this.isTamed() ? InteractionResult.PASS : super.mobInteract(player, hand);
    }
}

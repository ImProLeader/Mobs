package net.minecraft.world.entity.animal.horse;

import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
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
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.SoundType;

public class Horse extends AbstractHorse implements VariantHolder<Variant> {
    private static final EntityDataAccessor<Integer> DATA_ID_TYPE_VARIANT = SynchedEntityData.defineId(Horse.class, EntityDataSerializers.INT);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.HORSE
        .getDimensions()
        .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityType.HORSE.getHeight() + 0.125F, 0.0F))
        .scale(0.5F);

    public Horse(EntityType<? extends Horse> type, Level world) {
        super(type, world);
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double)generateMaxHealth(random::nextInt));
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(generateSpeed(random::nextDouble));
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ID_TYPE_VARIANT, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Variant", this.getTypeVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setTypeVariant(nbt.getInt("Variant"));
    }

    private void setTypeVariant(int variant) {
        this.entityData.set(DATA_ID_TYPE_VARIANT, variant);
    }

    private int getTypeVariant() {
        return this.entityData.get(DATA_ID_TYPE_VARIANT);
    }

    public void setVariantAndMarkings(Variant color, Markings marking) {
        this.setTypeVariant(color.getId() & 0xFF | marking.getId() << 8 & 0xFF00);
    }

    @Override
    public Variant getVariant() {
        return Variant.byId(this.getTypeVariant() & 0xFF);
    }

    @Override
    public void setVariant(Variant variant) {
        this.setTypeVariant(variant.getId() & 0xFF | this.getTypeVariant() & -256);
    }

    public Markings getMarkings() {
        return Markings.byId((this.getTypeVariant() & 0xFF00) >> 8);
    }

    @Override
    public void containerChanged(Container sender) {
        ItemStack itemStack = this.getBodyArmorItem();
        super.containerChanged(sender);
        ItemStack itemStack2 = this.getBodyArmorItem();
        if (this.tickCount > 20 && this.isBodyArmorItem(itemStack2) && itemStack != itemStack2) {
            this.playSound(SoundEvents.HORSE_ARMOR, 0.5F, 1.0F);
        }
    }

    @Override
    protected void playGallopSound(SoundType group) {
        super.playGallopSound(group);
        if (this.random.nextInt(10) == 0) {
            this.playSound(SoundEvents.HORSE_BREATHE, group.getVolume() * 0.6F, group.getPitch());
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Nullable
    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.HORSE_EAT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.HORSE_ANGRY;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean bl = !this.isBaby() && this.isTamed() && player.isSecondaryUseActive();
        if (!this.isVehicle() && !bl) {
            ItemStack itemStack = player.getItemInHand(hand);
            if (!itemStack.isEmpty()) {
                if (this.isFood(itemStack)) {
                    return this.fedFood(player, itemStack);
                }

                if (!this.isTamed()) {
                    this.makeMad();
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            return super.mobInteract(player, hand);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && (other instanceof Donkey || other instanceof Horse) && this.canParent() && ((AbstractHorse)other).canParent();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity) {
        if (entity instanceof Donkey) {
            Mule mule = EntityType.MULE.create(world);
            if (mule != null) {
                this.setOffspringAttributes(entity, mule);
            }

            return mule;
        } else {
            Horse horse = (Horse)entity;
            Horse horse2 = EntityType.HORSE.create(world);
            if (horse2 != null) {
                int i = this.random.nextInt(9);
                Variant variant;
                if (i < 4) {
                    variant = this.getVariant();
                } else if (i < 8) {
                    variant = horse.getVariant();
                } else {
                    variant = Util.getRandom(Variant.values(), this.random);
                }

                int j = this.random.nextInt(5);
                Markings markings;
                if (j < 2) {
                    markings = this.getMarkings();
                } else if (j < 4) {
                    markings = horse.getMarkings();
                } else {
                    markings = Util.getRandom(Markings.values(), this.random);
                }

                horse2.setVariantAndMarkings(variant, markings);
                this.setOffspringAttributes(entity, horse2);
            }

            return horse2;
        }
    }

    @Override
    public boolean canWearBodyArmor() {
        return true;
    }

    @Override
    public boolean isBodyArmorItem(ItemStack stack) {
        if (stack.getItem() instanceof AnimalArmorItem animalArmorItem && animalArmorItem.getBodyType() == AnimalArmorItem.BodyType.EQUESTRIAN) {
            return true;
        }

        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        RandomSource randomSource = world.getRandom();
        Variant variant;
        if (entityData instanceof Horse.HorseGroupData) {
            variant = ((Horse.HorseGroupData)entityData).variant;
        } else {
            variant = Util.getRandom(Variant.values(), randomSource);
            entityData = new Horse.HorseGroupData(variant);
        }

        this.setVariantAndMarkings(variant, Util.getRandom(Markings.values(), randomSource));
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    public static class HorseGroupData extends AgeableMob.AgeableMobGroupData {
        public final Variant variant;

        public HorseGroupData(Variant color) {
            super(true);
            this.variant = color;
        }
    }
}

package net.minecraft.world.entity.monster;

import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableWitchTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestHealableRaiderTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class Witch extends Raider implements RangedAttackMob {

    private static final UUID SPEED_MODIFIER_DRINKING_UUID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181E");
    private static final AttributeModifier SPEED_MODIFIER_DRINKING = new AttributeModifier(Witch.SPEED_MODIFIER_DRINKING_UUID, "Drinking speed penalty", -0.25D, AttributeModifier.Operation.ADD_VALUE);
    private static final EntityDataAccessor<Boolean> DATA_USING_ITEM = SynchedEntityData.defineId(Witch.class, EntityDataSerializers.BOOLEAN);
    public int usingTime;
    private NearestHealableRaiderTargetGoal<Raider> healRaidersGoal;
    private NearestAttackableWitchTargetGoal<Player> attackPlayersGoal;

    public Witch(EntityType<? extends Witch> type, Level world) {
        super(type, world);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.healRaidersGoal = new NearestHealableRaiderTargetGoal<>(this, Raider.class, true, (entityliving) -> {
            return entityliving != null && this.hasActiveRaid() && entityliving.getType() != EntityType.WITCH;
        });
        this.attackPlayersGoal = new NearestAttackableWitchTargetGoal<>(this, Player.class, 10, true, false, (Predicate) null);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 60, 10.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[]{Raider.class}));
        this.targetSelector.addGoal(2, this.healRaidersGoal);
        this.targetSelector.addGoal(3, this.attackPlayersGoal);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Witch.DATA_USING_ITEM, false);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITCH_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WITCH_DEATH;
    }

    public void setUsingItem(boolean drinking) {
        this.getEntityData().set(Witch.DATA_USING_ITEM, drinking);
    }

    public boolean isDrinkingPotion() {
        return (Boolean) this.getEntityData().get(Witch.DATA_USING_ITEM);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26.0D).add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive()) {
            this.healRaidersGoal.decrementCooldown();
            if (this.healRaidersGoal.getCooldown() <= 0) {
                this.attackPlayersGoal.setCanAttack(true);
            } else {
                this.attackPlayersGoal.setCanAttack(false);
            }

            if (this.isDrinkingPotion()) {
                if (this.usingTime-- <= 0) {
                    this.setUsingItem(false);
                    ItemStack itemstack = this.getMainHandItem();

                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    PotionContents potioncontents = (PotionContents) itemstack.get(DataComponents.POTION_CONTENTS);
                    // Paper start - WitchConsumePotionEvent
                    if (itemstack.is(Items.POTION)) {
                        com.destroystokyo.paper.event.entity.WitchConsumePotionEvent event = new com.destroystokyo.paper.event.entity.WitchConsumePotionEvent((org.bukkit.entity.Witch) this.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack));
                        potioncontents = event.callEvent() ? org.bukkit.craftbukkit.inventory.CraftItemStack.unwrap(event.getPotion()).get(DataComponents.POTION_CONTENTS) : null;
                    }
                    // Paper end - WitchConsumePotionEvent

                    if (itemstack.is(Items.POTION) && potioncontents != null) {
                        potioncontents.forEachEffect((effect) -> this.addEffect(effect, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK)); // CraftBukkit
                    }

                    this.gameEvent(GameEvent.DRINK);
                    this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(Witch.SPEED_MODIFIER_DRINKING.id());
                }
            } else {
                Holder<Potion> holder = null;

                if (this.random.nextFloat() < 0.15F && this.isEyeInFluid(FluidTags.WATER) && !this.hasEffect(MobEffects.WATER_BREATHING)) {
                    holder = Potions.WATER_BREATHING;
                } else if (this.random.nextFloat() < 0.15F && (this.isOnFire() || this.getLastDamageSource() != null && this.getLastDamageSource().is(DamageTypeTags.IS_FIRE)) && !this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    holder = Potions.FIRE_RESISTANCE;
                } else if (this.random.nextFloat() < 0.05F && this.getHealth() < this.getMaxHealth()) {
                    holder = Potions.HEALING;
                } else if (this.random.nextFloat() < 0.5F && this.getTarget() != null && !this.hasEffect(MobEffects.MOVEMENT_SPEED) && this.getTarget().distanceToSqr((Entity) this) > 121.0D) {
                    holder = Potions.SWIFTNESS;
                }

                if (holder != null) {
                    this.setDrinkingPotion(PotionContents.createItemStack(Items.POTION, holder)); // Paper - logic moved into setDrinkingPotion, copy exact impl into the method and then comment out
                }
            }

            if (this.random.nextFloat() < 7.5E-4F) {
                this.level().broadcastEntityEvent(this, (byte) 15);
            }
        }

        super.aiStep();
    }

    // Paper start - moved to its own method
    public void setDrinkingPotion(ItemStack potion) {
        potion = org.bukkit.craftbukkit.event.CraftEventFactory.handleWitchReadyPotionEvent(this, potion);
        this.setItemSlot(EquipmentSlot.MAINHAND, potion);
        this.usingTime = this.getMainHandItem().getUseDuration();
        this.setUsingItem(true);
        if (!this.isSilent()) {
            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_DRINK, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
        }

        AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

        attributemodifiable.removeModifier(Witch.SPEED_MODIFIER_DRINKING.id());
        attributemodifiable.addTransientModifier(Witch.SPEED_MODIFIER_DRINKING);
    }
    // Paper end

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.WITCH_CELEBRATE;
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 15) {
            for (int i = 0; i < this.random.nextInt(35) + 10; ++i) {
                this.level().addParticle(ParticleTypes.WITCH, this.getX() + this.random.nextGaussian() * 0.12999999523162842D, this.getBoundingBox().maxY + 0.5D + this.random.nextGaussian() * 0.12999999523162842D, this.getZ() + this.random.nextGaussian() * 0.12999999523162842D, 0.0D, 0.0D, 0.0D);
            }
        } else {
            super.handleEntityEvent(status);
        }

    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        amount = super.getDamageAfterMagicAbsorb(source, amount);
        if (source.getEntity() == this) {
            amount = 0.0F;
        }

        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            amount *= 0.15F;
        }

        return amount;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        if (!this.isDrinkingPotion()) {
            Vec3 vec3d = target.getDeltaMovement();
            double d0 = target.getX() + vec3d.x - this.getX();
            double d1 = target.getEyeY() - 1.100000023841858D - this.getY();
            double d2 = target.getZ() + vec3d.z - this.getZ();
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);
            Holder<Potion> holder = Potions.HARMING;

            if (target instanceof Raider) {
                if (target.getHealth() <= 4.0F) {
                    holder = Potions.HEALING;
                } else {
                    holder = Potions.REGENERATION;
                }

                this.setTarget((LivingEntity) null);
            } else if (d3 >= 8.0D && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                holder = Potions.SLOWNESS;
            } else if (target.getHealth() >= 8.0F && !target.hasEffect(MobEffects.POISON)) {
                holder = Potions.POISON;
            } else if (d3 <= 3.0D && !target.hasEffect(MobEffects.WEAKNESS) && this.random.nextFloat() < 0.25F) {
                holder = Potions.WEAKNESS;
            }

            // Paper start - WitchThrowPotionEvent
            ItemStack potion = PotionContents.createItemStack(Items.SPLASH_POTION, holder);
            com.destroystokyo.paper.event.entity.WitchThrowPotionEvent event = new com.destroystokyo.paper.event.entity.WitchThrowPotionEvent((org.bukkit.entity.Witch) this.getBukkitEntity(), (org.bukkit.entity.LivingEntity) target.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(potion));
            if (!event.callEvent()) {
                return;
            }
            potion = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getPotion());
            ThrownPotion entitypotion = new ThrownPotion(this.level(), this);
            entitypotion.setItem(potion);
            // Paper end - WitchThrowPotionEvent
            entitypotion.setXRot(entitypotion.getXRot() - -20.0F);
            entitypotion.shoot(d0, d1 + d3 * 0.2D, d2, 0.75F, 8.0F);
            if (!this.isSilent()) {
                this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
            }

            this.level().addFreshEntity(entitypotion);
        }
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {}

    @Override
    public boolean canBeLeader() {
        return false;
    }
}

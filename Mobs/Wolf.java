package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class Wolf extends TamableAnimal implements NeutralMob, VariantHolder<Holder<WolfVariant>> {

    private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Holder<WolfVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.WOLF_VARIANT);
    public static final Predicate<LivingEntity> PREY_SELECTOR = (entityliving) -> {
        EntityType<?> entitytypes = entityliving.getType();

        return entitytypes == EntityType.SHEEP || entitytypes == EntityType.RABBIT || entitytypes == EntityType.FOX;
    };
    private static final float START_HEALTH = 8.0F;
    private static final float TAME_HEALTH = 40.0F;
    private static final float ARMOR_REPAIR_UNIT = 0.125F;
    private float interestedAngle;
    private float interestedAngleO;
    private boolean isWet;
    private boolean isShaking;
    private float shakeAnim;
    private float shakeAnimO;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    @Nullable
    private UUID persistentAngerTarget;

    public Wolf(EntityType<? extends Wolf> type, Level world) {
        super(type, world);
        this.setTame(false, false);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new Wolf.WolfPanicGoal(this, 1.5D));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new Wolf.WolfAvoidEntityGoal<>(this, Llama.class, 24.0F, 1.5D, 1.5D));
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(7, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new BegGoal(this, 8.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, Wolf.PREY_SELECTOR));
        this.targetSelector.addGoal(6, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
        this.targetSelector.addGoal(7, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public ResourceLocation getTexture() {
        WolfVariant wolfvariant = (WolfVariant) this.getVariant().value();

        return this.isTame() ? wolfvariant.tameTexture() : (this.isAngry() ? wolfvariant.angryTexture() : wolfvariant.wildTexture());
    }

    @Override
    public Holder<WolfVariant> getVariant() {
        return (Holder) this.entityData.get(Wolf.DATA_VARIANT_ID);
    }

    public void setVariant(Holder<WolfVariant> holder) {
        this.entityData.set(Wolf.DATA_VARIANT_ID, holder);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.30000001192092896D).add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Wolf.DATA_VARIANT_ID, this.registryAccess().registryOrThrow(Registries.WOLF_VARIANT).getHolderOrThrow(WolfVariants.PALE));
        builder.define(Wolf.DATA_INTERESTED_ID, false);
        builder.define(Wolf.DATA_COLLAR_COLOR, DyeColor.RED.getId());
        builder.define(Wolf.DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putByte("CollarColor", (byte) this.getCollarColor().getId());
        nbt.putString("variant", ((ResourceKey) this.getVariant().unwrapKey().orElse(WolfVariants.PALE)).location().toString());
        this.addPersistentAngerSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        Optional.ofNullable(ResourceLocation.tryParse(nbt.getString("variant"))).map((minecraftkey) -> {
            return ResourceKey.create(Registries.WOLF_VARIANT, minecraftkey);
        }).flatMap((resourcekey) -> {
            return this.registryAccess().registryOrThrow(Registries.WOLF_VARIANT).getHolder(resourcekey);
        }).ifPresent(this::setVariant);
        if (nbt.contains("CollarColor", 99)) {
            this.setCollarColor(DyeColor.byId(nbt.getInt("CollarColor")));
        }

        this.readPersistentAngerSaveData(this.level(), nbt);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        Holder<Biome> holder = world.getBiome(this.blockPosition());
        Holder holder1;

        if (entityData instanceof Wolf.WolfPackData entitywolf_b) {
            holder1 = entitywolf_b.type;
        } else {
            holder1 = WolfVariants.getSpawnVariant(this.registryAccess(), holder);
            entityData = new Wolf.WolfPackData(holder1);
        }

        this.setVariant(holder1);
        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAngry() ? SoundEvents.WOLF_GROWL : (this.random.nextInt(3) == 0 ? (this.isTame() && this.getHealth() < 20.0F ? SoundEvents.WOLF_WHINE : SoundEvents.WOLF_PANT) : SoundEvents.WOLF_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.canArmorAbsorb(source) ? SoundEvents.WOLF_ARMOR_DAMAGE : SoundEvents.WOLF_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
            this.level().broadcastEntityEvent(this, (byte) 8);
        }

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.interestedAngleO = this.interestedAngle;
            if (this.isInterested()) {
                this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
            } else {
                this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
            }

            if (this.isInWaterRainOrBubble()) {
                this.isWet = true;
                if (this.isShaking && !this.level().isClientSide) {
                    this.level().broadcastEntityEvent(this, (byte) 56);
                    this.cancelShake();
                }
            } else if ((this.isWet || this.isShaking) && this.isShaking) {
                if (this.shakeAnim == 0.0F) {
                    this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_ACTION);
                }

                this.shakeAnimO = this.shakeAnim;
                this.shakeAnim += 0.05F;
                if (this.shakeAnimO >= 2.0F) {
                    this.isWet = false;
                    this.isShaking = false;
                    this.shakeAnimO = 0.0F;
                    this.shakeAnim = 0.0F;
                }

                if (this.shakeAnim > 0.4F) {
                    float f = (float) this.getY();
                    int i = (int) (Mth.sin((this.shakeAnim - 0.4F) * 3.1415927F) * 7.0F);
                    Vec3 vec3d = this.getDeltaMovement();

                    for (int j = 0; j < i; ++j) {
                        float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;

                        this.level().addParticle(ParticleTypes.SPLASH, this.getX() + (double) f1, (double) (f + 0.8F), this.getZ() + (double) f2, vec3d.x, vec3d.y, vec3d.z);
                    }
                }
            }

        }
    }

    private void cancelShake() {
        this.isShaking = false;
        this.shakeAnim = 0.0F;
        this.shakeAnimO = 0.0F;
    }

    @Override
    public void die(DamageSource damageSource) {
        this.isWet = false;
        this.isShaking = false;
        this.shakeAnimO = 0.0F;
        this.shakeAnim = 0.0F;
        super.die(damageSource);
    }

    public boolean isWet() {
        return this.isWet;
    }

    public float getWetShade(float tickDelta) {
        return Math.min(0.75F + Mth.lerp(tickDelta, this.shakeAnimO, this.shakeAnim) / 2.0F * 0.25F, 1.0F);
    }

    public float getBodyRollAngle(float tickDelta, float f1) {
        float f2 = (Mth.lerp(tickDelta, this.shakeAnimO, this.shakeAnim) + f1) / 1.8F;

        if (f2 < 0.0F) {
            f2 = 0.0F;
        } else if (f2 > 1.0F) {
            f2 = 1.0F;
        }

        return Mth.sin(f2 * 3.1415927F) * Mth.sin(f2 * 3.1415927F * 11.0F) * 0.15F * 3.1415927F;
    }

    public float getHeadRollAngle(float tickDelta) {
        return Mth.lerp(tickDelta, this.interestedAngleO, this.interestedAngle) * 0.15F * 3.1415927F;
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            // CraftBukkit start
            boolean result = super.hurt(source, amount);
            if (!this.level().isClientSide && result) {
                // CraftBukkit end
                this.setOrderedToSit(false);
            }

            return result; // CraftBukkit
        }
    }

    @Override
    public boolean actuallyHurt(DamageSource damagesource, float f) { // CraftBukkit - void -> boolean
        if (!this.canArmorAbsorb(damagesource)) {
            return super.actuallyHurt(damagesource, f); // CraftBukkit
        } else {
            ItemStack itemstack = this.getBodyArmorItem();
            int i = itemstack.getDamageValue();
            int j = itemstack.getMaxDamage();

            itemstack.hurtAndBreak(Mth.ceil(f), this, EquipmentSlot.BODY);
            if (Crackiness.WOLF_ARMOR.byDamage(i, j) != Crackiness.WOLF_ARMOR.byDamage(this.getBodyArmorItem())) {
                this.playSound(SoundEvents.WOLF_ARMOR_CRACK);
                Level world = this.level();

                if (world instanceof ServerLevel) {
                    ServerLevel worldserver = (ServerLevel) world;

                    worldserver.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.ARMADILLO_SCUTE.getDefaultInstance()), this.getX(), this.getY() + 1.0D, this.getZ(), 20, 0.2D, 0.1D, 0.2D, 0.1D);
                }
            }

        }
        return false; // CraftBukkit
    }

    private boolean canArmorAbsorb(DamageSource source) {
        return this.hasArmor() && !source.is(DamageTypeTags.BYPASSES_WOLF_ARMOR);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean flag = target.hurt(this.damageSources().mobAttack(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));

        if (flag) {
            this.doEnchantDamageEffects(this, target);
        }

        return flag;
    }

    @Override
    protected void applyTamingSideEffects() {
        if (this.isTame()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
            this.setHealth(this.getMaxHealth()); // CraftBukkit - 40.0 -> getMaxHealth()
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0D);
        }

    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, new EquipmentSlot[]{EquipmentSlot.BODY});
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (this.level().isClientSide && (!this.isBaby() || !this.isFood(itemstack))) {
            boolean flag = this.isOwnedBy(player) || this.isTame() || itemstack.is(Items.BONE) && !this.isTame() && !this.isAngry();

            return flag ? InteractionResult.CONSUME : InteractionResult.PASS;
        } else if (this.isTame()) {
            if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                itemstack.consume(1, player);
                FoodProperties foodinfo = (FoodProperties) itemstack.get(DataComponents.FOOD);
                float f = foodinfo != null ? (float) foodinfo.nutrition() : 1.0F;

                this.heal(2.0F * f, EntityRegainHealthEvent.RegainReason.EATING); // CraftBukkit
                return InteractionResult.sidedSuccess(this.level().isClientSide());
            } else {
                if (item instanceof DyeItem) {
                    DyeItem itemdye = (DyeItem) item;

                    if (this.isOwnedBy(player)) {
                        DyeColor enumcolor = itemdye.getDyeColor();

                        if (enumcolor != this.getCollarColor()) {
                            // Paper start - Add EntityDyeEvent and CollarColorable interface
                            final io.papermc.paper.event.entity.EntityDyeEvent event = new io.papermc.paper.event.entity.EntityDyeEvent(this.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData((byte) enumcolor.getId()), ((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity());
                            if (!event.callEvent()) {
                                return InteractionResult.FAIL;
                            }
                            enumcolor = DyeColor.byId(event.getColor().getWoolData());
                            // Paper end - Add EntityDyeEvent and CollarColorable interface

                            this.setCollarColor(enumcolor);
                            itemstack.consume(1, player);
                            return InteractionResult.SUCCESS;
                        }

                        return super.mobInteract(player, hand);
                    }
                }

                if (itemstack.is(Items.WOLF_ARMOR) && this.isOwnedBy(player) && !this.hasArmor() && !this.isBaby()) {
                    this.setBodyArmorItem(itemstack.copyWithCount(1));
                    itemstack.consume(1, player);
                    return InteractionResult.SUCCESS;
                } else {
                    ItemStack itemstack1;

                    if (itemstack.is(Items.SHEARS) && this.isOwnedBy(player) && this.hasArmor() && (!EnchantmentHelper.hasBindingCurse(this.getBodyArmorItem()) || player.isCreative())) {
                        itemstack.hurtAndBreak(1, player, getSlotForHand(hand));
                        this.playSound(SoundEvents.ARMOR_UNEQUIP_WOLF);
                        itemstack1 = this.getBodyArmorItem();
                        this.setBodyArmorItem(ItemStack.EMPTY);
                        this.forceDrops = true; // Paper - add missing forceDrops toggles
                        this.spawnAtLocation(itemstack1);
                        this.forceDrops = false; // Paper - add missing forceDrops toggles
                        return InteractionResult.SUCCESS;
                    } else if (((Ingredient) ((ArmorMaterial) ArmorMaterials.ARMADILLO.value()).repairIngredient().get()).test(itemstack) && this.isInSittingPose() && this.hasArmor() && this.isOwnedBy(player) && this.getBodyArmorItem().isDamaged()) {
                        itemstack.shrink(1);
                        this.playSound(SoundEvents.WOLF_ARMOR_REPAIR);
                        itemstack1 = this.getBodyArmorItem();
                        int i = (int) ((float) itemstack1.getMaxDamage() * 0.125F);

                        itemstack1.setDamageValue(Math.max(0, itemstack1.getDamageValue() - i));
                        return InteractionResult.SUCCESS;
                    } else {
                        InteractionResult enuminteractionresult = super.mobInteract(player, hand);

                        if (!enuminteractionresult.consumesAction() && this.isOwnedBy(player)) {
                            this.setOrderedToSit(!this.isOrderedToSit());
                            this.jumping = false;
                            this.navigation.stop();
                            this.setTarget((LivingEntity) null, EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit - reason
                            return InteractionResult.SUCCESS_NO_ITEM_USED;
                        } else {
                            return enuminteractionresult;
                        }
                    }
                }
            }
        } else if (itemstack.is(Items.BONE) && !this.isAngry()) {
            itemstack.consume(1, player);
            this.tryToTame(player);
            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void tryToTame(Player player) {
        // CraftBukkit - added event call and isCancelled check.
        if (this.random.nextInt(3) == 0 && !CraftEventFactory.callEntityTameEvent(this, player).isCancelled()) {
            this.tame(player);
            this.navigation.stop();
            this.setTarget((LivingEntity) null);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 8) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
        } else if (status == 56) {
            this.cancelShake();
        } else {
            super.handleEntityEvent(status);
        }

    }

    public float getTailAngle() {
        if (this.isAngry()) {
            return 1.5393804F;
        } else if (this.isTame()) {
            float f = this.getMaxHealth();
            float f1 = (f - this.getHealth()) / f;

            return (0.55F - f1 * 0.4F) * 3.1415927F;
        } else {
            return 0.62831855F;
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.WOLF_FOOD);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 8;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return (Integer) this.entityData.get(Wolf.DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int angerTime) {
        this.entityData.set(Wolf.DATA_REMAINING_ANGER_TIME, angerTime);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(Wolf.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID angryAt) {
        this.persistentAngerTarget = angryAt;
    }

    public DyeColor getCollarColor() {
        return DyeColor.byId((Integer) this.entityData.get(Wolf.DATA_COLLAR_COLOR));
    }

    public boolean hasArmor() {
        return !this.getBodyArmorItem().isEmpty();
    }

    public void setCollarColor(DyeColor color) {
        this.entityData.set(Wolf.DATA_COLLAR_COLOR, color.getId());
    }

    @Nullable
    @Override
    public Wolf getBreedOffspring(ServerLevel world, AgeableMob entity) {
        Wolf entitywolf = (Wolf) EntityType.WOLF.create(world);

        if (entitywolf != null && entity instanceof Wolf entitywolf1) {
            if (this.random.nextBoolean()) {
                entitywolf.setVariant(this.getVariant());
            } else {
                entitywolf.setVariant(entitywolf1.getVariant());
            }

            if (this.isTame()) {
                entitywolf.setOwnerUUID(this.getOwnerUUID());
                entitywolf.setTame(true, true);
                if (this.random.nextBoolean()) {
                    entitywolf.setCollarColor(this.getCollarColor());
                } else {
                    entitywolf.setCollarColor(entitywolf1.getCollarColor());
                }
            }
        }

        return entitywolf;
    }

    public void setIsInterested(boolean begging) {
        this.entityData.set(Wolf.DATA_INTERESTED_ID, begging);
    }

    @Override
    public boolean canMate(Animal other) {
        if (other == this) {
            return false;
        } else if (!this.isTame()) {
            return false;
        } else if (!(other instanceof Wolf)) {
            return false;
        } else {
            Wolf entitywolf = (Wolf) other;

            return !entitywolf.isTame() ? false : (entitywolf.isInSittingPose() ? false : this.isInLove() && entitywolf.isInLove());
        }
    }

    public boolean isInterested() {
        return (Boolean) this.entityData.get(Wolf.DATA_INTERESTED_ID);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (!(target instanceof Creeper) && !(target instanceof Ghast) && !(target instanceof ArmorStand)) {
            if (target instanceof Wolf) {
                Wolf entitywolf = (Wolf) target;

                return !entitywolf.isTame() || entitywolf.getOwner() != owner;
            } else {
                if (target instanceof Player) {
                    Player entityhuman = (Player) target;

                    if (owner instanceof Player) {
                        Player entityhuman1 = (Player) owner;

                        if (!entityhuman1.canHarmPlayer(entityhuman)) {
                            return false;
                        }
                    }
                }

                if (target instanceof AbstractHorse) {
                    AbstractHorse entityhorseabstract = (AbstractHorse) target;

                    if (entityhorseabstract.isTamed()) {
                        return false;
                    }
                }

                boolean flag;

                if (target instanceof TamableAnimal) {
                    TamableAnimal entitytameableanimal = (TamableAnimal) target;

                    if (entitytameableanimal.isTame()) {
                        flag = false;
                        return flag;
                    }
                }

                flag = true;
                return flag;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return !this.isAngry() && super.canBeLeashed(player);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.6F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static boolean checkWolfSpawnRules(EntityType<Wolf> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getBlockState(pos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(world, pos);
    }

    private class WolfPanicGoal extends PanicGoal {

        public WolfPanicGoal(final Wolf speed, final double wolf) {
            super(speed, wolf);
        }

        @Override
        protected boolean shouldPanic() {
            return this.mob.isFreezing() || this.mob.isOnFire();
        }
    }

    private class WolfAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Wolf wolf;

        public WolfAvoidEntityGoal(final Wolf entitywolf, final Class oclass, final float f, final double d0, final double d1) {
            super(entitywolf, oclass, f, d0, d1);
            this.wolf = entitywolf;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.toAvoid instanceof Llama ? !this.wolf.isTame() && this.avoidLlama((Llama) this.toAvoid) : false;
        }

        private boolean avoidLlama(Llama llama) {
            return llama.getStrength() >= Wolf.this.random.nextInt(5);
        }

        @Override
        public void start() {
            Wolf.this.setTarget((LivingEntity) null);
            super.start();
        }

        @Override
        public void tick() {
            Wolf.this.setTarget((LivingEntity) null);
            super.tick();
        }
    }

    public static class WolfPackData extends AgeableMob.AgeableMobGroupData {

        public final Holder<WolfVariant> type;

        public WolfPackData(Holder<WolfVariant> variant) {
            super(false);
            this.type = variant;
        }
    }
}

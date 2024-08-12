package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class Pillager extends AbstractIllager implements CrossbowAttackMob, InventoryCarrier {

    private static final EntityDataAccessor<Boolean> IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Pillager.class, EntityDataSerializers.BOOLEAN);
    private static final int INVENTORY_SIZE = 5;
    private static final int SLOT_OFFSET = 300;
    public final SimpleContainer inventory = new SimpleContainer(5);

    public Pillager(EntityType<? extends Pillager> type, Level world) {
        super(type, world);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new Raider.HoldGroundAttackGoal(this, 10.0F)); // Paper - decomp fix
        this.goalSelector.addGoal(3, new RangedCrossbowAttackGoal<>(this, 1.0D, 8.0F));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 15.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 15.0F));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{Raider.class})).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.3499999940395355D).add(Attributes.MAX_HEALTH, 24.0D).add(Attributes.ATTACK_DAMAGE, 5.0D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Pillager.IS_CHARGING_CROSSBOW, false);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon == Items.CROSSBOW;
    }

    public boolean isChargingCrossbow() {
        return (Boolean) this.entityData.get(Pillager.IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        this.entityData.set(Pillager.IS_CHARGING_CROSSBOW, charging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        this.writeInventoryToTag(nbt, this.registryAccess());
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        return this.isChargingCrossbow() ? AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE : (this.isHolding(Items.CROSSBOW) ? AbstractIllager.IllagerArmPose.CROSSBOW_HOLD : (this.isAggressive() ? AbstractIllager.IllagerArmPose.ATTACKING : AbstractIllager.IllagerArmPose.NEUTRAL));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.readInventoryFromTag(nbt, this.registryAccess());
        this.setCanPickUpLoot(true);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return 0.0F;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        RandomSource randomsource = world.getRandom();

        this.populateDefaultEquipmentSlots(randomsource, difficulty);
        this.populateDefaultEquipmentEnchantments(randomsource, difficulty);
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance localDifficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
    }

    @Override
    protected void enchantSpawnedWeapon(RandomSource random, float power) {
        super.enchantSpawnedWeapon(random, power);
        if (random.nextInt(300) == 0) {
            ItemStack itemstack = this.getMainHandItem();

            if (itemstack.is(Items.CROSSBOW)) {
                itemstack.enchant(Enchantments.PIERCING, 1);
                this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
            }
        }

    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        this.performCrossbowAttack(this, 1.6F);
    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        ItemStack itemstack = item.getItem();

        if (itemstack.getItem() instanceof BannerItem) {
            super.pickUpItem(item);
        } else if (this.wantsItem(itemstack)) {
            this.onItemPickup(item);
            ItemStack itemstack1 = this.inventory.addItem(itemstack);

            if (itemstack1.isEmpty()) {
                item.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            } else {
                itemstack.setCount(itemstack1.getCount());
            }
        }

    }

    private boolean wantsItem(ItemStack stack) {
        return this.hasActiveRaid() && stack.is(Items.WHITE_BANNER);
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        int j = mappedIndex - 300;

        return j >= 0 && j < this.inventory.getContainerSize() ? SlotAccess.forContainer(this.inventory, j) : super.getSlot(mappedIndex);
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {
        Raid raid = this.getCurrentRaid();
        boolean flag1 = this.random.nextFloat() <= raid.getEnchantOdds();

        if (flag1) {
            ItemStack itemstack = new ItemStack(Items.CROSSBOW);

            if (wave > raid.getNumGroups(Difficulty.NORMAL)) {
                itemstack.enchant(Enchantments.QUICK_CHARGE, 2);
            } else if (wave > raid.getNumGroups(Difficulty.EASY)) {
                itemstack.enchant(Enchantments.QUICK_CHARGE, 1);
            }

            itemstack.enchant(Enchantments.MULTISHOT, 1);
            this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
        }

    }

    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.PILLAGER_CELEBRATE;
    }
}

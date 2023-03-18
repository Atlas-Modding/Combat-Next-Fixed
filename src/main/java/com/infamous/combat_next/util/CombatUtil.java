package com.infamous.combat_next.util;

import com.infamous.combat_next.CombatNext;
import com.infamous.combat_next.client.ClientCombatUtil;
import com.infamous.combat_next.mixin.ItemAccessor;
import com.infamous.combat_next.mixin.LivingEntityAccessor;
import com.infamous.combat_next.mixin.ThrownTridentAccessor;
import com.infamous.combat_next.network.CNNetwork;
import com.infamous.combat_next.network.ServerboundMissPacket;
import com.infamous.combat_next.registry.EnchantmentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class CombatUtil {
    private static final UUID BONUS_REACH_MODIFIER_UUID = UUID.fromString("30a9271c-d6b2-4651-b088-800acc43f282");

    private static final String DAMAGE_BOOST_MODIFIER_UUID = "648D7064-6A60-4F59-8ABE-C2C23A6DD7A9";
    private static final double DAMAGE_BOOST_MODIFIER_VALUE = 0.2D;
    private static final int DEFAULT_SHIELD_STUN_TICKS = 32;
    private static final int CLEAVING_TICKS_PER_LEVEL = 10;
    public static final double SHIELD_ARC = -5.0D / 18.0D; // -5/18 * pi = -50 degrees
    public static final int BASE_HEAL_AMOUNT = 6;
    private static final int SNOWBALL_MAX_STACK_SIZE = 64;
    private static final int POTION_MAX_STACK_SIZE = 16;
    public static final int DRINK_USE_DURATION = 20;
    public static final int FOOD_LEVEL_FOR_FOOD_HEALING = 7;
    public static final int TICKS_BEFORE_FOOD_HEALING = 40;
    public static final int HEALING_FOOD_LEVEL_DECREASE_TIME = 2;
    private static final double MIN_HITBOX_SIZE_FOR_ATTACK = 0.9D;
    public static final int OPTIMAL_CHARGE_TIME = 60;
    public static final float NEW_ARROW_INACCURACY = 0.25F;
    private static final int MISSED_ATTACK_COOLDOWN = 4;
    public static final double INSTANT_ARROW_EFFECT_MULTIPLIER = 0.375D;
    public static final float MAX_SHIELD_BLOCKED_DAMAGE = 5.0F;
    public static final int THROWABLE_ITEM_COOLDOWN = 4;
    public static final float SHIELD_KNOCKBACK_SCALE = 0.5F;
    public static final float SUPERCHARGED_MAX_ATTACK_STRENGTH = 2.0F;
    private static final String BONUS_REACH_MODIFIER_NAME = new ResourceLocation(CombatNext.MODID, "bonus_reach").toString();
    private static final double BONUS_REACH = 1.0D;
    public static final float SWEEPING_DAMAGE_SCALE = 0.5F;

    public static void registerTridentDispenseBehavior(){
        DispenserBlock.registerBehavior(Items.TRIDENT, new AbstractProjectileDispenseBehavior() {
            protected Projectile getProjectile(Level level, Position position, ItemStack stack) {
                ThrownTrident thrownTrident = new ThrownTrident(EntityType.TRIDENT, level);
                ((ThrownTridentAccessor)thrownTrident).setTridentItem(stack.copy());
                thrownTrident.getEntityData().set(ThrownTridentAccessor.getID_LOYALTY(), (byte) EnchantmentHelper.getLoyalty(stack));
                thrownTrident.getEntityData().set(ThrownTridentAccessor.getID_FOIL(), stack.hasFoil());
                thrownTrident.setPos(position.x(), position.y(), position.z());
                thrownTrident.pickup = AbstractArrow.Pickup.ALLOWED;
                return thrownTrident;
            }
        });
        CombatNext.LOGGER.info("Registered DispenseItemBehavior for Item {}", "minecraft:trident");
    }

    public static void modifyStrengthEffect(){
        MobEffects.DAMAGE_BOOST.addAttributeModifier(Attributes.ATTACK_DAMAGE, DAMAGE_BOOST_MODIFIER_UUID, DAMAGE_BOOST_MODIFIER_VALUE, AttributeModifier.Operation.MULTIPLY_TOTAL);
        CombatNext.LOGGER.info("Modified MobEffect {} to have an AttributeModifier for Attribute {} with UUID {}, value of {}, and Operation of {}",
                "minecraft:strength", Attributes.ATTACK_DAMAGE, DAMAGE_BOOST_MODIFIER_UUID, DAMAGE_BOOST_MODIFIER_VALUE,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    public static void modifyItemMaxStackSizes(){
        ((ItemAccessor)Items.SNOWBALL).setMaxStackSize(SNOWBALL_MAX_STACK_SIZE);
        CombatNext.LOGGER.info("Changed max stack size of {} to {}", "minecraft:snowball", SNOWBALL_MAX_STACK_SIZE);
        ((ItemAccessor)Items.POTION).setMaxStackSize(POTION_MAX_STACK_SIZE);
        CombatNext.LOGGER.info("Changed max stack size of {} to {}", "minecraft:potion", POTION_MAX_STACK_SIZE);
    }

    public static boolean canInterruptConsumption(DamageSource source){
        return source.getDirectEntity() instanceof LivingEntity || (source.isProjectile() && source.getEntity() instanceof LivingEntity);
    }

    public static float recalculateEnchantmentDamage(ItemStack stack, float amount, Entity target){
        int impalingLevel = stack.getEnchantmentLevel(Enchantments.IMPALING);
        if(impalingLevel > 0){
            if(target instanceof LivingEntity victim){
                float originalBonus = Enchantments.IMPALING.getDamageBonus(impalingLevel, victim.getMobType(), stack);
                amount -= originalBonus;
            }
            amount += getImpalingDamageBonus(impalingLevel, target);
        }
        return amount;
    }

    public static float getImpalingDamageBonus(int level, Entity target){
        return target.isInWaterOrRain() ? (float)level * 2.5F : 0.0F;
    }

    public static void newDisableShield(Player victim, LivingEntity attacker){
        int cleavingLevel = attacker.getMainHandItem().getEnchantmentLevel(EnchantmentRegistry.CLEAVING.get());
        int cleavingTicks = CLEAVING_TICKS_PER_LEVEL * cleavingLevel;
        victim.getCooldowns().addCooldown(victim.getUseItem().getItem(), DEFAULT_SHIELD_STUN_TICKS + cleavingTicks);
        victim.stopUsingItem();
        victim.level.broadcastEntityEvent(victim, (byte)30);
    }

    public static void attackEmpty(Player player) {
        if(player.level.isClientSide){
            ClientCombatUtil.ensureHasSentCarriedItem();
            CNNetwork.INSTANCE.sendToServer(ServerboundMissPacket.createMissPacket());
        }
        if (!player.isSpectator()) {
            sweepAttack(player);
            resetAttackStrengthTicker(player, true);
        }
    }
    
    public static void resetAttackStrengthTicker(Player player, boolean miss){
        if(miss){
            LivingEntityAccessor accessor = (LivingEntityAccessor) player;
            accessor.setAttackStrengthTicker((int) (player.getCurrentItemAttackStrengthDelay() - MISSED_ATTACK_COOLDOWN));
        } else{
            player.resetAttackStrengthTicker();
        }
    }

    private static void sweepAttack(Player player){
        float attackDamage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damageBonus = EnchantmentHelper.getDamageBonus(player.getMainHandItem(), MobType.UNDEFINED);

        float attackStrengthScale = player.getAttackStrengthScale(0.5F);
        attackDamage *= 0.2F + attackStrengthScale * attackStrengthScale * 0.8F;
        damageBonus *= attackStrengthScale;
        if (attackDamage > 0.0F || damageBonus > 0.0F) {
            boolean fullStrength = attackStrengthScale > 0.9F;
            boolean sprintAttack = player.isSprinting() && fullStrength;
            boolean critAttack = fullStrength && player.fallDistance > 0.0F && !player.isOnGround() && !player.onClimbable() && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger();
            critAttack = critAttack && !player.isSprinting();

            attackDamage += damageBonus;

            boolean sweepAttack = false;
            double walkDistD = player.walkDist - player.walkDistO;
            if (fullStrength && !critAttack && !sprintAttack && player.isOnGround() && walkDistD < (double)player.getSpeed()) {
                ItemStack itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
                sweepAttack = itemInHand.getEnchantmentLevel(Enchantments.SWEEPING_EDGE) > 0;
            }

            if(sweepAttack){
                float sweepDamage = 1.0F + EnchantmentHelper.getSweepingDamageRatio(player) * attackDamage;

                for(LivingEntity sweepTarget : player.level.getEntitiesOfClass(LivingEntity.class, getSweepHitBox(player, player.getItemInHand(InteractionHand.MAIN_HAND)))) {
                    if (sweepTarget != player && !player.isAlliedTo(sweepTarget) && (!(sweepTarget instanceof ArmorStand armorStand) || !armorStand.isMarker()) && player.canHit(sweepTarget, 0)) { // Original check was dist < 3, range is 3, so vanilla used padding=0
                        sweepTarget.knockback(0.4D, Mth.sin(player.getYRot() * ((float)Math.PI / 180F)), -Mth.cos(player.getYRot() * ((float)Math.PI / 180F)));
                        sweepTarget.hurt(DamageSource.playerAttack(player), sweepDamage);
                    }
                }

                player.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0F, 1.0F);
                player.sweepAttack();
            }
        }
    }

    private static AABB getSweepHitBox(Player player, ItemStack weapon) {
        double xShift = (-Mth.sin(player.yBodyRot * ((float)Math.PI / 180F))) * 2.0D;
        double zShift = Mth.cos(player.yBodyRot * ((float)Math.PI / 180F)) * 2.0D;
        // we use the player as the "target", so weapons with custom sweep hitboxes work
        return weapon.getSweepHitBox(player, player).move(xShift, 0.0, zShift);
    }

    public static boolean onAttackCooldown(Player player, float partialTick) {
        return player.getAttackStrengthScale(partialTick) < 1.0F;
    }

    public static AABB adjustBBForRayTrace(AABB boundingBox) {
        if(boundingBox.getSize() < MIN_HITBOX_SIZE_FOR_ATTACK){
            double xAdjust = adjustSize(boundingBox.getXsize());
            double yAdjust = adjustSize(boundingBox.getYsize());
            double zAdjust = adjustSize(boundingBox.getZsize());
            boundingBox = boundingBox.inflate(xAdjust, yAdjust, zAdjust);
        }
        return boundingBox;
    }

    private static double adjustSize(double size) {
        return size < MIN_HITBOX_SIZE_FOR_ATTACK ? (MIN_HITBOX_SIZE_FOR_ATTACK - size) / 2.0D : 0.0D;
    }

    public static boolean hitThroughBlock(Level level, BlockPos blockPos, Player player, Predicate<Player> hitEntity){
        BlockState clickedBlock = level.getBlockState(blockPos);
        if (!clickedBlock.getCollisionShape(level, blockPos).isEmpty() || clickedBlock.getDestroySpeed(level, blockPos) != 0.0F) {
            return false;
        }
        return hitEntity.test(player);
    }

    public static boolean hitEntity(Player player){
        return getEntityHit(player).isPresent();
    }

    public static Optional<EntityHitResult> getEntityHit(Player player) {
        double blockReach = player.getReachDistance();
        Vec3 from = player.getEyePosition(1.0F);
        double reach;
        double entityReach = player.getAttackRange();
        blockReach = reach = Math.max(blockReach, entityReach); // Pick entities with the max of the reach distance and attack range.

        double reachSqr = Mth.square(reach);

        Vec3 viewVector = player.getViewVector(1.0F);
        Vec3 to = from.add(viewVector.x * blockReach, viewVector.y * blockReach, viewVector.z * blockReach);
        AABB searchBox = player.getBoundingBox().expandTowards(viewVector.scale(blockReach)).inflate(1.0D, 1.0D, 1.0D);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(player, from, to, searchBox, (e) -> !e.isSpectator() && e.isPickable(), reachSqr);
        if (entityHitResult != null) {
            return Optional.of(entityHitResult).filter(ehr -> {
                Vec3 hitLocation = ehr.getLocation();
                double distanceToSqr = from.distanceToSqr(hitLocation);
                return distanceToSqr <= Mth.square(entityReach) && distanceToSqr <= reachSqr;
            });
        }
        return Optional.empty();
    }

    public static boolean isSprintCritical(Player player, Entity target) {
        boolean fullStrength = player.getAttackStrengthScale(0.5F) > 0.9F;
        boolean canSprintCrit = fullStrength
                && player.fallDistance <= 0.0F // can't sprint and fall
                && player.isOnGround() // can only sprint on ground
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && target instanceof LivingEntity;
        canSprintCrit = canSprintCrit && player.isSprinting();
        return canSprintCrit;
    }

    public static void handleBonusReach(Player player, boolean add) {
        AttributeInstance entityReachInstance = player.getAttribute(ForgeMod.ATTACK_RANGE.get());
        if (entityReachInstance != null) {
            AttributeModifier bonusReachModifier = entityReachInstance.getModifier(BONUS_REACH_MODIFIER_UUID);
            if(bonusReachModifier != null){
                if(!add) {
                    entityReachInstance.removeModifier(BONUS_REACH_MODIFIER_UUID);
                }
            } else if(add){
                entityReachInstance.addTransientModifier(
                        new AttributeModifier(BONUS_REACH_MODIFIER_UUID, BONUS_REACH_MODIFIER_NAME, BONUS_REACH, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    public static boolean isSupercharged(Player player, float partialTick) {
        return getSuperchargedAttackStrengthScale(player, partialTick) >= SUPERCHARGED_MAX_ATTACK_STRENGTH;
    }

    private static float getSuperchargedAttackStrengthScale(Player player, float partialTick) {
        return Mth.clamp(((float) ((LivingEntityAccessor) player).getAttackStrengthTicker() + partialTick) / player.getCurrentItemAttackStrengthDelay(), 0.0F, SUPERCHARGED_MAX_ATTACK_STRENGTH);
    }

    public static float scaleEnchantmentDamage(LivingEntity player, float base) {
        AttributeInstance attributeInstance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        float result = base;

        if(attributeInstance != null){
            for(AttributeModifier mod : attributeInstance.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) {
                result += base * mod.getAmount();
            }

            for(AttributeModifier mod : attributeInstance.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) {
                result *= 1.0D + mod.getAmount();
            }
        }
        return result;
    }
}

package com.infamous.combat_next.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor
    int getAttackStrengthTicker();

    @Accessor
    void setAttackStrengthTicker(int attackStrengthTicker);
}

package com.infamous.combat_next.mixin;

import com.infamous.combat_next.util.CombatUtil;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MobEffect.class)
public class MobEffectMixin {

    @ModifyConstant(method = "applyEffectTick", constant = @Constant(intValue = 4, ordinal = 0))
    private int getTickBaseHeal(int constant){
        return CombatUtil.BASE_HEAL_AMOUNT;
    }

    @ModifyConstant(method = "applyInstantenousEffect", constant = @Constant(intValue = 4, ordinal = 0))
    private int getInstantBaseHeal(int constant){
        return CombatUtil.BASE_HEAL_AMOUNT;
    }
}

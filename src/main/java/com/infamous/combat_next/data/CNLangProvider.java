package com.infamous.combat_next.data;

import com.infamous.combat_next.CombatNext;
import com.infamous.combat_next.registry.EnchantmentRegistry;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.LanguageProvider;

public class CNLangProvider extends LanguageProvider {
    public CNLangProvider(DataGenerator gen) {
        super(gen, CombatNext.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        this.add(EnchantmentRegistry.CLEAVING.get(), "Cleaving");
    }
}

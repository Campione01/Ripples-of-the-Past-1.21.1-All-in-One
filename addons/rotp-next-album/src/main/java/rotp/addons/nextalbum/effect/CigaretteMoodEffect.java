package rotp.addons.nextalbum.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Next Album 0.1.13 Focus (beneficial) and Lax (harmful). Their Stand
 * stat and stamina effects live in CigaretteFocus; milk does not cure
 * them.
 */
public final class CigaretteMoodEffect extends MobEffect {
    public CigaretteMoodEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void fillEffectCures(
            Set<EffectCure> cures,
            MobEffectInstance effectInstance) {
        cures.clear();
    }
}

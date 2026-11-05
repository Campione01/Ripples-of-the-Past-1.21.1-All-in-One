package rotp.addons.novemberrain.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** The remake's cut_eyelid: a harmful marker effect; its user's client blacks out the right half of the screen. */
public class CutEyelidEffect extends MobEffect {
    public static final int COLOR = 7997962;

    public CutEyelidEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}

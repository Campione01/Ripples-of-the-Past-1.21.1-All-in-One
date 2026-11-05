package rotp.addons.beachboy.util;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Next Album's Collapse effect (1.16 InitEffects.COLLAPSE, ported as rotp_nextalbum:collapse in rotp-next-album),
 * which the heart crush gives for 8 s. Without that mod the hit gives what the effect does each tick in 1.16
 * (StaggeredEffectEventHandler): Slowness II and Weakness II.
 */
public final class BeachBoyCollapse {
    public static final ResourceLocation COLLAPSE_ID = ResourceLocation.fromNamespaceAndPath("rotp_nextalbum", "collapse");

    private BeachBoyCollapse() {}

    public static Optional<Holder.Reference<MobEffect>> effect() {
        return BuiltInRegistries.MOB_EFFECT.getHolder(COLLAPSE_ID);
    }

    public static boolean isCollapsed(@Nullable LivingEntity living) {
        return living != null && effect().map(living::hasEffect).orElse(false);
    }

    public static void apply(LivingEntity target, int ticks) {
        Optional<Holder.Reference<MobEffect>> collapse = effect();
        if (collapse.isPresent()) {
            target.addEffect(new MobEffectInstance(collapse.get(), ticks, 0, false, true, true));
        }
        else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false, true));
        }
    }
}

package rotp.addons.nextalbum.state;

import rotp.core.init.ModEntityAttributes;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.NextAlbumIds;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Next Album 0.1.13 CigaretteFocusUtil: Focus raises the user's Stand
 * durability and precision by 10% a level and stamina regeneration by 15%
 * a level; a Focus of at least 30 s turns into Lax (the same amounts the
 * other way, regeneration not below 40%) for as long as the Focus lasted.
 */
public final class CigaretteFocus {
    public static final int MIN_LAX_FOCUS_TICKS = 600;
    public static final int FOCUS_TIER2_TICKS = 2400;
    public static final int FOCUS_TIER3_TICKS = 6000;
    public static final int MAX_FOCUS_TICKS = 12000;
    public static final int INHALE_FOCUS_PER_TICK = 15;
    private static final String FOCUS_APPLIED = "RotpNextAlbumFocusApplied";
    private static final String FOCUS_AMP = "RotpNextAlbumFocusAmp";
    static final ResourceLocation DURABILITY_MODIFIER =
            NextAlbumIds.id("cigarette_stand_durability");
    static final ResourceLocation PRECISION_MODIFIER =
            NextAlbumIds.id("cigarette_stand_precision");

    private CigaretteFocus() {}

    public static void addFocus(LivingEntity entity, int ticks) {
        if (entity == null || ticks <= 0
                || entity.level().isClientSide()) {
            return;
        }
        entity.removeEffect(NextAlbumAddon.LAX);
        MobEffectInstance existing =
                entity.getEffect(NextAlbumAddon.FOCUS);
        int duration = ticks
                + (existing != null ? existing.getDuration() : 0);
        duration = Math.min(duration, MAX_FOCUS_TICKS);
        int amplifier = amplifierForDuration(duration);
        entity.addEffect(new MobEffectInstance(
                NextAlbumAddon.FOCUS, duration, amplifier,
                false, false, true));
        CompoundTag data = entity.getPersistentData();
        data.putInt(FOCUS_APPLIED, duration);
        data.putInt(FOCUS_AMP, amplifier);
        applyStandModifiers(entity);
    }

    // 1.16 ran this from FocusEffect.removeAttributeModifiers; a tick
    // check sees every way a Focus ends without adding an effect while
    // the entity's effect map is being iterated.
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (data.contains(FOCUS_APPLIED)
                && !entity.hasEffect(NextAlbumAddon.FOCUS)) {
            int recorded = data.getInt(FOCUS_APPLIED);
            int amplifier = data.getInt(FOCUS_AMP);
            data.remove(FOCUS_APPLIED);
            data.remove(FOCUS_AMP);
            if (recorded >= MIN_LAX_FOCUS_TICKS) {
                entity.addEffect(new MobEffectInstance(
                        NextAlbumAddon.LAX, recorded, amplifier,
                        false, false, true));
            }
        }
        applyStandModifiers(entity);
    }

    public static float staminaRegenMultiplier(LivingEntity entity) {
        int signed = signedAmplifier(entity);
        if (signed == 0) {
            return 1.0F;
        }
        float step = 0.15F * Math.abs(signed);
        return signed > 0 ? 1.0F + step : Math.max(0.4F, 1.0F - step);
    }

    public static float standStatFactor(LivingEntity entity) {
        int signed = signedAmplifier(entity);
        return signed == 0 ? 0.0F : 0.1F * signed;
    }

    public static int amplifierForDuration(int durationTicks) {
        if (durationTicks >= FOCUS_TIER3_TICKS) {
            return 2;
        }
        return durationTicks >= FOCUS_TIER2_TICKS ? 1 : 0;
    }

    // 1.16 put the modifier on the user and on its Stand entity; the port
    // core copies the user's values to the Stand every tick, so a second
    // modifier there would count twice.
    static void applyStandModifiers(LivingEntity entity) {
        double factor = standStatFactor(entity);
        apply(entity, ModEntityAttributes.STAND_DURABILITY,
                DURABILITY_MODIFIER, factor);
        apply(entity, ModEntityAttributes.STAND_PRECISION,
                PRECISION_MODIFIER, factor);
    }

    private static int signedAmplifier(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        MobEffectInstance focus = entity.getEffect(NextAlbumAddon.FOCUS);
        if (focus != null) {
            return focus.getAmplifier() + 1;
        }
        MobEffectInstance lax = entity.getEffect(NextAlbumAddon.LAX);
        return lax != null ? -(lax.getAmplifier() + 1) : 0;
    }

    private static void apply(
            LivingEntity entity,
            Holder<Attribute> attribute,
            ResourceLocation id,
            double factor) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(factor) < 0.001D) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current == null || current.amount() != factor) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(
                    id, factor,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}

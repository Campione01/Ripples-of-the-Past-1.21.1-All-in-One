package rotp.addons.gratefuldead.service;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Next Album 0.1.13 TheGratefulDeadGrabUtil.evaluateEntityGrab: The
 * Grateful Dead holds only what it overpowers, and aging weakens a target
 * enough to be held. The core's grab takes its target as the Stand reaches
 * out, so a hold that the rule refuses is let go on the Stand's next tick
 * with 0.1.13's message.
 */
public final class GrabStrength {
    // StandStats power of The Grateful Dead (GratefulDeadAddon.createStand)
    public static final float GRAB_POWER = 10.0F;
    public static final float MIN_RESIST = 4.0F;
    // The held entity weakly: through its level it reaches the Stand, the key, which a strong value would never let go.
    private static final Map<StandEntity, WeakReference<LivingEntity>> ALLOWED = new WeakHashMap<>();

    private GrabStrength() {}

    public static void tickStand(StandEntity stand) {
        LivingEntity held = LivingComponentGrab.getEntityGrabbedBy(stand);
        if (held == null) {
            ALLOWED.remove(stand);
            return;
        }
        if (allowed(stand) == held) {
            return;
        }
        LivingEntity user = stand.getUser();
        if (user == null || !tooStrong(user, held)) {
            ALLOWED.put(stand, new WeakReference<>(held));
            return;
        }
        LivingComponentGrab grab = stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
        if (grab != null) {
            grab.setGrabTarget(null);
        }
        if (user instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(
                    "jojo.message.action_condition.grateful_dead_too_strong"), true);
        }
    }

    public static boolean tooStrong(LivingEntity user, LivingEntity target) {
        float myPower = withAging(user, GRAB_POWER);
        float resist = resist(target);
        LivingEntity aged = AgingService.canonicalLivingTarget(target);
        float aging = aged.hasData(GratefulDeadAddon.LIVING_AGING)
                ? LivingAgingState.get(aged).progress()
                : 0.0F;
        float required = Mth.lerp(Mth.clamp(aging, 0.0F, 1.0F), 1.05F, 0.35F);
        if (aging >= 0.3F) {
            required *= 0.85F;
        }
        return myPower + 0.001F < resist * required;
    }

    private static float resist(LivingEntity target) {
        if (target instanceof StandEntity stand) {
            LivingEntity owner = stand.getUser();
            float standResist = (float) Math.max(stand.getAttackDamage(), MIN_RESIST);
            return owner != null ? withAging(owner, standResist) : standResist;
        }
        StandPower power = StandPower.get(target);
        StandEntity targetStand = power != null && power.hasPower()
                ? power.getSummonedStandEntity()
                : null;
        if (targetStand != null) {
            return withAging(target, (float) Math.max(targetStand.getAttackDamage(), MIN_RESIST));
        }
        float attack = target.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? (float) target.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : 0.0F;
        float body = Math.max(target.getMaxHealth(), 1.0F) / 5.0F + attack;
        return withAging(target, Math.max(MIN_RESIST, body));
    }

    // 0.1.13 applyAgingPowerModifier: aging takes strength, self-aging does not
    private static float withAging(LivingEntity entity, float base) {
        if (!entity.hasData(GratefulDeadAddon.LIVING_AGING)) {
            return base;
        }
        LivingAgingState state = LivingAgingState.get(entity);
        if (state.selfInflicted(entity)) {
            return base;
        }
        return base * (float) LivingAgingState.strengthEfficiency(state.progress());
    }

    private static LivingEntity allowed(StandEntity stand) {
        WeakReference<LivingEntity> held = ALLOWED.get(stand);
        return held != null ? held.get() : null;
    }

    /** The session's grabs, forgotten when its server has stopped. */
    public static void forgetSession() {
        ALLOWED.clear();
    }

    // For the game tests.
    public static void allowForTests(StandEntity stand, LivingEntity held) {
        ALLOWED.put(stand, new WeakReference<>(held));
    }

    public static boolean hasAllowedForTests(StandEntity stand) {
        return ALLOWED.containsKey(stand);
    }
}

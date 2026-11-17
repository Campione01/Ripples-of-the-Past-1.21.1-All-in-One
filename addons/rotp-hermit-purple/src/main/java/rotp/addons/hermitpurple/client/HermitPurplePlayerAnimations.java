package rotp.addons.hermitpurple.client;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import rotp.core.api.client.animation.AddonPlayerAnimations;
import rotp.core.api.client.animation.AddonPlayerAnimations.PlayerAnimationState;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public final class HermitPurplePlayerAnimations {
    private static final ResourceLocation ANIMATION_SET =
            HermitPurpleAddon.id("hermit_purple");
    private static final Map<EntityActionInstance, Playback>
            PLAYBACK = new WeakHashMap<>();
    // 1.16 KosmxVineHandler.stopAnim: the vine pose fades out over 10 ticks.
    private static final float VINE_FADE_TICKS = 10.0F;
    private static final float VINE_RAISE_TICKS = 5.0F;
    private static final Map<Player, Float> VINE_FADE = new WeakHashMap<>();

    public static void register() {
        AddonPlayerAnimations.register(
                HermitPurpleAddon.id("player_actions"),
                100,
                HermitPurplePlayerAnimations::resolve);
    }

    @Nullable
    static PlayerAnimationState resolve(
            Player player, float partialTick) {
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(player);
        EntityActionInstance action =
                stand != null ? stand.getCurStandAction() : null;
        if (action == null
                || action.ability.getAbilityId() == null) {
            return vineFade(player, partialTick);
        }

        String abilityName =
                action.ability.getAbilityId().nameInMoveset();
        if (HermitPurpleAddon.HP_BREATH_NAME.equals(abilityName)) {
            return action.getPhase() == ActionPhase.PERFORM
                    ? new PlayerAnimationState(
                            ModPlayerPowers.HAMON.get().getId(),
                            "hamon_breath",
                            Math.max(0.0F,
                                    action.getAnimPhaseTick(partialTick)))
                    : null;
        }
        AnimationSpec animation = animationFor(abilityName);
        if (animation == null || action.getPhase() == null) {
            return vineFade(player, partialTick);
        }
        if ("vine".equals(animation.name())) {
            VINE_FADE.put(player, Float.NaN);
        }
        else {
            VINE_FADE.remove(player);
        }

        float worldTime = player.tickCount + partialTick;
        Playback playback = PLAYBACK.computeIfAbsent(
                action, ignored -> new Playback());
        float animationTime = playback.animationTime(
                action, animation.returnTick(),
                raiseTicks(abilityName), worldTime, partialTick);
        String side = player.getMainArm() == HumanoidArm.LEFT
                ? "_left"
                : "_right";
        return new PlayerAnimationState(
                ANIMATION_SET,
                animation.name() + side,
                animationTime);
    }

    @Nullable
    private static PlayerAnimationState vineFade(
            Player player, float partialTick) {
        Float started = VINE_FADE.get(player);
        if (started == null) {
            return null;
        }
        float worldTime = player.tickCount + partialTick;
        if (started.isNaN()) {
            started = worldTime;
            VINE_FADE.put(player, started);
        }
        float elapsed = worldTime - started;
        if (elapsed >= VINE_FADE_TICKS || elapsed < 0.0F) {
            VINE_FADE.remove(player);
            return null;
        }
        return new PlayerAnimationState(
                ANIMATION_SET,
                player.getMainArm() == HumanoidArm.LEFT
                        ? "vine_end_left"
                        : "vine_end_right",
                elapsed);
    }

    @Nullable
    private static AnimationSpec animationFor(String abilityName) {
        return switch (abilityName) {
            case HermitPurpleAddon.HP_VINE_ATTACK_NAME,
                    HermitPurpleAddon.HP_VINE_SHIFT_NAME ->
                    new AnimationSpec("vine", 5.0F);
            case HermitPurpleAddon.HP_GRAB_NAME ->
                    new AnimationSpec("grab", 20.0F);
            case HermitPurpleAddon.HP_VINE_ENTITY_NAME ->
                    new AnimationSpec("grab", 20.0F);
            case HermitPurpleAddon.HP_VINE_NAME ->
                    new AnimationSpec("grapple", 10.0F);
            case HermitPurpleAddon.HP_DOXX_NAME,
                    HermitPurpleAddon.HP_CAMERA_NAME ->
                    new AnimationSpec("doxx_left", 20.0F);
            default -> null;
        };
    }

    // 1.16 hp_grab, hp_vine and hp_vine_entity are hold-only: they have no
    // windup phase, so the raise pose runs from the first frame of the action.
    // The lash ran at its file's own speed up to the return tick
    // (KosmxVineHandler.windupStopsAt), through and past its 3-tick wind-up.
    private static float raiseTicks(String abilityName) {
        return switch (abilityName) {
            case HermitPurpleAddon.HP_VINE_ATTACK_NAME,
                    HermitPurpleAddon.HP_VINE_SHIFT_NAME ->
                    VINE_RAISE_TICKS;
            case HermitPurpleAddon.HP_GRAB_NAME ->
                    HermitPurpleSpec.GRAB_WINDUP_TICKS;
            case HermitPurpleAddon.HP_VINE_NAME,
                    HermitPurpleAddon.HP_VINE_ENTITY_NAME ->
                    HermitPurpleSpec.GRAPPLE_WINDUP_TICKS;
            default -> 0.0F;
        };
    }

    private record AnimationSpec(String name, float returnTick) {}

    private static final class Playback {
        private float performStartedAt = Float.NaN;
        private float startedAt = Float.NaN;

        private float animationTime(
                EntityActionInstance action,
                float returnTick,
                float raiseTicks,
                float worldTime,
                float partialTick) {
            if (raiseTicks > 0.0F) {
                if (Float.isNaN(startedAt)) {
                    startedAt = worldTime;
                }
                float elapsed = Math.max(0.0F, worldTime - startedAt);
                return elapsed < raiseTicks
                        ? returnTick * elapsed / raiseTicks
                        : returnTick + 1.0F + (elapsed - raiseTicks);
            }
            ActionPhase phase = action.getPhase();
            if (phase == ActionPhase.BUTTON_CHARGE
                    || phase == ActionPhase.WINDUP) {
                performStartedAt = Float.NaN;
                float phaseLength = Math.max(
                        action.getAnimPhaseLength(), 0.0001F);
                float ratio = Math.max(
                        0.0F,
                        Math.min(
                                action.getAnimPhaseTick(partialTick)
                                        / phaseLength,
                                1.0F));
                return returnTick * ratio;
            }
            if (Float.isNaN(performStartedAt)) {
                performStartedAt = worldTime;
            }
            return returnTick + 1.0F
                    + Math.max(0.0F, worldTime - performStartedAt);
        }
    }

    private HermitPurplePlayerAnimations() {}
}

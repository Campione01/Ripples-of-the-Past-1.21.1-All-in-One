package rotp.core.impl.stands.goldexperience;

import java.util.Optional;
import java.util.stream.Stream;

import rotp.core.client.ui.utils.GuiItemIcon;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeformsScreen;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.UserStandEffects;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/*
 * The owner's rule: aimed at one of the user's lifeforms, Revert turns it back at once; aimed at none of them, his
 * client opens the menu of all his lifeforms (GoldExperienceLifeformsScreen), where the server reverts the one he picks.
 */
public class GoldExperienceRevertLifeformAbility extends GoldExperienceUtilityAbility {
    public static final double MAX_LIFEFORM_DISTANCE = 128.0;
    // beyond the creature's own size, how far off the crosshair may be and still point at it
    private static final double AIM_TOLERANCE = Math.toRadians(3.0);

    public GoldExperienceRevertLifeformAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        usageGroup = AbilityUsageGroup.UTILITY;
    }

    @Override
    public Component getName(Power<?> context) {
        return getTargetedLifeformEffect(context)
                .map(GECreatedLifeformEffect::getSourceName)
                .filter(name -> !name.getString().isEmpty())
                .map(name -> abilityName(context, ".param", name))
                .orElseGet(() -> super.getName(context));
    }

    @Override
    public void renderAbilityIcon(Power<?> context, GuiGraphics guiGraphics, TextureAtlasSprite sprite, float x, float y, int color) {
        getTargetedLifeformEffect(context)
                .map(GECreatedLifeformEffect::getItemView)
                .filter(item -> !item.isEmpty())
                .ifPresentOrElse(
                        item -> GuiItemIcon.render(guiGraphics, context.getUser(), item, (int) x, (int) y, color),
                        () -> super.renderAbilityIcon(context, guiGraphics, sprite, x, y, color));
    }

    // the menu lists every lifeform of the user, wherever it is
    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        if (!super.isAbilityAvailable(context)) {
            return false;
        }
        LivingEntity user = context.getUser();
        return user == null || !user.level().isClientSide()
                || context instanceof StandPower standPower && hasAnyLifeform(standPower);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user != null && user.level().isClientSide()
                && (!(context instanceof StandPower standPower) || !hasAnyLifeform(standPower))) {
            return ConditionCheck.createNegative("ge_no_lifeform");
        }
        return super.checkSpecificConditions(context);
    }

    private static Optional<GECreatedLifeformEffect> getTargetedLifeformEffect(Power<?> context) {
        if (context instanceof StandPower standPower) {
            return findAimedLifeform(standPower, context.getUser());
        }
        return Optional.empty();
    }

    @Override
    public void writeExtraInput(FriendlyByteBuf serverboundBuf, LivingEntity user, boolean isClientPlayer) {
        StandPower standPower = StandPower.get(user);
        int effectId = findAimedLifeform(standPower, user)
                .map(GECreatedLifeformEffect::getId)
                .orElse(-1);
        serverboundBuf.writeVarInt(effectId);
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        if (level.isClientSide()) {
            // aimed at none of his lifeforms: his own client opens the menu, the server reverts nothing
            if (GoldExperienceLifeforms.pressedOnThisClient(user) && readEffectId(extraClientInput, user).orElse(-1) <= 0) {
                GoldExperienceLifeformsScreen.openOnRevert();
            }
            return;
        }
        StandPower standPower = StandPower.get(user);
        if (standPower != null) {
            Optional<Integer> inputEffectId = readEffectId(extraClientInput, user);
            Optional<GECreatedLifeformEffect> effect = inputEffectId.isPresent()
                    ? createdLifeformEffectFromInput(standPower, user, inputEffectId.get())
                    : findNearestInSight(standPower, user);
            effect.ifPresent(lifeform -> revertLifeform(standPower, lifeform));
        }
    }

    private static Optional<Integer> readEffectId(FriendlyByteBuf extraClientInput, LivingEntity user) {
        if (extraClientInput == null || extraClientInput.readableBytes() <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(extraClientInput.readVarInt());
        }
        catch (RuntimeException e) {
            JojoMod.getLogger().warn("Ignoring malformed Gold Experience revert lifeform input from {}.",
                    user.getName().getString(), e);
            return Optional.empty();
        }
    }

    static void revertLifeform(StandPower standPower, GECreatedLifeformEffect effect) {
        Entity target = effect.getTarget();
        effect.remove();
        if (target instanceof GETransformationEntity transformation && transformation.actionCooldown > 0) {
            int createCooldown = standPower.getAbilityCooldown(GoldExperienceCreateLifeformAbility.CREATE_LIFEFORM_ABILITY_NAME);
            standPower.setAbilityCooldown(
                    GoldExperienceCreateLifeformAbility.CREATE_LIFEFORM_ABILITY_NAME,
                    createCooldown - transformation.actionCooldown);
        }
    }

    static boolean hasAnyLifeform(StandPower standPower) {
        return standPower != null && standPower.userStandEffects
                .getEffectsOfType(ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
                .anyMatch(effect -> !effect.isStopped());
    }

    /** The user's lifeforms Revert can reach where he stands: loaded, in his dimension, within 128 blocks. */
    static Stream<GECreatedLifeformEffect> reachableLifeforms(StandPower standPower, LivingEntity user) {
        if (standPower == null || user == null) {
            return Stream.empty();
        }
        double rangeSqr = MAX_LIFEFORM_DISTANCE * MAX_LIFEFORM_DISTANCE;
        return standPower.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get())
                .filter(effect -> !effect.isStopped() && isWithinReach(effect.getTarget(), user, rangeSqr));
    }

    static boolean isWithinReach(Entity target, LivingEntity user, double rangeSqr) {
        return target != null && target.isAlive() && target.level() == user.level() && target.distanceToSqr(user) < rangeSqr;
    }

    /**
     * The lifeform the user points at: one his crosshair is on, give or take a few degrees, that he can see. Many in
     * line: the one closest to the crosshair.
     */
    public static Optional<GECreatedLifeformEffect> findAimedLifeform(StandPower standPower, LivingEntity user) {
        if (user == null) {
            return Optional.empty();
        }
        Vec3 eye = user.getEyePosition(1.0F);
        Vec3 look = user.getLookAngle();
        GECreatedLifeformEffect best = null;
        double bestRatio = Double.MAX_VALUE;
        for (GECreatedLifeformEffect effect : reachableLifeforms(standPower, user).toList()) {
            Entity target = effect.getTarget();
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 toCenter = center.subtract(eye);
            double distance = toCenter.length();
            if (distance < 1.0E-4) {
                return Optional.of(effect);
            }
            double angle = Math.acos(Mth.clamp(look.dot(toCenter.scale(1.0 / distance)), -1.0, 1.0));
            double halfSize = Math.max(target.getBbWidth(), target.getBbHeight()) / 2.0;
            double allowed = Math.atan2(halfSize, distance) + AIM_TOLERANCE;
            if (angle <= allowed && angle / allowed < bestRatio && canSee(user, eye, target)) {
                best = effect;
                bestRatio = angle / allowed;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean canSee(LivingEntity user, Vec3 eye, Entity target) {
        Level level = user.level();
        for (Vec3 point : new Vec3[] { target.getBoundingBox().getCenter(), target.getEyePosition() }) {
            if (level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user))
                    .getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    // a press with no input (a Stand-bearing mob): the reachable lifeform nearest to where it looks, as before
    private static Optional<GECreatedLifeformEffect> findNearestInSight(StandPower standPower, LivingEntity user) {
        return UserStandEffects.getTargetLookedAt(reachableLifeforms(standPower, user), user)
                .map(GECreatedLifeformEffect.class::cast);
    }

    private static Optional<GECreatedLifeformEffect> createdLifeformEffectFromInput(StandPower standPower, LivingEntity user, int effectId) {
        if (standPower == null || user == null || effectId <= 0) {
            return Optional.empty();
        }
        if (!(standPower.userStandEffects.getById(effectId) instanceof GECreatedLifeformEffect effect)
                || effect.effectType != ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get()
                || effect.getStandUser() != user || effect.isStopped()) {
            return Optional.empty();
        }
        return isWithinReach(effect.getTarget(), user, MAX_LIFEFORM_DISTANCE * MAX_LIFEFORM_DISTANCE)
                ? Optional.of(effect) : Optional.empty();
    }
}

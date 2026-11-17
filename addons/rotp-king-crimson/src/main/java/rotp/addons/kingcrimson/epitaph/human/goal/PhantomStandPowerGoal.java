package rotp.addons.kingcrimson.epitaph.human.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.PhantomAbilityAllowList;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * The Stand power of a phantom, used as a creature that bears a Stand uses it (EPITAPH-ARCH 3.1): with somebody to fight
 * within range the Stand is out; once a second an ability is pressed, one of those on the table that the power offers
 * now and can pay for; a guard goes up when the phantom was just hurt; a key that is held is let go when its action is
 * over, when its charge is full, or when a barrage or a guard has run its time. Written here because this add-on may
 * not call the one that gives creatures their Stands. It has no combos.
 */
public final class PhantomStandPowerGoal extends Goal {
    /** How far the one it fights may be for the Stand to be out, and how near for a blow to be worth its stamina. */
    private static final double RANGE = 12.5D;
    private static final double STRIKE = 4.0D;
    private static final int PICK_EVERY = 20;
    private static final int GUARD_AGAIN = 80;
    private static final int HURT_LATELY = 20;
    private static final int LONGEST_HOLD = 120;
    private static final int BARRAGE_OR_GUARD = 20;
    private static final int OTHER_HOLD = 12;
    private static final String GUARD = "guard";

    private final Mob thinker;
    private final LivingEntity body;
    private final Map<String, Integer> waitUntil = new HashMap<>();
    private int ticks;
    private int guardWait;
    private short heldKey;
    private int heldSince = -1;
    @Nullable
    private EntityActionInstance heldAction;
    @Nullable
    private String heldName;
    /** The hold was taken over at the press: no key holds it, and it is counted from the press. */
    private boolean keyless;

    /**
     * The thinker is the creature whose target is fought; the body has the power and presses. For a creature that bears
     * a Stand they are one; for a human phantom they are its mind and itself.
     */
    public PhantomStandPowerGoal(Mob thinker, LivingEntity body) {
        this.thinker = thinker;
        this.body = body;
        setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    /** The copy of a creature that bears a Stand uses it in the room: the goal its add-on gives it at a join is not on a copy. */
    public static void giveTo(Mob copy) {
        StandPower power = StandPower.get(copy);
        if (power != null && power.hasPower()) {
            copy.goalSelector.addGoal(1, new PhantomStandPowerGoal(copy, copy));
        }
    }

    /** The goal that presses for this body and runs: none for a creature whose mind does not run, or a human without one. */
    @Nullable
    public static PhantomStandPowerGoal of(LivingEntity body) {
        Mob thinker = body instanceof EpitaphPlayerPhantom human ? human.mind() : body instanceof Mob mob && !mob.isNoAi() ? mob : null;
        if (thinker != null) {
            for (WrappedGoal wrapped : thinker.goalSelector.getAvailableGoals()) {
                if (wrapped.getGoal() instanceof PhantomStandPowerGoal goal && goal.body == body) {
                    return goal;
                }
            }
        }
        return null;
    }

    /**
     * A hold its body was in the middle of at the press is its own from there (EPITAPH-ARCH, ruling R12): it is let go by
     * the rule for a hold this goal pressed itself.
     */
    public void takeOver(EntityActionInstance action) {
        release();
        heldAction = action;
        heldName = action.ability instanceof Ability ability ? ability.name() : null;
        heldSince = body.tickCount;
        keyless = true;
    }

    @Nullable
    private StandPower power() {
        StandPower power = StandPower.get(body);
        return power != null && power.hasPower() ? power : null;
    }

    @Override
    public boolean canUse() {
        return body.isAlive() && power() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        release();
    }

    @Override
    public void tick() {
        StandPower power = power();
        if (power == null) {
            release();
            return;
        }
        if (heldSince >= 0 && shouldRelease()) {
            release();
        }
        if (guardWait > 0) {
            guardWait--;
        }
        waitUntil.values().removeIf(until -> until <= body.tickCount);
        LivingEntity foe = thinker.getTarget();
        if (foe == null || !foe.isAlive() || foe == body || body.distanceToSqr(foe) > RANGE * RANGE) {
            release();
            return;
        }
        boolean inReach = body.distanceToSqr(foe) <= STRIKE * STRIKE;
        if (!power.isSummoned()) {
            power.getPowerType().summon(body, power);
        }
        // What the body and its Stand strike at: the one it fights when he is near enough to be struck, else nothing.
        ActionTarget aim = inReach ? new ActionTarget(foe) : ActionTarget.EMPTY;
        LivingComponentAction.getComponent(body).entityAim.setTarget(aim);
        StandEntity stand = power.getSummonedStandEntity();
        if (stand != null) {
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aim.copy());
        }
        if (++ticks % PICK_EVERY != 0 || heldSince >= 0) {
            return;
        }
        Map<String, Ability> offered = power.updateAvailableMoves().inMovesetAndCanBeUsed;
        Ability guard = offered.get(GUARD);
        if (guardWait == 0 && body.tickCount - body.getLastHurtByMobTimestamp() < HURT_LATELY && usable(power, guard)) {
            guardWait = GUARD_AGAIN;
            press(power, guard);
            return;
        }
        if (!inReach) {
            return;
        }
        List<Ability> blows = new ArrayList<>();
        for (Ability ability : offered.values()) {
            if (!GUARD.equals(ability.name()) && usable(power, ability)) {
                blows.add(ability);
            }
        }
        if (!blows.isEmpty()) {
            // In an order of its own: the draw is the same for the same state of the random source.
            blows.sort(Comparator.comparing(Ability::name));
            press(power, blows.get(body.getRandom().nextInt(blows.size())));
        }
    }

    private boolean usable(StandPower power, @Nullable Ability ability) {
        return ability instanceof EntityActionAbility action && PhantomAbilityAllowList.allows(ability, power)
                && !waitUntil.containsKey(ability.name()) && ability.getStaminaCost(power) <= power.getStamina()
                // A press the core would hold back behind a running action is lost to a key that is not a player.
                && !action.wouldBufferInput(body);
    }

    private void press(StandPower power, Ability ability) {
        String name = ability.name();
        waitUntil.put(name, body.tickCount + Math.max(1, ability.getCooldownTechnical(power) + 10));
        ConditionCheck check = ability.checkConditions(power);
        if (!check.isPositive() && !check.shouldContinueHold()) {
            return;
        }
        HeldInputEntry held = AbilityInput.keyPressMob(ability, body, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
        if (held != null) {
            heldKey = held.keyId;
            heldSince = body.tickCount;
            heldName = name;
            heldAction = held.action instanceof EntityActionInstance action ? action : null;
        }
    }

    private void release() {
        if (heldSince >= 0) {
            if (!keyless) {
                AbilityInput.keyRelease(heldKey, body);
            } else if (heldAction != null && !heldAction.isOver()) {
                // What letting go of its key does to an action.
                heldAction.onKeyRelease(body);
            }
            heldSince = -1;
            heldAction = null;
            heldName = null;
            keyless = false;
        }
    }

    // The key stays down while letting it go would cut the action short; it goes up when the action is over, when a
    // charge that fires on release is full, or when a hold without an end of its own has run its usual time.
    private boolean shouldRelease() {
        int held = body.tickCount - heldSince;
        EntityActionInstance action = heldAction;
        if (action == null) {
            return held >= 1;
        }
        if (action.isOver() || held >= LONGEST_HOLD || !(action.ability instanceof EntityActionAbility ability)
                || !ability.canBeStoppedByOriginalHoldCancel(action)) {
            return true;
        }
        int usual = heldName != null && (heldName.contains("barrage") || heldName.contains(GUARD)) ? BARRAGE_OR_GUARD : OTHER_HOLD;
        float at = keyless ? held : action.getPhaseTick();
        float length = action.getCurPhaseLength();
        if (length > LONGEST_HOLD) {
            return at >= usual;
        }
        return switch (action.getPhase()) {
            case BUTTON_CHARGE, WINDUP -> at >= length;
            case PERFORM -> ability.isActionHeld(action) && at >= usual;
            default -> true;
        };
    }
}

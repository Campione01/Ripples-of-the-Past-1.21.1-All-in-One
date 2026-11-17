package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.PhantomAbilityAllowList;
import rotp.addons.kingcrimson.epitaph.human.goal.PhantomStandPowerGoal;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionReplayState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The actions of the core in a room, for the record (EPITAPH-ARCH 2.4). An action is not in saved data and is set by
 * code of the core, so the room looks: after each of its ticks, at both slots of every living phantom. Where the
 * generation of a slot changed, the action as it then stands is an op; and for every tick a slot holds an action, its
 * phase and how far into it are kept on the track.
 */
public final class PhantomActions {
    private PhantomActions() {}

    /**
     * The action a root is in the middle of at the press is taken over by its copy, as it stands. It is no change of the
     * room: no op tells of it, and the one who foresees is shown it. Only an action a phantom may press is taken over. A
     * player is not asked: what a walked player was in the middle of himself ended at the press.
     */
    static void takeOver(SandboxRoom room, LivingEntity real, LivingEntity copy) {
        SandboxRoom.Phantom phantom = room.info(copy);
        LivingEntity powerUser = copy instanceof StandEntity stand ? stand.getUser() : copy;
        // A copy takes over only what its body will be held to: the Stand of a human who is not walked is left out, as he is.
        if (phantom == null || powerUser == null || real instanceof Player || room.hides(copy) || room.leavesOut(phantom.owner)) {
            return;
        }
        int slots = real instanceof StandEntity ? 1 : 2;
        for (int slot = 0; slot < slots; slot++) {
            LivingComponentAction from = slot == LivingComponentAction.MAIN_SLOT ? LivingComponentAction.getExistingComponent(real)
                    : LivingComponentAction.getExistingComponent(real, PowerClass.STAND);
            EntityActionInstance going = from != null ? from.getAction() : null;
            // What a phantom may not press it does not go on with either.
            if (going == null || !(going.ability instanceof Ability ability) || !PhantomAbilityAllowList.allows(ability, StandPower.get(powerUser))) {
                continue;
            }
            ActionReplayState state = from.captureReplayState();
            if (!state.hasAction()) {
                continue;
            }
            LivingComponentAction to = LivingComponentAction.getComponentForSlot(copy, slot);
            EntityActionInstance taken = to.applyReplayState(state, powerUser, SyncType.TRACKING_AND_SELF);
            if (taken != null) {
                phantom.actionGenerations[slot] = to.actionGeneration();
                phantom.acting[slot] = true;
                phantom.phases[slot] = taken.getPhase();
                phantom.takenOver[slot] = state;
                phantom.track.mark(0, slot, new EntityTrack.ActionMark(taken.getPhase(), (int) taken.getPhaseTick()));
                handOver(powerUser, taken);
            }
        }
    }

    // A hold that was taken over is the hold of the copy from the press on (EPITAPH-ARCH, ruling R12): its mind lets it go by
    // its own rule. A copy whose mind is not ours, or does not run, lets it go at once: else nobody ever would.
    private static void handOver(LivingEntity powerUser, EntityActionInstance taken) {
        if (!(taken.ability instanceof EntityActionAbility ability) || !ability.isHeldSincePress(taken)) {
            return;
        }
        PhantomStandPowerGoal mind = PhantomStandPowerGoal.of(powerUser);
        if (mind != null) {
            mind.takeOver(taken);
        } else if (!(powerUser instanceof EpitaphPlayerPhantom)) {
            // A human phantom is given its mind once every copy is made: the hold is handed to it then.
            taken.onKeyRelease(powerUser);
        }
    }

    /** A human phantom has been given its mind: a hold its Stand took over at the press is the hold of that mind from here. */
    public static void handOverToMind(SandboxRoom room, LivingEntity human) {
        StandPower power = StandPower.get(human);
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        SandboxRoom.Phantom phantom = stand != null ? room.info(stand) : null;
        EntityActionInstance taken = phantom != null && phantom.acting[LivingComponentAction.MAIN_SLOT]
                ? LivingComponentAction.getCurEntityAction(stand) : null;
        PhantomStandPowerGoal mind = PhantomStandPowerGoal.of(human);
        if (mind != null && taken != null && !taken.isOver() && taken.ability instanceof EntityActionAbility ability
                && ability.isHeldSincePress(taken)) {
            mind.takeOver(taken);
        }
    }

    /**
     * Looked at twice for each phantom in a tick of the room: before its own tick, for what another set on it earlier in
     * that tick, which it is about to tick; and when all have ticked.
     */
    static void note(SandboxRoom room, SandboxRoom.Phantom phantom, boolean beforeItsTick) {
        if (!(phantom.entity instanceof LivingEntity living)) {
            return;
        }
        note(room, phantom, LivingComponentAction.MAIN_SLOT, LivingComponentAction.getExistingComponent(living), beforeItsTick);
        // A Stand has the one slot; its user has a second for what he performs of his Stand power himself.
        if (!(living instanceof StandEntity)) {
            note(room, phantom, LivingComponentAction.STAND_POWER_SLOT,
                    LivingComponentAction.getExistingComponent(living, PowerClass.STAND), beforeItsTick);
        }
    }

    /** What the Stand power of a copy waits for as the room begins: what a wait is measured against when it is looked at. */
    static void baseline(SandboxRoom room, @Nullable Entity copy) {
        SandboxRoom.Phantom phantom = room.info(copy);
        if (phantom != null && copy instanceof LivingEntity living && !(living instanceof StandEntity)) {
            phantom.waits = cooldowns(living);
            phantom.waitsAt = room.workingTick();
        }
    }

    /**
     * When every phantom has ticked: the Stand power of each that pressed, or whose action or whose Stand ended an action
     * or changed its phase in this tick, is looked at, and a wait that rose is an op of this tick. The core sets the wait
     * of an action that is held from its press when the hold ends, later than the press (EPITAPH-ARCH, ruling R10).
     */
    static void noteWaits(SandboxRoom room) {
        for (SandboxRoom.Phantom phantom : room.all()) {
            if (!phantom.waitsDue) {
                continue;
            }
            phantom.waitsDue = false;
            if (phantom.ended || !(phantom.entity instanceof LivingEntity living)) {
                continue;
            }
            List<Wait> before = phantom.waits != null ? phantom.waits : List.of();
            // A wait runs down by one in each tick of the room.
            int passed = room.workingTick() - phantom.waitsAt;
            List<Wait> now = cooldowns(living);
            for (Wait wait : now) {
                if (wait.ticks() > Math.max(0, ticksOf(before, wait.ability()) - passed)) {
                    room.record(new RecordedOp.Cooldown(phantom.key, wait.ability(), wait.ticks(), wait.total()));
                }
            }
            phantom.waits = now;
            phantom.waitsAt = room.workingTick();
        }
    }

    // The power is that of the user: a Stand acts for him.
    private static void due(SandboxRoom room, SandboxRoom.Phantom phantom) {
        SandboxRoom.Phantom of = phantom.entity instanceof StandEntity stand ? room.info(stand.getUser()) : phantom;
        if (of != null) {
            of.waitsDue = true;
        }
    }

    /** What a Stand power waits for one ability: so many ticks, of so many in all. */
    public record Wait(String ability, int ticks, int total) {}

    private static int ticksOf(List<Wait> waits, String ability) {
        for (Wait wait : waits) {
            if (wait.ability().equals(ability)) {
                return wait.ticks();
            }
        }
        return 0;
    }

    /**
     * What the Stand power of an entity waits for, ability by ability. The names are read from its saved form, the one place
     * the core lists them: its key and its layout are those of the core the add-on is built against.
     */
    public static List<Wait> cooldowns(LivingEntity entity) {
        List<Wait> waits = new ArrayList<>();
        StandPower power = StandPower.get(entity);
        if (power != null && power.hasPower()) {
            CompoundTag saved = power.serializeNBT(entity.registryAccess()).getCompound("AbilityCooldowns");
            for (String ability : saved.getAllKeys()) {
                int ticks = power.getAbilityCooldown(ability);
                if (ticks > 0) {
                    waits.add(new Wait(ability, ticks, power.getAbilityCooldownTotal(ability)));
                }
            }
        }
        return waits;
    }

    private static void note(SandboxRoom room, SandboxRoom.Phantom phantom, int slot, @Nullable LivingComponentAction component,
            boolean beforeItsTick) {
        if (component == null) {
            return;
        }
        EntityActionInstance action = component.getAction();
        boolean acting = action != null && !action.isOver();
        long generation = component.actionGeneration();
        ActionPhase phase = acting ? action.getPhase() : null;
        if (generation != phantom.actionGenerations[slot]) {
            phantom.actionGenerations[slot] = generation;
            // A generation also moves when a slot is only synced: no action before and none now is no change.
            if (acting || phantom.acting[slot]) {
                room.record(new RecordedOp.Action(phantom.key, slot, component.captureReplayState(), beforeItsTick));
                due(room, phantom);
            }
        } else if (phase != phantom.phases[slot]) {
            due(room, phantom);
        }
        phantom.phases[slot] = phase;
        phantom.acting[slot] = acting;
        if (acting && !beforeItsTick) {
            phantom.track.mark(room.workingTick(), slot, new EntityTrack.ActionMark(action.getPhase(), (int) action.getPhaseTick()));
        }
    }
}

package rotp.addons.kingcrimson.epitaph.replay;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.record.TrackFrame;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomActions;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.TrAbilityUsePacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionReplayState;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The Stands and the actions of the core on the bodies (EPITAPH-ARCH 2.4). A body on a track does not tick, so nothing of
 * its power runs by itself. The Stand its phantom summoned is summoned for it, under the id the phantom Stand has. The
 * action its phantom began is put on it as the core puts one on a client, and for every tick of the future the bodies
 * pass it is ticked once in the muted mode: it runs, its phases go on and what the core sends of it goes out, but what it
 * writes to the world is absorbed, for what it really did is in the other ops of the record. After the ops of the tick it
 * is set to the phase the record has for it, and ended where the record has none.
 */
public final class ActionMirror {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean failureLogged;

    private ActionMirror() {}

    /** What a muted tick finds, and what it is told of its hits: the bodies on their tracks, and the record of this tick. */
    private record Scope(EpitaphSession session, int tick, BoundBodies bodies) implements EpitaphSandbox.Mirror {
        @Override
        public List<ServerPlayer> players() {
            return bodies.players();
        }

        @Override
        public boolean lands(LivingEntity victim, DamageSource source) {
            ActorKey struck = ActorKey.of(victim);
            ActorKey direct = ActorKey.of(source.getDirectEntity());
            ActorKey causing = ActorKey.of(source.getEntity());
            for (RecordedOp op : session.record().ops(tick)) {
                if (op instanceof RecordedOp.Damage hit && hit.victim().equals(struck)
                        && ActorKey.of(hit.source().getDirectEntity()).equals(direct)
                        && ActorKey.of(hit.source().getEntity()).equals(causing) && !session.skips(op)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Work of the core on a real performer, in the muted mode, with the record of this tick to answer for its hits. Open to
     * the test of the barrier of that mode, which makes each kind of write itself.
     */
    public static void muted(EpitaphSession session, int tick, Runnable work) {
        if (EpitaphSandbox.depth != 0) {
            work.run();
            return;
        }
        EpitaphSandbox.enterMuted(session.room(), new Scope(session, tick, new BoundBodies(session.binder())));
        try {
            work.run();
        }
        finally {
            EpitaphSandbox.exit();
        }
    }

    /** A Stand was summoned in the room: the body of its user summons his, under the id and the UUID of the phantom Stand. */
    static boolean summon(EpitaphSession session, int tick, RecordedOp.StandSummon op) {
        if (!(session.bodyOf(op.user()) instanceof LivingEntity user) || !user.isAlive()) {
            return false;
        }
        StandPower power = StandPower.get(user);
        if (power == null || power.isSummoned() || !(power.getPowerType() instanceof EntityStandType type)) {
            return false;
        }
        SandboxRoom.Phantom phantom = session.room().info(op.stand());
        EntityTrack track = session.record().track(op.stand());
        boolean made = type.summon(user, power, stand -> {
            stand.setId(op.stand().id());
            if (phantom != null) {
                stand.setUUID(phantom.entity.getUUID());
            }
            if (op.armsOnly()) {
                stand.setArmsOnlyMode(op.mainArm(), op.offArm());
            }
            if (track != null) {
                // Where its phantom stood when it came out, not where the core would put it beside its user.
                TrackFrame frame = track.frame(tick);
                stand.moveTo(frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());
            }
        }, false);
        StandEntity stand = made ? power.getSummonedStandEntity() : null;
        if (stand == null) {
            return false;
        }
        // The summon of the core from here on: into the level, with its sound and the word to the clients.
        type.finalizeStandSummonFromAction(user, power, stand, true);
        if (!stand.isAddedToLevel() || stand.isRemoved()) {
            return false;
        }
        if (track != null) {
            PuppetTick.place(session.level(), session.bind(stand, track), tick);
        } else {
            session.registerFree(stand);
        }
        return true;
    }

    /** A Stand left the room: the Stand of the body is taken back by its power, which then knows that it has none out. */
    static boolean recall(StandEntity stand) {
        LivingEntity user = stand.getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null || power.getSummonedStandEntity() != stand || !(power.getPowerType() instanceof EntityStandType type)) {
            return false;
        }
        type.forceUnsummon(user, power);
        return true;
    }

    /** The action of a slot changed in the room: it is put on the body of the performer, or the slot is cleared. */
    static boolean apply(EpitaphSession session, int tick, RecordedOp.Action op) {
        Entity body = session.bodyOf(op.actor());
        Binding binding = session.binder().of(body);
        // A walked player ticks his own slots himself, and nothing mutes that tick: nothing is put there.
        if (binding == null || !(body instanceof LivingEntity performer) || body instanceof Player) {
            return false;
        }
        LivingEntity powerUser = performer instanceof StandEntity stand ? stand.getUser() : performer;
        if (powerUser == null) {
            return false;
        }
        LivingComponentAction component = LivingComponentAction.getComponentForSlot(performer, op.slot());
        EntityActionInstance[] now = new EntityActionInstance[1];
        muted(session, tick, () -> now[0] = component.applyReplayState(op.state(), powerUser, SyncType.TRACKING_AND_SELF));
        boolean put = now[0] != null || !op.state().hasAction();
        // Its performer ticked it in the tick of the room in which it was set: so does the body, here.
        if (op.ticksAtOnce() && now[0] != null && component.getAction() == now[0]) {
            muted(session, tick, component::tick);
        }
        binding.mirrored[op.slot()] = component.getAction() == now[0] ? now[0] : null;
        return put;
    }

    /** A press of the phantom set a cooldown: the power of the body waits as long. */
    static boolean cooldown(EpitaphSession session, RecordedOp.Cooldown op) {
        StandPower power = session.bodyOf(op.actor()) instanceof LivingEntity body ? StandPower.get(body) : null;
        if (power == null || !power.hasPower()) {
            return false;
        }
        power.restoreAbilityCooldown(op.ability(), op.ticks(), op.total());
        return true;
    }

    /**
     * The end of a track a creature outlived: what the Stand power of its phantom has left to spend and to wait for is
     * its own from here. The power itself stays the one the body has, with the Stand it has out.
     */
    static void takePower(EpitaphSession session, Binding binding, LivingEntity body) {
        SandboxRoom.Phantom phantom = session.room().info(binding.track.key());
        StandPower from = phantom != null && phantom.entity instanceof LivingEntity copy ? StandPower.get(copy) : null;
        StandPower to = StandPower.get(body);
        if (from == null || to == null || !from.hasPower() || !to.hasPower()) {
            return;
        }
        // What the muted ticks booked for the next tick of this power is in the numbers of the phantom already: this tick
        // takes it, and what it leaves is written over at once.
        to.tick();
        to.setStamina(from.getStamina());
        List<PhantomActions.Wait> waits = PhantomActions.cooldowns((LivingEntity) phantom.entity);
        for (PhantomActions.Wait own : PhantomActions.cooldowns(body)) {
            if (waits.stream().noneMatch(wait -> wait.ability().equals(own.ability()))) {
                to.restoreAbilityCooldown(own.ability(), 0);
            }
        }
        for (PhantomActions.Wait wait : waits) {
            to.restoreAbilityCooldown(wait.ability(), wait.ticks(), wait.total());
        }
    }

    /**
     * One tick of the future for the actions the record put on the bodies, before the ops of that tick: what an action
     * does in it are those ops.
     */
    public static void step(EpitaphSession session, int tick) {
        for (Binding binding : session.binder().all()) {
            if (binding.body.isRemoved() || !(binding.body instanceof LivingEntity performer)) {
                continue;
            }
            for (int slot = 0; slot < binding.mirrored.length; slot++) {
                EntityActionInstance action = binding.mirrored[slot];
                if (action == null) {
                    continue;
                }
                LivingComponentAction component = LivingComponentAction.getComponentForSlot(performer, slot);
                if (component.getAction() == action) {
                    try {
                        muted(session, tick, component::tick);
                    }
                    catch (RuntimeException failure) {
                        fault(failure);
                        end(session, tick, component);
                    }
                }
                if (component.getAction() != action) {
                    binding.mirrored[slot] = null;
                }
            }
            // The lock a fresh Stand is under runs out in its own tick, which a body on a track does not have.
            if (performer instanceof StandEntity stand && stand.summonLockTicks > 0) {
                stand.summonLockTicks--;
            }
        }
    }

    /** After the ops of a tick: each action stands in the phase the record has for it then, and ends where it has none. */
    public static void align(EpitaphSession session, int tick) {
        for (Binding binding : session.binder().all()) {
            if (binding.body.isRemoved() || !(binding.body instanceof LivingEntity performer)) {
                continue;
            }
            for (int slot = 0; slot < binding.mirrored.length; slot++) {
                EntityActionInstance action = binding.mirrored[slot];
                if (action == null) {
                    continue;
                }
                LivingComponentAction component = LivingComponentAction.getComponentForSlot(performer, slot);
                EntityTrack.ActionMark mark = binding.track.mark(tick, slot);
                if (component.getAction() != action) {
                    binding.mirrored[slot] = null;
                } else if (mark == null) {
                    binding.mirrored[slot] = null;
                    end(session, tick, component);
                } else if (action.getPhase() != mark.phase() || (int) action.getPhaseTick() != mark.phaseTick()) {
                    Runnable set = () -> {
                        action.setPhase(mark.phase(), mark.phaseTick());
                        action.syncPhaseChanges();
                    };
                    try {
                        muted(session, tick, set);
                    }
                    catch (RuntimeException failure) {
                        fault(failure);
                        binding.mirrored[slot] = null;
                        end(session, tick, component);
                    }
                }
            }
        }
    }

    /**
     * A body is put on its track at the pull-back of a window (EPITAPH-ARCH, the plan after stage D, point 2): what it is
     * doing then ends, and what its copy took over at the press is put on it again, as the record puts an action on a
     * body, and held to the record from there. Not for a player: his tick is his.
     */
    public static void putBack(EpitaphSession session, Binding binding, @Nullable ActionReplayState[] taken) {
        if (!(binding.body instanceof LivingEntity performer) || performer instanceof Player) {
            return;
        }
        LivingEntity powerUser = performer instanceof StandEntity stand ? stand.getUser() : performer;
        int slots = performer instanceof StandEntity ? 1 : binding.mirrored.length;
        for (int slot = 0; slot < slots; slot++) {
            LivingComponentAction component = LivingComponentAction.getComponentForSlot(performer, slot);
            if (component.getAction() != null) {
                end(session, 0, component);
            }
            ActionReplayState state = taken != null ? taken[slot] : null;
            if (state != null && powerUser != null) {
                EntityActionInstance[] now = new EntityActionInstance[1];
                muted(session, 0, () -> now[0] = component.applyReplayState(state, powerUser, SyncType.TRACKING_AND_SELF));
                binding.mirrored[slot] = now[0] != null && component.getAction() == now[0] ? now[0] : null;
            }
        }
        // A key of his that held what has just ended holds nothing.
        if (performer instanceof StandEntity && powerUser instanceof Player holder) {
            dropKeys(holder, EntityActionInstance::isOver);
        }
    }

    /**
     * A body is put on its track at the press: the action it is in the middle of, which its copy took over in the room, is
     * ticked for it from here like one the record put on it, and held to the record. Not that of a player: his tick is his.
     */
    public static void adopt(Binding binding) {
        if (!(binding.body instanceof LivingEntity performer) || performer instanceof Player) {
            return;
        }
        int slots = performer instanceof StandEntity ? 1 : binding.mirrored.length;
        for (int slot = 0; slot < slots; slot++) {
            LivingComponentAction component = slot == LivingComponentAction.MAIN_SLOT
                    ? LivingComponentAction.getExistingComponent(performer)
                    : LivingComponentAction.getExistingComponent(performer, PowerClass.STAND);
            EntityActionInstance action = component != null ? component.getAction() : null;
            // Only what its copy took over: an action a room has no place for is left on the body as it was.
            binding.mirrored[slot] = action != null && !action.isOver() && binding.track.mark(0, slot) != null ? action : null;
            // The Stand of a walked player: the hold is that of its copy from here, and his key holds it no more.
            if (binding.mirrored[slot] != null && performer instanceof StandEntity stand && stand.getUser() instanceof Player holder) {
                dropKeys(holder, held -> held == action);
            }
        }
    }

    /**
     * The keys of a player that hold one of these actions hold it no more, as if let go, but the action is not told. For a
     * hold its copy took over, which is the hold of the copy from the press on (EPITAPH-ARCH, ruling R12), and for what
     * the press has just ended.
     */
    public static void dropKeys(LivingEntity user, Predicate<EntityActionInstance> which) {
        EntityActionInputState input = user.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT).orElse(null);
        if (input == null) {
            return;
        }
        for (HeldInputEntry entry : List.copyOf(input.heldKeys.values())) {
            if (entry.action instanceof EntityActionInstance held && which.test(held) && input.heldKeys.get(entry.keyId) == entry) {
                input.heldKeys.remove(entry.keyId);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(user,
                        TrAbilityUsePacket.releaseHold(user.getId(), entry.keyId, entry.generation));
            }
        }
    }

    /** A body leaves its track: an action the record put on it does not go on by itself. */
    public static void letGo(EpitaphSession session, Binding binding) {
        if (!(binding.body instanceof LivingEntity performer)) {
            return;
        }
        for (int slot = 0; slot < binding.mirrored.length; slot++) {
            EntityActionInstance action = binding.mirrored[slot];
            binding.mirrored[slot] = null;
            if (action == null || performer.isRemoved()) {
                continue;
            }
            LivingComponentAction component = LivingComponentAction.getComponentForSlot(performer, slot);
            if (component.getAction() == action) {
                end(session, session.passed(), component);
            }
        }
    }

    // Ends what a slot holds; the clients are told.
    private static void end(EpitaphSession session, int tick, LivingComponentAction component) {
        try {
            muted(session, tick, () -> component.setAction(null, SyncType.TRACKING_AND_SELF));
        }
        catch (RuntimeException failure) {
            fault(failure);
        }
    }

    private static void fault(RuntimeException failure) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.error("Epitaph[action] an action the record put on a body threw in its tick and is ended", failure);
        }
    }
}

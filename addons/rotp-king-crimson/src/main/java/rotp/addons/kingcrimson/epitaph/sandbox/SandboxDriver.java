package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.PhantomBrainDriver;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.Checkpoint;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.record.TrackFrame;
import rotp.addons.kingcrimson.mixin.epitaph.ServerPlayerStateAccessor;
import rotp.core.api.simulation.EntitySimulations;
import rotp.core.init.ModDataAttachmentTypes;

/**
 * Ticks a room. One step is one tick of the future: every phantom runs its own tick, in the order the level would
 * tick the bodies, with the core's work around it and no tick event (the driver posts none, and those the game posts
 * inside a tick are kept back by RoomEventGateMixin); then a frame of each is written. A phantom whose tick throws ends
 * alone, there.
 */
public final class SandboxDriver {
    /** How long a death is seen before the dead are gone. */
    private static final int DEATH_TICKS = 20;

    private SandboxDriver() {}

    public static void step(SandboxRoom room) {
        EpitaphSandbox.enter(room, EpitaphSandbox.Mode.SEALED);
        room.beginStep();
        try {
            for (SandboxRoom.Phantom phantom : order(room)) {
                if (phantom.ended || phantom.entity.isPassenger()) {
                    continue;
                }
                if (phantom.faulted || phantom.entity.isRemoved()) {
                    settle(room, phantom);
                } else {
                    tick(room, phantom, false);
                }
            }
            for (SandboxRoom.Phantom phantom : room.all()) {
                if (!phantom.ended) {
                    PhantomActions.note(room, phantom, false);
                    frame(room, phantom);
                }
            }
            PhantomActions.noteWaits(room);
        }
        finally {
            EpitaphSandbox.setActor(null);
            room.endStep();
            EpitaphSandbox.exit();
        }
    }

    /** The foresight is lived to its end: every phantom still in the room stands at the last frame of its track. */
    public static void finish(SandboxRoom room) {
        for (SandboxRoom.Phantom phantom : room.all()) {
            if (!phantom.ended) {
                close(room, phantom, EntityTrack.End.FORESIGHT, checkpoint(phantom));
            }
        }
    }

    // Roots by the id of their bodies, then children as they were spawned, human phantoms last.
    private static List<SandboxRoom.Phantom> order(SandboxRoom room) {
        List<SandboxRoom.Phantom> roots = new ArrayList<>();
        List<SandboxRoom.Phantom> children = new ArrayList<>();
        List<SandboxRoom.Phantom> humans = new ArrayList<>();
        for (SandboxRoom.Phantom phantom : room.all()) {
            (phantom.entity instanceof ServerPlayer ? humans : phantom.child ? children : roots).add(phantom);
        }
        roots.sort(Comparator.comparingInt(phantom -> phantom.key.id()));
        roots.addAll(children);
        roots.addAll(humans);
        return roots;
    }

    private static void tick(SandboxRoom room, SandboxRoom.Phantom phantom, boolean riding) {
        Entity entity = phantom.entity;
        EpitaphSandbox.setActor(entity);
        phantom.taken.clear();
        PhantomActions.note(room, phantom, true);
        try {
            // The work of the core for the start of a tick ends a sprint in a stun: for a phantom that is steered it comes
            // after the steering, as the tick event of a real player comes after his input.
            boolean steered = entity instanceof EpitaphPlayerPhantom;
            if (!steered) {
                EntitySimulations.runTickStartWork(entity);
            }
            entity.setOldPosAndRot();
            entity.tickCount++;
            if (riding && !steered) {
                entity.rideTick();
            } else if (entity instanceof ServerPlayer human) {
                if (human instanceof EpitaphPlayerPhantom body) {
                    PhantomBrainDriver.steer(body);
                    EntitySimulations.runTickStartWork(entity);
                }
                human.doTick();
                // Of what a real player does in ServerPlayer.tick, which a phantom does not run: the two clocks that
                // let the next hit through.
                ServerPlayerStateAccessor clocks = (ServerPlayerStateAccessor) human;
                clocks.kingCrimson$setSpawnInvulnerableTime(clocks.kingCrimson$spawnInvulnerableTime() - 1);
                if (human.invulnerableTime > 0) {
                    human.invulnerableTime--;
                }
                if (riding && entity.getVehicle() != null) {
                    // What the ride tick of an entity does after its own tick: its seat is its place, and it does not fall.
                    entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    entity.getVehicle().positionRider(entity);
                    entity.resetFallDistance();
                }
            } else {
                entity.tick();
            }
            EntitySimulations.runTickEndWork(entity);
            // What an add-on asked to do for a copy each tick, which gets no tick event.
            EntitySimulations.runTickListeners(entity);
        }
        catch (Throwable error) {
            SandboxGuards.fault(entity, error);
            room.counts().phantomFaults++;
            phantom.faulted = true;
        }
        settle(room, phantom);
        EpitaphSandbox.setActor(null);
        for (Entity rider : List.copyOf(entity.getPassengers())) {
            SandboxRoom.Phantom seated = room.info(rider);
            if (seated != null && !seated.ended && !rider.isRemoved() && rider.getVehicle() == entity) {
                tick(room, seated, true);
            }
        }
    }

    // What the tick of this phantom left behind: who is gone, what it wears now, where it sits.
    private static void settle(SandboxRoom room, SandboxRoom.Phantom actor) {
        for (SandboxRoom.Phantom other : room.all()) {
            if (other.ended) {
                continue;
            }
            if (other.dying) {
                close(room, other, EntityTrack.End.DEATH, null);
                other.entity.setRemoved(Entity.RemovalReason.KILLED);
            } else if (other.entity.isRemoved()) {
                room.record(new RecordedOp.Remove(other.key, actor.key, other.entity.getRemovalReason()));
                if (other != actor && other.entity instanceof ItemEntity) {
                    actor.taken.add(other.key);
                }
                close(room, other, EntityTrack.End.REMOVED, null);
            }
        }
        if (actor.ended) {
            return;
        }
        if (actor.faulted) {
            close(room, actor, EntityTrack.End.FAULT, null);
            return;
        }
        if (actor.atEdge) {
            close(room, actor, EntityTrack.End.EDGE, checkpoint(actor));
            return;
        }
        Entity entity = actor.entity;
        if (entity instanceof LivingEntity living && actor.equipment != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack now = living.getItemBySlot(slot);
                ItemStack before = actor.equipment[slot.ordinal()];
                // Wear alone is no change: the real hits wear the real things.
                if (!ItemStack.isSameItem(before, now) || before.getCount() != now.getCount()) {
                    actor.equipment[slot.ordinal()] = now.copy();
                    room.record(new RecordedOp.Equip(actor.key, slot, now.copy(), List.copyOf(actor.taken)));
                }
            }
        }
        if (entity.getVehicle() != actor.vehicle) {
            actor.vehicle = entity.getVehicle();
            room.record(new RecordedOp.Mount(actor.key, ActorKey.of(actor.vehicle)));
        }
    }

    private static void frame(SandboxRoom room, SandboxRoom.Phantom phantom) {
        if (phantom.track.lastTick() >= room.workingTick()) {
            return;
        }
        // What its tracker would tell a client of this tick: shown to the one who foresees, and the data it changed is
        // kept in the frame for the body.
        List<SynchedEntityData.DataValue<?>> changed = List.of();
        boolean shown = room.shows(phantom.entity);
        for (Packet<?> packet : phantom.tracker.changes()) {
            if (packet instanceof ClientboundSetEntityDataPacket data) {
                changed = data.packedItems();
            }
            if (shown) {
                room.showOf(phantom.entity, packet, 0);
            }
        }
        phantom.track.add(TrackFrame.of(phantom.entity, changed));
    }

    private static void close(SandboxRoom room, SandboxRoom.Phantom phantom, EntityTrack.End how,
            @Nullable Checkpoint state) {
        frame(room, phantom);
        if (room.shows(phantom.entity)) {
            if (how == EntityTrack.End.REMOVED) {
                room.showOf(phantom.entity, new ClientboundRemoveEntitiesPacket(phantom.key.id()), 0);
            } else if (how == EntityTrack.End.DEATH) {
                // It is seen to die, and is gone when a death has played out.
                room.showOf(phantom.entity, new ClientboundEntityEventPacket(phantom.entity, (byte) 3), 0);
                room.showOf(phantom.entity, new ClientboundRemoveEntitiesPacket(phantom.key.id()), DEATH_TICKS);
            }
        }
        phantom.track.close(how, state);
        room.end(phantom);
    }

    private static Checkpoint checkpoint(SandboxRoom.Phantom phantom) {
        Entity entity = phantom.entity;
        ActorKey target = entity instanceof Mob mob ? ActorKey.of(mob.getTarget()) : ActorKey.NONE;
        return new Checkpoint(entity.saveWithoutId(new CompoundTag()), phantom.stripped, target);
    }

    /**
     * What a client is sent of an entity it begins to track, for the eyes the room is lived for: the packets that make
     * the phantom there, and what the core syncs of an entity to a player who begins to track it.
     */
    static void pair(SandboxRoom room, SandboxRoom.Phantom phantom) {
        SandboxView view = room.view();
        if (view == null || phantom == null || !room.shows(phantom.entity)) {
            return;
        }
        boolean outside = EpitaphSandbox.depth == 0;
        if (outside) {
            EpitaphSandbox.enter(room, EpitaphSandbox.Mode.SEALED);
        }
        ServerPlayer before = EpitaphSandbox.viewer();
        EpitaphSandbox.setViewer(view.viewer());
        try {
            for (Packet<?> packet : phantom.tracker.pairing(view.viewer())) {
                room.showOf(phantom.entity, packet, 0);
            }
            var listeners = ModDataAttachmentTypes.DATA_EVENT_HELPER.get();
            if (phantom.entity.hasData(listeners)) {
                phantom.entity.getData(listeners).onTracking(view.viewer());
            }
        }
        catch (RuntimeException error) {
            SandboxGuards.unshown(phantom.entity, error);
            room.counts().notShown++;
        }
        finally {
            EpitaphSandbox.setViewer(before);
            if (outside) {
                EpitaphSandbox.exit();
            }
        }
    }

    /**
     * The core's work for an entity that joins a level, for a phantom, which joins none. What it does to the world is
     * absorbed without a record: a root joined long ago, and a child joins for real when it is spawned.
     */
    static void joinQuietly(SandboxRoom room, Entity entity) {
        SandboxRoom.Phantom phantom = room.info(entity);
        boolean outside = EpitaphSandbox.depth == 0;
        if (outside) {
            EpitaphSandbox.enter(room, EpitaphSandbox.Mode.SEALED);
        }
        Entity acting = EpitaphSandbox.actor();
        room.mute();
        room.silence();
        EpitaphSandbox.setActor(entity);
        try {
            EntitySimulations.runJoinWork(entity, phantom == null || !phantom.child);
        }
        catch (Throwable error) {
            SandboxGuards.fault(entity, error);
            room.counts().phantomFaults++;
            if (phantom != null) {
                phantom.faulted = true;
            }
        }
        finally {
            EpitaphSandbox.setActor(acting);
            room.unsilence();
            room.unmute();
            if (outside) {
                EpitaphSandbox.exit();
            }
        }
    }
}

package rotp.addons.kingcrimson.epitaph.sandbox;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.ScheduledTick;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.core.core.JojoMod;
import rotp.core.network.s2c.StandSkinSoundPacket;

/**
 * What the barrier does with packets and scheduled ticks made in a room. Nothing is sent. A packet for the trackers of
 * a phantom, or for everybody near a place, is kept as bytes, encoded now, to go to the real trackers when the bodies
 * get there; one sent straight to a player is dropped and counted.
 */
public final class SandboxPackets {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int dropped;
    private static boolean encodeFailureLogged;

    private SandboxPackets() {}

    public static void tracker(Entity about, Packet<?> packet, boolean andSelf) {
        SandboxRoom room = EpitaphSandbox.room();
        // What is muted is not to happen again for real, but the one who foresees still sees it (a phantom flinches).
        boolean shown = room.shows(about);
        if (room.muted() && !shown) {
            return;
        }
        if (shown) {
            room.showOf(about, packet, 0);
        }
        if (room.muted()) {
            return;
        }
        byte[] bytes = encode(room, packet);
        if (bytes != null) {
            room.record(new RecordedOp.Packets(EpitaphSandbox.actorKey(), ActorKey.of(about), bytes, andSelf, ofTheCore(packet)));
        }
    }

    public static void area(Entity except, double x, double y, double z, double radius, Packet<?> packet) {
        SandboxRoom room = EpitaphSandbox.room();
        if (room.muted()) {
            return;
        }
        byte[] bytes = encode(room, packet);
        room.show(packet);
        if (bytes != null) {
            room.record(new RecordedOp.AreaPackets(EpitaphSandbox.actorKey(), ActorKey.of(except), x, y, z, radius, bytes));
        }
    }

    /** A packet room code sent straight to the one who foresees, while a phantom is being shown to him: it is his to see. */
    public static void toViewer(Packet<?> packet) {
        EpitaphSandbox.room().show(packet);
    }

    /** Particles of the room, for the eyes of the one who foresees. */
    public static void viewParticles(ParticleOptions particle, boolean far, double x, double y, double z, int count,
            double xSpread, double ySpread, double zSpread, double speed) {
        EpitaphSandbox.room().show(new ClientboundLevelParticlesPacket(particle, far, x, y, z, (float) xSpread, (float) ySpread,
                (float) zSpread, (float) speed, count));
    }

    public static void viewLevelEvent(int type, BlockPos pos, int data, boolean global) {
        EpitaphSandbox.room().show(new ClientboundLevelEventPacket(type, pos, data, global));
    }

    /** A sound of the room: shown only where the phantoms are to be heard. */
    public static void viewSound(@Nullable Entity on, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed) {
        SandboxRoom room = EpitaphSandbox.room();
        if (room.view() == null || !room.view().sounds()) {
            return;
        }
        if (on != null && room.shows(on)) {
            room.showOf(on, new ClientboundSoundEntityPacket(sound, source, on, volume, pitch, seed), 0);
        } else if (on == null || !room.hides(on)) {
            room.show(new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed));
        }
    }

    /**
     * A packet that room code sent straight to a player. The sound of a blow of a Stand, which the core sends so to each
     * player in earshot, is for the one who foresees where the phantoms are to be heard (EPITAPH-ARCH, ruling R14): it goes
     * into the frame of its tick, and his client plays it by its rule for the sounds of phantoms. All else is dropped.
     */
    public static void straight(@Nullable ServerPlayer to, Packet<?> packet) {
        SandboxRoom room = EpitaphSandbox.room();
        if (to != null && room != null && !room.muted() && room.view() != null && room.view().sounds() && room.view().viewer() == to
                && packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof StandSkinSoundPacket) {
            room.show(packet);
            return;
        }
        drop(packet);
    }

    public static void drop(Packet<?> packet) {
        dropped++;
        SandboxRoom room = EpitaphSandbox.room();
        if (room != null) {
            room.counts().dropped++;
        }
    }

    public static int dropped() {
        return dropped;
    }

    public static void schedule(ScheduledTick<?> tick) {
        SandboxRoom room = EpitaphSandbox.room();
        int delay = (int) Math.max(0L, tick.triggerTick() - room.level().getLevelData().getGameTime());
        Object what = tick.type();
        if (what instanceof Block block) {
            room.record(new RecordedOp.ScheduleTick(EpitaphSandbox.actorKey(), tick.pos(), block, null, delay, tick.priority()));
        } else if (what instanceof Fluid fluid) {
            room.record(new RecordedOp.ScheduleTick(EpitaphSandbox.actorKey(), tick.pos(), null, fluid, delay, tick.priority()));
        }
    }

    // The core syncs powers and actions by the performer's UUID and a generation of its own counter: those packets are for
    // the view of the phantoms, and what they tell of is made again on the bodies: by the action that ticks muted on its
    // performer, from which they alone go out.
    public static boolean ofTheCore(Packet<?> packet) {
        return packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload().type().id().getNamespace().equals(JojoMod.MOD_ID);
    }

    private static byte[] encode(SandboxRoom room, Packet<?> packet) {
        try {
            return PacketBytes.encode(packet, room.level().registryAccess());
        }
        catch (RuntimeException failure) {
            dropped++;
            if (!encodeFailureLogged) {
                encodeFailureLogged = true;
                LOGGER.warn("Epitaph[packet] a packet made in a prediction could not be encoded and is left out: {}",
                        packet.getClass().getName(), failure);
            }
            return null;
        }
    }
}

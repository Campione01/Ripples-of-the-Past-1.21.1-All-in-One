package rotp.addons.kingcrimson.client.epitaph;

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;

/**
 * Applies the frames of a prediction on the client. A frame is the packets of one tick of the foreseen future; they
 * are read with the codec they were written with and handled by the game's own handlers, inside the flag, so that
 * what they add, move and remove is a phantom. After each frame the phantoms live one tick. The frames that came
 * since the last client tick are applied in that tick, one after the other: while the phantoms run through the future
 * faster than time, they live several ticks in one.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantomFrameApplier {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A phantom is moved towards where a packet put it over three ticks: after the last frame it still gets them. */
    private static final int SETTLE_TICKS = 3;

    private final EpitaphClientSession session;
    private final Deque<EpitaphPhantomFramePayload> waiting = new ArrayDeque<>();
    private int settle;
    private boolean failureLogged;

    PhantomFrameApplier(EpitaphClientSession session) {
        this.session = session;
    }

    /** From the network handler, on the client thread. */
    public static void accept(EpitaphPhantomFramePayload frame) {
        EpitaphClientSession session = EpitaphClientSession.current();
        if (session == null || session.id() != frame.session()) {
            return;
        }
        if (frame.tick() == EpitaphPhantomFramePayload.NOW) {
            session.frames().apply(frame);
        } else {
            session.frames().waiting.add(frame);
        }
    }

    boolean isResting() {
        return waiting.isEmpty() && settle == 0;
    }

    /** Once a client tick. */
    void tick() {
        if (waiting.isEmpty()) {
            if (settle > 0) {
                settle--;
                live();
            }
            return;
        }
        while (!waiting.isEmpty()) {
            apply(waiting.poll());
            live();
        }
        settle = SETTLE_TICKS;
    }

    private void apply(EpitaphPhantomFramePayload frame) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        PhantomContext.active = true;
        PhantomContext.applying = true;
        try {
            for (byte[] bytes : frame.packets()) {
                for (Packet<? super ClientGamePacketListener> packet : PacketBytes.decode(bytes, connection.registryAccess())) {
                    handle(packet, connection);
                }
            }
        }
        catch (RuntimeException failure) {
            // A frame that cannot be read is left out; the next ones may still be whole.
            warn("a frame of tick " + frame.tick() + " could not be read", failure);
        }
        finally {
            PhantomContext.applying = false;
            PhantomContext.active = false;
        }
    }

    private void handle(Packet<? super ClientGamePacketListener> packet, ClientPacketListener connection) {
        try {
            if (packet instanceof ClientboundAddEntityPacket add && add.getType() == EntityType.PLAYER) {
                session.world().addHuman(add);
            } else {
                packet.handle(connection);
            }
        }
        catch (RuntimeException failure) {
            warn("a " + packet.getClass().getSimpleName() + " of a phantom could not be handled", failure);
        }
    }

    private void live() {
        PhantomContext.active = true;
        try {
            session.world().tick();
        }
        finally {
            PhantomContext.active = false;
        }
    }

    private void warn(String what, RuntimeException failure) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn("Epitaph[client-frame] {}", what, failure);
        }
    }
}

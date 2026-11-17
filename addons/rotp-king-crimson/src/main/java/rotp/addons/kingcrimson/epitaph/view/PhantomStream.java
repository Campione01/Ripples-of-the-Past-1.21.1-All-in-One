package rotp.addons.kingcrimson.epitaph.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxView;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;

/**
 * What one prediction shows the one who foresees. The room hands it the packets of its phantoms as it lives each tick
 * of the future; they are kept as bytes, a frame per tick, and a frame is sent when the user is to be shown that
 * tick. Only he is sent them: to everybody else the future has not happened.
 */
public final class PhantomStream implements SandboxView {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerPlayer viewer;
    private final int session;
    private final boolean sounds;
    private final List<List<Shown>> frames = new ArrayList<>();
    private int sent = -1;
    private int unwritten;

    /** A packet as it is kept: its bytes, the phantom it is about, and whether it makes an entity on the client. */
    private record Shown(int about, byte[] bytes, boolean makes) {}

    /** One more who is shown a part of it: the phantoms this answers yes for. */
    private record Told(ServerPlayer player, IntPredicate about) {}

    private final List<Told> told = new ArrayList<>();

    public PhantomStream(ServerPlayer viewer, int session, int foresightTicks) {
        this.viewer = viewer;
        this.session = session;
        this.sounds = KingCrimsonConfig.epitaphPhantomSounds();
        for (int tick = 0; tick <= foresightTicks; tick++) {
            frames.add(new ArrayList<>());
        }
    }

    @Override
    public ServerPlayer viewer() {
        return viewer;
    }

    // Whom the room does not hide is shown: the phantom of a human who is not walked is hidden by its prediction.
    @Override
    public boolean shows(Entity phantom) {
        return true;
    }

    @Override
    public boolean sounds() {
        return sounds;
    }

    // Written now, not when it is sent: a packet may read the phantom as it is written, and by then the phantom has
    // lived on. What comes for a tick the user was already sent goes with the next frame.
    @Override
    public void show(int tick, int about, Packet<?> packet) {
        byte[] bytes = written(packet);
        if (bytes != null) {
            frames.get(Math.max(0, Math.min(Math.max(tick, sent + 1), frames.size() - 1)))
                    .add(new Shown(about, bytes, makesAnEntity(packet)));
        }
    }

    private static boolean makesAnEntity(Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> part : bundle.subPackets()) {
                if (makesAnEntity(part)) {
                    return true;
                }
            }
            return false;
        }
        return packet instanceof ClientboundAddEntityPacket || packet instanceof ClientboundAddExperienceOrbPacket;
    }

    /**
     * The packets of one tick of the future, as they are sent: those that make an entity first, the others after them,
     * each kind in the order it was made. A client parks what it is told of an entity it does not know yet by the int id,
     * and would later hand it to the real entity of that id (ruling R7).
     */
    public List<byte[]> frame(int tick) {
        return frame(tick, about -> true);
    }

    /** The same frame, with only the packets about the phantoms this answers yes for. */
    public List<byte[]> frame(int tick, IntPredicate about) {
        List<byte[]> packets = new ArrayList<>();
        for (Shown shown : frames.get(tick)) {
            if (shown.makes() && about.test(shown.about())) {
                packets.add(shown.bytes());
            }
        }
        for (Shown shown : frames.get(tick)) {
            if (!shown.makes() && about.test(shown.about())) {
                packets.add(shown.bytes());
            }
        }
        return List.copyOf(packets);
    }

    /**
     * From now on this player is sent, with the one who foresees, every frame as far as it is about these phantoms, and
     * first the frames that were sent already. He is told of the prediction: to anybody else the future has not happened.
     */
    public void alsoTo(ServerPlayer player, IntPredicate about) {
        notTo(player);
        told.add(new Told(player, about));
        for (int tick = 0; tick <= sent; tick++) {
            send(player, new EpitaphPhantomFramePayload(session, tick, frame(tick, about)));
        }
    }

    /** This player is sent no more of it. */
    public void notTo(ServerPlayer player) {
        told.removeIf(one -> one.player() == player);
    }

    /**
     * These phantoms leave the view: what was still to be sent about them is taken out of the frames, and they go from
     * the screen at once.
     */
    public void forget(Collection<Integer> phantoms) {
        for (int tick = sent + 1; tick < frames.size(); tick++) {
            frames.get(tick).removeIf(shown -> phantoms.contains(shown.about()));
        }
        for (int id : phantoms) {
            arrived(new ActorKey(id));
        }
    }

    /** The last tick of the future the user was sent, -1 before the first. */
    public int sentThrough() {
        return sent;
    }

    /** Sends every frame up to this tick that the user has not been sent yet, the empty ones too: a frame is a tick. */
    public void sendThrough(int tick) {
        int last = Math.min(tick, frames.size() - 1);
        while (sent < last) {
            sent++;
            send(new EpitaphPhantomFramePayload(session, sent, frame(sent)));
            for (Told one : told) {
                send(one.player(), new EpitaphPhantomFramePayload(session, sent, frame(sent, one.about())));
            }
        }
    }

    /** A body stands where its phantom stood, or the thing it foresaw is real now: that phantom goes at once. */
    public void arrived(ActorKey key) {
        byte[] gone = written(new ClientboundRemoveEntitiesPacket(key.id()));
        if (gone != null) {
            send(new EpitaphPhantomFramePayload(session, EpitaphPhantomFramePayload.NOW, List.of(gone)));
            for (Told one : told) {
                if (one.about().test(key.id())) {
                    send(one.player(), new EpitaphPhantomFramePayload(session, EpitaphPhantomFramePayload.NOW, List.of(gone)));
                }
            }
        }
    }

    /** How many packets could not be written and were left out. */
    public int unwritten() {
        return unwritten;
    }

    @Nullable
    private byte[] written(Packet<?> packet) {
        try {
            return PacketBytes.encode(packet, viewer.registryAccess());
        }
        catch (RuntimeException failure) {
            if (unwritten++ == 0) {
                LOGGER.warn("Epitaph[packet] a {} of a phantom could not be written and is not shown", packet.getClass().getName(),
                        failure);
            }
            return null;
        }
    }

    private void send(EpitaphPhantomFramePayload frame) {
        if (viewer.connection != null && !viewer.hasDisconnected()) {
            PacketDistributor.sendToPlayer(viewer, frame);
        }
    }

    // To a player who is told that the prediction is aimed at him: his part of a frame.
    private static void send(ServerPlayer told, EpitaphPhantomFramePayload part) {
        if (told.connection != null && !told.hasDisconnected()) {
            PacketDistributor.sendToPlayer(told, part);
        }
    }
}

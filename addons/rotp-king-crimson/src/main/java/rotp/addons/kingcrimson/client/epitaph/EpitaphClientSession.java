package rotp.addons.kingcrimson.client.epitaph;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.core.api.client.time.ClientEntityClockRates;

/**
 * The local player's own prediction, as his client knows it: that it runs, in which mode, which bodies are held to
 * their tracks, and the world of its phantoms. There is one at most; it ends when the server says so, and when the
 * player leaves the level it began in.
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphClientSession {
    @Nullable
    private static EpitaphClientSession current;

    private final int id;
    private final EpitaphMode mode;
    private final IntSet bodies;
    private final boolean sounds;
    private final EpitaphPhantomWorld world;
    private final PhantomFrameApplier frames;
    private boolean inPhase = true;

    private EpitaphClientSession(EpitaphSessionPayload begin, ClientLevel level) {
        this.id = begin.session();
        this.mode = begin.mode();
        this.bodies = new IntOpenHashSet(begin.bodies());
        this.sounds = begin.sounds();
        this.world = new EpitaphPhantomWorld(level);
        this.frames = new PhantomFrameApplier(this);
    }

    /** From the network handler, on the client thread. */
    public static void accept(EpitaphSessionPayload payload) {
        switch (payload.kind()) {
            case BEGIN -> {
                end();
                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) {
                    current = new EpitaphClientSession(payload, level);
                    LocalPlayer player = Minecraft.getInstance().player;
                    ForcedWalk.localPlayerSlowed = payload.mode() == EpitaphMode.SOLO_SLOW && player != null
                            && current.bodies.contains(player.getId());
                }
            }
            case PHASE_OVER -> {
                if (current != null && current.id == payload.session()) {
                    current.inPhase = false;
                    ForcedWalk.localPlayerSlowed = false;
                }
            }
            case END -> {
                if (current != null && current.id == payload.session()) {
                    end();
                }
            }
        }
    }

    @Nullable
    public static EpitaphClientSession current() {
        return current;
    }

    /**
     * Whether the local player foresees: a prediction runs on his client and it is his own. One he is only told of, since
     * it is aimed at him, shows him his phantom and no more.
     */
    public static boolean isOwn() {
        EpitaphClientSession session = current;
        return session != null && !EpitaphNotice.isOf(session.id);
    }

    int id() {
        return id;
    }

    EpitaphPhantomWorld world() {
        return world;
    }

    PhantomFrameApplier frames() {
        return frames;
    }

    /** Once a client tick: the frames that came are applied; a prediction of a level the player has left is over. */
    public static void tick() {
        EpitaphClientSession session = current;
        if (session == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != session.world.level() || minecraft.player == null) {
            end();
            return;
        }
        session.frames.tick();
    }

    /** Every phantom goes, whatever state the prediction was in. */
    public static void end() {
        EpitaphClientSession session = current;
        current = null;
        PhantomContext.active = false;
        PhantomContext.applying = false;
        ForcedWalk.localPlayerSlowed = false;
        if (session != null) {
            session.world.clear();
        }
    }

    /** From the level, inside the flag: an entity a packet of a frame made. True when it was taken as a phantom. */
    public static boolean adopt(Entity entity) {
        EpitaphClientSession session = current;
        if (session == null) {
            return false;
        }
        session.world.add(entity);
        return true;
    }

    /** From the level, inside the flag: a packet of a frame removes the phantom with this id, and nothing else. */
    public static void discard(int entityId) {
        EpitaphClientSession session = current;
        if (session != null) {
            session.world.remove(entityId);
        }
    }

    /** From the level, inside the flag: its lookups with the phantoms before the world. */
    public static LevelEntityGetter<Entity> over(LevelEntityGetter<Entity> worldEntities) {
        EpitaphClientSession session = current;
        return session != null ? session.world.over(worldEntities) : worldEntities;
    }

    /** Whether no frame moves the phantoms any more: they stand at their last pose. */
    public static boolean isResting() {
        EpitaphClientSession session = current;
        return session == null || session.frames.isResting();
    }

    /** Whether the sounds of the phantoms are to be heard: the server's setting, told at the beginning. */
    public static boolean hearsPhantoms() {
        EpitaphClientSession session = current;
        return session != null && session.sounds;
    }

    /** Whether this very entity is a phantom of the running prediction. */
    public static boolean isPuppet(@Nullable Entity entity) {
        EpitaphClientSession session = current;
        return session != null && session.world.isPuppet(entity);
    }

    /**
     * The rate the animations of a body run at. With one player online the bodies on their tracks live a quarter as
     * fast while the phantoms run; the server moves them so, and their own clocks follow.
     */
    public static float bodyRate(Entity entity) {
        EpitaphClientSession session = current;
        if (session == null || !session.inPhase || session.mode != EpitaphMode.SOLO_SLOW || !session.bodies.contains(entity.getId())
                || session.world.isPuppet(entity)) {
            return ClientEntityClockRates.NO_RATE;
        }
        return (float) KingCrimsonEpitaphForesightSpec.SOLO_BODY_RATE;
    }
}

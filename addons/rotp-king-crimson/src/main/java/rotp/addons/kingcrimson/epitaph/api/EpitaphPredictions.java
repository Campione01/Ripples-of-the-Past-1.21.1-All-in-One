package rotp.addons.kingcrimson.epitaph.api;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;

/**
 * What the rest of the add-on, and other add-ons, may ask of Epitaph's predictions. Each answer costs one read of a
 * static flag while no prediction runs.
 */
public final class EpitaphPredictions {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The rule of one add-on. */
    private record Exemption(ResourceLocation owner, Predicate<ServerPlayer> rule) {}

    private static final List<Exemption> EXEMPTIONS = new CopyOnWriteArrayList<>();
    private static boolean ruleFailureLogged;

    private EpitaphPredictions() {}

    /**
     * Another add-on names players whom a prediction that is aimed at them neither walks nor pulls back (the holders of
     * Gold Experience Requiem). Such a player is told of the prediction instead: a sign on his screen and the frames of
     * his own phantom, for as long as isPredictedPlayer answers true for him; and one call of breakPrediction ends it
     * for him. Nothing foreseen of him or for him ever happens to him, broken or not.
     * <p>
     * One rule for each owner: a second call with the same owner takes the place of the first. The rule is asked on the
     * server thread, once for each player under the crosshair at the press of a prediction, and what it answers then
     * holds for that prediction. It must answer without changing anything. Call this at the setup of the add-on.
     */
    public static void registerExemption(ResourceLocation owner, Predicate<ServerPlayer> rule) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(rule, "rule");
        EXEMPTIONS.removeIf(known -> known.owner().equals(owner));
        EXEMPTIONS.add(new Exemption(owner, rule));
    }

    /** The rule of this owner is asked no more. */
    public static void removeExemption(ResourceLocation owner) {
        EXEMPTIONS.removeIf(known -> known.owner().equals(owner));
    }

    /** Whether the rule of an add-on accepts this player now. A rule that throws answers no, and the log says so once. */
    public static boolean isExempt(ServerPlayer player) {
        for (Exemption known : EXEMPTIONS) {
            try {
                if (known.rule().test(player)) {
                    return true;
                }
            }
            catch (RuntimeException failure) {
                if (!ruleFailureLogged) {
                    ruleFailureLogged = true;
                    LOGGER.warn("Epitaph[exemption] the rule of {} threw and is taken for a no", known.owner(), failure);
                }
            }
        }
        return false;
    }

    /**
     * The one press that breaks it. When a prediction is aimed at this player and tells him so (a rule exempted him at its
     * press, and isPredictedPlayer answers true for him), his phantom ends there and then and he is told no more; the
     * prediction of the other goes on without him, and nothing is given back to its user. For anybody else nothing
     * changes. On the server thread.
     *
     * @param cause what broke it, for the log (the id of the ability)
     * @return whether a prediction was broken: the caller lets that press do nothing else
     */
    public static boolean breakPrediction(ServerPlayer player, ResourceLocation cause) {
        EpitaphSession session = EpitaphSessions.any ? EpitaphSessions.telling(player) : null;
        if (session == null) {
            return false;
        }
        session.breakFor(player, cause);
        return true;
    }

    /** Whether any prediction runs on this server. */
    public static boolean any() {
        return EpitaphSessions.any;
    }

    /**
     * Whether the entity is a body that is being carried along the track of a prediction: its future is fixed until
     * its track ends, and it does not tick by itself.
     */
    public static boolean isOnTrack(@Nullable Entity entity) {
        return EpitaphSessions.any && EpitaphSessions.isOnTrack(entity);
    }

    /**
     * Whether the prediction of another player is aimed at this one and holds him: from its press until he is let go or
     * his part of it is void. For a player a rule exempts: from the press until he breaks it, its phantoms have been
     * watched (in a window: until the pull-back), or it ends. The one who foresees is not asked about here.
     */
    public static boolean isPredictedPlayer(@Nullable Player player) {
        return EpitaphSessions.any && EpitaphSessions.predicts(player);
    }

    /** Whether this player is being walked along his foreseen track: his own keys and moves do nothing meanwhile. */
    public static boolean isForcedWalking(@Nullable Entity player) {
        return EpitaphSessions.any && EpitaphSessions.isWalked(player);
    }

    /**
     * From Time Erase, whenever an erase of this player begins, by key or by itself: his own part of his prediction is
     * void from that moment (his walk ends, nothing foreseen of him or for him happens). Everybody else goes on as
     * recorded.
     */
    public static void onUserEraseBegan(ServerPlayer user) {
        EpitaphSession session = EpitaphSessions.any ? EpitaphSessions.of(user) : null;
        if (session != null) {
            session.noteErase();
        }
    }

    /** From the step of Time Erase, when it takes this player away: it frees him as an erase does. */
    public static void onUserStepped(ServerPlayer user) {
        onUserEraseBegan(user);
    }
}

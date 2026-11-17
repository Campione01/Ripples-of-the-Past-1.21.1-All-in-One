package rotp.addons.kingcrimson.epitaph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.epitaph.human.PhantomPlayerPool;
import rotp.addons.kingcrimson.epitaph.replay.ActionMirror;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxGuards;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.addons.kingcrimson.epitaph.window.WindowCover;
import rotp.core.api.time.event.RewindAppliedEvent;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The predictions that run, one per user at most, and their entry points from the game: the press, the level tick,
 * and whatever ends one. {@link #any} is the one flag the hooks of the replay read: false whenever no prediction runs.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class EpitaphSessions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Whether any prediction runs. Every hook of the replay starts by reading it. */
    public static boolean any;
    private static final List<EpitaphSession> SESSIONS = new ArrayList<>();
    private static int lastId;
    /** Only a test sets it: the next prediction fails as it starts, as one does whose first steps throw. */
    public static boolean failNextStart;

    private EpitaphSessions() {}

    /**
     * Starts the prediction of a user at the press: who is foreseen is chosen now, the room is built and the bodies
     * are bound. Null when he already has one or Epitaph is switched off.
     */
    @Nullable
    public static EpitaphSession begin(ServerPlayer user, @Nullable Entity crosshair) {
        if (!KingCrimsonConfig.epitaphEnabled() || of(user) != null) {
            return null;
        }
        return predict(user, EpitaphTargeting.select(user, crosshair, KingCrimsonConfig.epitaphRadius(),
                KingCrimsonConfig.epitaphMaxTargets()), crosshair);
    }

    /** Starts the prediction of exactly these entities and of the user, with nothing under his crosshair. */
    @Nullable
    public static EpitaphSession predict(ServerPlayer user, List<Entity> targets) {
        return predict(user, targets, null);
    }

    /** What a press does once it has chosen whom: the prediction of these entities and of the user begins. */
    @Nullable
    public static EpitaphSession predict(ServerPlayer user, List<Entity> targets, @Nullable Entity crosshair) {
        if (of(user) != null) {
            return null;
        }
        StandPower power = StandPower.get(user);
        int resolve = power != null ? power.getResolveLevel() : 0;
        boolean alone = playersOnline(user.server) <= 1;
        // A player under the crosshair with the pull-back switched on: real play for the phase, then everybody is put back.
        boolean window = !alone && KingCrimsonConfig.epitaphPullBack() && WindowCover.payloads && crosshair instanceof ServerPlayer
                && targets.contains(crosshair);
        EpitaphMode mode = alone ? EpitaphMode.SOLO_SLOW : window ? EpitaphMode.MULTI_WINDOW : EpitaphMode.MULTI_BOUND;
        int phase = alone ? KingCrimsonEpitaphForesightSpec.phaseTicks(resolve)
                : KingCrimsonConfig.epitaphMultiplayerPhaseTicks(resolve);
        EpitaphSession session;
        try {
            session = new EpitaphSession(++lastId, user, mode, KingCrimsonConfig.epitaphForesightTicks(resolve), phase, targets,
                    crosshair);
        }
        catch (RuntimeException failure) {
            LOGGER.error("Epitaph[build] the prediction of {} could not be built", user.getScoreboardName(), failure);
            return null;
        }
        SESSIONS.add(session);
        any = true;
        try {
            // From here to its end others see that he foresees (the face), but not one the prediction is aimed at.
            user.setData(KingCrimsonAddon.EPITAPH_FORESEEING, Unit.INSTANCE);
            session.begin();
            if (failNextStart) {
                failNextStart = false;
                throw new IllegalStateException("the prediction fails as it starts, as a test asked");
            }
            session.runAhead();
        }
        catch (RuntimeException failure) {
            fail(session, failure);
            return null;
        }
        return session;
    }

    /**
     * What a player who is walked from the press was in the middle of ends there, before the copies are made. An action
     * of his own two slots is not in saved data and cannot be put on him from the record, so his copy starts without
     * it and he must start as his copy does. Ended as the Stand cancel key of the core ends one: with the keys that
     * held it and a press waiting behind it. The Stand itself stays out, where it is. For the one who foresees his
     * Stand ends what it does as well (EPITAPH-ARCH, rulings R4 and R12 point 5); the Stand of a player he aimed at
     * goes on, and its copy takes that over.
     */
    static void endActionsInProgress(ServerPlayer player, boolean standToo) {
        endAction(LivingComponentAction.getExistingComponent(player));
        endAction(LivingComponentAction.getExistingComponent(player, PowerClass.STAND));
        StandPower power = StandPower.get(player);
        if (power == null) {
            return;
        }
        if (standToo) {
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                endAction(LivingComponentAction.getExistingComponent(stand));
            }
            power.interruptHeldInputs();
        } else {
            // The keys of what has just ended hold nothing; one that holds what his Stand does is looked at with its copy.
            ActionMirror.dropKeys(player, held -> held.isOver());
        }
        player.getExistingData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT)
                .ifPresent(input -> input.inputBuffer(PowerClass.STAND).clear());
    }

    private static void endAction(@Nullable LivingComponentAction slot) {
        if (slot != null && slot.getAction() != null) {
            slot.setAction(null, SyncType.TRACKING_AND_SELF);
        }
    }

    static void remove(EpitaphSession session, EpitaphEnd reason) {
        SESSIONS.remove(session);
        any = !SESSIONS.isEmpty();
    }

    @Nullable
    public static EpitaphSession of(@Nullable Player user) {
        for (EpitaphSession session : SESSIONS) {
            if (session.user() == user) {
                return session;
            }
        }
        return null;
    }

    public static List<EpitaphSession> all() {
        return List.copyOf(SESSIONS);
    }

    /** The binding of a body that is being carried along its track, null for anything else. */
    @Nullable
    public static Binding bindingOf(@Nullable Entity entity) {
        for (EpitaphSession session : SESSIONS) {
            Binding binding = session.binder().of(entity);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    /** Whether the tick of this entity is held because it is on a track. Counted as a hook going past its flag. */
    public static boolean holdsTick(Entity entity) {
        EpitaphSandbox.countPass();
        Binding binding = bindingOf(entity);
        return binding != null && binding.walk == null;
    }

    /**
     * Whether this player is told that the holder foresees: everybody who sees him is, but a player his prediction is
     * aimed at, who must not read off his face that a prediction runs.
     */
    public static boolean showsFaceTo(IAttachmentHolder holder, ServerPlayer to) {
        EpitaphSession session = holder instanceof ServerPlayer user ? of(user) : null;
        return session == null || !session.aimsAt(to);
    }

    /** The prediction that is aimed at this player and tells him so, null when none does. */
    @Nullable
    public static EpitaphSession telling(@Nullable Player player) {
        for (EpitaphSession session : SESSIONS) {
            if (session.tells(player)) {
                return session;
            }
        }
        return null;
    }

    /**
     * Whether the tick of the being of this player is held now: he is walked, and his track passed no whole tick in the
     * last server tick (EPITAPH-ARCH, ruling R5). Counted as a hook going past its flag.
     */
    public static boolean holdsBeing(ServerPlayer player) {
        EpitaphSandbox.countPass();
        EpitaphSession session = of(player);
        return session != null && session.holdsBeingOfUser();
    }

    /** Whether this is a player who is being walked along his foreseen track. */
    public static boolean isWalked(@Nullable Entity entity) {
        Binding binding = any ? bindingOf(entity) : null;
        return binding != null && binding.walk != null;
    }

    /** The prediction that walks this player along his track, his own or that of another; null when none does. */
    @Nullable
    public static EpitaphSession walking(@Nullable Entity player) {
        for (EpitaphSession session : SESSIONS) {
            Binding binding = session.binder().of(player);
            if (binding != null && binding.walk != null) {
                return session;
            }
        }
        return null;
    }

    /** Whether this player is walked by his own prediction: he is the one who foresees, and has a way out. */
    public static boolean walksItsUser(@Nullable Entity player) {
        EpitaphSession session = any ? walking(player) : null;
        return session != null && session.user() == player;
    }

    /** Whether the prediction of another player is aimed at this one and holds him still. */
    public static boolean predicts(@Nullable Player player) {
        for (EpitaphSession session : SESSIONS) {
            if (session.predicts(player)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOnTrack(@Nullable Entity entity) {
        return any && bindingOf(entity) != null;
    }

    /** Whether a prediction holds this entity: it is on its track, or it is in a window and will be put back. */
    public static boolean holds(@Nullable Entity entity) {
        return any && (bindingOf(entity) != null || WindowJournal.isParticipant(entity));
    }

    /** What the core asks of every entity its own state would take in: whether it is a phantom. */
    public static boolean simulates(Entity entity) {
        if (!any) {
            return false;
        }
        EpitaphSandbox.countPass();
        return isPhantom(entity);
    }

    /** Whether this very object is a phantom of a running prediction: a copy that is not in the world. */
    public static boolean isPhantom(@Nullable Entity entity) {
        return roomOf(entity) != null;
    }

    /** The room this very object is a phantom of, null for anything else. */
    @Nullable
    public static SandboxRoom roomOf(@Nullable Entity entity) {
        for (EpitaphSession session : SESSIONS) {
            if (session.room().owns(entity)) {
                return session.room();
            }
        }
        return null;
    }

    /**
     * Whether one player at most is on the server: what the module calls single-player. A prediction asks at its press,
     * a Time Erase at its start.
     */
    public static boolean isAlone(MinecraftServer server) {
        return playersOnline(server) <= 1;
    }

    // Players on the server, whatever level they are in; the phantoms of players are none.
    private static int playersOnline(MinecraftServer server) {
        int online = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!(player instanceof FakePlayer)) {
                    online++;
                }
            }
        }
        return online;
    }

    /**
     * The rate a prediction holds an entity to: with one player online the bodies on their tracks live a quarter as
     * fast during the phantom phase. Answered for the channel other add-ons read and for Stand actions; the bodies'
     * motion and ticks are the puppet clock's already.
     */
    public static float rate(Entity entity, EntityTimeRates.Channel channel) {
        if (!any) {
            return EntityTimeRates.NO_RATE;
        }
        EpitaphSandbox.countPass();
        // An action the record put on a body is ticked once for each tick of the future its body passes: its pace is in that.
        if (EpitaphSandbox.mirroring() || channel != EntityTimeRates.Channel.BEING && channel != EntityTimeRates.Channel.STAND_ACTIONS) {
            return EntityTimeRates.NO_RATE;
        }
        for (EpitaphSession session : SESSIONS) {
            if (session.mode() == EpitaphMode.SOLO_SLOW && session.clock().inPhase() && session.binder().of(entity) != null
                    && !session.isOnOwnTime(entity)) {
                return (float) KingCrimsonEpitaphForesightSpec.SOLO_BODY_RATE;
            }
        }
        return EntityTimeRates.NO_RATE;
    }

    /**
     * The share of his stamina a Stand user gains in this tick (EPITAPH-ARCH, ruling R10). One who is held to his track in
     * the slow phase lives at the pace of that track, and his phantom filled its bar once for each of its ticks: his own
     * fills by that share for each tick of the server. 1 for everybody else, on his own time and after the phase. His
     * client answers the same from what it was told, or his bar would run ahead on his screen.
     */
    public static float staminaGain(StandPower power) {
        LivingEntity user = power.getUser();
        if (user == null) {
            return 1.0F;
        }
        if (user.level().isClientSide()) {
            return ForcedWalk.localPlayerWalked && ForcedWalk.localPlayerSlowed && user instanceof Player player
                    && player.isLocalPlayer() ? (float) KingCrimsonEpitaphForesightSpec.SOLO_BODY_RATE : 1.0F;
        }
        float rate = rate(user, EntityTimeRates.Channel.BEING);
        return Float.isFinite(rate) ? rate : 1.0F;
    }

    private static void fail(EpitaphSession session, Throwable failure) {
        // A warning: the game goes on without the prediction.
        LOGGER.warn("Epitaph[failed] the prediction of {} failed and is called off", session.user().getScoreboardName(), failure);
        if (EpitaphSandbox.depth != 0) {
            EpitaphSandbox.exit();
        }
        EpitaphSandbox.setReplaying(false);
        session.end(EpitaphEnd.FAULT);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTickStart(LevelTickEvent.Pre event) {
        if (!any || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        EpitaphSandbox.countPass();
        for (EpitaphSession session : all()) {
            if (session.level() == level) {
                try {
                    session.tick();
                }
                catch (RuntimeException failure) {
                    fail(session, failure);
                }
            }
        }
    }

    // What the level's tick took away is no longer carried.
    @SubscribeEvent
    public static void onLevelTickEnd(LevelTickEvent.Post event) {
        if (!any || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        EpitaphSandbox.countPass();
        for (EpitaphSession session : all()) {
            if (session.level() == level) {
                for (Binding binding : session.binder().all()) {
                    if (binding.body.isRemoved() || binding.body.level() != level) {
                        if (binding.body instanceof ServerPlayer gone) {
                            session.voidPartOf(gone);
                        }
                        session.unbind(binding.body);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (any && event.getEntity() instanceof ServerPlayer player) {
            EpitaphSandbox.countPass();
            EpitaphSession session = of(player);
            if (session != null) {
                session.end(EpitaphEnd.USER_DIED);
            }
            freeFromOthers(player);
        }
    }

    // A player the prediction of another walks, who died, logged out or left the level: he goes free where he is, as a
    // player like any other, and nothing foreseen of him or for him happens from there.
    private static void freeFromOthers(ServerPlayer player) {
        EpitaphSession walking = walking(player);
        if (walking != null) {
            walking.voidPartOf(player);
        }
        // In a window he is not walked yet: he is a hole, and what he did stays.
        WindowJournal.holeIfParticipant(player, "he died, logged out or left the level");
        // One who was told of it is told no more, and what was guessed of him goes.
        EpitaphSession telling = telling(player);
        if (telling != null) {
            telling.untell(player);
        }
    }

    // Posted by a rewind right after it has put things back (Return to Zero of Gold Experience Requiem, and any other).
    @SubscribeEvent
    public static void onRewindApplied(RewindAppliedEvent event) {
        if (!any) {
            return;
        }
        EpitaphSandbox.countPass();
        for (EpitaphSession session : all()) {
            if (session.level() == event.getLevel()) {
                session.rewound(event.getRegion());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EpitaphSession session = any ? of(event.getEntity()) : null;
        if (session != null) {
            session.end(EpitaphEnd.USER_LOGGED_OUT);
        }
        if (any && event.getEntity() instanceof ServerPlayer player) {
            freeFromOthers(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        EpitaphSession session = any ? of(event.getEntity()) : null;
        if (session != null) {
            session.end(EpitaphEnd.USER_LEFT_LEVEL);
        }
        if (any && event.getEntity() instanceof ServerPlayer player) {
            freeFromOthers(player);
        }
    }

    // Before the world is saved for the last time: nothing of a prediction is ever saved.
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (EpitaphSession session : all()) {
            session.end(EpitaphEnd.SERVER_STOPPING);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        any = false;
        SandboxGuards.reset();
        PhantomPlayerPool.reset();
        EpitaphSandbox.resetLeaks();
    }
}

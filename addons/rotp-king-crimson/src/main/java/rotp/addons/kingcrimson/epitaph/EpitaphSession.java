package rotp.addons.kingcrimson.epitaph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseAbility;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.epitaph.human.PhantomBrain;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.EpitaphRecord;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.replay.ActionMirror;
import rotp.addons.kingcrimson.epitaph.replay.FateRules;
import rotp.addons.kingcrimson.epitaph.replay.OpExecutor;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.replay.PuppetTick;
import rotp.addons.kingcrimson.epitaph.replay.ReleaseState;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomFactory;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDriver;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.addons.kingcrimson.epitaph.view.PhantomStream;
import rotp.addons.kingcrimson.epitaph.window.PullBack;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal;
import rotp.addons.kingcrimson.network.EpitaphNoticePayload;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.addons.kingcrimson.network.EpitaphShortcutPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * One prediction: the room where the future is lived ahead, the record it leaves, and the bodies that then carry the
 * record out. Each server tick, before the level ticks, the clocks advance, the room is kept ahead of them, and for
 * every tick of the future the bodies pass they are placed on their tracks and the ops of that tick happen for real.
 */
public final class EpitaphSession {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int id;
    private final ServerPlayer user;
    private final ServerLevel level;
    private final EpitaphMode mode;
    private final EpitaphClock clock;
    private final EpitaphRecord record;
    private final SandboxRoom room;
    private final PhantomStream stream;
    private final PuppetBinder binder = new PuppetBinder();
    /** The real entities of the prediction by key: the bodies of the roots, and what was spawned for real since. */
    private final Map<Integer, Entity> bodies = new HashMap<>();
    /**
     * Humans whose phantoms are foreseen while their bodies are not carried: nothing foreseen of or for them happens.
     * The user from the press when he is not to be walked; any walked player from the moment his part is void.
     */
    private final Set<ActorKey> unwalked = new HashSet<>();
    /** The journal of the window, from the press to the pull-back of a prediction that has one; null at any other time. */
    @Nullable
    private WindowJournal window;
    private final boolean userWalkedFromPress;
    /** The players a rule exempted at the press: never walked, never pulled back, told instead. */
    private final Set<ServerPlayer> exempt = new HashSet<>();
    /** Those of them who are still told: their phantoms are shown, to the user and to themselves. */
    private final List<ServerPlayer> told = new ArrayList<>();
    private boolean userVoid;
    private boolean eraseNoted;
    /** The deaths that are about to happen as they were foreseen, by body: their loot is the loot of the record. */
    private final Map<Integer, ForeseenDeath> foreseenDeaths = new HashMap<>();
    private final long budgetNanos;
    private final int leaksAtStart = EpitaphSandbox.leaks();
    private boolean phaseOverTold;
    private boolean roomDone;
    private boolean ended;
    @Nullable
    private EpitaphEnd endedAs;
    private boolean paused;
    private int passed;
    /** Whether the last server tick of the prediction carried the bodies past a whole tick of the future. */
    private boolean wholeTickPassed;
    /** The walk let the way out of the user through: until that comes to nothing or frees him, his time is his own. */
    private boolean ownTime;
    private long ownTimeJudgedFrom;

    EpitaphSession(int id, ServerPlayer user, EpitaphMode mode, int foresightTicks, int phaseTicks, List<Entity> targets,
            @Nullable Entity crosshair) {
        this.id = id;
        this.user = user;
        this.level = user.serverLevel();
        this.mode = mode;
        this.clock = new EpitaphClock(mode, foresightTicks, phaseTicks);
        this.record = new EpitaphRecord(foresightTicks);
        this.room = new SandboxRoom(level, record, KingCrimsonConfig.epitaphMaxChildren());
        this.stream = new PhantomStream(user, id, foresightTicks);
        room.watch(stream);
        this.budgetNanos = KingCrimsonConfig.epitaphSimBudgetNanos();
        this.userWalkedFromPress = KingCrimsonConfig.epitaphUserFollowsPhantom();
        boolean inWindow = mode == EpitaphMode.MULTI_WINDOW;
        List<Entity> roots = new ArrayList<>(targets);
        if (!user.isPassenger()) {
            roots.add(user);
        } else if (userWalkedFromPress) {
            // A user who rides is one with what he rides (the owner, 2026-11-11), as a player he aims at is: both are
            // foreseen and he is walked on it. One who keeps control keeps his seat and his mount, and neither is
            // foreseen; so it is when what he rides cannot be foreseen.
            for (Entity member : EpitaphTargeting.groupOf(user)) {
                if (!roots.contains(member)) {
                    roots.add(member);
                }
            }
        }
        for (Entity target : targets) {
            // Asked once, at the press: what he is at the press is what he is for this prediction.
            if (target instanceof ServerPlayer human && human != user && EpitaphPredictions.isExempt(human)) {
                exempt.add(human);
            }
        }
        for (Entity root : roots) {
            if (!(root instanceof ServerPlayer human)) {
                continue;
            }
            if (exempt.contains(human)) {
                // Nothing of his ends, and his phantom is shown: it is what the mind guesses of him (EPITAPH-ARCH 3.7).
                continue;
            }
            if (!walked(human)) {
                // A human who is not walked: his part is void from the press, and his phantom, which only stands, is
                // nobody to watch.
                room.hide(ActorKey.of(root));
            } else if (!inWindow) {
                // What he was in the middle of ends before the copies are made: only what is walked from the press.
                EpitaphSessions.endActionsInProgress(human, human == user);
            } else if (human == user) {
                // In a window nothing ends at the press. What the Stand of the user does is taken over by no copy all
                // the same: it ends when he is pulled back (EPITAPH-ARCH, rulings R4 point 3 and R12 point 5).
                room.leaveOut(ActorKey.of(root));
            }
        }
        List<Entity> made = PhantomFactory.build(room, roots);
        for (Entity real : made) {
            bodies.put(real.getId(), real);
            EntityTrack track = record.track(real.getId());
            if (real instanceof ServerPlayer human) {
                if (track == null || !walked(human)) {
                    unwalked.add(ActorKey.of(real));
                } else if (!inWindow) {
                    bind(real, track).walk = new ForcedWalk(human);
                }
            } else if (!inWindow && track != null && !unwalked.contains(track.owner())) {
                // The Stand of a human who is not walked stays his own, as he does.
                ActionMirror.adopt(bind(real, track));
            }
        }
        if (!walked(user) || record.track(user.getId()) == null) {
            userVoid = true;
            unwalked.add(ActorKey.of(user));
        }
        if (inWindow) {
            openWindow(made);
        }
        giveMinds(crosshair);
    }

    // Whether this human is walked along what his phantom does: the user by his setting, a player the prediction is
    // aimed at always. From the press, or in a window from the pull-back.
    private boolean walked(ServerPlayer human) {
        return human == user ? userWalkedFromPress : !exempt.contains(human);
    }

    // The window opens (EPITAPH-ARCH 3.5): everybody who will be walked or carried is kept as he is now, and from here
    // to the pull-back what he does to the world is written down. Nobody is bound; the bodies wait at tick 0.
    private void openWindow(List<Entity> made) {
        window = WindowJournal.open(level, user.getScoreboardName(), (gone, why) -> voidPart(gone));
        for (Entity real : made) {
            EntityTrack track = record.track(real.getId());
            if (track != null && !unwalked.contains(track.owner()) && !unwalked.contains(track.key())) {
                window.join(real, track.owner().equals(track.key()) ? null : bodies.get(track.owner().id()));
            }
        }
        for (Entity real : made) {
            // What he moves in a container cannot be put back blind.
            if (real instanceof ServerPlayer human && human.containerMenu != human.inventoryMenu) {
                WindowJournal.holeIfParticipant(human, "he had a container open at the press");
            }
        }
        clock.holdBodies();
    }

    // A walked human is led by what his phantom does. That of the user fights what hunts him, and what he aimed at if
    // that is a foe; that of a player he aimed at fights him back, with the same mind. A human who is not walked gets
    // none: his phantom stands for nobody to be led by.
    private void giveMinds(@Nullable Entity crosshair) {
        Entity own = room.phantom(user.getId());
        Entity aimed = crosshair instanceof Enemy || crosshair instanceof Player ? room.phantom(crosshair.getId()) : null;
        for (Entity body : List.copyOf(bodies.values())) {
            if (!(body instanceof ServerPlayer human) || unwalked.contains(ActorKey.of(human)) && !exempt.contains(human)
                    || !(room.phantom(human.getId()) instanceof EpitaphPlayerPhantom copy)) {
                continue;
            }
            if (human == user) {
                PhantomBrain.give(room, copy, foe -> foe instanceof Mob mob && mob.getTarget() == copy || foe == aimed);
            } else {
                PhantomBrain.give(room, copy, foe -> foe == own);
            }
        }
    }

    /** The number the client of the user knows this prediction by. */
    public int id() {
        return id;
    }

    public ServerPlayer user() {
        return user;
    }

    /** What the user is shown of the phantoms. */
    public PhantomStream stream() {
        return stream;
    }

    public ServerLevel level() {
        return level;
    }

    public EpitaphMode mode() {
        return mode;
    }

    public EpitaphClock clock() {
        return clock;
    }

    public EpitaphRecord record() {
        return record;
    }

    public SandboxRoom room() {
        return room;
    }

    public PuppetBinder binder() {
        return binder;
    }

    public boolean ended() {
        return ended;
    }

    /** Whether this prediction foresees that other player: he is one of those it was aimed at. */
    public boolean aimsAt(ServerPlayer player) {
        return player != user && bodies.get(player.getId()) == player;
    }

    /**
     * Whether this prediction is aimed at that other player and holds him still: from the press until he is let go or his
     * part is void.
     */
    public boolean predicts(@Nullable Player player) {
        if (player == null || player == user || bodies.get(player.getId()) != player) {
            return false;
        }
        // In a window he is not on his track yet, and is predicted all the same until he is a hole.
        WindowJournal.Participant waiting = window != null ? window.of(player) : null;
        return binder.of(player) != null || waiting != null && !waiting.isHole() || told.contains(player);
    }

    /** Whether a rule exempted this player at the press of this prediction. */
    public boolean exempts(@Nullable Player player) {
        return exempt.contains(player);
    }

    /** Whether this player is told of this prediction now: it is aimed at him, a rule exempted him, and it is not broken. */
    public boolean tells(@Nullable Player player) {
        return told.contains(player);
    }

    // He is told, and from here he is sent what the phantoms that are his do.
    private void tell(ServerPlayer player) {
        if (player.isRemoved() || player.connection == null || player.hasDisconnected() || bodies.get(player.getId()) != player) {
            return;
        }
        ActorKey key = ActorKey.of(player);
        told.add(player);
        PacketDistributor.sendToPlayer(player, new EpitaphNoticePayload(id, true));
        PacketDistributor.sendToPlayer(player, new EpitaphSessionPayload(id, EpitaphSessionPayload.Kind.BEGIN, mode, clock.foresight(),
                clock.phase(), stream.sounds(), new int[0]));
        stream.alsoTo(player, about -> {
            SandboxRoom.Phantom phantom = room.info(new ActorKey(about));
            return phantom != null && phantom.owner.equals(key);
        });
    }

    /**
     * He is told no more, and his phantom and what is its leave the view of the user: he broke the prediction, its phantom
     * phase is over, or it ended. Nothing foreseen of him or for him ever happens, told or not.
     */
    public void untell(ServerPlayer player) {
        if (!told.remove(player)) {
            return;
        }
        ActorKey key = ActorKey.of(player);
        room.hide(key);
        List<Integer> gone = new ArrayList<>();
        for (SandboxRoom.Phantom phantom : room.all()) {
            if (phantom.owner.equals(key)) {
                gone.add(phantom.key.id());
            }
        }
        stream.forget(gone);
        stream.notTo(player);
        if (player.connection != null && !player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, EpitaphSessionPayload.of(id, EpitaphSessionPayload.Kind.END));
            PacketDistributor.sendToPlayer(player, new EpitaphNoticePayload(id, false));
        }
    }

    private void untellAll() {
        for (ServerPlayer player : List.copyOf(told)) {
            untell(player);
        }
    }

    /**
     * The one call that breaks it for a player who is told (EPITAPH-ARCH 3.7): his phantom ends there and then. The
     * prediction goes on without him, and nothing is given back to its user.
     */
    public void breakFor(ServerPlayer player, ResourceLocation cause) {
        if (told.contains(player)) {
            LOGGER.info("Epitaph[break] {}: {} broke the prediction that was aimed at him ({})", user.getScoreboardName(),
                    player.getScoreboardName(), cause);
            untell(player);
        }
    }

    /** Why the prediction is over, null while it runs. */
    @Nullable
    public EpitaphEnd endedAs() {
        return endedAs;
    }

    public boolean paused() {
        return paused;
    }

    /** The last whole tick of the future whose ops were carried out. */
    public int passed() {
        return passed;
    }

    /** The real entity that stands under a key now, null when it has none or is gone. */
    @Nullable
    public Entity bodyOf(ActorKey key) {
        Entity body = bodies.get(key.id());
        return body != null && !body.isRemoved() ? body : null;
    }

    /** Whether the real entity under this key was there and is gone. */
    public boolean isGone(ActorKey key) {
        Entity body = bodies.get(key.id());
        return body != null && body.isRemoved();
    }

    public Binding bind(Entity body, EntityTrack track) {
        bodies.put(body.getId(), body);
        if (body instanceof Mob mob) {
            // A pause of its thinking it had at the press is lived by its copy, which was given it (T5).
            MobAiPause.end(mob);
        }
        return binder.bind(body, track);
    }

    /** A real entity of the prediction that is on no track: a dropped item, an entity past the cap of children. */
    public void registerFree(Entity entity) {
        bodies.put(entity.getId(), entity);
        stream.arrived(ActorKey.of(entity));
    }

    private record ForeseenDeath(LivingEntity body, RecordedOp.Death death) {}

    /** The next death of this body is the one that was foreseen. */
    public void expectDeath(LivingEntity body, RecordedOp.Death death) {
        foreseenDeaths.put(body.getId(), new ForeseenDeath(body, death));
    }

    public void forgetDeath(Entity body) {
        ForeseenDeath foreseen = foreseenDeaths.get(body.getId());
        if (foreseen != null && foreseen.body == body) {
            foreseenDeaths.remove(body.getId());
        }
    }

    /** The foreseen death this body is dying of, taken out so that it is used once; null for any other death. */
    @Nullable
    public RecordedOp.Death takeForeseenDeath(LivingEntity body) {
        ForeseenDeath foreseen = foreseenDeaths.get(body.getId());
        if (foreseen == null || foreseen.body != body) {
            return null;
        }
        foreseenDeaths.remove(body.getId());
        return foreseen.death;
    }

    /** Takes a body off its track without an end state: it was removed, or it died as foreseen. A walked player is free. */
    public void unbind(Entity body) {
        Binding binding = binder.release(body);
        if (binding != null) {
            PuppetBinder.givePause(binding);
            stream.arrived(ActorKey.of(body));
            if (binding.walk != null) {
                binding.walk.end();
            }
            ActionMirror.letGo(this, binding);
        }
    }

    /** The user is told that his prediction began and is sent the phantoms as they stand at the press. */
    void begin() {
        int[] held = new int[binder.all().size()];
        int index = 0;
        for (Binding binding : binder.all()) {
            held[index++] = binding.body.getId();
        }
        tell(new EpitaphSessionPayload(id, EpitaphSessionPayload.Kind.BEGIN, mode, clock.foresight(), clock.phase(),
                stream.sounds(), held));
        for (ServerPlayer player : exempt) {
            tell(player);
        }
        stream.sendThrough(0);
        for (Binding binding : binder.all()) {
            if (binding.walk != null) {
                binding.walk.begin();
            }
        }
    }

    private void tell(EpitaphSessionPayload what) {
        if (user.connection != null && !user.hasDisconnected()) {
            PacketDistributor.sendToPlayer(user, what);
        }
    }

    /** A human is foreseen but not walked: what was foreseen of him, of what he made, and for him does not happen. */
    public boolean skips(RecordedOp op) {
        if (unwalked.isEmpty()) {
            return false;
        }
        if (unwalked.contains(record.ownerOf(op))) {
            return true;
        }
        ActorKey on = switch (op) {
            case RecordedOp.Damage damage -> damage.victim();
            case RecordedOp.Death death -> death.victim();
            case RecordedOp.Effect effect -> effect.victim();
            case RecordedOp.EffectRemoved gone -> gone.victim();
            case RecordedOp.Packets packets -> packets.about();
            default -> ActorKey.NONE;
        };
        return unwalked.contains(on);
    }

    /** Whether the op is the own of a walked player, or was done to one: after such an op an erase of his is looked for. */
    public boolean touchesWalked(RecordedOp op) {
        ActorKey on = op instanceof RecordedOp.Damage damage ? damage.victim()
                : op instanceof RecordedOp.Effect effect ? effect.victim() : ActorKey.NONE;
        return isWalked(record.ownerOf(op)) || isWalked(on);
    }

    private boolean isWalked(ActorKey key) {
        Binding binding = key.isNone() || !(bodies.get(key.id()) instanceof ServerPlayer human) ? null : binder.of(human);
        return binding != null && binding.walk != null;
    }

    /**
     * The user began an erase, or stepped through erased time (EPITAPH-ARCH section 4, T2 and T8): his own part of the
     * prediction is void from this moment, whatever becomes of the erase. In the middle of an op of the record it is
     * taken note of, and done when the op is.
     */
    public void noteErase() {
        eraseNoted = true;
        if (!EpitaphSandbox.replaying()) {
            checkErase();
        }
    }

    /** Looked at as each tick of the prediction begins and after each op that touched a walked player. */
    public void checkErase() {
        if (!userVoid && (eraseNoted || KingCrimsonTemporalStateService.isErasing(user))) {
            voidPart(user);
        }
        // A player the prediction is aimed at who erases time, by a King Crimson of his own: his part is void as well.
        for (Entity body : List.copyOf(bodies.values())) {
            if (body != user && body instanceof ServerPlayer walked && !unwalked.contains(ActorKey.of(walked))
                    && KingCrimsonTemporalStateService.isErasing(walked)) {
                voidPart(walked);
            }
        }
    }

    public boolean userVoid() {
        return userVoid;
    }

    /**
     * A player lived through the death that was foreseen for him (EPITAPH-ARCH, ruling R14). From here he is as a user
     * whose part is void: he and what is his go free where they stand, his Stand stays out, and no later op of his own
     * or on him is carried out.
     */
    public void livedThroughHisDeath(Entity player) {
        if (player instanceof ServerPlayer human) {
            voidPartOf(human);
        }
    }

    /**
     * From here nothing foreseen of this player or for him happens, and he and what is his go free where they stand: he
     * died, logged out or left the level, or lived through the death foreseen for him. Everybody else goes on as recorded.
     */
    public void voidPartOf(ServerPlayer player) {
        if (bodies.get(player.getId()) == player && !unwalked.contains(ActorKey.of(player))) {
            voidPart(player);
        }
    }

    /**
     * A walked player pressed his way out, Time Erase or its step, and the walk let it through (EPITAPH-ARCH, ruling R5;
     * for a player the prediction is aimed at, the decision of the owner of 2026-11-11). His Stand is his again at once. For
     * the one who foresees, his time is his own from here, though his body stays on its track until his part is void: his
     * tick is not held and no rate is answered for him or his Stand, so that the wind-up of his erase is as long as always.
     */
    public void wayOutPressed(ServerPlayer walked) {
        Binding own = binder.of(walked);
        if (own != null && own.walk != null) {
            // His Stand is his again at once: it is the Stand that winds his erase up, and a body on a track does not tick.
            for (Binding binding : binder.all()) {
                if (binding.body instanceof StandEntity stand && stand.getUser() == walked) {
                    unbind(stand);
                }
            }
            if (walked == user) {
                ownTime = true;
                // The press is asked before it does anything: what came of it is looked at from the tick after next.
                ownTimeJudgedFrom = level.getGameTime() + 2L;
            }
        }
    }

    public boolean ownTime() {
        return ownTime;
    }

    /** Whether this is the user or his Stand while his time is his own. */
    public boolean isOnOwnTime(Entity entity) {
        return ownTime && (entity == user || entity instanceof StandEntity stand && stand.getUser() == user);
    }

    /**
     * Whether the tick of the being of the user is held now: he is walked, his time is not his own, and the last server
     * tick took his track past no whole tick (in the slow phase three server ticks in four). His being then lives at
     * the pace of his track: hunger, air, fire, his effects, his cooldowns.
     */
    public boolean holdsBeingOfUser() {
        Binding own = binder.of(user);
        return own != null && own.walk != null && !ownTime && !wholeTickPassed && !TimeStopState.shouldFreezeOnServer(user);
    }

    private boolean wayOutUnderWay() {
        if (KingCrimsonTemporalStateService.isErasing(user)) {
            return true;
        }
        StandPower power = StandPower.get(user);
        StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
        return isWayOut(LivingComponentAction.getCurEntityAction(user, PowerClass.STAND))
                || stand != null && isWayOut(LivingComponentAction.getCurEntityAction(stand));
    }

    private static boolean isWayOut(@Nullable EntityActionInstance action) {
        // The step is no action: it is done within its press.
        return action != null && !action.isOver() && action.ability instanceof KingCrimsonTimeEraseAbility;
    }

    // His walk ends where he stands, his phantom and what it made leave the view with the loot of the deaths that will
    // not happen now, and no later op of his or on him is carried out. Everybody else goes on as recorded.
    private void voidPart(Entity player) {
        if (player == user) {
            userVoid = true;
        }
        ActorKey key = ActorKey.of(player);
        unwalked.add(key);
        if (window != null) {
            // In a window: he is a hole, and is not pulled back.
            window.leave(player, "nothing foreseen of him happens any more");
        }
        unbind(player);
        // What is his in the world goes free with him: his Stand, and what he threw.
        for (Binding binding : binder.all()) {
            if (binding.track.owner().equals(key)) {
                unbind(binding.body);
            }
        }
        room.hide(key);
        List<Integer> gone = new ArrayList<>();
        for (SandboxRoom.Phantom phantom : room.all()) {
            if (phantom.owner.equals(key)) {
                gone.add(phantom.key.id());
            }
        }
        for (int tick = passed + 1; tick <= record.foresight(); tick++) {
            for (RecordedOp op : record.ops(tick)) {
                if (op instanceof RecordedOp.Death death && key.equals(record.ownerOf(op))) {
                    for (RecordedOp.Drop drop : death.drops()) {
                        room.unsee(drop.child());
                        gone.add(drop.child().id());
                    }
                }
            }
        }
        stream.forget(gone);
    }

    /**
     * A rewind went over this region (EPITAPH-ARCH, the ruling on question 7): a rewind is the one power that undoes what
     * happened, so for whoever of the prediction is in the region, or was taken out of the world by it, the prediction is
     * over. One the prediction was aimed at, or its user, leaves it with what is his, and nothing foreseen of him or for
     * him happens any more: a creature stands where the rewind put it, as a player whose part is void does. A thing of
     * one who stays leaves alone. In a window they are holes. Everybody else goes on as recorded.
     */
    public void rewound(AABB region) {
        if (ended) {
            return;
        }
        for (Entity body : List.copyOf(bodies.values())) {
            ActorKey key = ActorKey.of(body);
            EntityTrack track = record.track(body.getId());
            if (track == null || unwalked.contains(key) || unwalked.contains(track.owner())
                    || !body.isRemoved() && !body.getBoundingBox().intersects(region)
                    || binder.of(body) == null && (window == null || window.of(body) == null)) {
                continue;
            }
            if (track.owner().equals(key)) {
                voidPart(body);
                if (body instanceof Mob mob && !mob.isRemoved()) {
                    mob.getNavigation().stop();
                }
            } else {
                unwalked.add(key);
                if (window != null) {
                    window.leave(body, "a rewind went over it");
                }
                unbind(body);
                room.unsee(key);
                stream.forget(List.of(key.id()));
            }
        }
    }

    /** A creature is let go where it stands, as it is: its track is no longer its fate. */
    public void letGo(Entity body) {
        Binding binding = binder.of(body);
        if (binding != null) {
            unbind(body);
            if (body instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            FateRules.payDebt(binding);
        }
    }

    /** Lives the room ahead while this much time lasts: at the press, and to fill a tick's budget. */
    void runAhead() {
        simulate(0);
    }

    /** Lives the room to the end of the foresight now, whatever it costs: for a measurement, not for play. */
    public void foreseeAll() {
        simulate(clock.foresight());
    }

    /** Whether the room has lived all it will: to the end of the foresight, or until nothing was left in it. */
    public boolean foreseen() {
        return roomDone;
    }

    /** One server tick of the prediction, before the level ticks. */
    void tick() {
        if (ended) {
            return;
        }
        if (user.isRemoved() || user.level() != level) {
            end(EpitaphEnd.USER_LEFT_LEVEL);
            return;
        }
        if (!KingCrimsonAddon.isKingCrimsonUser(user)) {
            end(EpitaphEnd.STAND_LOST);
            return;
        }
        wholeTickPassed = false;
        if (ownTime && (userVoid || level.getGameTime() >= ownTimeJudgedFrom && !wayOutUnderWay())) {
            ownTime = false;
        }
        paused = isHeld();
        if (paused) {
            return;
        }
        checkErase();
        int before = passed;
        clock.advance();
        // In a window the bodies wait at the press and do not drive the room: it lives a tick for each tick of the server
        // at the least, and what the budget allows beyond that.
        simulate(window != null ? room.tick() + 1 : (int) Math.ceil(clock.body() - 1.0E-9D));
        clock.holdShownTo(room.tick());
        show();
        if (window != null) {
            if (!windowIsOver()) {
                return;
            }
            pullBack();
        }
        carry();
        wholeTickPassed = passed > before;
        if (passed >= clock.foresight()) {
            end(EpitaphEnd.COMPLETE);
        }
    }

    // Stopped time that holds the user or a body holds the whole prediction, and so does a frozen tick rate.
    private boolean isHeld() {
        if (!level.tickRateManager().runsNormally() || TimeStopState.shouldFreezeOnServer(user)) {
            return true;
        }
        for (Binding binding : binder.all()) {
            if (!binding.body.isRemoved() && TimeStopState.shouldFreezeOnServer(binding.body)) {
                return true;
            }
        }
        if (window != null) {
            for (WindowJournal.Participant participant : window.participants()) {
                if (!participant.isHole() && !participant.entity().isRemoved() && TimeStopState.shouldFreezeOnServer(participant.entity())) {
                    return true;
                }
            }
        }
        return false;
    }

    // The window is over when the phantoms have run their whole future and the phase has passed. A room that cannot keep
    // up stretches it, to twice the phase; then the prediction is cut to what was lived.
    private boolean windowIsOver() {
        return clock.elapsed() >= clock.phase() && roomDone || clock.elapsed() >= 2 * clock.phase();
    }

    /**
     * The end of the window (EPITAPH-ARCH 5.4), in one server tick: everybody who took part is put back as he was at the
     * press, with what he did undone; then the bodies are bound, the players walked, and the record is carried out from
     * its first tick. Who could not be put back is a hole: he keeps what he did, and nothing foreseen of him happens.
     */
    private void pullBack() {
        WindowJournal journal = window;
        window = null;
        untellAll();
        if (!roomDone) {
            SandboxDriver.finish(room);
            roomDone = true;
        }
        for (WindowJournal.Participant participant : journal.participants()) {
            // A prediction of his own that another player of the window runs is undone with the rest.
            EpitaphSession own = !participant.isHole() && participant.entity() instanceof ServerPlayer human && human != user
                    ? EpitaphSessions.of(human) : null;
            if (own != null) {
                own.end(EpitaphEnd.CANCELLED);
            }
        }
        boolean whole = PullBack.run(journal);
        clock.releaseBodies();
        if (!whole) {
            return;
        }
        for (WindowJournal.Participant participant : journal.participants()) {
            Entity real = participant.entity();
            EntityTrack track = record.track(real.getId());
            if (participant.isHole() || track == null || unwalked.contains(track.owner()) || unwalked.contains(track.key())) {
                continue;
            }
            Binding binding = bind(real, track);
            if (real instanceof ServerPlayer human) {
                // What he is in the middle of ends here, as at the press of a prediction that binds at once (ruling R4).
                EpitaphSessions.endActionsInProgress(human, human == user);
                binding.walk = new ForcedWalk(human);
                binding.walk.begin();
            } else {
                SandboxRoom.Phantom phantom = room.info(track.key());
                ActionMirror.putBack(this, binding, phantom != null ? phantom.takenOver() : null);
            }
            PuppetTick.place(level, binding, 0);
        }
    }

    /** Ends the window now, whatever its clock says, with the room as far as it has lived: for a test, which then sees the pull-back. */
    public void closeWindowNow() {
        if (window != null && !ended) {
            pullBack();
        }
    }

    /** The journal of the window while it is open, null before a press that has none and after the pull-back. */
    @Nullable
    public WindowJournal window() {
        return window;
    }

    // The user is sent every tick of the future the shown clock has passed: the phantoms do not wait for the bodies.
    private void show() {
        stream.sendThrough((int) Math.floor(clock.shown() + 1.0E-9D));
        if (!phaseOverTold && !clock.inPhase()) {
            phaseOverTold = true;
            if (window == null) {
                // The phantoms have been watched: what was guessed of an exempt player goes. In a window, at the pull-back.
                untellAll();
            }
            tell(EpitaphSessionPayload.of(id, EpitaphSessionPayload.Kind.PHASE_OVER));
            resendEffects();
        }
    }

    // In the slow phase the being of the walked user lived at the pace of his track while his client counted his effects
    // down at its own: at the end of the phase he is told what they really are.
    private void resendEffects() {
        Binding own = binder.of(user);
        if (mode == EpitaphMode.SOLO_SLOW && own != null && own.walk != null && user.connection != null && !user.hasDisconnected()) {
            for (MobEffectInstance effect : user.getActiveEffects()) {
                user.connection.send(new ClientboundUpdateMobEffectPacket(user.getId(), effect, false));
            }
        }
    }

    // The room is always ahead of the bodies; beyond that it runs on while the budget of this tick lasts.
    private void simulate(int needed) {
        long deadline = System.nanoTime() + budgetNanos;
        while (!roomDone && (room.tick() < needed || System.nanoTime() < deadline)) {
            SandboxDriver.step(room);
            if (room.tick() >= clock.foresight()) {
                SandboxDriver.finish(room);
                roomDone = true;
            } else if (room.phantoms().isEmpty()) {
                roomDone = true;
            }
        }
    }

    // For each whole tick the bodies pass: place them, then the ops of that tick, then the ends of tracks.
    private void carry() {
        for (Binding binding : binder.all()) {
            PuppetBinder.takePause(binding);
        }
        double at = clock.body();
        int whole = (int) Math.floor(at + 1.0E-9D);
        for (int tick = passed + 1; tick <= whole; tick++) {
            for (Binding binding : binder.all()) {
                if (binding.body.isRemoved()) {
                    unbind(binding.body);
                } else {
                    place(binding, tick);
                    PuppetTick.pass(binding, tick, room.uuids());
                }
            }
            ActionMirror.step(this, tick);
            OpExecutor.run(this, tick);
            ActionMirror.align(this, tick);
            for (Binding binding : binder.all()) {
                if (binding.body.isRemoved()) {
                    unbind(binding.body);
                    continue;
                }
                // What an op of the record did to its motion is no push from outside.
                PuppetTick.write(binding, PuppetTick.motionAt(binding, tick));
                if (!binding.track.isOpen() && binding.track.lastTick() <= tick) {
                    release(binding);
                }
            }
            passed = tick;
        }
        if (at > whole) {
            for (Binding binding : binder.all()) {
                if (!binding.body.isRemoved()) {
                    place(binding, at);
                }
            }
        }
    }

    // A body that took the short cut past what stands on its track is seen to flash where it left.
    private void place(Binding binding, double tick) {
        Entity body = binding.body;
        Vec3 left = body.position();
        if (PuppetTick.place(level, binding, tick)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(body, new EpitaphShortcutPayload(body.getId(), left.x, left.y, left.z));
        }
    }

    // The end of a track its body outlived: the end state, then what outsiders are owed.
    private void release(Binding binding) {
        unbind(binding.body);
        ReleaseState.apply(this, binding);
        FateRules.payDebt(binding);
    }

    /** The only way out. Whatever the reason, nothing stays held: every body is let go where it is, as it is. */
    public void end(EpitaphEnd reason) {
        if (ended) {
            return;
        }
        ended = true;
        endedAs = reason;
        if (window != null) {
            // A window that never reached its pull-back keeps what was done in it.
            window.close();
            window = null;
        }
        for (Binding binding : binder.all()) {
            if (binding.walk != null) {
                binding.walk.end();
            }
            ActionMirror.letGo(this, binding);
            if (!binding.body.isRemoved()) {
                if (binding.body instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
                PuppetBinder.givePause(binding);
                FateRules.payDebt(binding);
            }
        }
        binder.clear();
        foreseenDeaths.clear();
        untellAll();
        // While it still knows whom it was aimed at: not even the end of the mark reaches him.
        user.removeData(KingCrimsonAddon.EPITAPH_FORESEEING);
        for (SandboxRoom.Phantom phantom : room.all()) {
            PhantomFactory.release(phantom.entity);
        }
        bodies.clear();
        tell(EpitaphSessionPayload.of(id, EpitaphSessionPayload.Kind.END));
        summarise(reason);
        EpitaphSessions.remove(this, reason);
    }

    // One line per prediction: what it was and what went wrong in it, loud only when something did.
    private void summarise(EpitaphEnd reason) {
        SandboxRoom.Counts counts = room.counts();
        int leaks = Math.max(0, EpitaphSandbox.leaks() - leaksAtStart);
        String line = String.format(Locale.ROOT, "Epitaph[summary] %s end=%s mode=%s foresight=%d lived=%d carried=%d phantoms=%d "
                + "ops=%d skipped=%d failed=%d phantom-faults=%d not-copied=%d not-shown=%d unwritten=%d leaks=%d frames-sent=%d",
                user.getScoreboardName(), reason, mode, clock.foresight(), room.tick(), passed, room.all().size(),
                record.opCount(), record.skipped(), record.failed(), counts.phantomFaults, counts.notCopied,
                counts.notShown + counts.dropped,
                stream.unwritten(), leaks, stream.sentThrough() + 1);
        if (reason == EpitaphEnd.FAULT || record.failed() > 0 || counts.phantomFaults > 0 || counts.notCopied > 0
                || counts.notShown > 0 || stream.unwritten() > 0 || leaks > 0) {
            LOGGER.warn(line);
        } else {
            LOGGER.debug(line);
        }
    }
}

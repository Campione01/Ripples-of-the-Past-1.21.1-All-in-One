package rotp.addons.kingcrimson.state;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class KingCrimsonTemporalState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String MODE_TAG = "Mode";
    private static final String STARTED_AT_TAG = "StartedAt";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String PREVIOUS_INVISIBILITY_TAG =
            "PreviousInvisibility";
    private static final String PREVIOUS_INFINITE_TAG =
            "PreviousInfinite";
    private static final String PREVIOUS_DURATION_TAG =
            "PreviousDuration";
    private static final String PREVIOUS_AMPLIFIER_TAG =
            "PreviousAmplifier";
    private static final String PREVIOUS_AMBIENT_TAG =
            "PreviousAmbient";
    private static final String PREVIOUS_VISIBLE_TAG =
            "PreviousVisible";
    private static final String PREVIOUS_SHOW_ICON_TAG =
            "PreviousShowIcon";
    private static final String PREVIOUS_FULL_INVISIBILITY_TAG =
            "PreviousFullInvisibility";
    private static final String HIDDEN_STAND_UUID_TAG =
            "HiddenStandUuid";
    private static final String PREVIOUS_STAND_INVISIBILITY_TAG =
            "PreviousStandInvisibility";
    private static final String PAUSED_TICKS_TAG = "PausedTicks";
    private static final String ERASE_PAUSED_TAG = "ErasePaused";
    private static final String ERASE_KIND_TAG = "EraseKind";
    private static final String ERASE_LENGTH_TAG = "EraseLength";
    private static final String WORLD_TICKS_AHEAD_TAG = "WorldTicksAhead";
    private static final String SUSPENDED_EFFECTS_TAG = "SuspendedEffects";
    // States in an erase, on either side of this JVM. A state dropped without clear() leaves the count too high,
    // which only costs the hooks their quick way out until the next reset.
    private static final AtomicInteger ERASE_COUNT = new AtomicInteger();

    private Mode mode = Mode.IDLE;
    // The harmful effects taken off the user for the erase, as saved effect data: they do nothing in it, their
    // time runs on, and what is left of them comes back at its end.
    private ListTag suspendedEffects = new ListTag();
    private long startedAt;
    private long expiresAt;
    // Ticks the erase spent frozen in someone's time stop; expiresAt has already been moved by them.
    private long pausedTicks;
    private boolean erasePaused;
    private EraseKind eraseKind = EraseKind.ACTIVE;
    // The erase key was let go for a Blood Throw and the erase goes on: from then on a press of the key ends it.
    // Sent to the clients, never saved.
    private boolean keyUp;
    // The erase's length as decided at its start, in the world's ticks: a running erase never reads the setting again.
    private int eraseLengthTicks;
    // The world's ticks beyond the plain ones that expiresAt has already been moved by (a world that accelerates).
    private long worldTicksAhead;
    private transient boolean endRequestedWhilePaused;
    // Only for the length of a forced unsummon: KingCrimsonStandType.isRecall.
    private transient boolean standLeaving;
    // The part of a tick of the world's time beyond the plain tick that expiresAt has not taken yet.
    private transient double worldTimeCarry;
    @Nullable
    private transient Vec3 lastPosition;
    private boolean hadPreviousInvisibility;
    private boolean previousInvisibilityInfinite;
    private int previousInvisibilityDuration;
    private int previousInvisibilityAmplifier;
    private boolean previousInvisibilityAmbient;
    private boolean previousInvisibilityVisible;
    private boolean previousInvisibilityShowIcon;
    @Nullable
    private EffectSnapshot previousFullInvisibility;
    @Nullable
    private UUID hiddenStandUuid;
    @Nullable
    private EffectSnapshot previousStandInvisibility;
    @Nullable
    private transient KingCrimsonTimeEraseProjectionService.Session
            projectionSession;
    @Nullable
    private transient KingCrimsonTimeEraseShiftService.Session shiftSession;
    /** Whether this erase began in this run of the server: one read back from disk has nothing left to end. */
    private transient boolean live;
    // What the start of this erase really took from the bar; an erase read back from disk gives nothing back.
    private transient float startCharged;
    // Whom the player last hit, himself or through his Stand, and when: his decoy's threat. Never saved.
    @Nullable
    private transient WeakReference<LivingEntity> lastHit;
    private transient long lastHitAt;
    // When another time ability last threw the player out of an erase. Never saved, and not an erase's own: clear()
    // leaves it.
    private transient long thrownOutAt = Long.MIN_VALUE;
    // The blood the player threw in an erase that has not landed yet (Blood Throw). Never saved, and not an erase's
    // own either: it flies on after the erase.
    private final transient List<KingCrimsonBloodThrowService.Flight> bloodFlights = new ArrayList<>();

    public Mode mode() {
        return mode;
    }

    /** Whether any erase may be running: the one read every out-of-reach hook makes first. */
    public static boolean anyErase() {
        return ERASE_COUNT.get() > 0;
    }

    /** A server stop or a client leaving its world drops every state it held. */
    public static void resetEraseCount() {
        ERASE_COUNT.set(0);
    }

    private void enter(Mode newMode) {
        if (mode != Mode.TIME_ERASE && newMode == Mode.TIME_ERASE) {
            ERASE_COUNT.incrementAndGet();
        } else if (mode == Mode.TIME_ERASE && newMode != Mode.TIME_ERASE) {
            ERASE_COUNT.updateAndGet(count -> Math.max(count - 1, 0));
        }
        mode = newMode;
    }

    void suspendEffect(MobEffectInstance effect) {
        suspendedEffects.add(effect.save());
    }

    /** The suspended effects with what is left of their time now; those that ran out are not among them. */
    List<MobEffectInstance> suspendedEffectsLeft(long gameTime) {
        List<MobEffectInstance> left = new ArrayList<>();
        long elapsed = activeElapsed(gameTime);
        for (Tag saved : suspendedEffects) {
            CompoundTag shortened = saved instanceof CompoundTag tag ? shortened(tag, elapsed) : null;
            MobEffectInstance effect = shortened != null ? MobEffectInstance.load(shortened) : null;
            if (effect != null) {
                left.add(effect);
            }
        }
        return left;
    }

    // An effect that ran out uncovers the weaker, longer one it hid, as it would have on its bearer.
    @Nullable
    private static CompoundTag shortened(CompoundTag saved, long elapsed) {
        CompoundTag hidden = saved.contains("hidden_effect", Tag.TAG_COMPOUND)
                ? shortened(saved.getCompound("hidden_effect"), elapsed)
                : null;
        CompoundTag copy = saved.copy();
        copy.remove("hidden_effect");
        int duration = copy.getInt("duration");
        if (duration != MobEffectInstance.INFINITE_DURATION) {
            long left = duration - elapsed;
            if (left <= 0L) {
                return hidden;
            }
            copy.putInt("duration", (int) left);
        }
        if (hidden != null) {
            copy.put("hidden_effect", hidden);
        }
        return copy;
    }

    public long startedAt() {
        return startedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isIdle() {
        return mode == Mode.IDLE;
    }

    public boolean isErasing(long gameTime) {
        return mode == Mode.TIME_ERASE
                && (erasePaused || gameTime < expiresAt);
    }

    /** Whether the erase is frozen in someone's time stop: no time passes for it. */
    public boolean isErasePaused() {
        return mode == Mode.TIME_ERASE && erasePaused;
    }

    /** Whether the erase goes on with its key up (EV1105AC section 8). */
    public boolean isKeyUp() {
        return mode == Mode.TIME_ERASE && keyUp;
    }

    void setKeyUp() {
        keyUp = mode == Mode.TIME_ERASE;
    }

    /** One server tick frozen in a time stop; true when the pause starts with it. */
    boolean pauseEraseTick() {
        if (mode != Mode.TIME_ERASE) {
            return false;
        }
        expiresAt++;
        pausedTicks++;
        boolean started = !erasePaused;
        erasePaused = true;
        return started;
    }

    /** Ends the pause; true when the user asked to end the erase while it was frozen. */
    boolean resumeErase() {
        boolean endRequested = endRequestedWhilePaused;
        erasePaused = false;
        endRequestedWhilePaused = false;
        return endRequested;
    }

    /**
     * One server tick in which this much of the world's time passed (above 1 while the world accelerates): the erase
     * lasts in the world's time, so its end comes that much sooner. True when the end moved.
     */
    boolean advanceEraseWorldTime(float worldTimePerTick) {
        if (mode != Mode.TIME_ERASE || !(worldTimePerTick > 1.0F)) {
            return false;
        }
        worldTimeCarry += worldTimePerTick - 1.0D;
        if (worldTimeCarry < 1.0D) {
            return false;
        }
        long whole = (long) worldTimeCarry;
        expiresAt -= whole;
        worldTicksAhead += whole;
        worldTimeCarry -= whole;
        return true;
    }

    public boolean isStandLeaving() {
        return standLeaving;
    }

    public void setStandLeaving(boolean leaving) {
        standLeaving = leaving;
    }

    /** A Stand summoned during the erase takes the place of the one that was hidden when it began. */
    void takeOverHiddenStand(UUID standUuid, @Nullable MobEffectInstance previous) {
        hiddenStandUuid = standUuid;
        previousStandInvisibility = EffectSnapshot.from(previous);
    }

    void notePosition(Vec3 position) {
        lastPosition = position;
    }

    /** How far the user moved since his last tick: the server has no motion of its own for a player his client moves. */
    public Vec3 lastStepTo(Vec3 position) {
        return lastPosition != null ? position.subtract(lastPosition) : Vec3.ZERO;
    }

    void requestEndOnResume() {
        if (isErasePaused()) {
            endRequestedWhilePaused = true;
        }
    }

    public boolean isSkipping(long gameTime) {
        return mode == Mode.TIME_SKIP && gameTime < expiresAt;
    }

    public boolean beginTimeErase(
            long newStartedAt,
            long newExpiresAt,
            @Nullable MobEffectInstance previousInvisibility,
            @Nullable MobEffectInstance previousFullInvisibility,
            @Nullable UUID hiddenStandUuid,
            @Nullable MobEffectInstance previousStandInvisibility,
            EraseKind kind) {
        if (!isIdle()
                || newStartedAt < 0L
                || newExpiresAt <= newStartedAt) {
            return false;
        }
        enter(Mode.TIME_ERASE);
        startedAt = newStartedAt;
        expiresAt = newExpiresAt;
        eraseKind = kind;
        eraseLengthTicks = (int) Math.min(newExpiresAt - newStartedAt, Integer.MAX_VALUE);
        rememberPreviousInvisibility(previousInvisibility);
        this.previousFullInvisibility =
                EffectSnapshot.from(previousFullInvisibility);
        this.hiddenStandUuid = hiddenStandUuid;
        this.previousStandInvisibility =
                EffectSnapshot.from(previousStandInvisibility);
        return true;
    }

    public boolean beginTimeSkip(
            long newStartedAt, long newExpiresAt) {
        if (!isIdle()
                || newStartedAt < 0L
                || newExpiresAt <= newStartedAt) {
            return false;
        }
        enter(Mode.TIME_SKIP);
        startedAt = newStartedAt;
        expiresAt = newExpiresAt;
        clearPreviousInvisibility();
        clearVisibilitySnapshots();
        return true;
    }

    public int elapsedTicks(long gameTime, int maximum) {
        long elapsed = activeElapsed(gameTime);
        return (int) Math.min(elapsed, Math.max(maximum, 0));
    }

    public EraseKind eraseKind() {
        return eraseKind;
    }

    /** The running erase's whole length, in the world's ticks, as its start decided it. */
    public int eraseLengthTicks() {
        return mode == Mode.TIME_ERASE ? eraseLengthTicks : 0;
    }

    /**
     * How much of the erase has passed in the world's time (T3-ANSWERS Q10): an erase cut short by an accelerating
     * world has still run its length, and time spent frozen in a time stop does not count.
     */
    public int elapsedWorldTicks(long gameTime) {
        if (mode != Mode.TIME_ERASE) {
            return 0;
        }
        return (int) Math.min(activeElapsed(gameTime) + worldTicksAhead, eraseLengthTicks);
    }

    // The user's effects did not tick while frozen, so remembered durations skip the paused ticks too.
    private long activeElapsed(long gameTime) {
        return Math.max(0L, gameTime - startedAt - pausedTicks);
    }

    @Nullable
    public MobEffectInstance previousInvisibility(
            long gameTime) {
        if (!hadPreviousInvisibility) {
            return null;
        }
        int duration;
        if (previousInvisibilityInfinite) {
            duration = MobEffectInstance.INFINITE_DURATION;
        } else {
            long elapsed = activeElapsed(gameTime);
            duration = (int) Math.max(
                    0L,
                    (long) previousInvisibilityDuration - elapsed);
            if (duration <= 0) {
                return null;
            }
        }
        return new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.INVISIBILITY,
                duration,
                previousInvisibilityAmplifier,
                previousInvisibilityAmbient,
                previousInvisibilityVisible,
                previousInvisibilityShowIcon);
    }

    @Nullable
    public MobEffectInstance previousFullInvisibility(
            long gameTime) {
        return restoreEffect(
                previousFullInvisibility,
                ModStatusEffects.FULL_INVISIBILITY,
                gameTime);
    }

    @Nullable
    public UUID hiddenStandUuid() {
        return hiddenStandUuid;
    }

    @Nullable
    public MobEffectInstance previousStandInvisibility(
            long gameTime) {
        return restoreEffect(
                previousStandInvisibility,
                MobEffects.INVISIBILITY,
                gameTime);
    }

    public boolean clear() {
        boolean changed = mode != Mode.IDLE
                || startedAt != 0L
                || expiresAt != 0L
                || hadPreviousInvisibility
                || previousFullInvisibility != null
                || hiddenStandUuid != null
                || previousStandInvisibility != null
                || pausedTicks != 0L
                || erasePaused
                || !suspendedEffects.isEmpty();
        enter(Mode.IDLE);
        suspendedEffects = new ListTag();
        startedAt = 0L;
        expiresAt = 0L;
        pausedTicks = 0L;
        erasePaused = false;
        eraseKind = EraseKind.ACTIVE;
        keyUp = false;
        eraseLengthTicks = 0;
        worldTicksAhead = 0L;
        endRequestedWhilePaused = false;
        worldTimeCarry = 0.0D;
        clearPreviousInvisibility();
        clearVisibilitySnapshots();
        projectionSession = null;
        shiftSession = null;
        live = false;
        startCharged = 0.0F;
        return changed;
    }

    List<KingCrimsonBloodThrowService.Flight> bloodFlights() {
        return bloodFlights;
    }

    boolean hasBloodFlights() {
        return !bloodFlights.isEmpty();
    }

    void dropBloodFlights() {
        bloodFlights.clear();
    }

    void noteThrownOut(long gameTime) {
        thrownOutAt = gameTime;
    }

    /**
     * Whether a blow begun at this game time was begun inside an erase its user was then thrown out of (Gold
     * Experience Requiem near him): such a blow gives nothing. One tick of grace: the action counts its own ticks.
     */
    public boolean callsOffBlowBegunAt(long startedAt) {
        return thrownOutAt != Long.MIN_VALUE && startedAt <= thrownOutAt + 1L;
    }

    public void noteHit(LivingEntity target, long gameTime) {
        lastHit = new WeakReference<>(target);
        lastHitAt = gameTime;
    }

    /** The living thing the player hit within so many ticks, if it is still known. */
    @Nullable
    public LivingEntity lastHit(long gameTime, int withinTicks) {
        LivingEntity target = lastHit != null ? lastHit.get() : null;
        return target != null && gameTime - lastHitAt <= withinTicks ? target : null;
    }

    boolean isLive() {
        return live;
    }

    float startCharged() {
        return startCharged;
    }

    void noteStartCharged(float charged) {
        startCharged = charged;
    }

    void markLive() {
        live = true;
    }

    /** The other players' shifted images of this erase; null when it shifts nobody. */
    @Nullable
    KingCrimsonTimeEraseShiftService.Session shiftSession() {
        return shiftSession;
    }

    void setShiftSession(@Nullable KingCrimsonTimeEraseShiftService.Session session) {
        shiftSession = session;
    }

    @Nullable
    KingCrimsonTimeEraseShiftService.Session takeShiftSession() {
        KingCrimsonTimeEraseShiftService.Session session = shiftSession;
        shiftSession = null;
        return session;
    }

    @Nullable
    KingCrimsonTimeEraseProjectionService.Session
            projectionSession() {
        return projectionSession;
    }

    void setProjectionSession(
            KingCrimsonTimeEraseProjectionService.Session session) {
        projectionSession = session;
    }

    @Nullable
    KingCrimsonTimeEraseProjectionService.Session
            takeProjectionSession() {
        KingCrimsonTimeEraseProjectionService.Session session =
                projectionSession;
        projectionSession = null;
        return session;
    }

    public void applyClientSnapshot(
            int modeOrdinal,
            long newStartedAt,
            long newExpiresAt,
            boolean newErasePaused,
            int eraseKindOrdinal,
            boolean newKeyUp) {
        clear();
        Mode newMode = Mode.byOrdinal(modeOrdinal);
        if (newMode == Mode.IDLE
                || newStartedAt < 0L
                || newExpiresAt <= newStartedAt) {
            return;
        }
        enter(newMode);
        startedAt = newStartedAt;
        expiresAt = newExpiresAt;
        erasePaused = newMode == Mode.TIME_ERASE && newErasePaused;
        if (newMode == Mode.TIME_ERASE) {
            eraseKind = EraseKind.byOrdinal(eraseKindOrdinal);
            eraseLengthTicks = (int) Math.min(newExpiresAt - newStartedAt, Integer.MAX_VALUE);
            keyUp = newKeyUp;
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putInt(MODE_TAG, mode.ordinal());
        tag.putLong(STARTED_AT_TAG, startedAt);
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        tag.putLong(PAUSED_TICKS_TAG, pausedTicks);
        tag.putBoolean(ERASE_PAUSED_TAG, erasePaused);
        tag.putInt(ERASE_KIND_TAG, eraseKind.ordinal());
        tag.putInt(ERASE_LENGTH_TAG, eraseLengthTicks);
        tag.putLong(WORLD_TICKS_AHEAD_TAG, worldTicksAhead);
        if (!suspendedEffects.isEmpty()) {
            tag.put(SUSPENDED_EFFECTS_TAG, suspendedEffects.copy());
        }
        tag.putBoolean(
                PREVIOUS_INVISIBILITY_TAG,
                hadPreviousInvisibility);
        if (hadPreviousInvisibility) {
            tag.putBoolean(
                    PREVIOUS_INFINITE_TAG,
                    previousInvisibilityInfinite);
            tag.putInt(
                    PREVIOUS_DURATION_TAG,
                    previousInvisibilityDuration);
            tag.putInt(
                    PREVIOUS_AMPLIFIER_TAG,
                    previousInvisibilityAmplifier);
            tag.putBoolean(
                    PREVIOUS_AMBIENT_TAG,
                    previousInvisibilityAmbient);
            tag.putBoolean(
                    PREVIOUS_VISIBLE_TAG,
                    previousInvisibilityVisible);
            tag.putBoolean(
                    PREVIOUS_SHOW_ICON_TAG,
                    previousInvisibilityShowIcon);
        }
        if (previousFullInvisibility != null) {
            tag.put(
                    PREVIOUS_FULL_INVISIBILITY_TAG,
                    previousFullInvisibility.serialize());
        }
        if (hiddenStandUuid != null) {
            tag.putUUID(HIDDEN_STAND_UUID_TAG, hiddenStandUuid);
        }
        if (previousStandInvisibility != null) {
            tag.put(
                    PREVIOUS_STAND_INVISIBILITY_TAG,
                    previousStandInvisibility.serialize());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt(VERSION_TAG) != SERIALIZATION_VERSION) {
            return;
        }
        Mode loadedMode = Mode.byOrdinal(tag.getInt(MODE_TAG));
        long loadedStartedAt = tag.getLong(STARTED_AT_TAG);
        long loadedExpiresAt = tag.getLong(EXPIRES_AT_TAG);
        if (loadedMode == Mode.IDLE
                || loadedStartedAt < 0L
                || loadedExpiresAt <= loadedStartedAt) {
            return;
        }
        enter(loadedMode);
        startedAt = loadedStartedAt;
        expiresAt = loadedExpiresAt;
        if (loadedMode == Mode.TIME_ERASE) {
            pausedTicks = Math.max(0L, tag.getLong(PAUSED_TICKS_TAG));
            erasePaused = tag.getBoolean(ERASE_PAUSED_TAG);
            eraseKind = EraseKind.byOrdinal(tag.getInt(ERASE_KIND_TAG));
            suspendedEffects = tag.getList(SUSPENDED_EFFECTS_TAG, Tag.TAG_COMPOUND).copy();
            worldTicksAhead = Math.max(0L, tag.getLong(WORLD_TICKS_AHEAD_TAG));
            eraseLengthTicks = tag.contains(ERASE_LENGTH_TAG)
                    ? Math.max(0, tag.getInt(ERASE_LENGTH_TAG))
                    : (int) Math.min(loadedExpiresAt - loadedStartedAt, Integer.MAX_VALUE);
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.getBoolean(PREVIOUS_INVISIBILITY_TAG)) {
            hadPreviousInvisibility = true;
            previousInvisibilityInfinite =
                    tag.getBoolean(PREVIOUS_INFINITE_TAG);
            previousInvisibilityDuration =
                    tag.getInt(PREVIOUS_DURATION_TAG);
            previousInvisibilityAmplifier =
                    tag.getInt(PREVIOUS_AMPLIFIER_TAG);
            previousInvisibilityAmbient =
                    tag.getBoolean(PREVIOUS_AMBIENT_TAG);
            previousInvisibilityVisible =
                    tag.getBoolean(PREVIOUS_VISIBLE_TAG);
            previousInvisibilityShowIcon =
                    tag.getBoolean(PREVIOUS_SHOW_ICON_TAG);
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.contains(PREVIOUS_FULL_INVISIBILITY_TAG)) {
            previousFullInvisibility = EffectSnapshot.deserialize(
                    tag.getCompound(
                            PREVIOUS_FULL_INVISIBILITY_TAG));
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.hasUUID(HIDDEN_STAND_UUID_TAG)) {
            hiddenStandUuid = tag.getUUID(HIDDEN_STAND_UUID_TAG);
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.contains(PREVIOUS_STAND_INVISIBILITY_TAG)) {
            previousStandInvisibility = EffectSnapshot.deserialize(
                    tag.getCompound(
                            PREVIOUS_STAND_INVISIBILITY_TAG));
        }
    }

    private void rememberPreviousInvisibility(
            @Nullable MobEffectInstance previous) {
        clearPreviousInvisibility();
        if (previous == null) {
            return;
        }
        hadPreviousInvisibility = true;
        previousInvisibilityInfinite =
                previous.isInfiniteDuration();
        previousInvisibilityDuration = previous.getDuration();
        previousInvisibilityAmplifier = previous.getAmplifier();
        previousInvisibilityAmbient = previous.isAmbient();
        previousInvisibilityVisible = previous.isVisible();
        previousInvisibilityShowIcon = previous.showIcon();
    }

    private void clearPreviousInvisibility() {
        hadPreviousInvisibility = false;
        previousInvisibilityInfinite = false;
        previousInvisibilityDuration = 0;
        previousInvisibilityAmplifier = 0;
        previousInvisibilityAmbient = false;
        previousInvisibilityVisible = false;
        previousInvisibilityShowIcon = false;
    }

    private void clearVisibilitySnapshots() {
        previousFullInvisibility = null;
        hiddenStandUuid = null;
        previousStandInvisibility = null;
    }

    @Nullable
    private MobEffectInstance restoreEffect(
            @Nullable EffectSnapshot snapshot,
            Holder<MobEffect> effect,
            long gameTime) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.restore(
                effect,
                activeElapsed(gameTime));
    }

    private record EffectSnapshot(
            boolean infinite,
            int duration,
            int amplifier,
            boolean ambient,
            boolean visible,
            boolean showIcon) {
        private static final String INFINITE_TAG = "Infinite";
        private static final String DURATION_TAG = "Duration";
        private static final String AMPLIFIER_TAG = "Amplifier";
        private static final String AMBIENT_TAG = "Ambient";
        private static final String VISIBLE_TAG = "Visible";
        private static final String SHOW_ICON_TAG = "ShowIcon";

        @Nullable
        static EffectSnapshot from(
                @Nullable MobEffectInstance effect) {
            return effect == null
                    ? null
                    : new EffectSnapshot(
                            effect.isInfiniteDuration(),
                            effect.getDuration(),
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.isVisible(),
                            effect.showIcon());
        }

        CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(INFINITE_TAG, infinite);
            tag.putInt(DURATION_TAG, duration);
            tag.putInt(AMPLIFIER_TAG, amplifier);
            tag.putBoolean(AMBIENT_TAG, ambient);
            tag.putBoolean(VISIBLE_TAG, visible);
            tag.putBoolean(SHOW_ICON_TAG, showIcon);
            return tag;
        }

        static EffectSnapshot deserialize(CompoundTag tag) {
            return new EffectSnapshot(
                    tag.getBoolean(INFINITE_TAG),
                    tag.getInt(DURATION_TAG),
                    tag.getInt(AMPLIFIER_TAG),
                    tag.getBoolean(AMBIENT_TAG),
                    tag.getBoolean(VISIBLE_TAG),
                    tag.getBoolean(SHOW_ICON_TAG));
        }

        @Nullable
        MobEffectInstance restore(
                Holder<MobEffect> effect, long elapsed) {
            int remaining;
            if (infinite) {
                remaining = MobEffectInstance.INFINITE_DURATION;
            } else {
                remaining = (int) Math.max(
                        0L, (long) duration - elapsed);
                if (remaining <= 0) {
                    return null;
                }
            }
            return new MobEffectInstance(
                    effect,
                    remaining,
                    amplifier,
                    ambient,
                    visible,
                    showIcon);
        }
    }

    /** What began an erase: the key, or the passive's reflex or last chance (owner 2026-11-07). */
    public enum EraseKind {
        ACTIVE,
        REFLEX,
        LAST_CHANCE;

        public static EraseKind byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal]
                    : ACTIVE;
        }

        /** The passive's erases cover the scene at once, with no ground breaking and no floating. */
        public boolean isPassive() {
            return this != ACTIVE;
        }
    }

    public enum Mode {
        IDLE,
        TIME_ERASE,
        TIME_SKIP;

        public static Mode byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal]
                    : IDLE;
        }
    }
}

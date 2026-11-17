package rotp.addons.kingcrimson.state;

import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.network.TimeEraseTransitionPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.EraseKind;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTemporalStateService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private KingCrimsonTemporalStateService() {}

    // Owner 2026-11-11: "兩個紅王持有者的時間刪除互相獨立". Whether somebody else erases is never asked: the answer
    // would tell this holder that he is inside another's erase.
    public static boolean canStartTimeErase(
            ServerPlayer player) {
        return canStartTemporalAction(player);
    }

    public static boolean canStartTimeSkip(
            ServerPlayer player) {
        return canStartTemporalAction(player);
    }

    public static boolean beginTimeErase(
            ServerPlayer player) {
        return beginTimeErase(player, EraseKind.ACTIVE);
    }

    public static boolean beginTimeErase(
            ServerPlayer player, EraseKind kind) {
        if (!canStartTimeErase(player)) {
            return false;
        }
        // Owner 2026-11-07: the length is read once, here; a setting or a Resolve level that changes during the
        // erase counts from the next one.
        int lengthTicks = eraseLengthTicks(player, kind);
        if (lengthTicks <= 0) {
            return false;
        }
        // A2-EVENTS, owner Q4: an answering ability (Gold Experience Requiem) returns the erase to zero.
        if (!TimeEraseStartEvent.post(
                player,
                (int) Math.ceil(lengthTicks
                        / worldTimePerTick(player)))) {
            returnEraseToZero(player, kind);
            return false;
        }

        long startedAt = player.level().getGameTime();
        long expiresAt = startedAt + lengthTicks;
        MobEffectInstance previous =
                player.getEffect(MobEffects.INVISIBILITY);
        MobEffectInstance previousFullInvisibility =
                player.getEffect(
                        ModStatusEffects.FULL_INVISIBILITY);
        StandEntity hiddenStand = summonedStand(player);
        MobEffectInstance previousStandInvisibility =
                hiddenStand != null
                        ? hiddenStand.getEffect(
                                MobEffects.INVISIBILITY)
                        : null;
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (!state.beginTimeErase(
                startedAt,
                expiresAt,
                previous,
                previousFullInvisibility,
                hiddenStand != null
                        ? hiddenStand.getUUID()
                        : null,
                previousStandInvisibility,
                kind)) {
            return false;
        }
        // Epitaph, T2: his own part of a prediction that runs is void from here, by key or by reflex.
        EpitaphPredictions.onUserEraseBegan(player);

        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModStatusEffects.FULL_INVISIBILITY);
        if (hiddenStand != null) {
            hiddenStand.removeEffect(MobEffects.INVISIBILITY);
        }
        suspendHarmfulEffects(player, state);
        // Nothing is stored up: what burned or froze him before the erase is not waiting at its end.
        player.clearFire();
        player.setTicksFrozen(0);
        addEraseEffects(
                player,
                hiddenStand,
                lengthTicks + 2);
        player.playNotifySound(
                KingCrimsonAddon.TIME_ERASE_START.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        KingCrimsonAddon.syncTemporalState(player);
        PacketDistributor.sendToPlayer(
                player, TimeEraseTransitionPayload.INSTANCE);
        state.markLive();
        state.setShiftSession(KingCrimsonTimeEraseShiftService.begin(player));
        KingCrimsonTimeEraseProjectionService.begin(player);
        return true;
    }

    /**
     * Owner 2026-11-07: "時刪狀態下玩家不能受到任何傷害或負面buff影響，但所有buff自身持續時間也要正常流逝". A harmful effect he
     * carries is taken off for the erase, so it neither acts nor shows; the state counts its time on. An effect
     * another mod will not let go of stays and acts.
     */
    private static void suspendHarmfulEffects(
            ServerPlayer player, KingCrimsonTemporalState state) {
        for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            MobEffectInstance kept = new MobEffectInstance(effect);
            if (player.removeEffect(effect.getEffect())) {
                state.suspendEffect(kept);
            }
        }
    }

    /**
     * The length of an erase that starts now, in the world's ticks: the user's own longest (the setting's share for
     * his Resolve level), which the passive's two erases never exceed.
     */
    static int eraseLengthTicks(ServerPlayer player, EraseKind kind) {
        StandPower power = StandPower.get(player);
        int longest = KingCrimsonConfig.timeEraseMaxTicks(
                power != null ? power.getResolveLevel() : 0);
        return switch (kind) {
            case ACTIVE -> longest;
            case REFLEX -> Math.min(KingCrimsonConfig.reflexEraseTicks(), longest);
            case LAST_CHANCE -> Math.min(KingCrimsonConfig.lastChanceEraseTicks(), longest);
        };
    }

    private static void addEraseEffects(
            ServerPlayer player,
            @Nullable StandEntity hiddenStand,
            int effectTicks) {
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                effectTicks,
                0,
                false,
                false,
                false));
        player.addEffect(new MobEffectInstance(
                ModStatusEffects.FULL_INVISIBILITY,
                effectTicks,
                999999,
                false,
                false,
                false));
        player.addEffect(new MobEffectInstance(
                KingCrimsonAddon.TIME_ERASE,
                effectTicks,
                0,
                false,
                false,
                true));
        if (hiddenStand != null) {
            hiddenStand.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    effectTicks,
                    0,
                    false,
                    false,
                    false));
        }
    }

    /**
     * Owner 2026-11-05: an erase lasts in the world's time, not in its user's own, so it shrinks as the world
     * accelerates. This is how much of the world's time passes in one server tick for this eraser: the level's rate m
     * times his own. Made in Heaven's mode A holds him at 1/m and the world runs in plain ticks; mode B leaves him at 1
     * and the world runs m times faster. Never below 1: a slowdown of his own does not stretch the erase.
     */
    static float worldTimePerTick(LivingEntity eraser) {
        float rate = LevelTimeRates.rate(LevelTimeRates.Channel.TIME, eraser.level())
                * ownTimeRate(eraser);
        return rate > 1.0F
                ? Math.min(rate, EntityTimeRates.MAX_RATE)
                : 1.0F;
    }

    /**
     * KC-MIH (A2-COMBINE): how fast a being's own time runs, the slowest rate any ability holds it to (Made in
     * Heaven's mode A: 1/m), never faster than the world's here; 1 with no provider.
     */
    static float ownTimeRate(Entity entity) {
        float rate = EntityTimeRates.rate(
                EntityTimeRates.Channel.BEING, entity);
        return rate > 0.0F && rate < 1.0F
                ? Math.max(rate, 1.0F / EntityTimeRates.MAX_RATE)
                : 1.0F;
    }

    public static boolean beginTimeSkip(
            ServerPlayer player) {
        if (!canStartTimeSkip(player)) {
            return false;
        }

        Vec3 destination = findTimeSkipDestination(player);
        SoundEvent sound = chooseTimeSkipSound(
                player.getRandom());
        return beginTimeSkip(
                player,
                destination,
                // 1.16 KCConfig timeSkipDuration
                KingCrimsonConfig.timeSkipDurationTicks(),
                sound);
    }

    private static boolean beginTimeSkip(
            ServerPlayer player,
            Vec3 destination,
            int impliedTicks,
            SoundEvent sound) {
        List<Mob> nearbyMobs = collectTimeSkipMobs(player);
        long startedAt = player.level().getGameTime();
        long expiresAt = startedAt
                + KingCrimsonPhase5Spec
                        .TIME_SKIP_INVULNERABLE_TICKS;
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (!state.beginTimeSkip(startedAt, expiresAt)) {
            return false;
        }

        makeNearbyMobsLoseTarget(player, destination);
        player.teleportTo(
                destination.x,
                destination.y,
                destination.z);
        // a skip into stopped time freezes the user where he lands (A2-REPIN)
        TimeStopState.repinOrReleaseOnServer(player);
        advanceImpliedTickCounters(player, impliedTicks);
        advanceImpliedMobTicks(nearbyMobs, impliedTicks);
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        KingCrimsonAddon.syncTemporalState(player);
        return true;
    }

    /** What the start of the running erase really took from its user's bar: an early end gives a share of it back. */
    public static void noteStartCharged(ServerPlayer player, float charged) {
        KingCrimsonAddon.temporalState(player).noteStartCharged(charged);
    }

    public static void tick(ServerPlayer player) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        state.notePosition(player.position());
        // Thrown blood flies on after the erase it was thrown in.
        KingCrimsonBloodThrowService.tick(player);
        if (state.isIdle()) {
            return;
        }
        // T1: no erase time or upkeep passes while frozen; core ServerPlayerTimeStopTickMixin already skips
        // a frozen player's tick, a fake player is ticked by its owner
        if (state.mode() == Mode.TIME_ERASE
                && TimeStopState.shouldFreezeOnServer(player)) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (state.advanceEraseWorldTime(worldTimePerTick(player))) {
            KingCrimsonAddon.syncTemporalState(player);
        }
        if (!player.isAlive()
                || !KingCrimsonAddon.isKingCrimsonUser(player)
                || gameTime >= state.expiresAt()) {
            terminate(player);
            return;
        }

        if (state.mode() == Mode.TIME_ERASE) {
            if (!player.hasEffect(KingCrimsonAddon.TIME_ERASE)
                    || player.isUsingItem()) {
                terminate(player);
            } else {
                // Owner 2026-11-07 (EV1105AC section 14): another time ability may throw the eraser out of his erase
                // where he stands, "直接被強制退出時刪並顯示黃鎮的歸零特效"; the core's event carries the question.
                TimeEraseRunningEvent running =
                        TimeEraseRunningEvent.post(player, state.elapsedWorldTicks(gameTime));
                if (running.isCanceled()) {
                    throwOut(player, running.getAnsweredBy() != null);
                    return;
                }
                StandPower power = StandPower.get(player);
                Ability erase = power != null ? power.getAbility("kingcrimson_timeerase") : null;
                // 1.16 KingCrimsonTimeErase.standTickPerform: a failed upkeep ends the erase without a message.
                // KC-MIH (T3-13): the upkeep is paid in the user's own time.
                // The passive's erases are paid for at the hit, or cost nothing.
                if (!state.eraseKind().isPassive() && (erase == null || !StandAbilityStamina.consume(
                        erase, power, KingCrimsonConfig.timeEraseStaminaPerTick() * ownTimeRate(player), true))) {
                    terminate(player);
                    return;
                }
                if (!state.eraseKind().isPassive()) {
                    KingCrimsonEraseStamina.takeRegenerationBack(power);
                }
                KingCrimsonTimeEraseProjectionService.tick(player);
                KingCrimsonTimeEraseShiftService.Session shift = state.shiftSession();
                if (shift != null) {
                    shift.tick(player);
                }
                hideStandSummonedDuringErase(player, state);
                player.resetFallDistance();
            }
        }
    }

    // The erase goes on through a recall, so a Stand can be summoned in its course.
    private static void hideStandSummonedDuringErase(
            ServerPlayer player, KingCrimsonTemporalState state) {
        StandEntity stand = summonedStand(player);
        if (stand == null
                || stand.getUUID().equals(state.hiddenStandUuid())) {
            return;
        }
        state.takeOverHiddenStand(
                stand.getUUID(),
                stand.getEffect(MobEffects.INVISIBILITY));
        stand.removeEffect(MobEffects.INVISIBILITY);
        stand.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                (int) Math.max(
                        state.expiresAt() - player.level().getGameTime(),
                        1L) + 2,
                0,
                false,
                false,
                false));
    }

    /**
     * Whether the hit does not reach him. In an erase nothing does but what pierces it (KingCrimsonEraseReach, which
     * already answered the invulnerability check before any damage event); a Time Skip's ten ticks stop what
     * invulnerability stops.
     */
    public static boolean blocksDamage(
            ServerPlayer player, DamageSource source) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        long gameTime = player.level().getGameTime();
        if (state.isErasing(gameTime)) {
            return !KingCrimsonEraseReach.pierces(source);
        }
        return state.isSkipping(gameTime)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static void terminateOnInteraction(
            ServerPlayer player) {
        if (!player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            return;
        }
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (state.mode() == Mode.TIME_ERASE
                && !deferEndWhileFrozen(player)) {
            terminate(player);
        }
    }

    /**
     * Owner rule T1 (2026-10-30): an erase frozen in someone's time stop cannot be ended by its user, so a
     * release or an interaction during the stop ends it only when time resumes. True when the end was deferred.
     */
    public static boolean deferEndWhileFrozen(ServerPlayer player) {
        if (!player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            return false;
        }
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (!state.isErasePaused()) {
            return false;
        }
        state.requestEndOnResume();
        return true;
    }

    /**
     * Runs once per server tick for every erasing user, also while the user is frozen and so does not tick:
     * time spent frozen in someone's time stop is added to the erase, which goes on with what was left.
     */
    public static void tickTimeStopPause(ServerPlayer player) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        if (state.mode() != Mode.TIME_ERASE) {
            return;
        }
        if (player.isAlive()
                && TimeStopState.shouldFreezeOnServer(player)) {
            if (state.pauseEraseTick()) {
                KingCrimsonAddon.syncTemporalState(player);
            }
            return;
        }
        if (state.isErasePaused()) {
            boolean endRequested = state.resumeErase();
            KingCrimsonTimeEraseShiftService.Session shift = state.shiftSession();
            if (shift != null) {
                shift.afterPause();
            }
            KingCrimsonAddon.syncTemporalState(player);
            if (endRequested) {
                terminate(player);
            }
        }
    }

    /** Whether the player erases time now, on either side (the clients have his synced state). */
    public static boolean isErasing(Player player) {
        return player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && KingCrimsonAddon.temporalState(player)
                        .isErasing(player.level().getGameTime());
    }

    /**
     * Whether the player's erase goes on with its key up, on either side: Blood Throw was thrown as the key was let
     * go (EV1105AC section 8), and a press of the key now ends the erase.
     */
    public static boolean isKeyUpErase(Player player) {
        return player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && KingCrimsonAddon.temporalState(player).isKeyUp()
                && isErasing(player);
    }

    /** The erase key was let go and the erase goes on; true when an erase is running to go on. */
    public static boolean keepEraseWithKeyUp(ServerPlayer player) {
        if (!isErasing(player)) {
            return false;
        }
        KingCrimsonAddon.temporalState(player).setKeyUp();
        KingCrimsonAddon.syncTemporalState(player);
        return true;
    }

    public static boolean isErasePaused(Player player) {
        return player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                && KingCrimsonAddon.temporalState(player)
                        .isErasePaused();
    }

    public static void terminate(ServerPlayer player) {
        end(player, false, true);
    }

    /**
     * The erase ends at once where its user stands, and nothing of his own comes of it: a blow he began inside it is
     * called off. The cost is kept, the cooldown starts and the rest of the end runs as at any end. Whoever answered
     * shows its own effect in place of the erase's.
     */
    private static void throwOut(ServerPlayer player, boolean answered) {
        // TimeEraseAttackPhaseMixin calls off the blows begun up to now; a press that still waits for its turn goes too.
        KingCrimsonAddon.temporalState(player).noteThrownOut(player.level().getGameTime());
        ActionInputBuffer.get(player).clear();
        end(player, true, !answered);
    }

    private static void end(ServerPlayer player, boolean thrownOut, boolean ownExitEffects) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        Mode previousMode = state.mode();
        if (previousMode == Mode.IDLE) {
            return;
        }

        long gameTime = player.level().getGameTime();
        int elapsedEraseTicks = previousMode == Mode.TIME_ERASE
                ? state.elapsedWorldTicks(gameTime)
                : 0;
        MobEffectInstance previousInvisibility =
                previousMode == Mode.TIME_ERASE
                        ? state.previousInvisibility(gameTime)
                        : null;
        MobEffectInstance previousFullInvisibility =
                previousMode == Mode.TIME_ERASE
                        ? state.previousFullInvisibility(gameTime)
                        : null;
        UUID hiddenStandUuid = previousMode == Mode.TIME_ERASE
                ? state.hiddenStandUuid()
                : null;
        MobEffectInstance previousStandInvisibility =
                previousMode == Mode.TIME_ERASE
                        ? state.previousStandInvisibility(gameTime)
                        : null;
        StandEntity hiddenStand = previousMode == Mode.TIME_ERASE
                ? findStand(player, hiddenStandUuid)
                : null;
        List<MobEffectInstance> suspendedEffects = previousMode == Mode.TIME_ERASE
                ? state.suspendedEffectsLeft(gameTime)
                : List.of();
        if (previousMode == Mode.TIME_ERASE) {
            if (gameTime < state.expiresAt()) {
                // An erase cut short names what cut it: the server cannot tell a key let go on the client from a press.
                LOGGER.info("Time Erase of {} ended {} world ticks before its end (began at {}), by {}",
                        player.getGameProfile().getName(), state.expiresAt() - gameTime, state.startedAt(),
                        terminateCaller());
            }
            KingCrimsonTimeEraseProjectionService.finish(player);
        }
        boolean liveErase = previousMode == Mode.TIME_ERASE && state.isLive();
        EraseKind endedKind = state.eraseKind();
        KingCrimsonTimeEraseShiftService.Session shift = state.takeShiftSession();
        float startCharged = state.startCharged();
        int eraseLength = state.eraseLengthTicks();
        state.clear();
        // Time Erase removes the caster from danger, not other entities' outcomes.

        if (previousMode == Mode.TIME_ERASE) {
            player.removeEffect(KingCrimsonAddon.TIME_ERASE);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.removeEffect(ModStatusEffects.FULL_INVISIBILITY);
            if (previousInvisibility != null) {
                player.addEffect(previousInvisibility);
            }
            if (previousFullInvisibility != null) {
                player.addEffect(previousFullInvisibility);
            }
            for (MobEffectInstance effect : suspendedEffects) {
                player.addEffect(effect);
            }
            if (hiddenStand != null) {
                hiddenStand.removeEffect(MobEffects.INVISIBILITY);
                if (previousStandInvisibility != null) {
                    hiddenStand.addEffect(
                            previousStandInvisibility);
                }
            }
            // Owner 2026-11-07: the passive's two erases neither start the key's cooldown nor wait on it.
            if (!endedKind.isPassive()) {
                setTimeEraseCooldown(player, elapsedEraseTicks);
            }
            // Owner 2026-11-11: what an erase that ends early did not use of its start comes back, as with the core's
            // time stop. One who was thrown out of his erase gets nothing back: his cost is kept.
            if (!endedKind.isPassive() && !thrownOut) {
                KingCrimsonEraseStamina.refundUnused(player, startCharged, eraseLength, elapsedEraseTicks);
            }
            if (liveErase) {
                KingCrimsonTimeEraseEnd.run(player, shift, elapsedEraseTicks, thrownOut);
            }
            stopTimeEraseStartSound(player);
            if (ownExitEffects) {
                sendTimeEraseEndPresentation(player);
            }
        }
        KingCrimsonAddon.syncTemporalState(player);
    }

    /**
     * Owner Q4: an erase returned to zero never begins: its user stays where he began (the event's position is where
     * he stands), its activation stamina is paid by the action as for any refused erase, its cooldown is set, and the
     * end of an erase is heard and seen.
     */
    private static String terminateCaller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(KingCrimsonTemporalStateService.class.getName()))
                .limit(5)
                .map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                        + "." + frame.getMethodName())
                .collect(Collectors.joining(" <- ")));
    }

    private static void returnEraseToZero(ServerPlayer player, EraseKind kind) {
        // The passive's cost and cooldown are its own (KingCrimsonReflexErase): the key's cooldown is not touched.
        if (!kind.isPassive()) {
            setTimeEraseCooldown(player, 0, false);
        }
        stopTimeEraseStartSound(player);
        sendTimeEraseEndPresentation(player);
    }

    /** Rule G1 (A2-EVENTS RewindAppliedEvent): a rewind ends the erases that began after its point inside its region. */
    public static void endErasesBegunAfter(
            ServerLevel level, long rewindPoint, AABB region) {
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
                KingCrimsonTemporalState state =
                        KingCrimsonAddon.temporalState(player);
                if (state.mode() == Mode.TIME_ERASE
                        && state.startedAt() > rewindPoint
                        && region.intersects(player.getBoundingBox())) {
                    terminate(player);
                }
            }
        }
    }

    private static boolean canStartTemporalAction(
            ServerPlayer player) {
        // T1: a user frozen in someone's time stop starts neither an erase nor a skip (Epitaph's included).
        return player.isAlive()
                && KingCrimsonAddon.isKingCrimsonUser(player)
                && KingCrimsonAddon.temporalState(player).isIdle()
                && !TimeStopState.shouldFreezeOnServer(player);
    }

    private static Vec3 findTimeSkipDestination(
            ServerPlayer player) {
        boolean grounded = player.onGround();
        PlayerMovementInputData input = grounded
                ? PlayerMovementInputData.get(player)
                : null;
        return findDirectionalDestination(
                player,
                timeSkipDirection(
                        grounded,
                        player.getYRot(),
                        input != null ? input.left : 0.0F,
                        input != null ? input.forward : 0.0F,
                        player.getLookAngle()),
                KingCrimsonPhase5Spec.TIME_SKIP_MAX_DISTANCE);
    }

    static Vec3 timeSkipDirection(
            boolean grounded,
            float yaw,
            float left,
            float forward,
            Vec3 airborneDirection) {
        if (!grounded) {
            return airborneDirection;
        }
        if (!Float.isFinite(yaw)
                || !Float.isFinite(left)
                || !Float.isFinite(forward)
                || Math.abs(left) > 1.0F
                || Math.abs(forward) > 1.0F) {
            return Vec3.ZERO;
        }
        double lengthSqr = (double) left * left
                + (double) forward * forward;
        if (lengthSqr < 1.0E-8D) {
            return Vec3.ZERO;
        }
        // Grounded time skip advances movement input, not the camera's pitch.
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double length = Math.sqrt(lengthSqr);
        return new Vec3(
                (left * cos - forward * sin) / length,
                0.0D,
                (left * sin + forward * cos) / length);
    }

    private static Vec3 findDirectionalDestination(
            ServerPlayer player,
            Vec3 direction,
            double maximumDistance) {
        Vec3 origin = player.position();
        if (direction.lengthSqr() < 1.0E-8D) {
            return origin;
        }
        direction = direction.normalize();
        Vec3 best = origin;
        for (double distance = KingCrimsonPhase5Spec.TIME_SKIP_STEP;
                distance <= maximumDistance;
                distance += KingCrimsonPhase5Spec.TIME_SKIP_STEP) {
            Vec3 candidate =
                    origin.add(direction.scale(distance));
            if (!isLoadedCollisionFree(player, candidate)) {
                break;
            }
            best = candidate;
        }
        return best;
    }

    private static boolean isLoadedCollisionFree(
            ServerPlayer player, Vec3 candidate) {
        BlockPos blockPos = BlockPos.containing(candidate);
        return player.level().hasChunkAt(blockPos)
                && blockPos.getY()
                        >= player.level().getMinBuildHeight()
                && blockPos.getY() + 2
                        < player.level().getMaxBuildHeight()
                && player.level().noCollision(
                        player,
                        player.getBoundingBox().move(
                                candidate.subtract(
                                        player.position())));
    }

    static void makeNearbyMobsLoseTarget(
            ServerPlayer player, Vec3 destination) {
        player.level().getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(8.0D),
                        mob -> mob.getTarget() == player
                                && !TimeStopState.shouldFreezeOnServer(mob))
                .forEach(mob -> {
                    if (mob.distanceToSqr(destination)
                            > mob.distanceToSqr(player)) {
                        mob.setTarget(null);
                    }
                });
    }

    private static void advanceImpliedTickCounters(
            ServerPlayer player, int impliedTicks) {
        int ticks = Math.max(impliedTicks, 0);
        player.tickCount = saturatingAdd(player.tickCount, ticks);
        StandPower power = StandPower.get(player);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand != null) {
            stand.tickCount = saturatingAdd(
                    stand.tickCount, ticks);
            stand.overlayTickCount = saturatingAdd(
                    stand.overlayTickCount, ticks);
        }
    }

    private static List<Mob> collectTimeSkipMobs(
            ServerPlayer player) {
        // RS-T3 D3 / rule G2: mob.tick() here bypasses the core's ServerLevelTimeStopTickMixin, so a mob frozen
        // in a time stop must be left out or it would live through the skipped ticks.
        return player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(32.0D),
                mob -> mob.isAlive() && !mob.isRemoved()
                        && !TimeStopState.shouldFreezeOnServer(mob));
    }

    private static void advanceImpliedMobTicks(
            List<Mob> mobs, int impliedTicks) {
        if (impliedTicks <= 0) {
            return;
        }
        for (Mob mob : mobs) {
            // KC-MIH (T3-14): a mob whose own time is held back lives through that much less of the skip.
            int ticks = (int) Math.floor(
                    impliedTicks * ownTimeRate(mob) + 1.0E-4D);
            for (int i = 0; i < ticks; i++) {
                // a mob that walks into stopped time during the skip stops there
                if (!mob.isAlive() || mob.isRemoved()
                        || TimeStopState.shouldFreezeOnServer(mob)) {
                    break;
                }
                // The 1.16.5 ability advanced nearby entities through their
                // real tick path. Keep the scope to mobs, but preserve that
                // behavior instead of approximating it with velocity-only
                // movement and a synthetic tick counter.
                mob.tick();
            }
            mob.hurtMarked = true;
            mob.hasImpulse = true;
        }
    }

    @Nullable
    private static StandEntity summonedStand(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                ? power.getSummonedStandEntity()
                : null;
    }

    @Nullable
    private static StandEntity findStand(
            ServerPlayer player, @Nullable UUID standUuid) {
        if (standUuid == null) {
            return null;
        }
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(standUuid);
            if (entity instanceof StandEntity stand) {
                return stand;
            }
        }
        return null;
    }

    private static void stopTimeEraseStartSound(
            ServerPlayer source) {
        source.connection.send(new ClientboundStopSoundPacket(
                KingCrimsonAddon.id("time_erase_start"),
                SoundSource.PLAYERS));
    }

    static void sendTimeEraseEndPresentation(
            ServerPlayer source) {
        sendTimeEraseEndSound(source);
        // Owner 2026-11-07: those around hear an erase end, as in 1.16; its red flash is its user's alone.
        PacketDistributor.sendToPlayer(
                source, TimeEraseTransitionPayload.INSTANCE);
    }

    /** Those around hear an erase end, whoever erased: a player, or a creature that carries King Crimson. */
    static void sendTimeEraseEndSound(
            LivingEntity source) {
        if (!(source.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = KingCrimsonPhase5Spec
                .TIME_ERASE_PRESENTATION_RADIUS;
        double radiusSqr = radius * radius;
        ClientboundSoundPacket sound = new ClientboundSoundPacket(
                KingCrimsonAddon.TIME_ERASE_END,
                SoundSource.PLAYERS,
                source.getX(),
                source.getY(),
                source.getZ(),
                1.0F,
                1.0F,
                source.getRandom().nextLong());
        for (ServerPlayer observer :
                level.players()) {
            if (observer.distanceToSqr(source) <= radiusSqr) {
                observer.connection.send(sound);
            }
        }
    }

    private static int saturatingAdd(int value, int amount) {
        return value > Integer.MAX_VALUE - amount
                ? Integer.MAX_VALUE
                : value + amount;
    }

    private static SoundEvent chooseTimeSkipSound(
            RandomSource random) {
        return random.nextBoolean()
                ? KingCrimsonAddon.KINGCRIMSON_TIMESKIP.get()
                : KingCrimsonAddon.KINGCRIMSON_TIMESKIP2.get();
    }

    /** The key's cooldown at the end of an erase that ran so long. */
    static void setTimeEraseCooldown(
            LivingEntity user, int elapsedTicks) {
        setTimeEraseCooldown(user, elapsedTicks, true);
    }

    /**
     * @param ran whether an erase ran and ends here; false for one returned to zero as it began
     */
    private static void setTimeEraseCooldown(
            LivingEntity user, int elapsedTicks, boolean ran) {
        StandPower power = StandPower.get(user);
        if (power == null) {
            return;
        }
        @Nullable
        Ability ability = power.getAbility(
                "kingcrimson_timeerase");
        if (ability != null) {
            if (ran) {
                // The core's fatigue of the mode with no cooldowns, met as its time stop meets it: the start of
                // the erase paid the repeat (KingCrimsonTimeEraseAbility), its end adds the cooldown that mode
                // skips. Outside the wrap the end would be counted as a second use.
                StandAbilityFatigue.withoutFatigue(() -> ability.setCooldownOnUse(power, elapsedTicks));
                StandAbilityFatigue.addFatigue(power, ability, ability.getNormalCooldown(power, elapsedTicks));
            } else {
                // Pressed and over in one moment: a plain use for the core, repeat and fatigue at once.
                ability.setCooldownOnUse(power, elapsedTicks);
            }
            // KC-MIH (T3-13): the cooldown runs in the user's own time. The core counts Stand cooldowns down at
            // the STAND_ACTIONS rate already, so only a slower own time is added here.
            float own = ownTimeRate(user);
            float counted = Math.min(1.0F, EntityTimeRates.rate(
                    EntityTimeRates.Channel.STAND_ACTIONS, user));
            int cooldown = power.getAbilityCooldown(
                    "kingcrimson_timeerase");
            if (cooldown > 0 && own < counted) {
                // Worked out from the cooldown read back, which is in the Stand's cooldown share already.
                power.restoreAbilityCooldown(
                        "kingcrimson_timeerase",
                        (int) Math.ceil(cooldown * counted / own));
            }
            KingCrimsonTimeEraseStepService.onEraseCooldown(power);
        }
    }
}

package rotp.addons.madeinheaven.state;

import rotp.core.customobjects.AfterimageEntity;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.MadeInHeavenServerConfig;
import rotp.addons.madeinheaven.ability.UniverseResetAbility;
import rotp.addons.madeinheaven.network.TimeAccelerationStatePayload;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The manga's acceleration: the server keeps its tick rate, the day time
// runs on as in 1.16 GameplayEventHandler/TimeUtil and belongs to the
// acceleration (rule G3), other users' time stops are cut short, and the
// server config's mode decides the rest (TimeAccelerationMode): every other
// living being falls behind (TimeLagService), or the non-living world and
// the weather run m times faster (TimeAccelerationRates) while the owner
// adapts.
public final class TimeAccelerationService {
    public static final int BUFF_PHASE = 4;
    public static final int LAST_PHASE = TimeLagRates.LAST_PHASE;
    public static final int LEGACY_PHASE_TICKS = 150;
    public static final long DAY_TIME_STEP = 20L;
    // ServerLevel.THUNDER_DELAY, which is private
    private static final IntProvider THUNDER_DELAY =
            UniformInt.of(12000, 180000);

    private TimeAccelerationService() {}

    public static boolean start(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        if (level.dimension() != Level.OVERWORLD
                || !level.getGameRules().getBoolean(
                        GameRules.RULE_DAYLIGHT)
                || !canStart(owner)) {
            return false;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (state.hasSession()) {
            return false;
        }
        state.begin(owner.getUUID(),
                MadeInHeavenServerConfig.accelerationMode());
        state.ownDayTime(level.getDayTime());
        playLoopSounds(level, owner);
        syncAll(level.getServer(), state);
        return true;
    }

    public static boolean togglePause(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        TimeAccelerationState state = MadeInHeavenAddon.accelerationState(level);
        if (level.dimension() != Level.OVERWORLD || !owner.isAlive()
                || !stillOwnsStandPower(owner) || !state.ownsSession(owner.getUUID())) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (state.active()) {
            state.pause(server.getTickCount());
            stopLoopSounds(server);
            TimeLagService.endAll(server);
        }
        else {
            state.resume(server.getTickCount());
            state.ownDayTime(level.getDayTime());
            playLoopSounds(level, owner);
            TimeLagService.reconcileAttributes(server.getPlayerList().getPlayers());
        }
        syncAll(server, state);
        return true;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (!state.hasSession()) {
            return;
        }
        ServerPlayer owner =
                server.getPlayerList().getPlayer(
                        state.ownerUuid());
        if (owner == null
                || owner.serverLevel() != level
                || !owner.isAlive()) {
            clear(level, ClearReason.OWNER_INVALID);
            return;
        }
        if (!stillOwnsStandPower(owner)) {
            clear(level, ClearReason.STAND_LOSS);
            return;
        }
        if (!state.active()) {
            return;
        }

        TimeStopState timeStop =
                level.getData(ModDataAttachmentTypes.TIME_STOP);
        shortenTimeStops(level, state, timeStop);
        TimeLagService.reconcileAttributes(
                server.getPlayerList().getPlayers());
        long dayTime = ownedDayTime(level, state);
        // 1.16 onAccelerate: nothing advances while the owner stands in
        // stopped time.
        if (timeStop.isTimeStopped(owner)) {
            if (dayTime != level.getDayTime()) {
                setDayTime(level, state, dayTime);
            }
            state.ownDayTime(dayTime);
            return;
        }

        // 1.16 accelerateTime: 20 * phase extra day time every other tick,
        // a new phase every 150 ticks, and the reset once phase 31 ends.
        int phase = state.phase();
        int counter = state.tickCounter();
        long extraDayTime = counter % 2 == 0
                ? DAY_TIME_STEP * phase
                : 0L;
        int nextPhase = phase;
        if (counter % LEGACY_PHASE_TICKS == 0) {
            if (phase > LAST_PHASE) {
                setDayTime(level, state, dayTime + extraDayTime);
                UniverseResetAbility.execute(owner);
                clear(level, ClearReason.UNIVERSE_RESET);
                return;
            }
            nextPhase = phase + 1;
        }
        state.advance(nextPhase);
        setDayTime(level, state, dayTime + extraDayTime);
        if (state.worldAccelerates()) {
            accelerateWeather(level, calculatedMultiplier(phase) - 1);
        }
        StandPower power = StandPower.get(owner);
        if (power != null
                && !owner.getDeltaMovement().equals(Vec3.ZERO)) {
            power.consumeStamina(owner.getSpeed() * 5.0F);
        }
        showOwnerSpeed(owner, power, phase);
        if (nextPhase != phase) {
            syncAll(server, state);
        }
    }

    // 1.16 onLivingUpdate: a time stopper who spends
    // 2 * max / multiplier ticks in stopped time during the acceleration
    // gets their own time stop resumed, as a time-resume press would.
    private static void shortenTimeStops(
            ServerLevel level,
            TimeAccelerationState state,
            TimeStopState timeStop) {
        if (timeStop.getInstances().isEmpty()) {
            return;
        }
        int multiplier = calculatedMultiplier(state.phase());
        for (Entity entity : level.getAllEntities()) {
            // Only entities that actually tick, as with 1.16's update event.
            if (!(entity instanceof LivingEntity living)
                    || !timeStop.isTimeStopped(living)
                    || timeStop.shouldFreeze(living)
                    || !level.isPositionEntityTicking(
                            living.blockPosition())) {
                continue;
            }
            StandPower power = StandPower.get(living);
            if (power == null
                    || !power.hasPower()
                    || !hasUnlockedTimeStop(power)) {
                continue;
            }
            // maxTicks is the user's non-creative maximum, like 1.16's
            // getMaxTimeStopTicks.
            int ticks = state.countTimeStopTick(living);
            int maxTicks = TimeStopLearning.getMaxTrainingPoints(power)
                    + TimeStopLearning.MIN_TIME_STOP_TICKS;
            long limit = 2L * maxTicks / multiplier;
            if (ticks >= limit) {
                timeStop.requestManualResume(living.getId());
                state.resetTimeStopTicks(living);
            }
        }
    }

    private static boolean hasUnlockedTimeStop(StandPower power) {
        for (Ability ability :
                power.getMoveset().abilities.values()) {
            if (ability instanceof TimeStopAbility
                    && ability.isAbilityUnlocked(power)) {
                return true;
            }
        }
        return false;
    }

    // Rule G3 (RS-T3 rows T3-22 to T3-24): while the acceleration runs the
    // day time is its own. A rewind (Return to Zero, Bites the Dust, Mandom)
    // that set it back since the acceleration last set it is undone, at the
    // head of the next server tick (reassertDayTime) or here; vanilla's
    // daylight step and a forward change (a night slept through) go along.
    private static long ownedDayTime(
            ServerLevel level, TimeAccelerationState state) {
        long observed = level.getDayTime();
        if (!state.ownsDayTime() || observed >= state.dayTime()) {
            return observed;
        }
        return state.dayTime()
                + (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                        ? 1L
                        : 0L);
    }

    private static void setDayTime(
            ServerLevel level, TimeAccelerationState state, long dayTime) {
        level.setDayTime(dayTime);
        state.ownDayTime(dayTime);
        broadcastDayTime(level);
    }

    /** Server tick head: what set the day time back between two ticks is undone. */
    public static void reassertDayTime(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (state.active()
                && state.ownsDayTime()
                && level.getDayTime() < state.dayTime()) {
            level.setDayTime(state.dayTime());
            broadcastDayTime(level);
        }
    }

    // Mode B: the weather runs m times faster, as ServerLevel's
    // advanceWeatherCycle would on m - 1 more ticks: its countdowns (while
    // the weather cycle runs; a time stop turns it off), then the rain and
    // thunder levels, sent as vanilla sends them.
    static void accelerateWeather(ServerLevel level, int extraTicks) {
        if (extraTicks <= 0 || !level.dimensionType().hasSkyLight()) {
            return;
        }
        boolean wasRaining = level.isRaining();
        ServerLevelData data =
                level.getServer().getWorldData().overworldData();
        if (level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            int clear = data.getClearWeatherTime();
            int thunder = data.getThunderTime();
            int rain = data.getRainTime();
            boolean thundering = data.isThundering();
            boolean raining = data.isRaining();
            RandomSource random = level.getRandom();
            for (int tick = 0; tick < extraTicks; tick++) {
                if (clear > 0) {
                    clear--;
                    thunder = thundering ? 0 : 1;
                    rain = raining ? 0 : 1;
                    thundering = false;
                    raining = false;
                    continue;
                }
                if (thunder > 0) {
                    if (--thunder == 0) {
                        thundering = !thundering;
                    }
                }
                else {
                    thunder = thundering
                            ? ServerLevel.THUNDER_DURATION.sample(random)
                            : THUNDER_DELAY.sample(random);
                }
                if (rain > 0) {
                    if (--rain == 0) {
                        raining = !raining;
                    }
                }
                else {
                    rain = raining
                            ? ServerLevel.RAIN_DURATION.sample(random)
                            : ServerLevel.RAIN_DELAY.sample(random);
                }
            }
            data.setThunderTime(thunder);
            data.setRainTime(rain);
            data.setClearWeatherTime(clear);
            data.setThundering(thundering);
            data.setRaining(raining);
        }
        float step = 0.01F * extraTicks;
        float rainLevel = level.getRainLevel(1.0F);
        // getThunderLevel is the thunder's own level times the rain's
        if (rainLevel > 0.0F) {
            float thunderLevel = level.getThunderLevel(1.0F) / rainLevel;
            float nextThunder = Mth.clamp(thunderLevel
                    + (data.isThundering() ? step : -step), 0.0F, 1.0F);
            if (nextThunder != thunderLevel) {
                level.setThunderLevel(nextThunder);
                sendWeather(level, ClientboundGameEventPacket
                        .THUNDER_LEVEL_CHANGE, nextThunder);
            }
        }
        float nextRain = Mth.clamp(rainLevel
                + (data.isRaining() ? step : -step), 0.0F, 1.0F);
        if (nextRain != rainLevel) {
            level.setRainLevel(nextRain);
            sendWeather(level, ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
                    nextRain);
        }
        if (wasRaining != level.isRaining()) {
            sendWeather(level, wasRaining
                    ? ClientboundGameEventPacket.STOP_RAINING
                    : ClientboundGameEventPacket.START_RAINING, 0.0F);
            sendWeather(level, ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
                    level.getRainLevel(1.0F));
            float rainNow = level.getRainLevel(1.0F);
            sendWeather(level, ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,
                    rainNow > 0.0F ? level.getThunderLevel(1.0F) / rainNow
                            : 0.0F);
        }
    }

    private static void sendWeather(
            ServerLevel level,
            ClientboundGameEventPacket.Type type,
            float value) {
        level.getServer().getPlayerList().broadcastAll(
                new ClientboundGameEventPacket(type, value),
                level.dimension());
    }

    // 1.16 shipped with a required time mod that resent the day time every
    // tick; without it clients would see the sky jump once a second.
    private static void broadcastDayTime(ServerLevel level) {
        ClientboundSetTimePacket packet =
                new ClientboundSetTimePacket(
                        level.getGameTime(),
                        level.getDayTime(),
                        level.getGameRules().getBoolean(
                                GameRules.RULE_DAYLIGHT));
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    public static boolean isActiveOwner(
            LivingEntity candidate) {
        if (candidate == null) {
            return false;
        }
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(
                        candidate.level());
        return state.isOwner(candidate.getUUID());
    }

    public static boolean isSessionOwner(LivingEntity candidate) {
        return candidate != null && candidate.level().dimension() == Level.OVERWORLD
                && MadeInHeavenAddon.accelerationState(candidate.level())
                        .ownsSession(candidate.getUUID());
    }

    public static int phase(Level level) {
        return MadeInHeavenAddon
                .accelerationState(level)
                .phase();
    }

    // How far every other living being falls behind; also the 1.16
    // time-stop cut. 1.16 TimeUtil.getCalculatedPhase for phases 0-25, then
    // 8, 10, 12, 16, 20 for phases 26-30, and 20 again in phase 31.
    public static int calculatedMultiplier(int phase) {
        return TimeLagRates.lagMultiplier(phase);
    }

    public static void clearIfOwner(
            LivingEntity owner, ClearReason reason) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (state.ownsSession(owner.getUUID())) {
            clear(level, reason);
        }
    }

    public static void clear(
            ServerLevel level, ClearReason reason) {
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(level);
        if (!state.hasSession()) {
            return;
        }
        MinecraftServer server = level.getServer();
        stopLoopSounds(server);
        state.clear();
        TimeLagService.endAll(server);
        syncAll(server, state);
    }

    public static void syncTo(ServerPlayer player) {
        TimeAccelerationState state =
                MadeInHeavenAddon.accelerationState(
                        player.getServer().overworld());
        PacketDistributor.sendToPlayer(
                player,
                TimeAccelerationStatePayload.from(state));
    }

    private static boolean canStart(ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        StandEntity stand =
                power != null
                        ? power.getSummonedStandEntity()
                        : null;
        return stillOwnsStandPower(owner)
                && stand != null
                && !stand.isArmsOnlyMode()
                && stand.isAlive();
    }

    private static boolean stillOwnsStandPower(
            ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == MadeInHeavenAddon
                                .MADE_IN_HEAVEN.get();
    }

    // What is left of 1.16 boostOnAcceleration, with the phase from before
    // this tick's advance: the owner keeps his normal pace (the others fall
    // behind him instead), so the +40% per phase and the higher step are
    // gone. The afterimages stay as the sign of his speed, and so does the
    // 1.16 run across water and lava (the manga's last fight is at sea,
    // where Pucci keeps up with the others in the water). Unlike 1.16 it
    // keeps working while the Stand is retracted,
    // because the acceleration itself now outlives a retraction.
    private static void showOwnerSpeed(
            ServerPlayer owner,
            StandPower power,
            int phase) {
        boolean movingFast = owner.isSprinting()
                || (owner.isSwimming()
                        && owner.isInWater());
        if (!movingFast || phase < BUFF_PHASE) {
            return;
        }
        AfterimageEntity.addAfterimages(owner, 3, 75);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand != null) {
            AfterimageEntity.addAfterimages(stand, 3, 75);
        }
        liquidSurfaceTick(owner);
    }

    private static void liquidSurfaceTick(
            ServerPlayer owner) {
        if ((!owner.isInWater() && !owner.isInLava())
                || !owner.isSprinting()
                || owner.isShiftKeyDown()) {
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power == null
                || power.getStamina() <= 0.0F
                || !power.consumeStamina(2.0F, true)) {
            return;
        }
        Vec3 motion = owner.getDeltaMovement();
        owner.setDeltaMovement(
                motion.x,
                Math.max(motion.y, 0.0D),
                motion.z);
        owner.resetFallDistance();
        owner.setOnGround(true);
        owner.hasImpulse = true;
        if (owner.isInLava() && !owner.fireImmune()) {
            owner.hurt(owner.damageSources().lava(), 1.0F);
        }
    }

    private static void playLoopSounds(
            ServerLevel level,
            ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand != null) {
            level.playSound(
                    null,
                    stand.getX(),
                    stand.getY(),
                    stand.getZ(),
                    MadeInHeavenAddon
                            .MIH_TIME_ACCELERATION.get(),
                    SoundSource.VOICE,
                    1.0F,
                    1.0F);
        }
        level.playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                MadeInHeavenAddon
                        .MIH_TIME_ACCELERATION_USER.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void stopLoopSounds(
            MinecraftServer server) {
        ClientboundStopSoundPacket standStop =
                new ClientboundStopSoundPacket(
                        MadeInHeavenAddon.id(
                                "mih_time_acceleration"),
                        SoundSource.VOICE);
        ClientboundStopSoundPacket userStop =
                new ClientboundStopSoundPacket(
                        MadeInHeavenAddon.id(
                                "mih_time_acceleration_user"),
                        SoundSource.PLAYERS);
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            player.connection.send(standStop);
            player.connection.send(userStop);
        }
    }

    private static void syncAll(
            MinecraftServer server,
            TimeAccelerationState state) {
        TimeAccelerationStatePayload payload =
                TimeAccelerationStatePayload.from(state);
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public enum ClearReason {
        MANUAL,
        OWNER_DEATH,
        OWNER_LOGOUT,
        DIMENSION_CHANGE,
        STAND_LOSS,
        OWNER_INVALID,
        SERVER_STOP,
        UNIVERSE_RESET
    }
}

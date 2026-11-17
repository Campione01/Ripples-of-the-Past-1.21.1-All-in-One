package rotp.addons.diegosworld.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's rule for every time stop (2026-11-05 and 2026-11-06): the charge is the core's setting, 1.5 s unless a
 * player changes it; let go before half of it and nothing happens; a quick double press stops time at once. The donor's
 * THEWORLDTimeStop had no charge, and its press stopped time at once. Pressed by the key as the server takes a press, the
 * charge ticked by hand inside one game tick, the stop moved far off so it freezes nothing here.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class DiegoTimeStopChargeGameTests {
    private static final short KEY = 127;

    private DiegoTimeStopChargeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotpDiegoTimeStopCharge")
    public static void theTimeStopChargesForTheCoresSettingAndFiresFromHalfOfIt(GameTestHelper helper) {
        Scene s = new Scene(helper, "diego-ts-charge");
        try {
            s.power.setResolveLevel(3);
            EntityActionInstance held = s.press(JojoModConfig.TIME_STOP_CHARGE_SECONDS_DEFAULT);
            helper.assertTrue(held != null && held.getPhase() == ActionPhase.BUTTON_CHARGE && held.getCurPhaseLength() == 30.0F,
                    "The time stop must charge for the core's 1.5 s, 30 ticks: " + describe(held));
            s.release();
            s.restart();
            // a changed setting reaches the next press
            held = s.press(2.5D);
            helper.assertTrue(held != null && held.getCurPhaseLength() == 50.0F,
                    "At 2.5 s the charge must be 50 ticks: " + describe(held));
            s.tick(24);
            s.release();
            helper.assertTrue(s.instance() == null, "A release a tick under half of the charge stopped time: " + s.instance());
            s.restart();
            s.press(2.5D);
            s.tick(25);
            s.release();
            helper.assertTrue(s.instance() != null, "A release at half of the charge must stop time");
            s.endStop();
            // The World keeps two thirds of the charge from Resolve 4
            s.power.setResolveLevel(4);
            for (double[] row : new double[][] { { 1.5D, 20.0D }, { 2.5D, 33.0D } }) {
                s.restart();
                held = s.press(row[0]);
                helper.assertTrue(held != null && held.getCurPhaseLength() == (float) row[1],
                        "At Resolve 4 and " + row[0] + " s The World charges for " + row[1] + " ticks: " + describe(held));
                s.release();
            }
        }
        finally {
            s.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotpDiegoTimeStopDouble")
    public static void aTapStopsNothingAndAQuickDoublePressStopsTimeAtOnce(GameTestHelper helper) {
        Scene s = new Scene(helper, "diego-ts-double");
        try {
            EntityActionInstance first = s.press(JojoModConfig.TIME_STOP_CHARGE_SECONDS_DEFAULT);
            helper.assertTrue(first != null && first.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "A press must begin a charge: " + describe(first));
            s.release();
            helper.assertTrue(s.instance() == null, "A tap stopped time: " + s.instance());
            s.tick(1);
            s.press(JojoModConfig.TIME_STOP_CHARGE_SECONDS_DEFAULT);
            helper.assertTrue(s.instance() != null && s.slot().getAction() == null,
                    "A second press at once must stop time at once, with nothing left to hold: " + s.slot().getAction());
        }
        finally {
            s.close();
        }
        helper.succeed();
    }

    private static String describe(EntityActionInstance action) {
        return action == null ? "no action" : action.ability.getClass().getSimpleName() + " in " + action.getPhase() + " of "
                + action.getCurPhaseLength();
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer user;
        private final StandPower power;
        private final Consumer<TimeStopLifecycleEvent.PreStart> isolate;

        private Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            level = helper.getLevel();
            MinecraftServer server = level.getServer();
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            user = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            new Silent(server, connection, user, CommonListenerCookie.createInitial(profile, false));
            Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            user.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            level.addNewPlayer(user);
            user.setGameMode(GameType.SURVIVAL);
            user.setNoGravity(true);
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(DiegosTheWorldAddon.THE_WORLD.get()))
                    .status() == StandPowerTransitions.Status.APPLIED, "Setup: could not grant Diego's The World");
            power.skipProgression();
            helper.assertTrue(DiegosTheWorldAddon.THE_WORLD.get().summon(user, power), "Setup: could not summon it");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            ChunkPos farAway = new ChunkPos(52500 + user.getId() * 2, 52500);
            isolate = event -> {
                if (event.getInstance().userId() == user.getId()) {
                    event.setInstance(event.getInstance().withArea(farAway, 1));
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, isolate);
        }

        LivingComponentAction slot() {
            return LivingComponentAction.getComponent(user, PowerClass.STAND);
        }

        // a press reads the setting once, so it is back before anything else can read it
        EntityActionInstance press(double seconds) {
            ModConfigSpec.DoubleValue value = JojoModConfig.COMMON_SPEC.getValues()
                    .get(List.of("Stand settings", "Time Stop", "timeStopChargeSeconds"));
            double before = value.get();
            value.set(seconds);
            try {
                power.setStamina(power.getMaxStamina());
                Ability base = power.getAbility(DiegosTheWorldAddon.TIME_STOP);
                AbilityConditionCheck container = resolve(base);
                helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, user, InputMethod.HOLD),
                        "The time stop key was refused: " + (container != null ? container.conditionCheck : null));
                HeldInputEntry held = AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.HOLD, 0,
                        BufferingState.clickCanBuffer(), base.getAbilityId());
                return held != null && held.action instanceof EntityActionInstance action ? action : null;
            }
            finally {
                value.set(before);
            }
        }

        // the power resolves its keys once a tick (Power.updateAvailableMoves)
        private AbilityConditionCheck resolve(Ability ability) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(ability);
        }

        void tick(int ticks) {
            StandEntity stand = power.getSummonedStandEntity();
            for (int tick = 0; tick < ticks; tick++) {
                if (stand != null) {
                    stand.tickCount++;
                }
                slot().tick();
            }
        }

        void release() {
            AbilityInput.keyRelease(KEY, user);
        }

        // the next press is a first press again, not the second of a double press
        void restart() {
            slot().setAction(null, SyncType.NO_SYNC);
            user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).timeStopFirstPressTime =
                    EntityActionInputState.NO_PRESS;
        }

        void endStop() {
            level.getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
            user.removeAllEffects();
            power.setAbilityCooldown(DiegosTheWorldAddon.TIME_STOP, 0);
        }

        TimeStopState.Instance instance() {
            return level.getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(user.getId()).orElse(null);
        }

        void close() {
            NeoForge.EVENT_BUS.unregister(isolate);
            release();
            slot().setAction(null, SyncType.NO_SYNC);
            level.getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
            user.removeAllEffects();
            if (power.isSummoned()) {
                DiegosTheWorldAddon.THE_WORLD.get().forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    private static final class Silent extends ServerGamePacketListenerImpl {
        private Silent(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {}
    }
}

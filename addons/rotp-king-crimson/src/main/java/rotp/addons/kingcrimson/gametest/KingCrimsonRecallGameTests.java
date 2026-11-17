package rotp.addons.kingcrimson.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonGameEvents;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The owner's rules on what is left of the moveset's keys and what a recall ends. A forced unsummon is the recall that
 * reaches a held action: the summon key starts no unsummon while the key holds one (core StandEntity.retractAndUnsummon).
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonRecallGameTests {
    private static final short EPITAPH_KEY = 11;
    private static final short ERASE_KEY = 12;

    private KingCrimsonRecallGameTests() {}

    // G7, "这两个应该也是变体的才对": 1.16 declared both as a shiftVariationOf, and addHotbarSlotVariation says nothing
    // when its base has no slot.
    @GameTest(template = "empty", timeoutTicks = 20, batch = "kcSlotVariations")
    public static void theImpaleAndTheTripleThrowAreTheShiftOfTheirBases(GameTestHelper helper) {
        ControlSchemeTemplate template = KingCrimsonAddon.KING_CRIMSON.get().makeDefaultControlSchemeTemplate();
        for (String[] row : new String[][] {
                {"kingcrimson_impale", "kingcrimson_chop"},
                {"kingcrimson_projectile_throw_tripple", "kingcrimson_projectile_throw"}}) {
            boolean variation = false;
            for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
                for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
                    for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
                        Map<InputMethod, String> plain = slot.get(null);
                        Map<InputMethod, String> shift = slot.get(InputKey.Modifier.SHIFT);
                        helper.assertTrue(plain == null || !plain.containsValue(row[0]),
                                row[0] + " is a slot of its own");
                        variation |= plain != null && plain.containsValue(row[1]) && shift != null
                                && row[0].equals(shift.get(InputMethod.CLICK));
                    }
                }
            }
            helper.assertTrue(variation, row[0] + " is not the Shift click of " + row[1]);
        }
        helper.succeed();
    }

    // A prediction lives its whole length: the press alone starts it, and the Stand put away ends nothing of it.
    @GameTest(template = "empty", timeoutTicks = 320, batch = "kcRecallEpitaph")
    public static void thePredictionGoesOnThroughARecall(GameTestHelper helper) {
        ServerPlayer player = makePlayer(helper, "kc-recall-epitaph");
        StandPower power = summoned(helper, player);
        helper.runAfterDelay(2, () -> {
            EpitaphSession session;
            try {
                session = pressEpitaph(helper, power, player);
                recall(helper, power, player);
                helper.assertTrue(!session.ended() && EpitaphSessions.of(player) == session,
                        "The prediction ended with the recall: " + session.endedAs());
                tickPredictions(helper);
                helper.assertTrue(!session.ended(),
                        "With the Stand recalled the tick of the prediction ended it: " + session.endedAs());
            } catch (RuntimeException | AssertionError failure) {
                close(power, player);
                throw failure;
            }
            // Its length: the phantom phase, then what is left of the foresight at the pace of the world.
            int left = session.clock().phase() + session.clock().foresight();
            helper.runAfterDelay(left + 1, () -> {
                try {
                    helper.assertTrue(session.endedAs() == EpitaphEnd.COMPLETE,
                            "The prediction did not end by having lived its length: " + session.endedAs());
                } finally {
                    close(power, player);
                }
                helper.succeed();
            });
        });
    }

    // The recall a player makes: the summon key starts nothing while a key holds the action, as 1.16 retractStand(true).
    @GameTest(template = "empty", timeoutTicks = 60, batch = "kcRecallKey")
    public static void theSummonKeyWaitsForAHeldKeyAndThenLeavesThePredictionRunning(GameTestHelper helper) {
        ServerPlayer player = makePlayer(helper, "kc-recall-key");
        StandPower power = summoned(helper, player);
        helper.runAfterDelay(2, () -> {
            try {
                StandEntity stand = power.getSummonedStandEntity();
                holdErase(helper, power, player);
                EntityActionInstance erase = LivingComponentAction.getCurEntityAction(stand);
                KingCrimsonAddon.KING_CRIMSON.get().unsummon(player, power);
                helper.assertTrue(LivingComponentAction.getCurEntityAction(stand) == erase
                                && KingCrimsonTemporalStateService.isErasing(player),
                        "The summon key took the Stand from under a held erase");
                AbilityInput.keyRelease(ERASE_KEY, player);
                helper.assertTrue(KingCrimsonAddon.temporalState(player).isIdle(), "Setup: the erase's key did not end it");
                LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
                power.setAbilityCooldown("kingcrimson_timeerase", 0, 0);

                EpitaphSession session = pressEpitaph(helper, power, player);
                KingCrimsonAddon.KING_CRIMSON.get().unsummon(player, power);
                EntityActionInstance unsummon = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(unsummon != null, "Setup: the summon key started no unsummon");
                for (int tick = 0; tick < 40 && power.isSummoned(); tick++) {
                    unsummon._tickAction();
                }
                helper.assertTrue(!power.isSummoned(), "Setup: the unsummon did not end");
                tickPredictions(helper);
                helper.assertTrue(!session.ended(), "The prediction ended with the recall by the summon key: " + session.endedAs());
            } finally {
                close(power, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "kcRecallErase")
    public static void theEraseGoesOnThroughARecallAndItsKeyStillEndsIt(GameTestHelper helper) {
        ServerPlayer player = makePlayer(helper, "kc-recall-erase");
        StandPower power = summoned(helper, player);
        // The decoy shows its Stand in its first tick: the chunks around it are held, so that it ticks at once.
        KingCrimsonChunkTickets tickets = new KingCrimsonChunkTickets(helper.getLevel());
        tickets.force(player.position());
        helper.runAfterDelay(2, () -> {
            try {
                holdErase(helper, power, player);
            } catch (RuntimeException | AssertionError failure) {
                close(power, player);
                tickets.release();
                throw failure;
            }
            helper.runAfterDelay(3, () -> {
                try {
                    List<KingCrimsonTimeEraseDecoyEntity> decoys = helper.getLevel().getEntitiesOfClass(
                            KingCrimsonTimeEraseDecoyEntity.class, player.getBoundingBox().inflate(8.0D));
                    helper.assertTrue(decoys.size() == 1, "Setup: the erase spawned " + decoys.size() + " decoys");
                    KingCrimsonTimeEraseDecoyEntity decoy = decoys.get(0);
                    StandPower decoyPower = StandPower.get(decoy);
                    StandEntity decoyStand = decoyPower != null ? decoyPower.getSummonedStandEntity() : null;
                    helper.assertTrue(decoyStand != null, "Setup: the decoy shows no Stand");

                    recall(helper, power, player);
                    helper.assertTrue(KingCrimsonTemporalStateService.isErasing(player),
                            "The erase ended with the recall");
                    helper.assertTrue(!decoy.isRemoved() && !decoyStand.isRemoved(),
                            "The recall took the decoy or the Stand it shows");
                    KingCrimsonTemporalStateService.tick(player);
                    helper.assertTrue(KingCrimsonTemporalStateService.isErasing(player)
                                    && player.hasEffect(KingCrimsonAddon.TIME_ERASE),
                            "With the Stand recalled the erase's own tick ended it");

                    // A summon in its course: that Stand is as hidden as the one that left.
                    helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power),
                            "Setup: no summon during the erase");
                    StandEntity next = power.getSummonedStandEntity();
                    KingCrimsonTemporalStateService.tick(player);
                    helper.assertTrue(next.hasEffect(MobEffects.INVISIBILITY),
                            "A Stand summoned during the erase is not hidden");

                    AbilityInput.keyRelease(ERASE_KEY, player);
                    helper.assertTrue(KingCrimsonAddon.temporalState(player).isIdle(),
                            "The key's release did not end the erase that went on through a recall");
                    helper.assertTrue(power.getAbilityCooldown("kingcrimson_timeerase") > 0,
                            "An erase that went on through a recall ended with no cooldown");
                    helper.assertTrue(!next.hasEffect(MobEffects.INVISIBILITY),
                            "The Stand summoned during the erase stayed hidden after it");
                    helper.assertTrue(decoy.isRemoved() && decoyStand.isRemoved(),
                            "The decoy's end did not take the Stand it shows");
                    helper.assertTrue(!next.isRemoved(), "The decoy's end took its user's own Stand");
                } finally {
                    close(power, player);
                    tickets.release();
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kcRecallEnds")
    public static void whatGoesOnEndsWithItsUserAndALossOfTheStand(GameTestHelper helper) {
        ServerPlayer player = makePlayer(helper, "kc-recall-ends");
        StandPower power = summoned(helper, player);
        helper.runAfterDelay(2, () -> {
            try {
                assertEnds(helper, power, player, "A loss of the Stand", true, () -> {
                    power.setStand(null);
                    tickPredictions(helper);
                    KingCrimsonTemporalStateService.tick(player);
                });
                assertEnds(helper, power, player, "Death", true, () -> {
                    LivingDeathEvent death = new LivingDeathEvent(player, helper.getLevel().damageSources().generic());
                    EpitaphSessions.onDeath(death);
                    KingCrimsonGameEvents.onPlayerDeath(death);
                });
                assertEnds(helper, power, player, "A logout", true, () -> {
                    PlayerEvent.PlayerLoggedOutEvent logout = new PlayerEvent.PlayerLoggedOutEvent(player);
                    EpitaphSessions.onLogout(logout);
                    KingCrimsonGameEvents.onPlayerLogout(logout);
                });
                assertEnds(helper, power, player, "A change of level", true, () -> {
                    PlayerEvent.PlayerChangedDimensionEvent change =
                            new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER);
                    EpitaphSessions.onChangedDimension(change);
                    KingCrimsonGameEvents.onPlayerChangedDimension(change);
                });
                // The erase's own rule, 1.16 KingCrimsonTimeErase.standTickPerform: an upkeep it cannot pay ends it.
                assertEnds(helper, power, player, "An empty stamina bar", false, () -> {
                    power.setStamina(0.0F);
                    KingCrimsonTemporalStateService.tick(player);
                });
            } finally {
                close(power, player);
            }
            helper.succeed();
        });
    }

    private static void assertEnds(GameTestHelper helper, StandPower power, ServerPlayer player, String end,
            boolean endsThePrediction, Runnable ending) {
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        power.setAbilityCooldown("kingcrimson_epitaph", 0, 0);
        power.setAbilityCooldown("kingcrimson_timeerase", 0, 0);
        if (!power.isSummoned()) {
            helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Setup: summon failed");
        }
        power.getSummonedStandEntity().summonLockTicks = 0;
        EpitaphSession session = pressEpitaph(helper, power, player);
        holdErase(helper, power, player);
        recall(helper, power, player);
        helper.assertTrue(!session.ended() && KingCrimsonTemporalStateService.isErasing(player),
                "Setup: the prediction and the erase did not go on through the recall before: " + end);
        ending.run();
        helper.assertTrue(session.ended() == endsThePrediction,
                end + (endsThePrediction ? " must end the prediction" : " must not end the prediction"));
        helper.assertTrue(KingCrimsonAddon.temporalState(player).isIdle(), end + " must end the erase");
        session.end(EpitaphEnd.CANCELLED);
        player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
    }

    // Pressed by its key as ClAbilityInputPacket does; the moves are resolved again, since the test does not tick the power.
    private static EpitaphSession pressEpitaph(GameTestHelper helper, StandPower power, ServerPlayer player) {
        power.setStamina(power.getMaxStamina());
        Ability base = power.getAbility("kingcrimson_epitaph");
        power._curAvailableMoves.update(power, power.getMoveset());
        var container = power._curAvailableMoves.getContextVariationContainer(base);
        helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, player, InputMethod.CLICK),
                "Setup: Epitaph was refused");
        BufferingState state = BufferingState.clickCanBuffer();
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(EPITAPH_KEY, container.ability, player, input, InputMethod.CLICK, 0.0F, state, base.getAbilityId());
        } finally {
            input.release();
        }
        AbilityInput.keyRelease(EPITAPH_KEY, player);
        EpitaphSession session = EpitaphSessions.of(player);
        helper.assertTrue(state.isActionSuccess && session != null, "Setup: the key of Epitaph began no prediction");
        return session;
    }

    // One tick of every prediction of the level, as the level gives it before it ticks.
    private static void tickPredictions(GameTestHelper helper) {
        EpitaphSessions.onLevelTickStart(new LevelTickEvent.Pre(() -> true, helper.getLevel()));
    }

    private static void holdErase(GameTestHelper helper, StandPower power, ServerPlayer player) {
        EntityActionInstance action = hold(helper, power, player, "kingcrimson_timeerase", ERASE_KEY);
        for (int tick = 0; tick < 30 && !action.isOver()
                && !KingCrimsonTemporalStateService.isErasing(player); tick++) {
            action._tickAction();
        }
        helper.assertTrue(KingCrimsonTemporalStateService.isErasing(player),
                "Setup: the held erase did not start: " + action.getPhase());
    }

    // Pressed by its key as ClAbilityInputPacket does; the moves are resolved again, since the test does not tick the power.
    private static EntityActionInstance hold(GameTestHelper helper, StandPower power, ServerPlayer player,
            String name, short key) {
        power.setStamina(power.getMaxStamina());
        Ability base = power.getAbility(name);
        power._curAvailableMoves.update(power, power.getMoveset());
        var container = power._curAvailableMoves.getContextVariationContainer(base);
        helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, player, InputMethod.HOLD),
                "Setup: " + name + " was refused");
        BufferingState state = BufferingState.clickCanBuffer();
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AbilityInput.keyPress(key, container.ability, player, input, InputMethod.HOLD, 0.0F, state, base.getAbilityId());
        } finally {
            input.release();
        }
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(power.getSummonedStandEntity());
        helper.assertTrue(state.isActionSuccess && action != null && action.ability == container.ability,
                "Setup: " + name + " did not start: " + action);
        return action;
    }

    private static void recall(GameTestHelper helper, StandPower power, ServerPlayer player) {
        StandEntity stand = power.getSummonedStandEntity();
        KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(player, power);
        helper.assertTrue(!power.isSummoned() && stand.isRemoved(), "Setup: the Stand was not recalled");
    }

    private static StandPower summoned(GameTestHelper helper, ServerPlayer player) {
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Setup: summon failed");
        power.getSummonedStandEntity().summonLockTicks = 0;
        return power;
    }

    private static void close(StandPower power, ServerPlayer player) {
        player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
        EpitaphSession running = EpitaphSessions.of(player);
        if (running != null) {
            running.end(EpitaphEnd.CANCELLED);
        }
        KingCrimsonTemporalStateService.terminate(player);
        if (power.getPowerType() != null) {
            power.getPowerType().forceUnsummon(player, power);
        }
        player.discard();
    }

    private static ServerPlayer makePlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        level.addNewPlayer(player);
        return player;
    }

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private SilentListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {}
    }
}

package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.network.LoveTrainPresentationPayload;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * R772 EV-B SB3 n9: Love Train held as a player holds it in game (creative, the Complete Saint Corpse in the inventory
 * and in its Curios slot, the player's own tick running, so the corpse effects refresh every tick) fires after its
 * 540-tick charge (1.16 holdToFire(540, false)) and consumes one corpse; a charge let go early fires nothing and ends
 * on the user's client too.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CLoveTrainHoldGameTests {
    private D4CLoveTrainHoldGameTests() {}

    @GameTest(template = "empty", batch = "d4cLoveTrainInGameHold",
            timeoutTicks = 600 + GameTestChunkTickets.WAIT_TICKS)
    public static void loveTrainFiresAfterItsFullChargeAsHeldInGame(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> holdBody(h, false));
    }

    @GameTest(template = "empty", batch = "d4cLoveTrainEarlyRelease",
            timeoutTicks = 120 + GameTestChunkTickets.WAIT_TICKS)
    public static void loveTrainLetGoEarlyEndsOnTheClientToo(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> holdBody(h, true));
    }

    private static void holdBody(GameTestHelper helper, boolean releaseEarly) {
        D4CRecordingServerPlayer recording = new D4CRecordingServerPlayer(helper,
                releaseEarly ? "d4c-lt-release" : "d4c-lt-hold",
                helper.absoluteVec(new Vec3(4.5D, 4.0D, 1.5D)));
        ServerPlayer player = recording.player;
        List<String> trace = new ArrayList<>();
        try {
            player.setGameMode(GameType.CREATIVE);
            // the player's view follows it to the test area and its chunk counts as sent, so it tracks its Stand and
            // hears the Stand's action packets as a client does
            helper.getLevel().getChunkSource().move(player);
            player.connection.chunkSender.dropChunk(player, player.chunkPosition());
            StandPower power = StandPower.get(player);
            helper.assertTrue(power != null, "fixture: no Stand power");
            power.setStand(D4CAddon.D4C.get());
            power.skipProgression();
            helper.assertTrue(D4CAddon.D4C.get().summon(player, power), "fixture: D4C could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "fixture: no summoned D4C");
            stand.summonLockTicks = 0;
            player.getInventory().setItem(0, new ItemStack(D4CAddon.COMPLETE_SAINT_CORPSE.get()));
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.setEquippedCurio(
                    D4CCuriosAdapter.SAINT_CORPSE_SLOT, 0, new ItemStack(D4CAddon.COMPLETE_SAINT_CORPSE.get())));
            boolean curio = CuriosApi.getCuriosInventory(player)
                    .flatMap(handler -> handler.findFirstCurio(D4CAddon.COMPLETE_SAINT_CORPSE.get()))
                    .isPresent();
            trace.add("curio " + curio);
            power.tick();
            Ability ability = power.getAbility("love_train");
            helper.assertTrue(AbilityInput.withConditionCheck(ability, player),
                    "the Love Train press was refused");
            short key = 77;
            AbilityInput.keyPress(key, ability, player, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.HOLD,
                    0.0F, BufferingState.clickCanBuffer(), ability.abilityId);
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(action != null && action.ability == ability
                            && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "the press did not start Love Train's charge");
            boolean[] heardPress = {recording.payloads(CustomPacketPayload.class).stream()
                    .anyMatch(payload -> payload.getClass().getSimpleName().equals("TrEntityActionInstancePacket"))};
            recording.clear();
            int[] tick = {0};
            int[] endedAt = {-1};
            helper.onEachTick(() -> {
                tick[0]++;
                // the connection's tick runs the player's own tick (PlayerTickEvent) in game
                player.doTick();
                if (endedAt[0] < 0 && (action.isOver() || LivingComponentAction.getCurEntityAction(stand) != action)) {
                    endedAt[0] = tick[0];
                    trace.add("action ended at tick " + tick[0]);
                }
                if (releaseEarly && tick[0] == 40) {
                    AbilityInput.keyRelease(key, player);
                }
            });
            if (releaseEarly) {
                helper.runAfterDelay(60, () -> {
                    try {
                        helper.assertTrue(!player.hasEffect(D4CAddon.LOVE_TRAIN)
                                        && player.getInventory().getItem(0).is(D4CAddon.COMPLETE_SAINT_CORPSE.get()),
                                "a charge let go after 40 ticks fired Love Train");
                        helper.assertTrue(endedAt[0] > 0 && endedAt[0] <= 41,
                                "the released charge did not end on the server at once: " + trace);
                        helper.assertTrue(heardPress[0], "fixture: the player's client did not hear its Stand's "
                                + "Love Train start, so it cannot hear its end either");
                        boolean told = recording.payloads(TrEntityActionPhaseTimePacket.class).stream()
                                .anyMatch(packet -> packet.performerId() == stand.getId() && packet.phase() == null);
                        helper.assertTrue(told, "the server dropped the released charge without telling its "
                                + "user's client, whose copy charges and poses on to its end");
                        helper.assertTrue(signals(recording).contains(LoveTrainPresentationPayload.Signal.CANCEL),
                                "the released charge did not cancel the presentation: " + signals(recording));
                        helper.succeed();
                    }
                    finally {
                        recording.close();
                    }
                });
                return;
            }
            helper.succeedWhen(() -> {
                if (!player.hasEffect(D4CAddon.LOVE_TRAIN)) {
                    helper.assertTrue(tick[0] < 560, "Love Train did not fire after its 540-tick hold: phase "
                            + action.getPhase() + " tick " + action.getPhaseTick() + ", " + trace + ", messages "
                            + keys(recording) + ", presentation " + signals(recording));
                    helper.fail("still charging (tick " + tick[0] + ")");
                }
                helper.assertTrue(tick[0] >= 540, "Love Train fired before its 540-tick charge: tick " + tick[0]);
                helper.assertTrue(!player.getInventory().getItem(0).is(D4CAddon.COMPLETE_SAINT_CORPSE.get())
                                && D4CPhase2BService.ownsLoveTrainCorpse(player),
                        "Love Train did not consume the inventory corpse first");
                recording.close();
            });
        }
        catch (RuntimeException failure) {
            recording.close();
            throw failure;
        }
    }

    private static List<LoveTrainPresentationPayload.Signal> signals(D4CRecordingServerPlayer recording) {
        List<LoveTrainPresentationPayload.Signal> signals = new ArrayList<>();
        for (LoveTrainPresentationPayload payload : recording.payloads(LoveTrainPresentationPayload.class)) {
            signals.add(payload.signal());
        }
        return signals;
    }

    private static List<String> keys(D4CRecordingServerPlayer recording) {
        List<String> keys = new ArrayList<>();
        for (Component message : recording.actionBarMessages()) {
            keys.add(message.getContents() instanceof TranslatableContents translatable
                    ? translatable.getKey() : message.getString());
        }
        return keys;
    }
}

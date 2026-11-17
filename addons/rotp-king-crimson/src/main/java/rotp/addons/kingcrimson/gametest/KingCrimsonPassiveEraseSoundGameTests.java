package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.EraseKind;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Ruling of 2026-11-11 on the donor sound that nothing played any more (epitaph_timeskip, the sound of the future skip
 * the passive erase replaced): it plays at the start of a passive erase, heard by its user alone. The owner on the
 * passive: nobody around is told that an erase began. The hits are real damage on a real server player.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonPassiveEraseSoundGameTests {
    private KingCrimsonPassiveEraseSoundGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_passive_sound")
    public static void aPassiveErasesStartIsHeardByItsUserAlone(GameTestHelper helper) {
        KingCrimsonEraseFixture fixture = new KingCrimsonEraseFixture(helper);
        fixture.floor(12, 6);
        ServerPlayer user = fixture.player("kc-passive-sound", 2.5D, 2.0D, 1.5D);
        StandPower power = fixture.kingCrimson(user, 4);
        ServerPlayer witness = fixture.player("kc-passive-witness", 5.5D, 2.0D, 1.5D);
        List<String> broken = new ArrayList<>();
        helper.runAfterDelay(2, () -> fixture.guarded(() -> {
            int userFrom = KingCrimsonEraseFixture.listener(user).sentCount();
            int witnessFrom = KingCrimsonEraseFixture.listener(witness).sentCount();
            boolean landed = hit(user, 6.0F);
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(user);
            helper.assertTrue(!landed && KingCrimsonEraseFixture.erasing(user) && state.eraseKind() == EraseKind.REFLEX,
                    "Setup: a hit of 6 on 20 health began no reflex erase: landed " + landed + ", kind " + state.eraseKind());
            int heard = heard(user, userFrom, KingCrimsonAddon.EPITAPH_TIMESKIP.get());
            if (heard != 1) {
                broken.add("the user of a reflex erase heard the future skip sound " + heard + " times at its start, not once");
            }
            if (heard(user, userFrom, KingCrimsonAddon.TIME_ERASE_START.get()) != 1) {
                broken.add("the start sound of an erase did not play once beside it");
            }
            int around = heard(witness, witnessFrom, KingCrimsonAddon.EPITAPH_TIMESKIP.get());
            if (around != 0) {
                broken.add("a player 3 blocks from a reflex erase heard its future skip sound " + around + " times");
            }
        }));
        helper.runAfterDelay(52, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.state(user).isIdle(), "Setup: the reflex erase of 2 s still runs");
            int userFrom = KingCrimsonEraseFixture.listener(user).sentCount();
            int witnessFrom = KingCrimsonEraseFixture.listener(witness).sentCount();
            boolean landed = hit(user, 1000.0F);
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(user);
            helper.assertTrue(!landed && KingCrimsonEraseFixture.erasing(user) && state.eraseKind() == EraseKind.LAST_CHANCE,
                    "Setup: a killing hit began no last-chance erase: landed " + landed + ", kind " + state.eraseKind());
            int heard = heard(user, userFrom, KingCrimsonAddon.EPITAPH_TIMESKIP.get());
            int around = heard(witness, witnessFrom, KingCrimsonAddon.EPITAPH_TIMESKIP.get());
            if (heard != 1 || around != 0) {
                broken.add("at the start of a last-chance erase its user heard the future skip sound " + heard
                        + " times (not once) and a player beside him " + around + " times (not at all)");
            }
        }));
        int[] keyFrom = new int[1];
        helper.runAfterDelay(140, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.state(user).isIdle(), "Setup: the last-chance erase of 4 s still runs");
            power.setStamina(power.getMaxStamina());
            keyFrom[0] = KingCrimsonEraseFixture.listener(user).sentCount();
            fixture.holdErase(user);
        }));
        helper.runAfterDelay(148, () -> fixture.guarded(() -> {
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(user);
            helper.assertTrue(KingCrimsonEraseFixture.erasing(user) && state.eraseKind() == EraseKind.ACTIVE,
                    "Setup: the key began no erase of its own: kind " + state.eraseKind());
            int heard = heard(user, keyFrom[0], KingCrimsonAddon.EPITAPH_TIMESKIP.get());
            if (heard != 0) {
                broken.add("the erase of the key played the sound of the passive " + heard + " times");
            }
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(),
                    "The sound of a passive erase: " + String.join("; ", broken)));
            fixture.succeed();
        }));
    }

    /** A hit that armour and absorption take from, and that no difficulty scales. */
    private static boolean hit(ServerPlayer player, float amount) {
        player.invulnerableTime = 0;
        return player.hurt(player.damageSources().cactus(), amount);
    }

    private static int heard(ServerPlayer player, int index, SoundEvent event) {
        int heard = 0;
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundSoundPacket sound && sound.getSound().value() == event) {
                heard++;
            }
        }
        return heard;
    }
}

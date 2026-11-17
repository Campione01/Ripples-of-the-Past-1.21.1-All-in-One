package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * An erase key that does not answer (EV1105AE item 5: "the key do nothing now and then right after a long teleport or
 * right after an erase; one more teleport or a short wait cleared it"). After a long teleport the summoned Stand is
 * left where its user was, in a chunk that stops ticking and is unloaded a little later; until then the press gave
 * the erase to a Stand that never acts. Once the chunk is unloaded the core lets the Stand go within a tick and the
 * key summons it anew. After an erase the key is on its cooldown, which the core refuses without a word, as 1.16
 * did. The first is put right here; the others are how it is meant to be.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEraseKeyAnswerGameTests {
    private static final String ERASE = "kingcrimson_timeerase";

    private KingCrimsonEraseKeyAnswerGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120, batch = "kc_erase_key_answer")
    public static void theEraseKeyAnswersWhenItsStandWasLeftBehindAndIsSilentOnlyForItsCooldown(GameTestHelper helper) {
        KingCrimsonEraseFixture fixture = new KingCrimsonEraseFixture(helper);
        fixture.floor(12, 6);
        ServerLevel level = helper.getLevel();
        ServerPlayer eraser = fixture.player("kc-key-answer", 2.5D, 2.0D, 2.5D);
        StandPower power = fixture.kingCrimson(eraser, 4);
        List<String> broken = new ArrayList<>();
        helper.onEachTick(() -> {
            if (!eraser.isRemoved()) {
                power.setStamina(power.getMaxStamina());
            }
        });
        // Where its user was before a long teleport: far off, in a chunk nothing holds.
        Vec3 behind = helper.absoluteVec(new Vec3(2.5D, 2.0D, 600.5D));
        helper.runAfterDelay(2, () -> fixture.guarded(() -> {
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && !stand.isRemoved(), "Setup: no Stand is out");
            stand.setPos(behind);
            helper.assertTrue(!level.isPositionEntityTicking(BlockPos.containing(behind)),
                    "Setup: the chunk the Stand was left in ticks its entities");
            fixture.holdErase(eraser);
        }));
        helper.runAfterDelay(8, () -> fixture.guarded(() -> {
            StandEntity stand = power.getSummonedStandEntity();
            rule(broken, KingCrimsonEraseFixture.erasing(eraser), "with his Stand left 600 blocks behind, in a chunk that "
                    + "no longer ticks, the erase key did nothing for six ticks");
            rule(broken, stand != null && !stand.isRemoved() && stand.distanceTo(eraser) < 8.0F, "the Stand left behind "
                    + "is " + (stand == null ? "gone" : stand.distanceTo(eraser) + " blocks from its user"));
            fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
        }));
        helper.runAfterDelay(14, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.state(eraser).isIdle(), "Setup: the first erase goes on");
            power.setAbilityCooldown(ERASE, 0, 0);
            // A little later the chunk it was left in is unloaded, and the Stand with it: the core's tick lets it go.
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                stand.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            }
        }));
        helper.runAfterDelay(17, () -> fixture.guarded(() -> fixture.holdErase(eraser)));
        helper.runAfterDelay(23, () -> fixture.guarded(() -> {
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                fixture.track(stand);
            }
            rule(broken, KingCrimsonEraseFixture.erasing(eraser), "three ticks after his Stand was unloaded with the "
                    + "chunk it was left in, the erase key did nothing");
            fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
        }));
        helper.runAfterDelay(29, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.state(eraser).isIdle(), "Setup: the second erase goes on");
            // Right after an erase the key is on its cooldown: refused, and without a message.
            int sent = KingCrimsonEraseFixture.listener(eraser).sentCount();
            boolean taken = fixture.holdErase(eraser).isActionSuccess;
            fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            rule(broken, !taken && power.getAbilityCooldown(ERASE) > 0 && messages(eraser, sent) == 0
                            && KingCrimsonEraseFixture.state(eraser).isIdle(),
                    "six ticks after an erase its key: taken " + taken + ", cooldown " + power.getAbilityCooldown(ERASE)
                            + " ticks, " + messages(eraser, sent) + " messages to its user");
            // And once the cooldown is over it answers (the press may wait a few ticks for its Stand).
            power.setAbilityCooldown(ERASE, 0, 0);
            fixture.holdErase(eraser);
        }));
        helper.runAfterDelay(35, () -> fixture.guarded(() -> {
            rule(broken, KingCrimsonEraseFixture.erasing(eraser), "with its cooldown over the erase key did nothing");
            fixture.release(eraser, KingCrimsonEraseFixture.ERASE_KEY);
            StandEntity stand = power.getSummonedStandEntity();
            if (stand != null) {
                fixture.track(stand);
            }
            helper.assertTrue(broken.isEmpty(), "The erase key's answer: " + String.join("; ", broken));
            fixture.succeed();
        }));
    }

    private static int messages(ServerPlayer player, int index) {
        int messages = 0;
        for (Packet<?> packet : KingCrimsonEraseFixture.listener(player).sentSince(index)) {
            if (packet instanceof ClientboundSystemChatPacket) {
                messages++;
            }
        }
        return messages;
    }

    private static void rule(List<String> broken, boolean holds, String broke) {
        if (!holds) {
            broken.add(broke);
        }
    }
}

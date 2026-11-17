package rotp.addons.kingcrimson.gametest;

import java.lang.ref.WeakReference;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDamage;

/**
 * A room hit that is refused before it is recorded leaves the dealt victim set: it must not keep that player, and with
 * him his level and server, alive.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonSandboxDamageLifetimeGameTests {
    private KingCrimsonSandboxDamageLifetimeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "kc_sandbox_damage_lifetime")
    public static void aDealtVictimLeftSetIsNotKeptAlive(GameTestHelper helper) {
        WeakReference<Player> victim = dealToAGone(helper);
        for (int i = 0; i < 40 && victim.get() != null; i++) {
            System.gc();
            try {
                Thread.sleep(25L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (victim.get() != null) {
            throw new GameTestAssertException("A dealt room victim left set is kept alive by SandboxDamage");
        }
        helper.assertTrue(SandboxDamage.dealtToForTests() == null, "The gone victim is still named as dealt to");
        helper.succeed();
    }

    // The player is never added to the level and lives only in this frame: once it returns, only the holder could
    // keep it.
    // A server player cannot be the victim: the player list keeps his advancements, and with them him.
    private static WeakReference<Player> dealToAGone(GameTestHelper helper) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "kc-dealt-to")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        SandboxDamage.dealt(player, 1.0F);
        helper.assertTrue(SandboxDamage.dealtToForTests() == player, "setup: the dealt victim was not kept");
        return new WeakReference<>(player);
    }
}

package rotp.addons.gratefuldead.gametest;

import java.lang.ref.WeakReference;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.GratefulDeadGameEvents;
import rotp.addons.gratefuldead.entity.GratefulDeadStandEntity;
import rotp.addons.gratefuldead.service.GrabStrength;

/**
 * The grab a Stand was allowed keeps no held entity alive (through its level the entity reaches the Stand, the weak
 * key), and the session's grabs are forgotten when the server has stopped.
 */
@GameTestHolder(GratefulDeadAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GratefulDeadHolderLifetimeGameTests {
    private GratefulDeadHolderLifetimeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "rotpGdHolderLifetime")
    public static void anAllowedGrabKeepsNoHeldEntityAndTheStopForgetsIt(GameTestHelper helper) {
        GratefulDeadStandEntity stand = GratefulDeadAddon.THE_GRATEFUL_DEAD_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(stand != null, "setup: could not create the Stand");
        WeakReference<Cow> held = allowAGone(helper, stand);
        awaitCollected(held, "A held entity is kept alive by GrabStrength.ALLOWED");
        Cow kept = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(kept != null, "setup: could not create the cow");
        GrabStrength.allowForTests(stand, kept);
        GratefulDeadGameEvents.onServerStopped(new ServerStoppedEvent(helper.getLevel().getServer()));
        helper.assertTrue(!GrabStrength.hasAllowedForTests(stand), "ServerStoppedEvent did not forget the grabs");
        helper.succeed();
    }

    // The cow lives only in this frame: once it returns, only the holder could keep it.
    private static WeakReference<Cow> allowAGone(GameTestHelper helper, GratefulDeadStandEntity stand) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "setup: could not create the cow");
        GrabStrength.allowForTests(stand, cow);
        helper.assertTrue(GrabStrength.hasAllowedForTests(stand), "setup: the grab was not kept");
        return new WeakReference<>(cow);
    }

    private static void awaitCollected(WeakReference<?> ref, String message) {
        for (int i = 0; i < 40 && ref.get() != null; i++) {
            System.gc();
            try {
                Thread.sleep(25L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (ref.get() != null) {
            throw new GameTestAssertException(message);
        }
    }
}

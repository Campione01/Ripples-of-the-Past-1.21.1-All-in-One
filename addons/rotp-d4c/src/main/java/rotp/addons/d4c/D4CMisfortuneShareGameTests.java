package rotp.addons.d4c;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * R772.1 EV-STANDCFG item3: the misfortune Love Train passes on to a creature near the user is dealt from an event
 * handler, outside the Stand's code, so it is applied in the user's name: a damage share of 50 % to mobs halves the 2
 * (a fifth of a cow's 10 health) the bystander takes.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CMisfortuneShareGameTests {
    private D4CMisfortuneShareGameTests() {}

    @GameTest(template = "empty", batch = "d4cMisfortuneShare", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void aPassedOnMisfortuneTakesTheStandsDamageShare(GameTestHelper helper) {
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 8)
                .whenTicking(() -> {}, h -> share(h));
    }

    private static void share(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandTuning tuning = D4CAddon.D4C.get().getTuning();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-misfortune-share")) {
            Cow bystander = scene.cow(new Vec3(5.0D, 0.0D, 0.0D));
            try {
                float whole = passedOn(level, scene, bystander);
                helper.assertTrue(Math.abs(whole - 2.0F) < 1.0E-4F,
                        "At the default share the misfortune takes a fifth of the cow's 10 health, 2; it took " + whole);
                JsonObject half = new JsonObject();
                half.addProperty("damageVsMobs", 50);
                tuning.applyConfig(half);
                float halved = passedOn(level, scene, bystander);
                helper.assertTrue(Math.abs(halved - 1.0F) < 1.0E-4F,
                        "At 50 % damage to mobs the misfortune takes 1; it took " + halved);
            }
            finally {
                tuning.restoreDefaults();
            }
        }
        helper.succeed();
    }

    // the user's misfortune passed on to the bystander; what it lost
    private static float passedOn(ServerLevel level, D4CGameTestScene scene, Cow bystander) {
        bystander.setHealth(bystander.getMaxHealth());
        bystander.invulnerableTime = 0;
        float before = bystander.getHealth();
        D4CCorpseService.redirectMisfortune(level, scene.owner, scene.owner);
        return before - bystander.getHealth();
    }
}

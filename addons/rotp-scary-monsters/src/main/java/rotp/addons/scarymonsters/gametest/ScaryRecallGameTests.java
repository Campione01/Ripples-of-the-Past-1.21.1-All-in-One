package rotp.addons.scarymonsters.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The owner's rule of 2026-11-05 (13.png): the forms and the vision on the user go on through a recall, against 1.16,
 * whose DragonAction and DynamicVisionAction asked for an active Stand each tick; they end with the loss of the Stand.
 */
@GameTestHolder("scary_monsters_owner_death")
@PrefixGameTestTemplate(false)
public final class ScaryRecallGameTests {
    private ScaryRecallGameTests() {}

    @GameTest(template = "empty", batch = "scary_recall", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void theDragonFormAndTheVisionGoOnThroughARecall(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper).whenTicking(() -> {}, ScaryRecallGameTests::recallBody);
    }

    private static void recallBody(GameTestHelper helper) {
        Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ScaryRecall"));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        user.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Scary Monsters user");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Scary Monsters");
            helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Scary Monsters could not be summoned");
            ScaryMonstersState state = ScaryMonstersAddon.state(user);
            ScaryMonstersService.enableDragonForm(user);
            ScaryMonstersService.enableDynamicVision(user);
            power.setStamina(power.getMaxStamina());

            ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(user, power);
            EventHooks.firePlayerTickPost(user);
            helper.assertTrue(!power.isSummoned(), "setup: the Stand is still out");
            helper.assertTrue(state.dragonForm() && user.hasEffect(ScaryMonstersAddon.DRAGON_FORM),
                    "the dragon form ended with the recall");
            helper.assertTrue(state.dynamicVision(), "the dynamic vision ended with the recall");

            power.setStand(null);
            EventHooks.firePlayerTickPost(user);
            helper.assertTrue(!state.dragonForm() && !user.hasEffect(ScaryMonstersAddon.DRAGON_FORM)
                            && !state.dynamicVision(),
                    "the forms and the vision must end when their user loses the Stand: dragon " + state.dragonForm()
                            + ", vision " + state.dynamicVision());
        }
        finally {
            ScaryMonstersService.cleanupAll(user);
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(user, power);
            }
            user.discard();
        }
        helper.succeed();
    }
}

package rotp.addons.echoes;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import rotp.addons.echoes.init.EchoesStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("rotp_owner_decisions")
@PrefixGameTestTemplate(false)
public final class OwnerDecisionEchoesGameTests {
    private OwnerDecisionEchoesGameTests() {}

    /**
     * R772 F11/E1: the player's latest Echoes (RotP-EchoesACT-master InitStands) replaced the 092 donor's above-cap
     * levels (finisher 5, degrade 6): ACT3 is reached at Resolve 3, its finisher needs nothing more, the gravity
     * infuse and the gravity throw need Resolve 4, and the ACT selector replaced the degrade step.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void actThreeTakesTheMasterResolveGates(GameTestHelper helper) {
        Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0,
                new GameProfile(UUID.randomUUID(), "echoes-gate-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return getAbilities().instabuild; }
        };
        try {
            StandPower power = PowerClass.STAND.attachGet(player);
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(power.getMaxResolveLevel() == 4, "Do not invent a six-level progression curve");
            power.setResolveLevel(3);
            Ability finisher = power.getAbility("finisher");
            Ability infuse = power.getAbility("gravity_infuse");
            Ability projectile = power.getAbility("gravity_projectile");
            helper.assertTrue(power.getAbility("degrade_to_act2") == null && power.getAbility("select_act") != null,
                    "The ACT selector replaces the degrade step");
            helper.assertTrue(finisher.getRequiredResolveLevel(power) <= 0
                    && infuse.getRequiredResolveLevel(power) == 4
                    && projectile.getRequiredResolveLevel(power) == 4,
                    "Master thresholds: finisher at ACT3's own level, infuse and throw 4");
            helper.assertTrue(finisher.getResolveUnlockConditionCheck(power).isPositive()
                    && !infuse.getResolveUnlockConditionCheck(power).isPositive()
                    && !projectile.getResolveUnlockConditionCheck(power).isPositive(),
                    "Survival Resolve 3 opens the finisher and not the Resolve 4 actions");
            power.setResolveLevel(4);
            helper.assertTrue(infuse.getResolveUnlockConditionCheck(power).isPositive()
                    && projectile.getResolveUnlockConditionCheck(power).isPositive(),
                    "Resolve 4 opens the infuse and the throw");
        }
        finally { player.discard(); }
        helper.succeed();
    }
}

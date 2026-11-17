package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.state.GerRtzPackRoute;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzPackRouteGameTests {
    private GerRtzPackRouteGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "ger_rtz_pack_route")
    public static void onlyAPackThatDeclaresTheCosmosTakesItsRoute(GameTestHelper helper) {
        helper.assertTrue(GerRtzPackRoute.choose(false, 10098) == GerRtzPackRoute.NO_SHADERS,
                "With no pack in use nothing a stale id map says counts");
        helper.assertTrue(GerRtzPackRoute.choose(true, 10098) == GerRtzPackRoute.DECLARED,
                "A pack that declares the cosmos at its id draws it");
        helper.assertTrue(GerRtzPackRoute.choose(true, -1) == GerRtzPackRoute.UNDECLARED,
                "An id map without the name is an ordinary pack");
        helper.assertTrue(GerRtzPackRoute.choose(true, 10099) == GerRtzPackRoute.UNDECLARED,
                "King Crimson's id is not this declaration: a pack may draw one void and not the other");
        helper.assertTrue(GerRtzPackRoute.MARKER_ID == 10098
                        && "geraddon".equals(GerRtzPackRoute.MARKER_NAMESPACE)
                        && "rtz_cosmic_void".equals(GerRtzPackRoute.MARKER_PATH),
                "The contract with the pack: entity.10098=geraddon:rtz_cosmic_void");
        helper.succeed();
    }
}

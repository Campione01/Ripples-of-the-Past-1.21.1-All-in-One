package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerRtzGhostFade;

/**
 * The owner, 2026-11-08, on the rewind's ghosts: "保留，改成金色並淡得更快".
 * The numbers the renderer draws by; how they look is an in-game check.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzGhostFadeGameTests {
    // the rewind's presentation, which every copy used to last
    private static final int PRESENTATION_TICKS = 200;

    private GerRtzGhostFadeGameTests() {}

    @GameTest(template = "empty", batch = "ger_rtz_ghost_fade")
    public static void theGhostsAreGoldAndTheOldestGoFirst(GameTestHelper helper) {
        int copies = GerRtzGhostFade.MAX_COPIES;
        List<String> wrong = new ArrayList<>();
        if (GerRtzGhostFade.GOLD != 0xFFE650 || GerRtzGhostFade.red() != 1.0F
                || Math.abs(GerRtzGhostFade.green() - 230 / 255.0F) > 1.0E-6F
                || Math.abs(GerRtzGhostFade.blue() - 80 / 255.0F) > 1.0E-6F) {
            wrong.add("the ghosts are not the rewind's gold: " + Integer.toHexString(GerRtzGhostFade.GOLD) + " as "
                    + GerRtzGhostFade.red() + ", " + GerRtzGhostFade.green() + ", " + GerRtzGhostFade.blue());
        }
        for (int copy = 1; copy < copies; copy++) {
            float older = GerRtzGhostFade.lifeTicks(copy, copies, PRESENTATION_TICKS);
            float newer = GerRtzGhostFade.lifeTicks(copy - 1, copies, PRESENTATION_TICKS);
            if (!(older < newer) || older <= 0.0F) {
                wrong.add("copy " + copy + " lasts " + older + " ticks, the newer one before it " + newer);
            }
            if (!(GerRtzGhostFade.alpha(copy, copies, 0.0F, PRESENTATION_TICKS)
                    < GerRtzGhostFade.alpha(copy - 1, copies, 0.0F, PRESENTATION_TICKS))) {
                wrong.add("copy " + copy + " is not fainter than the newer one before it");
            }
        }
        float newest = GerRtzGhostFade.lifeTicks(0, copies, PRESENTATION_TICKS);
        if (newest != GerRtzGhostFade.TRAIL_CLEAR_TICKS || GerRtzGhostFade.TRAIL_CLEAR_TICKS != 40
                || GerRtzGhostFade.alpha(0, copies, GerRtzGhostFade.TRAIL_CLEAR_TICKS, PRESENTATION_TICKS) != 0.0F
                || GerRtzGhostFade.alpha(0, copies, GerRtzGhostFade.TRAIL_CLEAR_TICKS - 1.0F, PRESENTATION_TICKS)
                        <= 0.0F) {
            wrong.add("the trail is not clear 40 ticks after the rewind: the newest copy lasts " + newest);
        }
        if (GerRtzGhostFade.lifeTicks(0, copies, 20) != 20.0F) {
            wrong.add("a shorter presentation does not end the trail with it");
        }
        float oldest = GerRtzGhostFade.lifeTicks(copies - 1, copies, PRESENTATION_TICKS);
        if (GerRtzGhostFade.alpha(copies - 1, copies, oldest, PRESENTATION_TICKS) != 0.0F
                || GerRtzGhostFade.alpha(copies - 1, copies, 0.0F, PRESENTATION_TICKS) <= 0.0F) {
            wrong.add("the oldest copy is not drawn at first and gone after its " + oldest + " ticks");
        }
        helper.assertTrue(wrong.isEmpty(), "rtz/ghost-fade: " + String.join("; ", wrong));
        helper.succeed();
    }
}

package rotp.addons.stand_bearing_creatures.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.init.power.ModStands;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.config.MobPowerConfig;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

/**
 * The Stand a creature rolls. The owner, 2026-11-12, on King Crimson, which no arrow gives a player: "開放，但機率偏低".
 * It is in a creature's draw at a quarter of a normal Stand's weight, whatever useAddonStands says; blockedStandsForMobs
 * takes it out as it takes any Stand out. King Crimson is in the test server for this test (build.gradle,
 * localRuntime).
 */
@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobStandRollGameTests {
    private static final String KING_CRIMSON = "rotp_kingcrimson:kingcrimson";

    private MobStandRollGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "mwp_stand_roll")
    public static void aCreaturesRollCanDrawKingCrimsonAtAQuarterOfANormalStandsWeight(GameTestHelper helper) {
        boolean useAddons = MobPowerConfig.USE_ADDON_STANDS.get();
        List<? extends String> blocked = MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.get();
        List<String> broken = new ArrayList<>();
        try {
            StandType kingCrimson = MobPowerUtil.findStand(KING_CRIMSON);
            helper.assertTrue(kingCrimson != null, "Setup: King Crimson is not in the test server (build.gradle, localRuntime).");
            MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.set(List.of());
            MobPowerConfig.USE_ADDON_STANDS.set(true);
            List<StandType> pool = MobPowerUtil.randomStandPool();
            if (!pool.contains(kingCrimson)) {
                broken.add("King Crimson is not among the Stands a creature's roll draws from: " + ids(pool));
            }
            MobPowerConfig.USE_ADDON_STANDS.set(false);
            pool = MobPowerUtil.randomStandPool();
            if (!pool.contains(kingCrimson)) {
                broken.add("with useAddonStands off King Crimson is not in the draw: " + ids(pool)
                        + " (the owner opened it for creatures; that setting is for the Stands an arrow gives)");
            }
            if (!pool.stream().allMatch(stand -> stand == kingCrimson || stand.getId().getNamespace().equals("jojo_ripples"))) {
                broken.add("with useAddonStands off another add-on's Stand is in the draw: " + ids(pool));
            }
            double normal = MobPowerUtil.randomStandWeight(ModStands.CRAZY_DIAMOND.get());
            double weight = MobPowerUtil.randomStandWeight(kingCrimson);
            if (normal != 2.0D || weight != 0.5D) {
                broken.add("in a creature's roll King Crimson weighs " + weight + " and Crazy Diamond " + normal
                        + ": not a quarter of a normal Stand's 2.0");
            }
            double total = 0.0D;
            double from = -1.0D;
            for (StandType stand : pool) {
                if (stand == kingCrimson) {
                    from = total;
                }
                total += MobPowerUtil.randomStandWeight(stand);
            }
            if (from >= 0.0D) {
                double to = from + weight;
                boolean drawn = MobPowerUtil.drawn(pool, from + 1.0E-9D) == kingCrimson
                        && MobPowerUtil.drawn(pool, (from + to) / 2.0D) == kingCrimson
                        && MobPowerUtil.drawn(pool, to - 1.0E-9D) == kingCrimson;
                boolean beside = from > 0.0D && MobPowerUtil.drawn(pool, from - 1.0E-6D) == kingCrimson
                        || to < total && MobPowerUtil.drawn(pool, to + 1.0E-6D) == kingCrimson;
                if (!drawn || beside) {
                    broken.add("King Crimson has " + weight + " of the " + total + " in the draw, from " + from
                            + ": a roll that falls there " + (drawn ? "draws it" : "does not draw it") + ", and one beside it "
                            + (beside ? "draws it too" : "does not"));
                }
            }
            MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.set(List.of(KING_CRIMSON));
            if (MobPowerUtil.randomStandPool().contains(kingCrimson)) {
                broken.add("listed in blockedStandsForMobs, King Crimson is still in the draw");
            }
            helper.assertTrue(broken.isEmpty(), "A creature's Stand roll and King Crimson: " + String.join("; ", broken));
            helper.succeed();
        } finally {
            MobPowerConfig.USE_ADDON_STANDS.set(useAddons);
            MobPowerConfig.BLOCKED_STANDS_FOR_MOBS.set(blocked);
        }
    }

    private static List<String> ids(List<StandType> stands) {
        return stands.stream().map(stand -> stand.getId().toString()).toList();
    }
}

package rotp.addons.yellowtemperance.ability;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import rotp.addons.yellowtemperance.effect.ErosionService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * R772.1 EV-STANDCFG item3: erosion hurts from the eroded creature's own tick, outside the Stand's code, so it is
 * applied in the name of the user who eroded it: a damage share of 50 % to mobs halves what a cow takes a second.
 */
@GameTestHolder(YellowTemperanceAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YellowTemperanceErosionShareGameTests {
    private YellowTemperanceErosionShareGameTests() {}

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_erosion_share",
            timeoutTicks = 40)
    public static void erosionTakesTheStandsDamageShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandTuning tuning = YellowTemperanceAddon.YELLOW_TEMPERANCE.get().getTuning();
        Vec3 feet = YellowTemperanceDisguiseGameTests.sceneFeet(helper);
        FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "YTErosionShare"));
        user.setPos(feet.x, feet.y, feet.z);
        user.setNoGravity(true);
        level.addNewPlayer(user);
        Cow cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        // three blocks from the user towards the middle of the cell's chunk, so that the cow stays in it too
        double chunkMinX = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(Mth.floor(feet.x)));
        double cowX = feet.x - chunkMinX < 8.0D ? feet.x + 3.0D : feet.x - 3.0D;
        cow.moveTo(cowX, feet.y, feet.z, 0.0F, 0.0F);
        try {
            PowerClass.STAND.attachGet(user).setStand(YellowTemperanceAddon.YELLOW_TEMPERANCE.get());
            helper.assertTrue(level.addFreshEntity(cow), "Fixture: the cow was not added");
            cow.addEffect(new MobEffectInstance(YellowTemperanceAddon.EROSION, 2000, 0, false, true, true));
            cow.getPersistentData().putUUID(ErosionService.CASTER_KEY, user.getUUID());
            float base = YellowTemperanceConfig.EROSION_BASE_DAMAGE.get().floatValue();
            float whole = erosionTick(cow);
            helper.assertTrue(Math.abs(whole - base) < 1.0E-4F,
                    "At the default share erosion of level 0 deals " + base + " a second; the cow took " + whole);
            JsonObject half = new JsonObject();
            half.addProperty("damageVsMobs", 50);
            tuning.applyConfig(half);
            float halved = erosionTick(cow);
            helper.assertTrue(Math.abs(halved - base / 2.0F) < 1.0E-4F,
                    "At 50 % damage to mobs erosion deals half of " + base + "; the cow took " + halved);
            LogUtils.getLogger().info("R772_YT_EROSION_PASS share");
        }
        finally {
            tuning.restoreDefaults();
            cow.discard();
            level.removePlayerImmediately(user, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    // the second's tick of the cow's erosion; what it lost
    private static float erosionTick(Cow cow) {
        cow.setHealth(cow.getMaxHealth());
        cow.invulnerableTime = 0;
        cow.tickCount = 20;
        float before = cow.getHealth();
        ErosionService.tick(cow);
        return before - cow.getHealth();
    }
}

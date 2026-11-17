package rotp.addons.whitesnake.ability;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.blockentity.MeltHeartBlockEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * R772.1 EV-STANDCFG item3: the melt Whitesnake leaves on the ground hurts from the block, with no Stand code running, so
 * it is applied in its owner's name: a damage share of 50 % to mobs halves the 0.5 it deals a creature in it.
 */
@GameTestHolder(WhitesnakeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WhitesnakeDamageShareGameTests {
    private WhitesnakeDamageShareGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_ws_memory", timeoutTicks = 40)
    public static void theMeltsDamageTakesTheStandsDamageShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandTuning tuning = WhitesnakeAddon.WHITESNAKE.get().getTuning();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ws-melt-share"));
        owner.setPos(at.getX() + 6.5D, at.getY(), at.getZ() + 0.5D);
        owner.setNoGravity(true);
        level.addNewPlayer(owner);
        Cow cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        try {
            StandPower power = PowerClass.STAND.attachGet(owner);
            power.setStand(WhitesnakeAddon.WHITESNAKE.get());
            helper.assertTrue(level.addFreshEntity(cow), "Fixture: the cow was not added");
            level.setBlockAndUpdate(at, WhitesnakeAddon.MELT_HEART_BLOCK.get().defaultBlockState());
            helper.assertTrue(level.getBlockEntity(at) instanceof MeltHeartBlockEntity, "Fixture: no melt on the ground");
            ((MeltHeartBlockEntity) level.getBlockEntity(at)).initialize(owner.getUUID());
            BlockState melt = level.getBlockState(at);
            float whole = meltDamage(level, melt, at, cow);
            helper.assertTrue(Math.abs(whole - 0.5F) < 1.0E-4F,
                    "At the default share the melt deals 0.5; the cow took " + whole);
            JsonObject half = new JsonObject();
            half.addProperty("damageVsMobs", 50);
            tuning.applyConfig(half);
            float halved = meltDamage(level, melt, at, cow);
            helper.assertTrue(Math.abs(halved - 0.25F) < 1.0E-4F,
                    "At 50 % damage to mobs the melt deals 0.25; the cow took " + halved);
        }
        finally {
            tuning.restoreDefaults();
            level.removeBlock(at, false);
            cow.discard();
            level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    // the cow in the melt for one tick; what it lost
    private static float meltDamage(ServerLevel level, BlockState melt, BlockPos at, Cow cow) {
        cow.setHealth(cow.getMaxHealth());
        cow.invulnerableTime = 0;
        float before = cow.getHealth();
        melt.entityInside(level, at, cow);
        return before - cow.getHealth();
    }
}

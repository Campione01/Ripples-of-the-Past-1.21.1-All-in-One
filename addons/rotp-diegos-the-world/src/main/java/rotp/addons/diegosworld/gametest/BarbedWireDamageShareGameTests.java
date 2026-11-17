package rotp.addons.diegosworld.gametest;

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
import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandTuning;

/**
 * R772.1 EV-STANDCFG item3: the barbed wire hurts from the block, with no Stand code running and no owner kept, so the
 * nearest Diego user stands for the Stand: a damage share of 50 % to mobs halves the 3 it deals a creature in it.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class BarbedWireDamageShareGameTests {
    private BarbedWireDamageShareGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotpDiegoBarbedWireShare")
    public static void theBarbedWireTakesTheStandsDamageShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StandTuning tuning = DiegosTheWorldAddon.THE_WORLD.get().getTuning();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "diego-wire-share"));
        user.setPos(at.getX() + 6.5D, at.getY(), at.getZ() + 0.5D);
        user.setNoGravity(true);
        level.addNewPlayer(user);
        Cow cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(DiegosTheWorldAddon.THE_WORLD.get());
            helper.assertTrue(level.addFreshEntity(cow), "Fixture: the cow was not added");
            BlockState wire = DiegosTheWorldAddon.BARBED_WIRE.get().defaultBlockState();
            float whole = wireDamage(level, wire, at, cow);
            helper.assertTrue(Math.abs(whole - 3.0F) < 1.0E-4F,
                    "At the default share the wire deals 3; the cow took " + whole);
            JsonObject half = new JsonObject();
            half.addProperty("damageVsMobs", 50);
            tuning.applyConfig(half);
            float halved = wireDamage(level, wire, at, cow);
            helper.assertTrue(Math.abs(halved - 1.5F) < 1.0E-4F,
                    "At 50 % damage to mobs the wire deals 1.5; the cow took " + halved);
        }
        finally {
            tuning.restoreDefaults();
            cow.discard();
            level.removePlayerImmediately(user, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    // the cow in the wire for one tick; what it lost
    private static float wireDamage(ServerLevel level, BlockState wire, BlockPos at, Cow cow) {
        cow.setHealth(cow.getMaxHealth());
        cow.invulnerableTime = 0;
        float before = cow.getHealth();
        wire.entityInside(level, at, cow);
        return before - cow.getHealth();
    }
}

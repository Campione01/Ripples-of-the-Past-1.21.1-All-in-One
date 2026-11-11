package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A summoned Stand stands at its place beside its user from its spawn on: the position its spawn packet carries, its
 * last position (the start of its first tick's glide on a client) and the place its first tick keeps it at are one, not
 * its user's own place, where it was drawn for a frame before it jumped aside. What the frame shows is an in-game check.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSummonPlacementGameTests {
	private static final List<String> STANDS = List.of("star_platinum", "the_world", "hierophant_green", "magicians_red",
			"silver_chariot", "crazy_diamond", "gold_experience");

	private StandSummonPlacementGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aSummonedStandStartsAtItsPlaceBesideItsUser(GameTestHelper helper) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		for (int i = 0; i < STANDS.size(); i++) {
			String id = STANDS.get(i);
			FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SummonPlace" + i));
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 90.0F * i, 0.0F);
			user.setYHeadRot(90.0F * i);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user of " + id);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			StandPower power = PowerClass.STAND.attachGet(user);
			try {
				helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "setup: could not grant " + id);
				helper.assertTrue(type.summon(user, power), "setup: could not summon " + id);
				StandEntity stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null && stand.isAddedToLevel(), "setup: " + id + " is not out");
				Vec3 spawned = stand.position();
				Vec3 last = new Vec3(stand.xo, stand.yo, stand.zo);
				Vec3 lastTick = new Vec3(stand.xOld, stand.yOld, stand.zOld);
				double aside = horizontal(spawned.subtract(user.position()));
				helper.assertTrue(aside > 0.25D, id + " was put at its user's own place, " + aside + " blocks aside, before"
						+ " its first tick moved it beside its user");
				helper.assertTrue(last.distanceTo(spawned) < 1.0E-6 && lastTick.distanceTo(spawned) < 1.0E-6,
						id + "'s last position " + last + " / " + lastTick + " is not where it was put, " + spawned
								+ ": its first frames glide from there");
				stand.summonLockTicks = 0;
				stand.tick();
				helper.assertTrue(stand.position().distanceTo(spawned) < 0.05D, id + "'s first tick moved it from "
						+ spawned + " to " + stand.position() + ": it was not put at its place");
			}
			finally {
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
				if (power.isSummoned() && type != null) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
		helper.succeed();
	}

	private static double horizontal(Vec3 offset) {
		return Math.sqrt(offset.x * offset.x + offset.z * offset.z);
	}
}

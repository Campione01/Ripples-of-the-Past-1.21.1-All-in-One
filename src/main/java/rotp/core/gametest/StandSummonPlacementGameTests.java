package rotp.core.gametest;

import java.util.ArrayList;
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
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A summoned Stand stands at its place beside its user from its spawn on: the position its spawn packet carries, its
 * last position (the start of its first tick's glide on a client) and the place its first tick keeps it at are one, not
 * its user's own place, where it was drawn for a frame before it jumped aside. What the frame shows is an in-game check.
 * A place the summon's caller gives the Stand before it joins the level (Echoes' new form where the old one stood) is
 * the one it appears at instead.
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

	// the summon's caller places the Stand in its set-up (EchoesForms.changeForm) or between an unadded summon and its add
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aStandItsSummonsCallerPlacedAppearsThereToATrackingPlayer(GameTestHelper helper) {
		Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))).add(2.5D, 0.5D, -1.5D);
		summonSeen(helper, "PlacedInSetUp", (scene, type) -> {
			helper.assertTrue(type.summon(scene.user, scene.power, stand -> {
				stand.moveTo(spot.x, spot.y, spot.z, 40.0F, 0.0F);
				stand.setYHeadRot(40.0F);
			}, true), "setup: could not summon Star Platinum");
			return "the summon's set-up";
		}, spot, 40.0F);
		summonSeen(helper, "PlacedBeforeAdd", (scene, type) -> {
			helper.assertTrue(type.summon(scene.user, scene.power, stand -> {}, false), "setup: could not summon Star Platinum");
			StandEntity stand = scene.power.getSummonedStandEntity();
			helper.assertTrue(stand != null && !stand.isAddedToLevel(), "setup: the unadded summon added the Stand");
			stand.setPos(spot.x, spot.y, spot.z);
			type.finalizeStandSummonFromAction(scene.user, scene.power, stand, true);
			return "a place set before its add";
		}, spot, Float.NaN);
		helper.succeed();
	}

	// a summon that adds the Stand itself (EntityStandType.summon) or adds the unadded Stand on its own (an add-on's quiet summon)
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aPlainSummonAppearsBesideItsUserToATrackingPlayer(GameTestHelper helper) {
		summonSeen(helper, "PlainSummon", (scene, type) -> {
			helper.assertTrue(type.summon(scene.user, scene.power), "setup: could not summon Star Platinum");
			return "a plain summon";
		}, null, Float.NaN);
		summonSeen(helper, "QuietSummon", (scene, type) -> {
			helper.assertTrue(type.summon(scene.user, scene.power, stand -> {}, false), "setup: could not summon Star Platinum");
			StandEntity stand = scene.power.getSummonedStandEntity();
			helper.assertTrue(stand != null && helper.getLevel().addFreshEntity(stand), "setup: could not add the Stand");
			return "a summon added by its caller";
		}, null, Float.NaN);
		helper.succeed();
	}

	private interface Summon {
		String run(Scene scene, EntityStandType type);
	}

	private record Scene(FakePlayer user, StandPower power) {}

	/*
	 * spot null: the Stand must appear beside its user; otherwise there, and at yaw when it is a number. The onlooker
	 * stands three blocks away and tracks the Stand as another player would; the chunks around the user count as sent to
	 * both, so a user near a chunk border still sees its Stand added in the next one.
	 */
	private static void summonSeen(GameTestHelper helper, String name, Summon summon, Vec3 spot, float yaw) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		FakePlayer onlooker = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name + "Eye"));
		RecordingConnection userView = new RecordingConnection(user);
		RecordingConnection onlookerView = new RecordingConnection(onlooker);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			onlooker.setNoGravity(true);
			onlooker.moveTo(origin.x - 3.0D, origin.y, origin.z, -90.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user) && helper.getLevel().addFreshEntity(onlooker),
					"setup: could not add " + name + "'s players");
			GameTestChunkSending.markSentAround(user);
			GameTestChunkSending.markSentAround(onlooker, user.chunkPosition());
			helper.assertTrue(type instanceof EntityStandType, "setup: no registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not grant Star Platinum");
			userView.packets.clear();
			onlookerView.packets.clear();
			String what = summon.run(new Scene(user, power), (EntityStandType) type);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.isAddedToLevel(), "setup: after " + what + " Star Platinum is not out");
			GameTestChunkSending.assertTracks(helper, onlooker, stand, "the Stand of " + what);
			GameTestChunkSending.assertTracks(helper, user, stand, "the Stand of " + what);
			Vec3 at = stand.position();
			Vec3 last = new Vec3(stand.xo, stand.yo, stand.zo);
			Vec3 lastTick = new Vec3(stand.xOld, stand.yOld, stand.zOld);
			if (spot == null) {
				double aside = horizontal(at.subtract(user.position()));
				helper.assertTrue(aside > 0.25D, "After " + what + " the Stand appeared on its user, " + aside + " blocks aside");
			}
			else {
				helper.assertTrue(at.distanceTo(spot) < 1.0E-6, "After " + what + " the Stand appeared at " + at
						+ ", not where it was placed, " + spot);
			}
			helper.assertTrue(last.distanceTo(at) < 1.0E-6 && lastTick.distanceTo(at) < 1.0E-6, "After " + what
					+ " the Stand's last position " + last + " / " + lastTick + " is not where it appeared, " + at
					+ ": its first frame glides from there");
			for (RecordingConnection view : List.of(userView, onlookerView)) {
				String who = view == userView ? "its user" : "the onlooker";
				ClientboundAddEntityPacket spawn = view.spawnOf(stand);
				helper.assertTrue(spawn != null, "Fixture: " + who + " got no spawn packet of the Stand of " + what);
				Vec3 seen = new Vec3(spawn.getX(), spawn.getY(), spawn.getZ());
				helper.assertTrue(seen.distanceTo(at) < 1.0E-4, who + " saw the Stand of " + what + " appear at " + seen
						+ ", not at " + at);
				if (!Float.isNaN(yaw)) {
					helper.assertTrue(Math.abs(spawn.getYRot() - yaw) < 1.5F, who + " saw the Stand of " + what
							+ " turned to " + spawn.getYRot() + ", not " + yaw);
				}
			}
		}
		finally {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
			onlooker.discard();
		}
	}

	private static double horizontal(Vec3 offset) {
		return Math.sqrt(offset.x * offset.x + offset.z * offset.z);
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		ClientboundAddEntityPacket spawnOf(StandEntity stand) {
			for (Packet<?> packet : packets) {
				if (packet instanceof ClientboundAddEntityPacket add && add.getId() == stand.getId()) {
					return add;
				}
			}
			return null;
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}
	}
}

package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.constants.EntityEvents;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 closed the Stand HUD with the unsummon task (StandEntityUnsummon.onTaskSet -> ActionsOverlayGui.onStandUnsummon), and
 * a forced unsummon left it open. The port's user's client keeps the bar on KEEP_STAND_HUD, so the summon key's recall must
 * not send it: the player's clip showed the whole bar back on screen as soon as every recall's fade ended.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandRecallHudGameTests {
	private StandRecallHudGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theSummonKeysRecallClosesTheStandHud(GameTestHelper helper) {
		Scene scene = new Scene(helper, "RecallHud");
		StandEntity stand = scene.power.getSummonedStandEntity();
		scene.standType.onUserSummonCommand(scene.user, scene.power);
		helper.assertTrue(stand.getCurStandAction() != null, "setup: the summon key started no unsummon");
		scene.connection.packets.clear();
		helper.succeedWhen(() -> {
			helper.assertTrue(stand.isRemoved(), "the recall's unsummon has not ended yet");
			try {
				helper.assertTrue(scene.power.getSummonedStandEntity() == null, "the recall left its Stand summoned");
				helper.assertTrue(scene.keepHudEvents() == 0,
						"the summon key's recall told its user's client to keep the Stand HUD after the Stand is gone");
			}
			finally {
				scene.user.discard();
			}
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aRetractionsUnsummonClosesTheStandHudToo(GameTestHelper helper) {
		Scene scene = new Scene(helper, "RetractHud");
		StandEntity stand = scene.power.getSummonedStandEntity();
		stand.retractAndUnsummon();
		helper.assertTrue(stand.getCurStandAction() != null, "setup: the retraction started no unsummon");
		scene.connection.packets.clear();
		helper.succeedWhen(() -> {
			helper.assertTrue(stand.isRemoved(), "the retraction's unsummon has not ended yet");
			try {
				helper.assertTrue(scene.keepHudEvents() == 0,
						"the unsummon task told its user's client to keep the Stand HUD after the Stand is gone");
			}
			finally {
				scene.user.discard();
			}
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aForcedUnsummonStillKeepsTheStandHud(GameTestHelper helper) {
		Scene scene = new Scene(helper, "ForcedHud");
		try {
			scene.connection.packets.clear();
			scene.standType.forceUnsummon(scene.user, scene.power);
			helper.assertTrue(scene.power.getSummonedStandEntity() == null, "the forced unsummon left its Stand summoned");
			helper.assertTrue(scene.keepHudEvents() == 1,
					"1.16 left the HUD open through a forced unsummon: it sent " + scene.keepHudEvents() + " keep events");
		}
		finally {
			scene.user.discard();
		}
		helper.succeed();
	}

	private static final class Scene {
		final FakePlayer user;
		final RecordingConnection connection;
		final StandPower power;
		final EntityStandType standType;

		Scene(GameTestHelper helper, String name) {
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			connection = new RecordingConnection(user);
			Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(position.x, position.y, position.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type instanceof EntityStandType, "setup: Star Platinum is not registered");
			standType = (EntityStandType) type;
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not grant Star Platinum");
			helper.assertTrue(standType.summon(user, power), "setup: the summon was refused");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.isAddedToLevel(), "setup: the Stand is not in the level");
		}

		int keepHudEvents() {
			int count = 0;
			for (Packet<?> packet : connection.packets) {
				if (packet instanceof ClientboundEntityEventPacket event && event.getEventId() == EntityEvents.KEEP_STAND_HUD) {
					count++;
				}
			}
			return count;
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(ServerPlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			record(packet);
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			record(packet);
		}

		private void record(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::record);
			}
			else {
				packets.add(packet);
			}
		}
	}
}

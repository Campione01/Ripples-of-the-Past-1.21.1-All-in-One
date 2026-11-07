package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModGamerules;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.mechanics.ScreenSplashes;
import rotp.core.network.s2c.ScreenSplashPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Who the server tells that blood or sand reached their face. A real hit goes through the same LivingDamageEvent as in
 * play. The watchers are not world entities, only in ServerLevel.players, and are removed again.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScreenSplashGameTests {
	private static final String BATCH = "screen_splash";

	private ScreenSplashGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aHitsBloodReachesOnlyAFaceLookingAtIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LivingEntity victim = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 3);
		LivingEntity attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 0, 2, 0);
		Vec3 at = victim.getBoundingBox().getCenter();
		try (Watchers watchers = new Watchers(level)) {
			Watcher looking = watchers.add(at.add(-2, -0.7, 0), at);
			Watcher turned = watchers.add(at.add(-2, -0.7, 0), at.add(-8, 0, 0));
			Watcher far = watchers.add(at.add(-5, -0.7, 0), at);
			Watcher spectator = watchers.add(at.add(0, -0.7, -2), at);
			spectator.setGameMode(GameType.SPECTATOR);
			helper.setBlock(new BlockPos(3, 2, 5), Blocks.STONE);
			helper.setBlock(new BlockPos(3, 3, 5), Blocks.STONE);
			Watcher walled = watchers.add(at.add(0, -0.7, 3), at);

			victim.invulnerableTime = 0;
			helper.assertTrue(victim.hurt(level.damageSources().mobAttack(attacker), 6.0F), "premise: the hit did not land");

			helper.assertTrue(looking.splashes.size() == 1 && !looking.splashes.get(0).sand(),
					"A player looking at a hit 2 blocks away must get blood: " + looking.splashes);
			helper.assertTrue(looking.splashes.get(0).strength() > 0.5F,
					"A full hit close by and head on is a strong splash: " + looking.splashes.get(0));
			helper.assertTrue(turned.splashes.isEmpty(), "A player turned away got blood in the face: " + turned.splashes);
			helper.assertTrue(far.splashes.isEmpty(), "A player out of reach got blood: " + far.splashes);
			helper.assertTrue(spectator.splashes.isEmpty(), "A spectator got blood: " + spectator.splashes);
			helper.assertTrue(walled.splashes.isEmpty(), "Blood came through a wall: " + walled.splashes);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aPlayerDoesNotSplashThemselves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LivingEntity attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 0, 2, 0);
		try (Watchers watchers = new Watchers(level)) {
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
			Watcher hurt = watchers.add(at, at.add(0, -1, 0));
			Vec3 bloodAt = hurt.getBoundingBox().getCenter();
			Watcher beside = watchers.add(bloodAt.add(-1.5, 0, 0), bloodAt);
			// a fake player takes no damage, so the bleed of LivingDamageEvent directly
			BleedingEffect.bleed(level.damageSources().mobAttack(attacker), 6.0F, hurt);
			helper.assertTrue(beside.splashes.size() == 1, "premise: the blood reached no one: " + beside.splashes);
			helper.assertTrue(hurt.splashes.isEmpty(), "A player got their own blood in the face: " + hurt.splashes);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void strengthFallsWithAngleAndDistance(GameTestHelper helper) {
		Vec3 eye = new Vec3(0, 0, 0);
		Vec3 look = new Vec3(1, 0, 0);
		float close = ScreenSplashes.strength(eye, look, new Vec3(1, 0, 0), 4, 1);
		float distant = ScreenSplashes.strength(eye, look, new Vec3(3.5, 0, 0), 4, 1);
		float aside = ScreenSplashes.strength(eye, look, new Vec3(1, 1.5, 0), 4, 1);
		float behind = ScreenSplashes.strength(eye, look, new Vec3(-1, 0, 0), 4, 1);
		helper.assertTrue(close == 1, "Close and head on must be all of it: " + close);
		helper.assertTrue(distant > 0 && distant < 0.5F, "Near the edge of reach must be little: " + distant);
		helper.assertTrue(aside > 0 && aside < close, "Off to the side must be less: " + aside);
		helper.assertTrue(behind == 0, "Behind the viewer must be nothing: " + behind);
		helper.assertTrue(ScreenSplashes.strength(eye, look, new Vec3(1, 0, 0), 4, 0.5F) == 0.5F, "Size must scale it");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void brokenSandSplashesInItsColourOtherBlocksDoNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos relative = new BlockPos(3, 3, 3);
		BlockPos pos = helper.absolutePos(relative);
		Vec3 at = Vec3.atCenterOf(pos);
		try (Watchers watchers = new Watchers(level)) {
			Watcher looking = watchers.add(at.add(-2, 0, 0), at);
			for (Block block : new Block[] { Blocks.STONE, Blocks.ANVIL }) {
				helper.setBlock(relative, block);
				helper.assertTrue(JojoModUtil.destroyBlock(level, pos, false, null), "premise: " + block + " did not break");
				helper.assertTrue(looking.splashes.isEmpty(), block + " threw sand: " + looking.splashes);
			}
			helper.setBlock(relative, Blocks.SAND);
			helper.assertTrue(JojoModUtil.destroyBlock(level, pos, false, null), "premise: sand did not break");
			helper.assertTrue(looking.splashes.size() == 1 && looking.splashes.get(0).sand()
					&& looking.splashes.get(0).tint() == 0xDBD3A0, "Broken sand must splash, sand coloured: " + looking.splashes);
			looking.splashes.clear();
			helper.setBlock(relative, Blocks.GRAVEL);
			helper.assertTrue(JojoModUtil.destroyBlock(level, pos, false, null), "premise: gravel did not break");
			helper.assertTrue(looking.splashes.size() == 1 && looking.splashes.get(0).tint() != 0xDBD3A0,
					"Broken gravel must splash in its own colour: " + looking.splashes);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = "screen_splash_stand")
	public static void aStandBreakingSandThrowsItAtAWatcher(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos relative = new BlockPos(3, 3, 3);
		BlockPos pos = helper.absolutePos(relative);
		Vec3 at = Vec3.atCenterOf(pos);
		String name = "ScreenSplashStand";
		FakePlayer user = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
		user.setGameMode(GameType.SURVIVAL);
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 2, 0)));
		user.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
		boolean breakBlocks = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
		level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, level.getServer());
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		try (Watchers watchers = new Watchers(level)) {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			Watcher looking = watchers.add(at.add(-2, 0, 0), at);
			helper.setBlock(relative, Blocks.SAND);
			StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(new ActionTarget(pos, Direction.UP), level, stand, false);
			helper.assertTrue(level.getBlockState(pos).isAir(), "premise: the Stand did not break the sand");
			helper.assertTrue(looking.splashes.size() == 1 && looking.splashes.get(0).sand(),
					"A Stand's broken sand must reach a face looking at it: " + looking.splashes);
		}
		finally {
			level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, level.getServer());
			if (power.isSummoned()) type.forceUnsummon(user, power);
			user.discard();
		}
		helper.succeed();
	}

	private static final class Watchers implements AutoCloseable {
		private final ServerLevel level;
		private final List<Watcher> added = new ArrayList<>();

		Watchers(ServerLevel level) {
			this.level = level;
		}

		Watcher add(Vec3 feetAndEye, Vec3 lookAt) {
			Watcher watcher = new Watcher(level);
			watcher.setPos(feetAndEye.x, feetAndEye.y - watcher.getEyeHeight(), feetAndEye.z);
			watcher.lookAt(EntityAnchorArgument.Anchor.EYES, lookAt);
			level.players().add(watcher);
			added.add(watcher);
			return watcher;
		}

		@Override
		public void close() {
			level.players().removeAll(added);
		}
	}

	private static final class Watcher extends FakePlayer {
		final List<ScreenSplashPacket> splashes = new ArrayList<>();

		Watcher(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "splash-watcher"));
			this.connection = new RecordingConnection(level, this);
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final List<ScreenSplashPacket> sink;

		RecordingConnection(ServerLevel level, Watcher player) {
			super(level.getServer(), openConnection(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
			this.sink = player.splashes;
		}

		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (sink != null && packet instanceof ClientboundCustomPayloadPacket custom
					&& custom.payload() instanceof ScreenSplashPacket splash) {
				sink.add(splash);
			}
		}
	}
}

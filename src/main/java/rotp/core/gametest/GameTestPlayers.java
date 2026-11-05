package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Vanilla GameTestHelper.makeMockPlayer answers isLocalPlayer() true in the server level, which sends
 * PlayerClientBroadcastedSettings.getPlayerSettings to the client's settings. A real server player is never the
 * local player, so tests that reach those settings use this mock: the same damageable player without that override.
 */
final class GameTestPlayers {
	private GameTestPlayers() {}

	static Player makeServerMockPlayer(GameTestHelper helper, GameType gameType) {
		return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
			@Override
			public boolean isSpectator() {
				return gameType == GameType.SPECTATOR;
			}

			@Override
			public boolean isCreative() {
				return gameType.isCreative();
			}
		};
	}

	/**
	 * Takes a player a fixture had added back out of the level, with the Stand it summoned, when the fixture's setup
	 * fails: a server player left in the level makes Mob.checkDespawn discard every non-persistent monster more than
	 * 128 blocks from it on its first tick, in every later test.
	 */
	static void discardAfterFailedSetup(Player user) {
		StandPower power = StandPower.get(user);
		if (power != null && power.hasPower() && power.isSummoned()) {
			power.getPowerType().forceUnsummon(user, power);
		}
		user.discard();
	}
}

package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * For a game test that reads the packets a fake player's connection gets (a recording ServerGamePacketListenerImpl), the
 * core's and an add-on's alike. A fake player that is never ticked receives no chunk, so it tracks only entities in chunks
 * marked as sent to it. A summoned Stand is added beside its user, in the next chunk when the user stands within a block
 * of a chunk border, so a test that marked only the user's own chunk missed every packet of that Stand where the test
 * grid put the user near a border (StandImpactSoundGameTests' 0 of 0 hit ticks).
 * <p>
 * Use: after adding the viewer to the level, {@link #markSentAround(ServerPlayer)} (or around another place); after the
 * entity is added, {@link #assertTracks} so that a missing premise fails with its own message; and when the test counts
 * packets, {@link #assertCounted} before it judges the count.
 */
public final class GameTestChunkSending {
	private GameTestChunkSending() {}

	/** Marks the 3x3 chunks around the viewer as sent to it. */
	public static void markSentAround(ServerPlayer viewer) {
		markSentAround(viewer, viewer.chunkPosition());
	}

	/** Marks the 3x3 chunks around center as sent to the viewer. */
	public static void markSentAround(ServerPlayer viewer, ChunkPos center) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				viewer.connection.chunkSender.dropChunk(viewer, new ChunkPos(center.x + dx, center.z + dz));
			}
		}
	}

	/** Whether the viewer's connection is among those the entity's packets go to. */
	public static boolean tracks(ServerPlayer viewer, Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		ChunkMap.TrackedEntity tracked = level.getChunkSource().chunkMap.entityMap.get(entity.getId());
		return tracked != null && tracked.seenBy.contains(viewer.connection);
	}

	/** Fails the test when the viewer does not track the entity, so none of its packets can reach it. */
	public static void assertTracks(GameTestHelper helper, ServerPlayer viewer, Entity entity, String what) {
		helper.assertTrue(tracks(viewer, entity), "Fixture: " + viewer.getGameProfile().getName() + " does not track " + what
				+ " (at " + entity.position() + ", the viewer at " + viewer.position() + "), so none of its packets can reach it");
	}

	/** Fails when nothing was counted: a count of zero proves nothing about what the test asks. */
	public static void assertCounted(GameTestHelper helper, int count, String what) {
		helper.assertTrue(count > 0, "Fixture: no " + what + " was counted");
	}
}

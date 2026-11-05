package rotp.core.gametest;

import java.util.function.Consumer;

import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R772's server crash: a chunk losing its last ticket walks its entity sections, and a leave listener that removes another
 * entity of the section (Stand Bearing Creatures did it with a mob's Stand, with a third entity behind it in the section)
 * broke the walk with a ConcurrentModificationException that stopped the server.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntitySectionWalkGameTests {
	private EntitySectionWalkGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 300)
	public static void removingAnEntityInItsLeaveEventWhileItsChunkLosesItsTicketKeepsTheServerUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
		// far from every plot, so this test's ticket is the only one on it
		ChunkPos far = new ChunkPos(origin.x + 400, origin.z + 400);
		BlockPos centre = far.getMiddleBlockPosition(helper.absolutePos(BlockPos.ZERO).getY());
		Pig[] pigs = new Pig[3];
		boolean[] left = new boolean[3];
		Consumer<EntityLeaveLevelEvent> listener = event -> {
			for (int i = 0; i < pigs.length; i++) {
				if (event.getEntity() == pigs[i]) {
					left[i] = true;
				}
			}
			if (event.getEntity() == pigs[0] && !pigs[1].isRemoved()) {
				pigs[1].discard();
			}
		};
		helper.assertTrue(level.setChunkForced(far.x, far.z, true), "Setup: the far chunk took no ticket");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(centre), "The far chunk does not tick entities yet"))
				.thenExecute(() -> {
					for (int i = 0; i < pigs.length; i++) {
						pigs[i] = new Pig(EntityType.PIG, level);
						pigs[i].setNoAi(true);
						pigs[i].setNoGravity(true);
						pigs[i].moveTo(centre.getX() + 0.5D, centre.getY() + 0.5D, centre.getZ() + 0.5D, 0.0F, 0.0F);
						helper.assertTrue(level.addFreshEntity(pigs[i]), "Setup: pig " + i + " was not added");
					}
					NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityLeaveLevelEvent.class, listener);
					level.setChunkForced(far.x, far.z, false);
				})
				.thenWaitUntil(() -> helper.assertTrue(left[0] && left[1] && left[2],
						"Every pig gets its leave, also the one removed on the way: " + left[0] + " " + left[1] + " " + left[2]))
				.thenExecute(() -> {
					NeoForge.EVENT_BUS.unregister(listener);
					helper.assertTrue(pigs[1].getRemovalReason() == Entity.RemovalReason.DISCARDED,
							"The listener's discard stands: " + pigs[1].getRemovalReason());
				})
				.thenSucceed();
	}
}

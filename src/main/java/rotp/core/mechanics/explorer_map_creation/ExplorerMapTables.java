package rotp.core.mechanics.explorer_map_creation;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import rotp.core.core.JojoMod;

/**
 * The cartography table each player last used, kept on the server: a menu does not tell where its block is, and the
 * official version asked the client instead.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ExplorerMapTables {
	private static final Map<Player, BlockPos> LAST_TABLE = new WeakHashMap<>();

	private ExplorerMapTables() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!event.getLevel().isClientSide() && event.getLevel().getBlockState(event.getPos()).is(Blocks.CARTOGRAPHY_TABLE)) {
			remember(event.getEntity(), event.getPos());
		}
	}

	public static void remember(Player player, BlockPos pos) {
		synchronized (LAST_TABLE) {
			LAST_TABLE.put(player, pos.immutable());
		}
	}

	public static @Nullable BlockPos lastTable(Player player) {
		synchronized (LAST_TABLE) {
			return LAST_TABLE.get(player);
		}
	}
}

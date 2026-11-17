package rotp.core.mechanics.explorer_map_creation;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/**
 * What the server takes from a player making an explorer map at a cartography table (the official version's
 * explorer_map_creation). The official took the table's position, the icon and the name from the client as sent;
 * here the server checks each of them.
 */
public final class ExplorerMapRules {
	public static final int NAME_MAX = 50;
	public static final double TABLE_REACH = 4.0;

	private ExplorerMapRules() {}

	/** A destination inside the world border. */
	public static int clampToBorder(int value, double borderMin, double borderMax) {
		int min = (int) Math.ceil(borderMin);
		int max = (int) Math.floor(borderMax) - 1;
		return max < min ? min : Math.max(min, Math.min(max, value));
	}

	public static int clampToBorder(int value, WorldBorder border, boolean xAxis) {
		return xAxis ? clampToBorder(value, border.getMinX(), border.getMaxX()) : clampToBorder(value, border.getMinZ(), border.getMaxZ());
	}

	/** The map's name as an anvil would allow it, at most 50 characters. */
	public static String cleanName(@Nullable String name) {
		String filtered = name == null ? "" : StringUtil.filterText(name);
		return filtered.length() > NAME_MAX ? filtered.substring(0, NAME_MAX) : filtered;
	}

	/** Only a fixed mark: an icon that tracks a player or a frame would draw a false one. */
	public static boolean canMark(@Nullable Holder<MapDecorationType> icon) {
		return icon != null && !icon.value().trackCount();
	}

	public static boolean isUsableTable(Player player, @Nullable BlockPos pos) {
		return pos != null && player.level().getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)
				&& player.canInteractWithBlock(pos, TABLE_REACH);
	}
}

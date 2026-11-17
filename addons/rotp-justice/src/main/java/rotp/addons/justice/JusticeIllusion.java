package rotp.addons.justice;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

/** The real, temporary Justice house. Call only on the logical server thread. */
public final class JusticeIllusion {
	private static final String DATA_NAME = "rotp_justice_illusion";
	private static final int MAX_HOUSES_PER_LEVEL = 128;
	private static final int MAX_BLOCKS_PER_HOUSE = 143;
	private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
	private static final SavedData.Factory<Journal> FACTORY = new SavedData.Factory<>(Journal::new, Journal::load);

	private JusticeIllusion() {}

	/** The caller checks Fog before invoking the Illusion click. Returns the number of new bricks. */
	public static int spawn(ServerPlayer caster) {
		UUID owner = caster.getUUID();
		ServerLevel level = caster.serverLevel();
		for (ServerLevel existingLevel : level.getServer().getAllLevels()) {
			Journal existing = existingLevel.getDataStorage().get(FACTORY, DATA_NAME);
			if (existing == null) continue;
			House previous = existing.houses.get(owner);
			if (previous == null) continue;
			if (previous.pending) restoreLoaded(existingLevel, existing, previous);
			if (!previous.blocks.isEmpty()) return 0;
			existing.houses.remove(owner);
			existing.setDirty();
		}
		Journal journal = level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
		if (journal.houses.size() >= MAX_HOUSES_PER_LEVEL) return 0;
		House house = new House();
		BlockPos center = caster.blockPosition();
		Direction front = caster.getDirection(), side = front.getClockWise();
		for (int y = 0; y <= 4; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (y != 4 && ((Math.abs(x) != 3 && Math.abs(z) != 3) || (z == 3 && x == 0 && y < 2))) continue;
					BlockPos pos = center.relative(side, x).relative(front, z).above(y);
					placeIfAir(level, journal, owner, house, pos);
				}
			}
		}
		return house.blocks.size();
	}

	/** Ends this owner's house wherever it was placed; unloaded cells remain pending. */
	public static int dismiss(MinecraftServer server, UUID owner) {
		int removed = 0;
		for (ServerLevel level : server.getAllLevels()) {
			Journal journal = level.getDataStorage().get(FACTORY, DATA_NAME);
			if (journal == null) continue;
			House house = journal.houses.get(owner);
			if (house == null) continue;
			house.pending = true;
			removed += restoreLoaded(level, journal, house);
			if (house.blocks.isEmpty()) {
				journal.houses.remove(owner);
				journal.setDirty();
			}
		}
		return removed;
	}

	/** Retries dismissed or recovered houses without loading chunks or touching active houses. */
	public static int cleanupLoaded(ServerLevel level) {
		Journal journal = level.getDataStorage().get(FACTORY, DATA_NAME);
		if (journal == null) return 0;
		int removed = 0;
		var houses = journal.houses.values().iterator();
		while (houses.hasNext()) {
			House house = houses.next();
			if (!house.pending) continue;
			removed += restoreLoaded(level, journal, house);
			if (house.blocks.isEmpty()) {
				houses.remove();
				journal.setDirty();
			}
		}
		return removed;
	}

	private static void placeIfAir(ServerLevel level, Journal journal, UUID owner, House house, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || !loadedNeighborhood(level, pos)) return;
		if (!level.getBlockState(pos).isAir()) return;
		// Seeing air proves an older claim is gone, even if this placement uses the same brick state.
		var houses = journal.houses.values().iterator();
		while (houses.hasNext()) {
			House previous = houses.next();
			if (previous.blocks.remove(pos)) {
				if (previous.blocks.isEmpty()) houses.remove();
				journal.setDirty();
			}
		}
		if (level.setBlock(pos, BRICKS, Block.UPDATE_ALL) && level.getBlockState(pos).equals(BRICKS)) {
			house.blocks.add(pos);
			journal.houses.putIfAbsent(owner, house);
			journal.setDirty();
		}
	}

	private static int restoreLoaded(ServerLevel level, Journal journal, House house) {
		int removed = 0;
		var blocks = house.blocks.iterator();
		while (blocks.hasNext()) {
			BlockPos pos = blocks.next();
			if (level.isOutsideBuildHeight(pos)) {
				blocks.remove();
				journal.setDirty();
				continue;
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
					SectionPos.blockToSectionCoord(pos.getZ()));
			if (chunk == null) continue;
			if (chunk.getBlockState(pos).equals(BRICKS)) {
				if (!loadedNeighborhood(level, pos) || !level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) continue;
				removed++;
			}
			blocks.remove();
			journal.setDirty();
		}
		return removed;
	}

	private static boolean loadedNeighborhood(ServerLevel level, BlockPos pos) {
		int minX = SectionPos.blockToSectionCoord(pos.getX() - 1), maxX = SectionPos.blockToSectionCoord(pos.getX() + 1);
		int minZ = SectionPos.blockToSectionCoord(pos.getZ() - 1), maxZ = SectionPos.blockToSectionCoord(pos.getZ() + 1);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (level.getChunkSource().getChunkNow(x, z) == null) return false;
			}
		}
		return true;
	}

	private static final class House {
		private final Set<BlockPos> blocks = new LinkedHashSet<>();
		private boolean pending;
	}

	private static final class Journal extends SavedData {
		private final Map<UUID, House> houses = new LinkedHashMap<>();

		private static Journal load(CompoundTag tag, HolderLookup.Provider registries) {
			Journal journal = new Journal();
			ListTag entries = tag.getList("Houses", Tag.TAG_COMPOUND);
			if (entries.size() > MAX_HOUSES_PER_LEVEL) return journal;
			for (int i = 0; i < entries.size(); i++) {
				CompoundTag entry = entries.getCompound(i);
				if (!entry.hasUUID("Owner")) continue;
				long[] positions = entry.getLongArray("Blocks");
				if (positions.length == 0 || positions.length > MAX_BLOCKS_PER_HOUSE) continue;
				House house = new House();
				// Fog itself does not survive a server restart.
				house.pending = true;
				for (long pos : positions) house.blocks.add(BlockPos.of(pos));
				journal.houses.putIfAbsent(entry.getUUID("Owner"), house);
			}
			return journal;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag entries = new ListTag();
			houses.forEach((owner, house) -> {
				CompoundTag entry = new CompoundTag();
				entry.putUUID("Owner", owner);
				entry.putLongArray("Blocks", house.blocks.stream().mapToLong(BlockPos::asLong).toArray());
				entries.add(entry);
			});
			tag.put("Houses", entries);
			return tag;
		}
	}
}

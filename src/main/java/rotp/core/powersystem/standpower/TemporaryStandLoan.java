package rotp.core.powersystem.standpower;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

record TemporaryStandLoan(
		UUID token, StandInstance originalStand, CompoundTag originalProgression, boolean originalSkipped,
		ResourceLocation borrowedId, int borrowedResolveLevel, boolean conflicted, boolean borrowedLostOnClone) {

	TemporaryStandLoan withBorrowedResolveLevel(int level) {
		return new TemporaryStandLoan(token, originalStand, originalProgression, originalSkipped, borrowedId, level,
				conflicted, borrowedLostOnClone);
	}

	TemporaryStandLoan withConflict() {
		return conflicted ? this : new TemporaryStandLoan(token, originalStand, originalProgression, originalSkipped,
				borrowedId, borrowedResolveLevel, true, borrowedLostOnClone);
	}

	TemporaryStandLoan copyForClone(boolean lostOnThisClone) {
		return new TemporaryStandLoan(token, originalStand.copy(), originalProgression.copy(), originalSkipped,
				borrowedId, borrowedResolveLevel, conflicted, borrowedLostOnClone || lostOnThisClone);
	}

	StandInstance copyOriginal() {
		if (originalStand.standExists()) {
			return originalStand.copy();
		}
		// A previously unavailable datapack Stand may now be registered.
		return StandInstance.CODEC.encodeStart(NbtOps.INSTANCE, originalStand)
				.flatMap(nbt -> StandInstance.CODEC.parse(NbtOps.INSTANCE, nbt))
				.result().orElseGet(originalStand::copy);
	}

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Token", token);
		tag.put("OriginalStand", StandInstance.CODEC.encodeStart(NbtOps.INSTANCE, originalStand).getOrThrow());
		tag.put("OriginalProgression", originalProgression.copy());
		tag.putBoolean("OriginalSkipped", originalSkipped);
		tag.putString("BorrowedStand", borrowedId.toString());
		tag.putInt("BorrowedResolveLevel", borrowedResolveLevel);
		tag.putBoolean("Conflicted", conflicted);
		tag.putBoolean("BorrowedLostOnClone", borrowedLostOnClone);
		return tag;
	}

	static Optional<TemporaryStandLoan> load(CompoundTag tag) {
		ResourceLocation borrowed = ResourceLocation.tryParse(tag.getString("BorrowedStand"));
		if (borrowed == null || !tag.contains("BorrowedStand", Tag.TAG_STRING)
				|| !tag.hasUUID("Token") || !tag.contains("OriginalStand", Tag.TAG_COMPOUND)
				|| !tag.contains("OriginalProgression", Tag.TAG_COMPOUND)) {
			return Optional.empty();
		}
		return StandInstance.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("OriginalStand")).result()
				.map(original -> new TemporaryStandLoan(tag.getUUID("Token"), original,
						tag.getCompound("OriginalProgression").copy(), tag.getBoolean("OriginalSkipped"), borrowed,
						tag.getInt("BorrowedResolveLevel"), tag.getBoolean("Conflicted"), tag.getBoolean("BorrowedLostOnClone")));
	}
}

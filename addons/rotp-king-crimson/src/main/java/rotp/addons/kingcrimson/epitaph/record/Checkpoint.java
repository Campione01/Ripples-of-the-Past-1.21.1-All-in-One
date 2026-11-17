package rotp.addons.kingcrimson.epitaph.record;

import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * A phantom's state at the end of its track: its saved data, still naming phantoms by their own UUIDs, the keys that
 * were taken out of the body's data before the copy was made, and whom it was after.
 */
public record Checkpoint(CompoundTag data, Map<String, Tag> stripped, ActorKey target) {
}

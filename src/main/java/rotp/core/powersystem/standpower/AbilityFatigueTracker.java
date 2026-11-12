package rotp.core.powersystem.standpower;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.api.stand.StandAbilityFatigue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * A Stand user's fatigue per ability of the no-cooldown mode (StandAbilityFatigue), on the server: the ticks left, the
 * repeats counted while they ran and the game tick of the last use. Saved with the Stand power like its cooldowns.
 */
@ApiStatus.Internal
public final class AbilityFatigueTracker {
	private static final long NO_USE = Long.MIN_VALUE;

	private static final class Entry {
		private float fatigue;
		// the cooldown the last use added: a repeat pays for one such cooldown at most
		private int cooldown;
		private int repeats;
		private long lastUseTime = NO_USE;
	}

	private final Map<String, Entry> entries = new HashMap<>();

	/** A use that charges a repeat and adds its fatigue at once; a second use of the same ability in one tick counts not. */
	public float use(String abilityName, int cooldownTicks, boolean practical, long gameTime) {
		Entry entry = entries.get(abilityName);
		if (entry != null && entry.lastUseTime == gameTime) {
			return 0.0F;
		}
		float extra = chargeRepeat(abilityName, practical, gameTime);
		addFatigue(abilityName, cooldownTicks, gameTime);
		return extra;
	}

	/** The repeat's extra stamina if the ability's fatigue still runs, counting the repeat; 0 otherwise. */
	public float chargeRepeat(String abilityName, boolean practical, long gameTime) {
		Entry entry = entries.get(abilityName);
		if (entry == null || !(entry.fatigue > 0.0F) || entry.lastUseTime == gameTime) {
			return 0.0F;
		}
		entry.repeats++;
		entry.lastUseTime = gameTime;
		return StandAbilityFatigue.repeatCost(entry.fatigue, entry.cooldown, entry.repeats, practical);
	}

	public void addFatigue(String abilityName, int cooldownTicks, long gameTime) {
		if (cooldownTicks < StandAbilityFatigue.TRIVIAL_COOLDOWN_TICKS) {
			return;
		}
		Entry entry = entries.computeIfAbsent(abilityName, name -> new Entry());
		entry.fatigue = StandAbilityFatigue.stackedFatigue(entry.fatigue, cooldownTicks);
		entry.cooldown = cooldownTicks;
		entry.lastUseTime = gameTime;
	}

	/** One tick of the user's time at this rate (EntityTimeRates STAND_ACTIONS): a run-out fatigue ends its repeats. */
	public void tick(float rate) {
		if (entries.isEmpty() || !(rate > 0.0F)) {
			return;
		}
		for (Iterator<Entry> iterator = entries.values().iterator(); iterator.hasNext();) {
			Entry entry = iterator.next();
			entry.fatigue -= rate;
			if (!(entry.fatigue > 0.0F)) {
				iterator.remove();
			}
		}
	}

	public float getFatigue(String abilityName) {
		Entry entry = entries.get(abilityName);
		return entry != null ? Math.max(entry.fatigue, 0.0F) : 0.0F;
	}

	/** The cooldown the ability's last use added to its fatigue. */
	public int getCooldown(String abilityName) {
		Entry entry = entries.get(abilityName);
		return entry != null ? entry.cooldown : 0;
	}

	public int getRepeats(String abilityName) {
		Entry entry = entries.get(abilityName);
		return entry != null ? entry.repeats : 0;
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public void clear() {
		entries.clear();
	}

	public void copyFrom(AbilityFatigueTracker other) {
		entries.clear();
		other.entries.forEach((name, entry) -> {
			Entry copy = new Entry();
			copy.fatigue = entry.fatigue;
			copy.cooldown = entry.cooldown;
			copy.repeats = entry.repeats;
			entries.put(name, copy);
		});
	}

	public CompoundTag save() {
		CompoundTag nbt = new CompoundTag();
		entries.forEach((name, entry) -> {
			CompoundTag entryNbt = new CompoundTag();
			entryNbt.putFloat("Ticks", entry.fatigue);
			entryNbt.putInt("Cooldown", entry.cooldown);
			entryNbt.putInt("Repeats", entry.repeats);
			nbt.put(name, entryNbt);
		});
		return nbt;
	}

	public void load(CompoundTag nbt) {
		entries.clear();
		for (String name : nbt.getAllKeys()) {
			if (!nbt.contains(name, Tag.TAG_COMPOUND)) {
				continue;
			}
			CompoundTag entryNbt = nbt.getCompound(name);
			float fatigue = entryNbt.getFloat("Ticks");
			if (fatigue > 0.0F && Float.isFinite(fatigue)) {
				Entry entry = new Entry();
				entry.fatigue = fatigue;
				entry.cooldown = Math.max(entryNbt.getInt("Cooldown"), 0);
				entry.repeats = Math.max(entryNbt.getInt("Repeats"), 0);
				entries.put(name, entry);
			}
		}
	}
}

package rotp.core.client.input.controlscheme;

/**
 * What the skill bar lets the player select: only a slot with something to use. Kept free of client classes so a smoke
 * test can check it. The number keys count those slots, so the digit drawn under an icon is the key that picks it.
 */
public final class HotbarSelectionRules {
	private HotbarSelectionRules() {}

	/**
	 * Where the selection goes once the bar has changed: the slot the player last picked while it can be used, else the
	 * current one, else the next one that can. Nothing to select leaves the index alone.
	 */
	public static int keepOnUsableSlot(int current, int picked, boolean[] selectable) {
		if (picked >= 0 && picked < selectable.length && selectable[picked]) {
			return picked;
		}
		if (current >= 0 && current < selectable.length && selectable[current]) {
			return current;
		}
		for (int step = 1; step <= selectable.length; step++) {
			int index = Math.floorMod(Math.max(current, -1) + step, selectable.length);
			if (selectable[index]) {
				return index;
			}
		}
		return current;
	}

	/** The slot a number key picks (0 is the first key), or -1. */
	public static int slotOfNumberKey(int key, boolean[] selectable) {
		int seen = 0;
		for (int i = 0; i < selectable.length; i++) {
			if (selectable[i] && seen++ == key) {
				return i;
			}
		}
		return -1;
	}

	/** The number key that picks a slot (0 is the first key), or -1 for a slot that cannot be selected. */
	public static int numberKeyOfSlot(int slot, boolean[] selectable) {
		if (slot < 0 || slot >= selectable.length || !selectable[slot]) {
			return -1;
		}
		int key = 0;
		for (int i = 0; i < slot; i++) {
			if (selectable[i]) {
				key++;
			}
		}
		return key;
	}
}

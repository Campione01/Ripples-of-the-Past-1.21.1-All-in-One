package rotp.core.init;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Lays a creative tab's stacks out in sections, on the tab's 9-wide grid: a named section starts on a row of its own, under
 * an empty row its name is drawn on, and a group of a section that does not fit what is left of the row goes to the next.
 */
public final class CreativeTabSections {
	public static final int ROW_LENGTH = 9;
	private static final int VISIBLE_ROWS = 5;

	private CreativeTabSections() {}

	public record Section<T, N>(@Nullable N name, List<List<T>> groups) {}

	public record Header<N>(int row, N name) {}

	public record Layout<T, N>(List<T> slots, List<Header<N>> headers) {}

	public static <T, N> Layout<T, N> layout(List<Section<T, N>> sections, T blank) {
		List<T> slots = new ArrayList<>();
		List<Header<N>> headers = new ArrayList<>();
		for (Section<T, N> section : sections) {
			if (section.groups().stream().allMatch(List::isEmpty)) {
				continue;
			}
			if (section.name() != null) {
				fillRow(slots, blank);
				headers.add(new Header<>(slots.size() / ROW_LENGTH, section.name()));
				pad(slots, blank, ROW_LENGTH);
			}
			else {
				fillRow(slots, blank);
			}
			for (List<T> group : section.groups()) {
				if (group.isEmpty()) {
					continue;
				}
				int used = slots.size() % ROW_LENGTH;
				if (used > 0) {
					boolean fitsWithAGap = used + 1 + group.size() <= ROW_LENGTH;
					pad(slots, blank, fitsWithAGap ? 1 : ROW_LENGTH - used);
				}
				slots.addAll(group);
			}
		}
		return new Layout<>(slots, headers);
	}

	/** The row at the top of the grid at this scroll, as the creative screen's menu counts it. */
	public static int firstVisibleRow(float scroll, int slotCount) {
		int hiddenRows = (slotCount + ROW_LENGTH - 1) / ROW_LENGTH - VISIBLE_ROWS;
		return Math.max((int) ((double) (scroll * (float) hiddenRows) + 0.5), 0);
	}

	private static <T> void fillRow(List<T> slots, T blank) {
		int used = slots.size() % ROW_LENGTH;
		if (used > 0) {
			pad(slots, blank, ROW_LENGTH - used);
		}
	}

	private static <T> void pad(List<T> slots, T blank, int count) {
		for (int i = 0; i < count; i++) {
			slots.add(blank);
		}
	}
}

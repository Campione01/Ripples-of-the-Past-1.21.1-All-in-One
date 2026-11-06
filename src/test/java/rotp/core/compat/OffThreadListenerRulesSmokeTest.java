package rotp.core.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Threads other than the main one fire these events. Pack log of 2026-11-06: a listener that took a number from the
 * world's random threw under C2ME 7,866 times, and can throw anywhere when two threads collide. The same rule for the
 * add-ons stands in verifyStandCrossCuttingRules of their root build.
 */
public final class OffThreadListenerRulesSmokeTest {
	private static final Pattern EVENT_PARAMETER = Pattern.compile(
			"\\b(?:PlaySoundEvent|PlaySoundSourceEvent|PlayStreamingSourceEvent|PlayLevelSoundEvent(?:\\.\\w+)?"
					+ "|ChunkEvent\\.\\w+|ChunkDataEvent\\.\\w+|FinalizeSpawnEvent|MobSpawnEvent\\.\\w+"
					+ "|EntityEvent\\.(?:Size|EntityConstructing))\\s+\\w+\\s*[,)]");
	private static final Pattern METHOD = Pattern.compile(
			"(?m)^[ \\t]*(?:[\\w<>\\[\\],.?@]+[ \\t]+)+(\\w+)[ \\t]*\\(([^)]*\\))[^;{]*\\{");
	private static final Pattern CALL = Pattern.compile("(?:\\b(\\w+)\\s*\\(|::(\\w+))");
	private static final Pattern WORLD_RANDOM = Pattern.compile(
			"\\b\\w*(?:[Ll]evel|[Ww]orld|[Pp]layer)(?:\\(\\))?\\.(?:random\\b|getRandom\\(\\))");
	private static final Set<String> NOT_METHODS = Set.of("if", "for", "while", "switch", "catch", "synchronized", "try");

	private OffThreadListenerRulesSmokeTest() {}

	public static void main(String[] args) throws IOException {
		verifyTheRuleItself();
		Path sources = Path.of(System.getProperty("user.dir")).resolve("src/main/java");
		List<String> failures = new ArrayList<>();
		try (Stream<Path> files = Files.walk(sources)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				if (EVENT_PARAMETER.matcher(source).find()) {
					for (String found : violations(source)) {
						failures.add(sources.relativize(file) + ": " + found);
					}
				}
			}
		}
		check(failures.isEmpty(), "a listener of an event fired off the main thread names the world's random,"
				+ " which belongs to that level's own thread; take a thread-safe source of its own: " + failures);
	}

	private static void verifyTheRuleItself() {
		check(!violations("class A {\n  public static void on(PlaySoundEvent event) {\n"
				+ "    RandomSource r = minecraft.level.random;\n  }\n}\n").isEmpty(),
				"the rule missed the world's random in a sound listener");
		check(!violations("class A {\n  static void on(PlayLevelSoundEvent.AtPosition event) {\n    helper(event);\n  }\n"
				+ "  private static void helper(Object event) {\n    long seed = level.getRandom().nextLong();\n  }\n}\n").isEmpty(),
				"the rule missed the world's random one call down from a listener");
		check(!violations("class A {\n  static void on(FinalizeSpawnEvent event) {\n"
				+ "    float roll = player.getRandom().nextFloat();\n  }\n}\n").isEmpty(),
				"the rule missed a player's random in a spawn listener");
		check(violations("class A {\n  static void on(FinalizeSpawnEvent event) {\n"
				+ "    float roll = event.getEntity().getRandom().nextFloat();\n  }\n}\n").isEmpty(),
				"the rule refused the spawning mob's own random");
		check(violations("class A {\n  static void tick(ClientTickEvent event) {\n"
				+ "    RandomSource r = level.random;\n  }\n}\n").isEmpty(),
				"the rule refused the world's random on the main thread");
	}

	private static List<String> violations(String rawSource) {
		String source = rawSource.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
		Map<String, String> bodies = new HashMap<>();
		List<String> listeners = new ArrayList<>();
		Matcher method = METHOD.matcher(source);
		while (method.find()) {
			String name = method.group(1);
			if (NOT_METHODS.contains(name)) {
				continue;
			}
			String body = body(source, method.end() - 1);
			bodies.merge(name, body, String::concat);
			if (EVENT_PARAMETER.matcher(method.group(2)).find()) {
				listeners.add(name);
			}
		}
		List<String> found = new ArrayList<>();
		for (String listener : listeners) {
			Set<String> seen = new HashSet<>();
			Deque<String> queue = new ArrayDeque<>(List.of(listener));
			while (!queue.isEmpty()) {
				String name = queue.poll();
				if (!seen.add(name) || !bodies.containsKey(name)) {
					continue;
				}
				String body = bodies.get(name);
				if (WORLD_RANDOM.matcher(body.replaceAll("\\s+", "")).find()) {
					found.add(listener + (name.equals(listener) ? "" : " through " + name));
				}
				Matcher call = CALL.matcher(body);
				while (call.find()) {
					queue.add(call.group(1) != null ? call.group(1) : call.group(2));
				}
			}
		}
		return found;
	}

	private static String body(String source, int open) {
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}' && --depth == 0) {
				return source.substring(open, i + 1);
			}
		}
		return source.substring(open);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

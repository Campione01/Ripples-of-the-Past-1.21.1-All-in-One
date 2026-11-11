package rotp.core.gametest;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.core.config.editor.AddonConfigCatalog;
import rotp.core.config.editor.AddonConfigKeys;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEditorPackets.Request;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.client.ClientConfigEditor;
import rotp.core.core.JojoMod;

/** An add-on's settings through the catalog's own path, with a stand-in spec on a file of its own. */
@GameTestHolder(CommonConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AddonConfigEditorGameTests {
	private static final String NAMESPACE = CommonConfigEditorGameTests.NAMESPACE;
	private static final String MOD = "rotp_addon_fixture";
	private static final String OTHER_MOD = "rotp_addon_second";
	private static final String GROUP = MOD + "/fixture";
	private static final String RANGE = GROUP + ".range";
	private static final String POWER = GROUP + ".standPowerMultiplier";
	private static final String HIDDEN = "rotp_kingcrimson/King Crimson Time Erase Config.timeEraseDynamicCooldown";

	private AddonConfigEditorGameTests() {}

	private enum Mode { FIRST, SECOND }

	/** A spec loaded by a tracker of its own into a folder of its own: FML's real files, events and saves, none of the game's. */
	private static final class Fixture {
		final ConfigTracker tracker = new ConfigTracker();
		final ModConfig.Type type;
		final Path folder;
		final ModConfig config;

		Fixture(String modId, ModConfig.Type type, ModConfigSpec spec) throws IOException {
			this.type = type;
			// under the run directory in build, as no temp file may go to drive C:
			folder = Files.createTempDirectory(Files.createDirectories(Path.of("tmp").toAbsolutePath()), "rotp-addon-config-");
			var container = ModList.get().getModContainerById(JojoMod.MOD_ID).orElseThrow();
			config = tracker.registerConfig(type, spec, container, modId + "-" + type.extension() + ".toml");
			tracker.loadConfigs(type, folder);
			AddonConfigCatalog.addForTest(modId, type, spec);
		}

		byte[] bytes() throws IOException {
			return Files.readAllBytes(config.getFullPath());
		}

		Object saved(String path) throws IOException {
			try (var reader = Files.newBufferedReader(config.getFullPath())) {
				return new TomlParser().parse(reader).get(path);
			}
		}

		// what the earlier lane saw natively: a read-only TOML makes FML's atomic write fail
		void failing(boolean failing) {
			config.getFullPath().toFile().setWritable(!failing);
		}

		void close() throws IOException {
			failing(false);
			tracker.unloadConfigs(type);
			try (var files = Files.list(folder)) {
				for (Path file : files.toList()) {
					Files.deleteIfExists(file);
				}
			}
			Files.deleteIfExists(folder);
		}
	}

	private static ModConfigSpec fixtureSpec() {
		var builder = new ModConfigSpec.Builder();
		builder.push("fixture");
		builder.defineInRange("range", 5, 0, 10);
		// the core has a synced leaf of this name, with a rule of its own
		builder.defineInRange("standPowerMultiplier", 1.0D, 0.0D, 2.0D);
		builder.define("enabled", true);
		builder.defineEnum("mode", Mode.FIRST);
		builder.defineList("names", List.of("a"), () -> "", item -> item instanceof String);
		builder.pop();
		builder.define("top", false);
		return builder.build();
	}

	private static ModConfigSpec secondSpec() {
		var builder = new ModConfigSpec.Builder();
		builder.define("flag", true);
		return builder.build();
	}

	private static Fixture register(String modId, ModConfig.Type type, ModConfigSpec spec) throws IOException {
		return new Fixture(modId, type, spec);
	}

	private static ConfigEntry entry(List<ConfigEntry> entries, String key) {
		return entries.stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElse(null);
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void anAddonValueIsReadSetSavedRefusedAndResetInItsOwnFile(GameTestHelper helper) throws IOException {
		Fixture fixture = register(MOD, ModConfig.Type.COMMON, fixtureSpec());
		try {
			List<ConfigEntry> entries = CommonConfigEditor.entries();
			ConfigEntry range = entry(entries, RANGE);
			helper.assertTrue(range != null && range.value().getAsInt() == 5 && range.bounds().maximum() == 10
					&& range.group().equals(GROUP) && range.translationKey().equals(MOD + ".configuration.range"),
					"the add-on's value must be in the server's catalog, with its bounds and its label key: " + range);
			helper.assertTrue(entry(entries, MOD + "/top").group().equals(MOD + "/"), "a value at the top of the file has its add-on for a group");
			helper.assertTrue(entry(entries, GROUP + ".mode").choices().equals(List.of("FIRST", "SECOND")), "an enum's choices");

			helper.assertTrue(CommonConfigEditor.set(RANGE, new JsonPrimitive(7)).status() == Status.OK, "a valid set was refused");
			helper.assertTrue(Integer.valueOf(7).equals(fixture.saved("fixture.range")), "the value must reach its own file");
			byte[] before = fixture.bytes();
			helper.assertTrue(CommonConfigEditor.set(RANGE, new JsonPrimitive(11)).status() == Status.INVALID_VALUE
					&& entry(CommonConfigEditor.entries(), RANGE).value().getAsInt() == 7 && Arrays.equals(before, fixture.bytes()),
					"a value outside the spec's range must be refused and change nothing");

			helper.assertTrue(CommonConfigEditor.reset(RANGE).status() == Status.OK
					&& Integer.valueOf(5).equals(fixture.saved("fixture.range")), "a reset must write the spec's default");

			CommonConfigEditor.set(RANGE, new JsonPrimitive(3));
			CommonConfigEditor.set(POWER, new JsonPrimitive(1.5D));
			CommonConfigEditor.set(MOD + "/top", new JsonPrimitive(true));
			var reset = CommonConfigEditor.resetGroup(GROUP);
			helper.assertTrue(reset.status() == Status.OK && reset.changes().size() == 2
					&& Integer.valueOf(5).equals(fixture.saved("fixture.range")) && Double.valueOf(1.0).equals(fixture.saved("fixture.standPowerMultiplier")),
					"a group's reset changes its own values in its file: " + reset);
			helper.assertTrue(entry(CommonConfigEditor.entries(), RANGE).value().getAsInt() == 5
					&& entry(CommonConfigEditor.entries(), MOD + "/top").value().getAsBoolean(),
					"a group's reset leaves the values outside the group");
			helper.assertTrue(CommonConfigEditor.resetGroup(MOD + "/").status() == Status.OK
					&& !entry(CommonConfigEditor.entries(), MOD + "/top").value().getAsBoolean(), "the file's top is a group too");
		}
		finally {
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.assertTrue(entry(CommonConfigEditor.entries(), RANGE) == null, "the stand-in must leave with its test");
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void anAddonWriteIsRefusedForANonOperatorAndAllowedForAnOperator(GameTestHelper helper) throws IOException {
		Fixture fixture = register(MOD, ModConfig.Type.COMMON, fixtureSpec());
		ServerPlayer guest = player(helper, "addon_guest");
		var ops = guest.server.getPlayerList().getOps();
		Path opsPath = ops.getFile().toPath();
		byte[] opsBytes = Files.exists(opsPath) ? Files.readAllBytes(opsPath) : null;
		try {
			Reply read = exchange(helper, guest, new Request(1, Operation.READ, "", null));
			helper.assertTrue(read.result().status() == Status.OK && !read.canEdit() && entry(read.entries(), RANGE) != null,
					"a non-operator reads the add-on's rows, read-only");
			gapPassed(guest);
			byte[] before = fixture.bytes();
			Reply refused = exchange(helper, guest, new Request(2, Operation.SET, RANGE, new JsonPrimitive(7)));
			helper.assertTrue(refused.result().status() == Status.FORBIDDEN && Arrays.equals(before, fixture.bytes())
					&& entry(CommonConfigEditor.entries(), RANGE).value().getAsInt() == 5,
					"a non-operator wrote an add-on's value");

			ops.add(new ServerOpListEntry(guest.getGameProfile(), 2, false));
			Reply accepted = exchange(helper, guest, new Request(3, Operation.SET, RANGE, new JsonPrimitive(7)));
			helper.assertTrue(accepted.result().status() == Status.OK && accepted.canEdit()
					&& accepted.result().changes().getFirst().key().equals(RANGE) && Integer.valueOf(7).equals(fixture.saved("fixture.range")),
					"an operator's write must go the way a core row's does");
			var longList = new JsonArray();
			for (int i = 0; i < 65; i++) {
				longList.add("x" + i);
			}
			Reply list = exchange(helper, guest, new Request(4, Operation.SET, GROUP + ".names", longList));
			helper.assertTrue(list.result().status() == Status.OK, "the core's 64-item cap is about what it syncs: " + list.result());
			Reply group = exchange(helper, guest, new Request(5, Operation.RESET_GROUP, GROUP, null));
			helper.assertTrue(group.result().status() == Status.OK && group.result().changes().size() == 2,
					"a group reset goes through the packets too: " + group.result());
		}
		finally {
			ops.remove(guest.getGameProfile());
			if (opsBytes == null) {
				Files.deleteIfExists(opsPath);
			}
			else {
				Files.write(opsPath, opsBytes);
			}
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void aFailedSavePutsEverySpecBack(GameTestHelper helper) throws IOException {
		Fixture first = register(MOD, ModConfig.Type.COMMON, fixtureSpec());
		Fixture second = register(OTHER_MOD, ModConfig.Type.COMMON, secondSpec());
		try {
			String flag = OTHER_MOD + "/flag";
			var both = new java.util.LinkedHashMap<String, com.google.gson.JsonElement>();
			both.put(RANGE, new JsonPrimitive(7));
			both.put(flag, new JsonPrimitive(false));
			byte[] secondBefore = second.bytes();
			second.failing(true);
			helper.assertTrue(CommonConfigEditor.apply(both).status() == Status.SAVE_FAILED, "a failed save must say so");
			List<ConfigEntry> entries = CommonConfigEditor.entries();
			helper.assertTrue(entry(entries, RANGE).value().getAsInt() == 5 && entry(entries, flag).value().getAsBoolean(),
					"a failed save must restore every spec's values");
			helper.assertTrue(Integer.valueOf(5).equals(first.saved("fixture.range")) && Arrays.equals(secondBefore, second.bytes()),
					"the file already written must be put back, and the other untouched");
			second.failing(false);
			helper.assertTrue(CommonConfigEditor.apply(both).status() == Status.OK && Integer.valueOf(7).equals(first.saved("fixture.range"))
					&& Boolean.FALSE.equals(second.saved("flag")), "both files take the batch once it can be written");
		}
		finally {
			AddonConfigCatalog.removeForTest(MOD);
			AddonConfigCatalog.removeForTest(OTHER_MOD);
			first.close();
			second.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void whatTheCatalogCannotHoldNeverTakesThePageDown(GameTestHelper helper) throws IOException {
		var builder = new ModConfigSpec.Builder();
		builder.defineInRange("wide", 5L, 0L, 10L);
		builder.define("fine", true);
		ModConfigSpec withLong = builder.build();
		var startupBuilder = new ModConfigSpec.Builder();
		startupBuilder.define("early", true);
		Fixture fixture = register(MOD, ModConfig.Type.COMMON, withLong);
		AddonConfigCatalog.addForTest(MOD, ModConfig.Type.STARTUP, startupBuilder.build());
		AddonConfigCatalog.addForTest(MOD, ModConfig.Type.COMMON, foreignSpec());
		try {
			List<ConfigEntry> entries = CommonConfigEditor.entries();
			helper.assertTrue(entry(entries, MOD + "/wide") == null, "a kind the catalog does not know must be left out");
			helper.assertTrue(entry(entries, MOD + "/fine") != null, "its neighbour must stay");
			helper.assertTrue(entry(entries, MOD + "/early") == null, "a STARTUP value has no live editor");
		}
		finally {
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.succeed();
	}

	private static IConfigSpec foreignSpec() {
		return (IConfigSpec) Proxy.newProxyInstance(IConfigSpec.class.getClassLoader(), new Class<?>[] {IConfigSpec.class},
				(proxy, method, args) -> { throw new AssertionError("a spec of another class must not be touched"); });
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void anAddonLeafEqualToACoreLeafStaysItsOwn(GameTestHelper helper) throws IOException {
		Fixture fixture = register(MOD, ModConfig.Type.COMMON, fixtureSpec());
		try {
			List<ConfigEntry> entries = CommonConfigEditor.entries();
			ConfigEntry own = entry(entries, POWER);
			helper.assertTrue(own != null && !own.synced(), "the add-on's value of a core leaf's name is not synced by the core");
			helper.assertTrue(entry(entries, "Stand settings.standPowerMultiplier").synced(), "the core's own still is");
			helper.assertTrue(entries.stream().filter(ConfigEntry::synced).noneMatch(candidate -> AddonConfigKeys.isAddonKey(candidate.key())),
					"nothing of an add-on is flagged as sent by the core's sync");
		}
		finally {
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.succeed();
	}

	/** The measure of the wire: this many rows with names as long as today's longest, through the codec both ways. */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void aReadOfFiveHundredRowsSurvivesTheCodecBothWays(GameTestHelper helper) throws IOException {
		var builder = new ModConfigSpec.Builder();
		builder.push("King Crimson Projectile Throw Config");
		for (int i = 0; i < 500; i++) {
			builder.translation("rotp_kingcrimson.configuration.King Crimson Projectile Throw Config.value" + i)
					.defineInRange("impaleGapingWoundLevelTheLongest" + i, 0.5D + i, 0.0D, 1000.0D);
		}
		builder.pop();
		Fixture fixture = register(MOD, ModConfig.Type.COMMON, builder.build());
		ServerPlayer reader = player(helper, "addon_reader");
		var context = new Exchange(reader);
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			ConfigEditorPackets.handleRequest(new Request(1, Operation.READ, "", null), context.context);
			helper.assertTrue(context.replies.size() == 1 && context.replies.getFirst().result().status() == Status.OK,
					"a catalog of this size must be answered");
			Reply sent = context.replies.getFirst();
			Reply.CODEC.encode(buf, sent);
			int bytes = buf.readableBytes();
			Reply received = Reply.CODEC.decode(buf);
			helper.assertTrue(received.entries().equals(sent.entries()) && received.entries().size() >= 500,
					"the catalog must arrive whole: " + received.entries().size());
			JojoMod.getLogger().info("Config editor wire measure: {} rows in {} bytes", received.entries().size(), bytes);
		}
		finally {
			buf.release();
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.succeed();
	}

	/** Three lists at the wire's own limit are over the reply's, and a player must get a refusal and not a kick. */
	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void aReplyTooLargeIsRefusedAndWritesNothing(GameTestHelper helper) throws IOException {
		String item = "x".repeat(250);
		List<String> full = Collections.nCopies(1024, item);
		var builder = new ModConfigSpec.Builder();
		builder.push("big");
		for (int i = 0; i < 3; i++) {
			builder.defineList("list" + i, full, () -> "", value -> value instanceof String);
		}
		builder.pop();
		ModConfigSpec spec = builder.build();
		Fixture fixture = register(MOD, ModConfig.Type.COMMON, spec);
		ServerPlayer player = player(helper, "addon_big");
		var ops = player.server.getPlayerList().getOps();
		Path opsPath = ops.getFile().toPath();
		byte[] opsBytes = Files.exists(opsPath) ? Files.readAllBytes(opsPath) : null;
		try {
			ops.add(new ServerOpListEntry(player.getGameProfile(), 2, false));
			var read = new Exchange(player);
			ConfigEditorPackets.handleRequest(new Request(1, Operation.READ, "", null), read.context);
			helper.assertTrue(read.replies.size() == 1 && read.replies.getFirst().result().status() == Status.UNAVAILABLE
					&& read.replies.getFirst().entries().isEmpty(), "a catalog over the reply's limit must be refused");

			var shorter = Collections.nCopies(1024, "y".repeat(250));
			var shortList = new JsonArray();
			shorter.forEach(shortList::add);
			for (int i = 0; i < 3; i++) {
				helper.assertTrue(CommonConfigEditor.set(MOD + "/big.list" + i, shortList).status() == Status.OK, "the fixture's edit");
			}
			byte[] before = fixture.bytes();
			var group = new Exchange(player);
			ConfigEditorPackets.handleRequest(new Request(2, Operation.RESET_GROUP, MOD + "/big", null), group.context);
			helper.assertTrue(group.replies.size() == 1 && group.replies.getFirst().result().status() == Status.UNAVAILABLE,
					"a reset whose answer would not fit must be refused");
			helper.assertTrue(Arrays.equals(before, fixture.bytes()), "a refused reset must write nothing");
		}
		finally {
			ops.remove(player.getGameProfile());
			if (opsBytes == null) {
				Files.deleteIfExists(opsPath);
			}
			else {
				Files.write(opsPath, opsBytes);
			}
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void aClientValueIsSetLocallyAndNeverOnTheServersPage(GameTestHelper helper) throws IOException {
		Fixture fixture = register(MOD, ModConfig.Type.CLIENT, fixtureSpec());
		try {
			helper.assertTrue(entry(CommonConfigEditor.entries(), RANGE) == null, "a CLIENT value must not be on the server's page");
			helper.assertTrue(ClientConfigEditor.catalog().stream().anyMatch(candidate -> candidate.key().equals(RANGE)
							&& candidate.side() == ConfigEntry.Side.CLIENT), "a CLIENT value must be on the client's page");
			helper.assertTrue(ClientConfigEditor.set(RANGE, new JsonPrimitive(11)).status() == Status.INVALID_VALUE
					&& Integer.valueOf(5).equals(fixture.saved("fixture.range")), "its own spec's range must hold on the client too");
			helper.assertTrue(ClientConfigEditor.set(RANGE, new JsonPrimitive(7)).status() == Status.OK
					&& Integer.valueOf(7).equals(fixture.saved("fixture.range")), "the client's edit must reach its own file");
			helper.assertTrue(CommonConfigEditor.set(RANGE, new JsonPrimitive(8)).status() == Status.UNKNOWN_KEY,
					"the server's path must not know a CLIENT value");
			helper.assertTrue(ClientConfigEditor.reset(RANGE).status() == Status.OK
					&& Integer.valueOf(5).equals(fixture.saved("fixture.range")), "reset");
			ClientConfigEditor.set(RANGE, new JsonPrimitive(7));
			helper.assertTrue(ClientConfigEditor.resetGroup(GROUP).status() == Status.OK
					&& Integer.valueOf(5).equals(fixture.saved("fixture.range")), "group reset");
			helper.assertTrue(ClientConfigEditor.resetGroup(MOD + "/nothing").status() == Status.UNKNOWN_GROUP, "an unknown group");
		}
		finally {
			AddonConfigCatalog.removeForTest(MOD);
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void aServerValueBeforeItsFileIsLoadedActsAsACoreRowBeforeLoad(GameTestHelper helper) {
		ModConfigSpec unloaded = fixtureSpec();
		AddonConfigCatalog.addForTest(MOD, ModConfig.Type.SERVER, unloaded);
		try {
			ConfigEntry range = entry(CommonConfigEditor.entries(), RANGE);
			helper.assertTrue(range != null && range.value().getAsInt() == 5, "a value without its file shows its default");
			helper.assertTrue(CommonConfigEditor.set(RANGE, new JsonPrimitive(7)).status() == Status.UNAVAILABLE,
					"a value without its file cannot be written");
		}
		finally {
			AddonConfigCatalog.removeForTest(MOD);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void aHiddenValueIsLeftAloneByAGroupReset(GameTestHelper helper) throws IOException {
		var builder = new ModConfigSpec.Builder();
		builder.push("King Crimson Time Erase Config");
		builder.define("timeEraseDynamicCooldown", true);
		builder.define("timeEraseLive", true);
		builder.pop();
		Fixture fixture = register("rotp_kingcrimson", ModConfig.Type.COMMON, builder.build());
		try {
			CommonConfigEditor.set(HIDDEN, new JsonPrimitive(false));
			CommonConfigEditor.set("rotp_kingcrimson/King Crimson Time Erase Config.timeEraseLive", new JsonPrimitive(false));
			var reset = CommonConfigEditor.resetGroup("rotp_kingcrimson/King Crimson Time Erase Config");
			helper.assertTrue(reset.status() == Status.OK && reset.changes().size() == 1
					&& !reset.changes().getFirst().key().equals(HIDDEN), "the diff never named it, so a reset must not touch it: " + reset);
			helper.assertTrue(Boolean.FALSE.equals(fixture.saved("King Crimson Time Erase Config.timeEraseDynamicCooldown")),
					"its value stays in its file");
		}
		finally {
			AddonConfigCatalog.removeForTest("rotp_kingcrimson");
			fixture.close();
		}
		helper.succeed();
	}

	private static void gapPassed(ServerPlayer player) {
		ConfigEditorPackets.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
	}

	private static Reply exchange(GameTestHelper helper, ServerPlayer player, Request request) {
		var fixture = new Exchange(player);
		RegistryFriendlyByteBuf in = buffer(helper), out = buffer(helper);
		try {
			Request.CODEC.encode(in, request);
			ConfigEditorPackets.handleRequest(Request.CODEC.decode(in), fixture.context);
			helper.assertTrue(fixture.replies.size() == 1, "expected one reply to the requesting player");
			Reply.CODEC.encode(out, fixture.replies.getFirst());
			return Reply.CODEC.decode(out);
		}
		finally {
			in.release();
			out.release();
		}
	}

	private static final class Exchange {
		private final List<Reply> replies = new ArrayList<>();
		private final IPayloadContext context;

		private Exchange(ServerPlayer player) {
			var listener = (ICommonPacketListener) Proxy.newProxyInstance(ICommonPacketListener.class.getClassLoader(),
					new Class<?>[] {ICommonPacketListener.class}, (proxy, method, args) -> switch (method.getName()) {
						case "hasChannel" -> Reply.TYPE.equals(args[0]);
						case "send" -> {
							replies.add((Reply) args[0]);
							yield null;
						}
						default -> throw new UnsupportedOperationException(method.getName());
					});
			context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
					new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
						case "player" -> player;
						case "listener" -> listener;
						case "reply" -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
						default -> throw new UnsupportedOperationException(method.getName());
					});
		}
	}

	private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
	}

	private static ServerPlayer player(GameTestHelper helper, String name) {
		var level = helper.getLevel();
		return new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
	}
}

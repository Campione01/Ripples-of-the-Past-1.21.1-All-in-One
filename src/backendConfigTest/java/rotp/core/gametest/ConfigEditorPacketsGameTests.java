package rotp.core.gametest;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEditorPackets.Request;
import rotp.core.core.JojoMod;

@GameTestHolder(CommonConfigEditorGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class ConfigEditorPacketsGameTests {
	private static final String NAMESPACE = CommonConfigEditorGameTests.NAMESPACE;
	private static final String GROUP = "Stand settings.Time Stop";
	private static final String RANGE = GROUP + ".timeStopRangeChunks";
	private static final String DAMAGE = GROUP + ".timeStopDamageMultiplier";

	private ConfigEditorPacketsGameTests() {}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void lanCheatsCannotWriteThroughTheConfigPacket(GameTestHelper helper) throws IOException {
		var guest = player(helper, "config_guest");
		var server = guest.server;
		var players = server.getPlayerList();
		var oldProfile = server.getSingleplayerProfile();
		boolean oldCheats = players.isAllowCommandsForAllPlayers();
		ModConfigSpec.ConfigValue<Integer> range = raw(RANGE);
		int original = range.get();
		Path path = configPath();
		byte[] originalBytes = Files.readAllBytes(path);
		try {
			server.setSingleplayerProfile(new GameProfile(UUID.randomUUID(), "config_owner"));
			players.setAllowCommandsForAllPlayers(true);
			helper.assertTrue(players.getOps().get(guest.getGameProfile()) == null
					&& players.isOp(guest.getGameProfile()) && guest.hasPermissions(2),
					"LAN-cheats fixture must grant vanilla permissions without an actual OP entry");

			Reply read = exchange(helper, guest, new Request(1, Operation.READ, "", null));
			helper.assertTrue(read.result().status() == Status.OK && !read.canEdit()
					&& read.entries().stream().anyMatch(entry -> entry.key().equals(RANGE)),
					"ordinary players may read the server descriptors but cannot edit them");
			Reply set = exchange(helper, guest, new Request(2, Operation.SET, RANGE,
					new JsonPrimitive(original == 7 ? 8 : 7)));
			helper.assertTrue(set.result().status() == Status.FORBIDDEN && !set.canEdit(),
					"unlisted LAN-cheats guest was allowed to write through the real config packet");
			for (Request request : List.of(new Request(3, Operation.RESET, RANGE, null),
					new Request(4, Operation.RESET_GROUP, GROUP, null),
					new Request(5, Operation.PREVIEW_PRESET, "pvp", null),
					new Request(6, Operation.APPLY_PRESET, "pvp", null),
					new Request(7, Operation.RESET_PRESET, "pvp", null))) {
				helper.assertTrue(exchange(helper, guest, request).result().status() == Status.FORBIDDEN,
						"unlisted LAN-cheats guest was allowed to use " + request.operation());
			}
			helper.assertTrue(range.get() == original && Arrays.equals(originalBytes, Files.readAllBytes(path)),
					"denied config request changed gameplay state or TOML bytes");
			helper.succeed();
		}
		finally {
			server.setSingleplayerProfile(oldProfile);
			players.setAllowCommandsForAllPlayers(oldCheats);
			if (range.get() != original) {
				range.set(original);
				JojoModConfig.COMMON_SPEC.save();
			}
		}
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void actualOperatorCanReadSetAndResetTheLiveConfig(GameTestHelper helper) throws IOException {
		var player = player(helper, "config_op");
		var ops = player.server.getPlayerList().getOps();
		Path opsPath = ops.getFile().toPath();
		byte[] opsBytes = Files.exists(opsPath) ? Files.readAllBytes(opsPath) : null;
		ModConfigSpec.ConfigValue<Integer> range = raw(RANGE);
		ModConfigSpec.ConfigValue<Double> damage = raw(DAMAGE);
		int originalRange = range.get();
		double originalDamage = damage.get();
		int nextRange = originalRange == 7 ? 8 : 7;
		double nextDamage = originalDamage == 0.625D ? 0.75D : 0.625D;
		Path path = configPath();
		try {
			ops.add(new ServerOpListEntry(player.getGameProfile(), 2, false));
			byte[] before = Files.readAllBytes(path);
			var unnegotiated = new Exchange(player, false);
			ConfigEditorPackets.handleRequest(new Request(1, Operation.SET, RANGE, new JsonPrimitive(nextRange)),
					unnegotiated.context);
			helper.assertTrue(unnegotiated.replies.isEmpty() && range.get() == originalRange
					&& Arrays.equals(before, Files.readAllBytes(path)), "an unnegotiated editor wrote server config");

			Reply invalid = exchange(helper, player, new Request(2, Operation.SET, RANGE, new JsonPrimitive("9")));
			helper.assertTrue(invalid.result().status() == Status.INVALID_VALUE && invalid.canEdit()
					&& Arrays.equals(before, Files.readAllBytes(path)), "typed string was coerced into a server integer");
			Reply set = exchange(helper, player, new Request(3, Operation.SET, RANGE, new JsonPrimitive(nextRange)));
			helper.assertTrue(set.result().status() == Status.OK && set.canEdit() && set.result().changes().size() == 1
					&& set.result().changes().getFirst().after().getAsInt() == nextRange
					&& range.get() == nextRange && persistedRange(path) == nextRange,
					"operator set did not update the real gameplay config and TOML");
			Reply read = exchange(helper, player, new Request(4, Operation.READ, "", null));
			helper.assertTrue(read.result().status() == Status.OK && read.canEdit() && read.entries().stream()
					.anyMatch(entry -> entry.key().equals(RANGE) && entry.value().getAsInt() == nextRange),
					"operator read did not return the authoritative descriptor and current value");
			Reply reset = exchange(helper, player, new Request(5, Operation.RESET, RANGE, null));
			helper.assertTrue(reset.result().status() == Status.OK && range.get().equals(range.getDefault())
					&& persistedRange(path) == range.getDefault(), "operator reset did not persist the real default");
			Reply setDamage = exchange(helper, player, new Request(6, Operation.SET, DAMAGE, new JsonPrimitive(nextDamage)));
			helper.assertTrue(setDamage.result().status() == Status.OK && damage.get() == nextDamage,
					"could not stage a changed value for group reset");
			Reply resetGroup = exchange(helper, player, new Request(7, Operation.RESET_GROUP, GROUP, null));
			helper.assertTrue(resetGroup.result().status() == Status.OK && damage.get().equals(damage.getDefault()),
					"group reset did not reach the real server config");
			byte[] beforePreview = Files.readAllBytes(path);
			Reply preview = exchange(helper, player, new Request(8, Operation.PREVIEW_PRESET, "pvp", null));
			helper.assertTrue(preview.result().status() == Status.OK && preview.result().changes().stream()
							.anyMatch(change -> change.key().equals(DAMAGE) && change.before().getAsDouble() == 1.0D
									&& change.after().getAsDouble() == 0.75D)
							&& preview.result().changes().stream().noneMatch(change -> change.key().equals(RANGE))
							&& range.get().equals(range.getDefault()) && Arrays.equals(beforePreview, Files.readAllBytes(path)),
					"operator preview must return the real diff without applying it");
			helper.succeed();
		}
		finally {
			try {
				range.set(originalRange);
				damage.set(originalDamage);
				JojoModConfig.COMMON_SPEC.save();
			}
			finally {
				ops.remove(player.getGameProfile());
				if (opsBytes == null) Files.deleteIfExists(opsPath);
				else Files.write(opsPath, opsBytes);
			}
		}
	}

	@GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 30)
	public static void malformedConfigPacketCannotReachTheHandler(GameTestHelper helper) throws IOException {
		var client = new Exchange(player(helper, "config_bad"), true);
		Path path = configPath();
		byte[] before = Files.readAllBytes(path);
		for (int count : new int[] {1, 1025}) {
			RegistryFriendlyByteBuf buf = buffer(helper);
			try {
				buf.writeVarLong(1);
				buf.writeEnum(Operation.SET);
				buf.writeUtf(RANGE);
				buf.writeByte(3); // list tag
				buf.writeVarInt(count);
				buf.writeByte(3); // a nested list is not a scalar
				try {
					ConfigEditorPackets.handleRequest(Request.CODEC.decode(buf), client.context);
					throw new AssertionError("Malformed config packet was decoded");
				}
				catch (DecoderException expected) {
					// Rejected at the codec boundary, before an edit or reply is possible.
				}
			}
			finally {
				buf.release();
			}
		}
		helper.assertTrue(client.replies.isEmpty() && Arrays.equals(before, Files.readAllBytes(path)),
				"malformed payload produced a reply or touched the TOML");
		helper.succeed();
	}

	private static Reply exchange(GameTestHelper helper, ServerPlayer player, Request request) {
		var fixture = new Exchange(player, true);
		RegistryFriendlyByteBuf in = buffer(helper), out = buffer(helper);
		try {
			Request.CODEC.encode(in, request);
			ConfigEditorPackets.handleRequest(Request.CODEC.decode(in), fixture.context);
			helper.assertTrue(fixture.replies.size() == 1, "expected one reply to the requesting player");
			Reply.CODEC.encode(out, fixture.replies.getFirst());
			Reply reply = Reply.CODEC.decode(out);
			helper.assertTrue(reply.id() == request.id() && reply.operation() == request.operation(),
					"config response was not correlated to the original request");
			return reply;
		}
		finally {
			in.release();
			out.release();
		}
	}

	private static final class Exchange {
		private final List<Reply> replies = new ArrayList<>();
		private final IPayloadContext context;

		private Exchange(ServerPlayer player, boolean negotiated) {
			var listener = (ICommonPacketListener) Proxy.newProxyInstance(ICommonPacketListener.class.getClassLoader(),
					new Class<?>[] {ICommonPacketListener.class}, (proxy, method, args) -> switch (method.getName()) {
						case "hasChannel" -> negotiated && Reply.TYPE.equals(args[0]);
						case "send" -> { replies.add((Reply) args[0]); yield null; }
						default -> throw new UnsupportedOperationException(method.getName());
					});
			context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
					new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
						case "player" -> player;
						case "listener" -> listener;
						case "reply" -> InvocationHandler.invokeDefault(proxy, method, args);
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

	private static Path configPath() {
		return ModConfigs.getModConfigs(JojoMod.MOD_ID).stream()
				.filter(config -> config.getSpec() == JojoModConfig.COMMON_SPEC).findFirst().orElseThrow().getFullPath();
	}

	private static int persistedRange(Path path) throws IOException {
		try (var reader = Files.newBufferedReader(path)) {
			return new TomlParser().parse(reader).<Integer>get(List.of(RANGE.split("\\.")));
		}
	}

	private static <T> ModConfigSpec.ConfigValue<T> raw(String key) {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of(key.split("\\.")));
	}
}

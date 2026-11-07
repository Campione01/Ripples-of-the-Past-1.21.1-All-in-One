package rotp.core.config.editor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import rotp.core.JojoModConfig;
import rotp.core.config.editor.ConfigEditResult.Change;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEntry.Bounds;
import rotp.core.config.editor.ConfigEntry.Kind;
import rotp.core.config.editor.ConfigEntry.Restart;
import rotp.core.config.editor.ConfigEntry.Side;
import rotp.core.config.editor.client.ServerConfigClient;
import rotp.core.core.JojoMod;
import rotp.core.network.NetworkPayloadValidation;

public final class ConfigEditorPackets {
	private static final int KEY_LIMIT = 160;
	private static final int TEXT_BYTES = 256;
	private static final int NUMBER_CHARS = 64;
	private static final int LIST_LIMIT = 1024;
	private static final int SYNCED_LIST_LIMIT = 64;
	private static final int ENTRY_LIMIT = 128;
	private static final int CHOICE_LIMIT = 32;
	private static final int REQUEST_BYTES = 272 * 1024;
	private static final int REPLY_BYTES = 1024 * 1024;

	private ConfigEditorPackets() {}

	public static void register(PayloadRegistrar registrar) {
		var editor = registrar.versioned("1").optional();
		editor.playToServer(Request.TYPE, Request.CODEC, ConfigEditorPackets::handleRequest);
		editor.playToClient(Reply.TYPE, Reply.CODEC, ConfigEditorPackets::handleReply);
	}

	public enum Operation { READ, SET, RESET, RESET_GROUP }

	public record Request(long id, Operation operation, String key, @Nullable JsonElement value)
			implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(JojoMod.resLoc("config_editor_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
				StreamCodec.ofMember(Request::write, Request::read);

		public Request {
			if (id <= 0) throw new IllegalArgumentException("Invalid config request id");
			Objects.requireNonNull(operation);
			NetworkPayloadValidation.requireUtfLength(key, KEY_LIMIT, "config key");
			if ((operation == Operation.SET) != (value != null) || value != null && !fitsValue(value)) {
				throw new IllegalArgumentException("Invalid config request value");
			}
			if (value != null) value = value.deepCopy();
		}

		@Override
		public Type<Request> type() { return TYPE; }

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
			buf.writeEnum(operation);
			if (operation != Operation.READ) buf.writeUtf(key, KEY_LIMIT);
			if (value != null) writeValue(buf, value);
		}

		private static Request read(RegistryFriendlyByteBuf buf) {
			NetworkPayloadValidation.requireByteLength(buf.readableBytes(), REQUEST_BYTES, "config request");
			long id = NetworkPayloadValidation.requireGeneration(buf.readVarLong(), "config request id");
			Operation operation = buf.readEnum(Operation.class);
			String key = operation == Operation.READ ? "" : buf.readUtf(KEY_LIMIT);
			JsonElement value = operation == Operation.SET ? readValue(buf) : null;
			if (buf.isReadable()) throw new DecoderException("Trailing config request bytes");
			return new Request(id, operation, key, value);
		}
	}

	public record Reply(long id, Operation operation, boolean canEdit, ConfigEditResult result,
			List<ConfigEntry> entries) implements CustomPacketPayload {
		public static final Type<Reply> TYPE = new Type<>(JojoMod.resLoc("config_editor_reply"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC =
				StreamCodec.ofMember(Reply::write, Reply::read);

		public Reply {
			entries = List.copyOf(entries);
		}

		@Override
		public Type<Reply> type() { return TYPE; }

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
			buf.writeEnum(operation);
			buf.writeBoolean(canEdit);
			buf.writeEnum(result.status());
			writeOptionalText(buf, result.key());
			writeCount(buf, result.changes().size(), ENTRY_LIMIT);
			for (Change change : result.changes()) {
				buf.writeUtf(change.key(), KEY_LIMIT);
				writeValue(buf, change.before());
				writeValue(buf, change.after());
				buf.writeEnum(change.restart());
			}
			writeCount(buf, entries.size(), ENTRY_LIMIT);
			for (ConfigEntry entry : entries) writeEntry(buf, entry);
		}

		private static Reply read(RegistryFriendlyByteBuf buf) {
			NetworkPayloadValidation.requireByteLength(buf.readableBytes(), REPLY_BYTES, "config reply");
			long id = NetworkPayloadValidation.requireGeneration(buf.readVarLong(), "config reply id");
			Operation operation = buf.readEnum(Operation.class);
			boolean canEdit = buf.readBoolean();
			Status status = buf.readEnum(Status.class);
			String key = readOptionalText(buf);
			int changed = readCount(buf, ENTRY_LIMIT);
			var changes = new ArrayList<Change>(changed);
			for (int i = 0; i < changed; i++) {
				changes.add(new Change(buf.readUtf(KEY_LIMIT), readValue(buf), readValue(buf), buf.readEnum(Restart.class)));
			}
			int count = readCount(buf, ENTRY_LIMIT);
			var entries = new ArrayList<ConfigEntry>(count);
			for (int i = 0; i < count; i++) entries.add(readEntry(buf));
			if (buf.isReadable()) throw new DecoderException("Trailing config reply bytes");
			return new Reply(id, operation, canEdit, new ConfigEditResult(status, key, changes), entries);
		}
	}

	public static void handleRequest(Request request, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player)) return;
		if (!context.listener().hasChannel(Reply.TYPE)) {
			logRefusal(request, player, Status.UNAVAILABLE);
			return;
		}
		var server = player.server;
		if (ServerLifecycleHooks.getCurrentServer() != server || !server.isRunning() || !JojoModConfig.COMMON_SPEC.isLoaded()) {
			refuse(request, context, player, false, Status.UNAVAILABLE);
			return;
		}
		boolean canEdit = canEdit(player, context);
		if (request.operation() != Operation.READ && !canEdit) {
			refuse(request, context, player, false, Status.FORBIDDEN);
			return;
		}
		if (request.operation() == Operation.READ) {
			List<ConfigEntry> entries = CommonConfigEditor.entries();
			if (entries.size() > ENTRY_LIMIT || entries.stream().anyMatch(entry -> !fitsEntry(entry))) {
				refuse(request, context, player, canEdit, Status.UNAVAILABLE);
				return;
			}
			context.reply(new Reply(request.id(), request.operation(), canEdit, ConfigEditResult.ok(List.of()), entries));
			return;
		}
		if (request.value() instanceof JsonArray list && list.size() > SYNCED_LIST_LIMIT
				&& !"Stand settings.bannedStands".equals(request.key())) {
			refuse(request, context, player, true, Status.INVALID_VALUE);
			return;
		}
		boolean group = request.operation() == Operation.RESET_GROUP;
		if (CommonConfigEditor.entries().stream()
				.filter(entry -> (group ? entry.group() : entry.key()).equals(request.key()))
				.anyMatch(entry -> !fitsEntry(entry))) {
			refuse(request, context, player, true, Status.UNAVAILABLE);
			return;
		}
		ConfigEditResult result = switch (request.operation()) {
			case SET -> CommonConfigEditor.set(request.key(), request.value());
			case RESET -> CommonConfigEditor.reset(request.key());
			case RESET_GROUP -> CommonConfigEditor.resetGroup(request.key());
			case READ -> throw new IllegalStateException("Read already handled");
		};
		if (result.status() != Status.OK) {
			refuse(request, context, player, true, result.status());
			return;
		}
		if (!result.changes().isEmpty()) {
			JojoMod.getLogger().info("Core config editor {} by {} changed {} setting(s)",
					request.operation(), player.getUUID(), result.changes().size());
		}
		context.reply(new Reply(request.id(), request.operation(), true, result, List.of()));
	}

	private static boolean canEdit(ServerPlayer player, IPayloadContext context) {
		var profile = player.getGameProfile();
		if (player.server.isSingleplayerOwner(profile)) return context.connection().isMemoryConnection();
		var op = player.server.getPlayerList().getOps().get(profile);
		return op != null && op.getLevel() >= 2;
	}

	private static void refuse(Request request, IPayloadContext context, ServerPlayer player, boolean canEdit, Status status) {
		logRefusal(request, player, status);
		context.reply(new Reply(request.id(), request.operation(), canEdit, ConfigEditResult.error(status, null), List.of()));
	}

	private static void logRefusal(Request request, ServerPlayer player, Status status) {
		if (status == Status.FORBIDDEN) {
			JojoMod.getLogger().warn("Core config editor refused {} from {}: {}",
					request.operation(), player.getUUID(), status);
		}
		else if (request.operation() != Operation.READ) {
			JojoMod.getLogger().debug("Core config editor refused {} from {}: {}",
					request.operation(), player.getUUID(), status);
		}
	}

	private static void handleReply(Reply reply, IPayloadContext context) {
		if (FMLEnvironment.dist.isClient()) ServerConfigClient.accept(reply, context.connection());
	}

	private static boolean fitsEntry(ConfigEntry entry) {
		return fitsValue(entry.defaultValue()) && fitsValue(entry.value());
	}

	private static void writeEntry(RegistryFriendlyByteBuf buf, ConfigEntry entry) {
		buf.writeUtf(entry.key(), KEY_LIMIT);
		buf.writeUtf(entry.group(), KEY_LIMIT);
		buf.writeEnum(entry.kind());
		buf.writeBoolean(entry.elementKind() != null);
		if (entry.elementKind() != null) buf.writeEnum(entry.elementKind());
		writeValue(buf, entry.defaultValue());
		writeValue(buf, entry.value());
		writeBounds(buf, entry.bounds());
		writeBounds(buf, entry.listSize());
		writeCount(buf, entry.choices().size(), CHOICE_LIMIT);
		for (String choice : entry.choices()) buf.writeUtf(choice, KEY_LIMIT);
		buf.writeEnum(entry.restart());
		buf.writeBoolean(entry.synced());
		writeOptionalText(buf, entry.translationKey());
	}

	private static ConfigEntry readEntry(RegistryFriendlyByteBuf buf) {
		String key = buf.readUtf(KEY_LIMIT), group = buf.readUtf(KEY_LIMIT);
		Kind kind = buf.readEnum(Kind.class), element = buf.readBoolean() ? buf.readEnum(Kind.class) : null;
		JsonElement defaultValue = readValue(buf), value = readValue(buf);
		Bounds bounds = readBounds(buf), listSize = readBounds(buf);
		int count = readCount(buf, CHOICE_LIMIT);
		var choices = new ArrayList<String>(count);
		for (int i = 0; i < count; i++) choices.add(buf.readUtf(KEY_LIMIT));
		return new ConfigEntry(key, group, Side.SERVER, kind, element, defaultValue, value, bounds, listSize, choices,
				buf.readEnum(Restart.class), buf.readBoolean(), readOptionalText(buf));
	}

	private static void writeBounds(RegistryFriendlyByteBuf buf, @Nullable Bounds bounds) {
		buf.writeBoolean(bounds != null);
		if (bounds != null) {
			buf.writeDouble(bounds.minimum());
			buf.writeDouble(bounds.maximum());
		}
	}

	private static @Nullable Bounds readBounds(RegistryFriendlyByteBuf buf) {
		return buf.readBoolean() ? new Bounds(buf.readDouble(), buf.readDouble()) : null;
	}

	private static void writeOptionalText(RegistryFriendlyByteBuf buf, @Nullable String value) {
		buf.writeBoolean(value != null);
		if (value != null) buf.writeUtf(value, KEY_LIMIT);
	}

	private static @Nullable String readOptionalText(RegistryFriendlyByteBuf buf) {
		return buf.readBoolean() ? buf.readUtf(KEY_LIMIT) : null;
	}

	private static void writeCount(RegistryFriendlyByteBuf buf, int count, int max) {
		buf.writeVarInt(NetworkPayloadValidation.requireOutboundCollectionSize(count, max, "config entries"));
	}

	private static int readCount(RegistryFriendlyByteBuf buf, int max) {
		return NetworkPayloadValidation.requireCollectionSize(buf.readVarInt(), max, buf.readableBytes(), "config entries");
	}

	// JSON values use typed tokens on the wire. A list can contain primitives only.
	private static boolean fitsValue(JsonElement value) {
		if (value.isJsonArray()) {
			var list = value.getAsJsonArray();
			if (list.size() > LIST_LIMIT) return false;
			for (JsonElement item : list) if (!fitsPrimitive(item)) return false;
			return true;
		}
		return fitsPrimitive(value);
	}

	private static boolean fitsPrimitive(JsonElement value) {
		if (!value.isJsonPrimitive()) return false;
		JsonPrimitive scalar = value.getAsJsonPrimitive();
		if (scalar.isBoolean()) return true;
		if (scalar.isString()) return ByteBufUtil.utf8Bytes(scalar.getAsString()) <= TEXT_BYTES;
		return scalar.isNumber() && Double.isFinite(scalar.getAsDouble()) && scalar.getAsString().length() <= NUMBER_CHARS;
	}

	private static void writeValue(RegistryFriendlyByteBuf buf, JsonElement value) {
		if (!fitsValue(value)) throw new IllegalArgumentException("Unrepresentable config value");
		if (value.isJsonArray()) {
			buf.writeByte(3);
			var list = value.getAsJsonArray();
			writeCount(buf, list.size(), LIST_LIMIT);
			for (JsonElement item : list) writePrimitive(buf, item.getAsJsonPrimitive());
		}
		else writePrimitive(buf, value.getAsJsonPrimitive());
	}

	private static void writePrimitive(RegistryFriendlyByteBuf buf, JsonPrimitive scalar) {
		if (scalar.isBoolean()) {
			buf.writeByte(0);
			buf.writeBoolean(scalar.getAsBoolean());
		}
		else if (scalar.isNumber()) {
			buf.writeByte(1);
			buf.writeUtf(scalar.getAsString(), NUMBER_CHARS);
		}
		else {
			buf.writeByte(2);
			buf.writeUtf(scalar.getAsString(), TEXT_BYTES);
		}
	}

	private static JsonElement readValue(RegistryFriendlyByteBuf buf) {
		int tag = buf.readUnsignedByte();
		if (tag != 3) return readPrimitive(buf, tag);
		int size = readCount(buf, LIST_LIMIT);
		var array = new JsonArray(size);
		for (int i = 0; i < size; i++) array.add(readPrimitive(buf, buf.readUnsignedByte()));
		return array;
	}

	private static JsonPrimitive readPrimitive(RegistryFriendlyByteBuf buf, int tag) {
		return switch (tag) {
			case 0 -> new JsonPrimitive(buf.readBoolean());
			case 1 -> {
				try {
					var value = new BigDecimal(buf.readUtf(NUMBER_CHARS));
					if (!Double.isFinite(value.doubleValue())) throw new DecoderException("Non-finite config number");
					yield new JsonPrimitive(value);
				}
				catch (NumberFormatException invalid) {
					throw new DecoderException("Invalid config number");
				}
			}
			case 2 -> {
				String text = buf.readUtf(TEXT_BYTES);
				NetworkPayloadValidation.requireByteLength(ByteBufUtil.utf8Bytes(text), TEXT_BYTES, "config text");
				yield new JsonPrimitive(text);
			}
			default -> throw new DecoderException("Invalid config value tag");
		};
	}
}

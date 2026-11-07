package rotp.core.config.editor.client;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEditorPackets.Request;
import rotp.core.core.JojoMod;

/** Client-thread API for the optional, server-owned config editor. */
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public final class ServerConfigClient {
	private static ClientPacketListener session;
	private static long nextId;
	private static long pendingId;
	private static Operation pendingOperation;
	private static Reply latest;

	private ServerConfigClient() {}

	public static boolean isAvailable() { return negotiated() != null; }

	public static long read() { return request(Operation.READ, "", null); }
	public static long set(String key, JsonElement value) { return request(Operation.SET, key, value); }
	public static long reset(String key) { return request(Operation.RESET, key, null); }
	public static long resetGroup(String group) { return request(Operation.RESET_GROUP, group, null); }
	public static long previewPreset(String id) { return request(Operation.PREVIEW_PRESET, id, null); }
	public static long applyPreset(String id) { return request(Operation.APPLY_PRESET, id, null); }
	public static long resetPreset(String id) { return request(Operation.RESET_PRESET, id, null); }

	/** Returns the correlation ID, or zero when the server does not support the editor. */
	public static long request(Operation operation, String key, @Nullable JsonElement value) {
		var listener = negotiated();
		if (listener == null) return 0;
		Request request = new Request(++nextId, operation, key, value);
		pendingId = request.id();
		pendingOperation = request.operation();
		listener.send(request);
		return request.id();
	}

	public static @Nullable Reply latestReply() {
		return negotiated() == null ? null : latest;
	}

	public static @Nullable Reply replyFor(long requestId) {
		Reply reply = latestReply();
		return reply != null && reply.id() == requestId ? reply : null;
	}

	public static void accept(Reply reply, Connection source) {
		var listener = negotiated();
		if (listener != null && listener.getConnection() == source
				&& reply.id() == pendingId && reply.operation() == pendingOperation) {
			latest = reply;
			pendingId = 0;
			pendingOperation = null;
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		clearSession(null);
	}

	private static @Nullable ClientPacketListener negotiated() {
		var listener = Minecraft.getInstance().getConnection();
		if (listener != session) clearSession(listener);
		return listener != null && listener.hasChannel(Request.TYPE) && listener.hasChannel(Reply.TYPE) ? listener : null;
	}

	private static void clearSession(@Nullable ClientPacketListener listener) {
		session = listener;
		latest = null;
		pendingId = 0;
		pendingOperation = null;
	}
}

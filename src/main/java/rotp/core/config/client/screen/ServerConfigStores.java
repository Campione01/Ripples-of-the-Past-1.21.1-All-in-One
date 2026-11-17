package rotp.core.config.client.screen;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEditorPackets.Operation;
import rotp.core.config.editor.client.ConfigEditSession;
import rotp.core.config.editor.client.ServerConfigClient;

/** Connects the screen's session to the real connection. */
final class ServerConfigStores {
	private ServerConfigStores() {}

	static ConfigEditSession open() {
		ConfigEditSession session = new ConfigEditSession(new ConfigEditSession.Transport() {
			@Override
			public boolean hasWorld() {
				return Minecraft.getInstance().getConnection() != null;
			}

			@Override
			public boolean supported() {
				return ServerConfigClient.isAvailable();
			}

			@Override
			public long send(Operation operation, String key, @Nullable JsonElement value) {
				try {
					return ServerConfigClient.request(operation, key, value);
				}
				catch (IllegalArgumentException tooLong) {
					return -1;
				}
			}

			@Override
			public @Nullable ConfigEditSession.Answer answer(long id) {
				var reply = ServerConfigClient.replyFor(id);
				return reply == null ? null : new ConfigEditSession.Answer(reply.canEdit(), reply.result(), reply.entries());
			}
		}, CommonConfigEditor::entries);
		session.open(net.minecraft.Util.getMillis());
		return session;
	}
}

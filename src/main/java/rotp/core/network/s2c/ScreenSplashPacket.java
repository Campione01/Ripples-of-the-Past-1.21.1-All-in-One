package rotp.core.network.s2c;

import rotp.core.PacketsRegister;
import rotp.core.client.ui.hud_misc.ScreenSplash;
import rotp.core.client.ui.hud_misc.ScreenSplashLayer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Blood or sand reached this player's face. Whether it shows is the client's setting. */
public record ScreenSplashPacket(boolean sand, float strength, int tint) implements CustomPacketPayload {

	private static CustomPacketPayload.Type<ScreenSplashPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<ScreenSplashPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ScreenSplashPacket> type() {
			return type;
		}

		@Override
		public void encode(ScreenSplashPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.sand);
			buf.writeFloat(packet.strength);
			buf.writeInt(packet.tint);
		}

		@Override
		public ScreenSplashPacket decode(RegistryFriendlyByteBuf buf) {
			return new ScreenSplashPacket(buf.readBoolean(), buf.readFloat(), buf.readInt());
		}

		@Override
		public void handle(ScreenSplashPacket payload, IPayloadContext context) {
			ClientSide.handle(payload);
		}
	}

	// client-only classes stay out of the common handler
	private static final class ClientSide {
		static void handle(ScreenSplashPacket payload) {
			ScreenSplashLayer.add(payload.sand ? ScreenSplash.Kind.SAND : ScreenSplash.Kind.BLOOD, payload.strength, payload.tint);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

package rotp.core.mechanics.jojopose;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player asks to strike a pose, or to end the one it holds (official ClJojoPoseActionPacket, the player only). */
public record ClJojoPosePacket(boolean start, @Nullable ResourceLocation poseSet, @Nullable String pose) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClJojoPosePacket> type;

	public static ClJojoPosePacket start(ResourceLocation poseSet, String pose) {
		return new ClJojoPosePacket(true, poseSet, pose);
	}

	public static ClJojoPosePacket stop() {
		return new ClJojoPosePacket(false, null, null);
	}

	/** What the server does with the packet from this player. */
	public static boolean handleOnServer(ClJojoPosePacket packet, Player sender) {
		if (packet.start()) {
			return packet.poseSet() != null && JojoPoseAction.start(sender, packet.poseSet(), packet.pose());
		}
		return JojoPoseAction.stop(sender);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClJojoPosePacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClJojoPosePacket> type() {
			return type;
		}

		@Override
		public void encode(ClJojoPosePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.start());
			if (packet.start()) {
				buf.writeResourceLocation(packet.poseSet());
				buf.writeUtf(packet.pose(), JojoPoseAction.MAX_POSE_NAME_LENGTH);
			}
		}

		@Override
		public ClJojoPosePacket decode(RegistryFriendlyByteBuf buf) {
			if (buf.readBoolean()) {
				return start(buf.readResourceLocation(), buf.readUtf(JojoPoseAction.MAX_POSE_NAME_LENGTH));
			}
			return stop();
		}

		@Override
		public void handle(ClJojoPosePacket payload, IPayloadContext context) {
			handleOnServer(payload, context.player());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

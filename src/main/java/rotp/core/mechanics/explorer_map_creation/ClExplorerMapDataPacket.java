package rotp.core.mechanics.explorer_map_creation;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.core.PacketsRegister;

/** One field of the explorer map the client edits: the destination's X or Z, the icon, or the map's name. */
public record ClExplorerMapDataPacket(Field field, int number, @Nullable Holder<MapDecorationType> icon, String name)
		implements CustomPacketPayload {
	private static final int NAME_BYTES = ExplorerMapRules.NAME_MAX * 4;
	private static CustomPacketPayload.Type<ClExplorerMapDataPacket> type;

	public enum Field { X, Z, ICON, NAME }

	public static ClExplorerMapDataPacket posX(int value) {
		return new ClExplorerMapDataPacket(Field.X, value, null, "");
	}

	public static ClExplorerMapDataPacket posZ(int value) {
		return new ClExplorerMapDataPacket(Field.Z, value, null, "");
	}

	public static ClExplorerMapDataPacket icon(Holder<MapDecorationType> icon) {
		return new ClExplorerMapDataPacket(Field.ICON, 0, icon, "");
	}

	public static ClExplorerMapDataPacket name(String name) {
		return new ClExplorerMapDataPacket(Field.NAME, 0, null, name);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	/** Applies the field to the explorer map menu the player has open; the menu keeps it inside its rules. */
	public void applyTo(ExplorerMapMenu menu) {
		switch (field) {
			case X -> menu.setTargetPosX(number);
			case Z -> menu.setTargetPosZ(number);
			case ICON -> menu.setTargetIcon(icon);
			case NAME -> menu.setMapItemName(name);
		}
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClExplorerMapDataPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClExplorerMapDataPacket> type() {
			return type;
		}

		@Override
		public void encode(ClExplorerMapDataPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeEnum(packet.field);
			switch (packet.field) {
				case X, Z -> buf.writeInt(packet.number);
				case ICON -> MapDecorationType.STREAM_CODEC.encode(buf, packet.icon);
				case NAME -> buf.writeUtf(packet.name, NAME_BYTES);
			}
		}

		@Override
		public ClExplorerMapDataPacket decode(RegistryFriendlyByteBuf buf) {
			Field field = buf.readEnum(Field.class);
			return switch (field) {
				case X, Z -> new ClExplorerMapDataPacket(field, buf.readInt(), null, "");
				case ICON -> new ClExplorerMapDataPacket(field, 0, MapDecorationType.STREAM_CODEC.decode(buf), "");
				case NAME -> new ClExplorerMapDataPacket(field, 0, null, buf.readUtf(NAME_BYTES));
			};
		}

		@Override
		public void handle(ClExplorerMapDataPacket packet, IPayloadContext context) {
			if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ExplorerMapMenu menu) {
				packet.applyTo(menu);
			}
		}
	}
}

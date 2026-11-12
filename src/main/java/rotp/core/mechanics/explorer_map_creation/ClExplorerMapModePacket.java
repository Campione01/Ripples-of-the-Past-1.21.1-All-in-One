package rotp.core.mechanics.explorer_map_creation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.core.PacketsRegister;

/** The client switched the cartography table between vanilla's mode and the explorer map mode. */
public record ClExplorerMapModePacket(boolean explorer) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClExplorerMapModePacket> type;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	/** Opens the other mode for a player at a cartography table, at the table the server saw them use. */
	public static boolean switchMode(ServerPlayer player, boolean explorer) {
		AbstractContainerMenu open = player.containerMenu;
		BlockPos pos = ExplorerMapTables.lastTable(player);
		if (!(open instanceof CartographyTableMenu || open instanceof ExplorerMapMenu) || !ExplorerMapRules.isUsableTable(player, pos)) {
			return false;
		}
		ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return explorer ? ExplorerMapMenu.EXPLORER_TITLE : ExplorerMapMenu.VANILLA_TITLE;
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
				return explorer ? new ExplorerMapMenu(containerId, inventory, access) : new CartographyTableMenu(containerId, inventory, access);
			}

			// switching modes keeps the mouse where it was
			@Override
			public boolean shouldTriggerClientSideContainerClosingOnOpen() {
				return false;
			}
		});
		return true;
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClExplorerMapModePacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClExplorerMapModePacket> type() {
			return type;
		}

		@Override
		public void encode(ClExplorerMapModePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.explorer);
		}

		@Override
		public ClExplorerMapModePacket decode(RegistryFriendlyByteBuf buf) {
			return new ClExplorerMapModePacket(buf.readBoolean());
		}

		@Override
		public void handle(ClExplorerMapModePacket packet, IPayloadContext context) {
			if (context.player() instanceof ServerPlayer player) {
				switchMode(player, packet.explorer);
			}
		}
	}
}

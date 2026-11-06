package rotp.core.subsystems.entity_puppetcontrol;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The keys of a user's Stand moves that the server cannot see by itself.
 * <p>
 * The variation key is a client setting, so the client reports it; see
 * {@link #isShiftHeld(LivingEntity)}. The sneak key: 1.16 kept the user's sneak
 * under manual control; the port clears it (ClientEntityController.clearInput),
 * so the controlling client sends that key on its own; see
 * {@link #isShiftHeld(LivingEntity, StandEntity)}.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class StandManualInput {
	private static final Set<UUID> SERVER_SHIFT = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Boolean> SERVER_VARIATION_KEY = new ConcurrentHashMap<>();
	private static volatile boolean clientShift;
	private static volatile boolean clientVariationKey;

	private StandManualInput() {}

	/**
	 * What a declared Modifier.SHIFT means, for a move that asks at its press: the
	 * variation key of the player's client. A user no client reports for, a mob or
	 * a fake player, has no such key and goes by its sneak.
	 */
	public static boolean isShiftHeld(@Nullable LivingEntity user) {
		if (user == null) {
			return false;
		}
		if (user.level().isClientSide()) {
			if (user instanceof Player player && player.isLocalPlayer()) {
				return clientVariationKey;
			}
		}
		else {
			Boolean reported = SERVER_VARIATION_KEY.get(user.getUUID());
			if (reported != null) {
				return reported;
			}
		}
		StandPower power = StandPower.get(user);
		return isShiftHeld(user, power != null ? power.getSummonedStandEntity() : null);
	}

	/**
	 * @return the user's sneak, or the sent sneak key while the given Stand is
	 *         manually controlled
	 */
	public static boolean isShiftHeld(@Nullable LivingEntity user, @Nullable StandEntity stand) {
		if (user == null) {
			return false;
		}
		if (stand == null || !stand.isManuallyControlled()) {
			return user.isShiftKeyDown();
		}
		if (user.level().isClientSide()) {
			return clientShift && user instanceof Player player && player.isLocalPlayer();
		}
		return SERVER_SHIFT.contains(user.getUUID());
	}

	public static void setServerShift(Player player, boolean held) {
		if (held) {
			SERVER_SHIFT.add(player.getUUID());
		}
		else {
			SERVER_SHIFT.remove(player.getUUID());
		}
	}

	public static void clear(Player player) {
		SERVER_SHIFT.remove(player.getUUID());
		SERVER_VARIATION_KEY.remove(player.getUUID());
	}

	public static void setServerVariationKey(Player player, boolean held) {
		SERVER_VARIATION_KEY.put(player.getUUID(), held);
	}

	public static void setClientVariationKey(boolean held) {
		clientVariationKey = held;
	}

	public static void setClientShift(boolean held) {
		clientShift = held;
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity());
	}

	public record VariationKeyPacket(boolean held) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<VariationKeyPacket> TYPE =
				new CustomPacketPayload.Type<>(JojoMod.resLoc("clvariationkey"));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static class Handler implements PacketsRegister.PacketCodecHandler<VariationKeyPacket> {
			public static final StreamCodec<RegistryFriendlyByteBuf, VariationKeyPacket> STREAM_CODEC =
					StreamCodec.composite(ByteBufCodecs.BOOL, VariationKeyPacket::held, VariationKeyPacket::new);

			@Override
			public Type<VariationKeyPacket> type() {
				return TYPE;
			}

			@Override
			public StreamCodec<? super RegistryFriendlyByteBuf, VariationKeyPacket> reader() {
				return STREAM_CODEC;
			}

			@Override
			public void handle(VariationKeyPacket packet, IPayloadContext context) {
				setServerVariationKey(context.player(), packet.held());
			}
		}
	}

	public record ShiftPacket(boolean held) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ShiftPacket> TYPE =
				new CustomPacketPayload.Type<>(JojoMod.resLoc("clstandshift"));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static class Handler implements PacketsRegister.PacketCodecHandler<ShiftPacket> {
			public static final StreamCodec<RegistryFriendlyByteBuf, ShiftPacket> STREAM_CODEC =
					StreamCodec.composite(ByteBufCodecs.BOOL, ShiftPacket::held, ShiftPacket::new);

			@Override
			public Type<ShiftPacket> type() {
				return TYPE;
			}

			@Override
			public StreamCodec<? super RegistryFriendlyByteBuf, ShiftPacket> reader() {
				return STREAM_CODEC;
			}

			@Override
			public void handle(ShiftPacket packet, IPayloadContext context) {
				setServerShift(context.player(), packet.held());
			}
		}
	}
}

package rotp.core.network.s2c;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.client.sound.ClientVoiceLineManager;
import rotp.core.mechanics.voiceline.VoiceLineSpeaker;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A voice line: the 1.16 sound, and the voice pack trigger with the speaker it filters on. A pack line found on the
 * client for the trigger and speaker is said instead of the sound; with no 1.16 sound, only a pack line is said.
 */
public record PlayVoiceLinePacket(@Nullable Holder<SoundEvent> sound, @Nullable Holder<SoundEvent> trigger,
		VoiceLineSpeaker speaker, @Nullable SoundSource source, int entityId,
		float volume, float pitch, boolean interrupt) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<PlayVoiceLinePacket> type;

	public PlayVoiceLinePacket(@Nullable Holder<SoundEvent> sound, @Nullable SoundSource source, int entityId,
			float volume, float pitch, boolean interrupt) {
		this(sound, null, VoiceLineSpeaker.NONE, source, entityId, volume, pitch, interrupt);
	}

	public static PlayVoiceLinePacket notTriggered(int entityId) {
		return new PlayVoiceLinePacket(null, null, VoiceLineSpeaker.NONE, null, entityId, 0.0F, 0.0F, false);
	}

	public boolean isTriggered() {
		return sound != null || trigger != null;
	}

	@Nullable
	public ResourceLocation triggerId() {
		return trigger != null ? trigger.unwrapKey().map(key -> key.location()).orElse(null) : null;
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<PlayVoiceLinePacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<PlayVoiceLinePacket> type() {
			return type;
		}

		@Override
		public void encode(PlayVoiceLinePacket packet, RegistryFriendlyByteBuf buf) {
			boolean triggerVoiceLine = packet.isTriggered();
			buf.writeBoolean(triggerVoiceLine);
			buf.writeInt(packet.entityId);
			if (triggerVoiceLine) {
				writeOptionalSound(buf, packet.sound);
				writeOptionalSound(buf, packet.trigger);
				VoiceLineSpeaker.STREAM_CODEC.encode(buf, packet.speaker);
				buf.writeEnum(packet.source);
				buf.writeFloat(packet.volume);
				buf.writeFloat(packet.pitch);
				buf.writeBoolean(packet.interrupt);
			}
		}

		@Override
		public PlayVoiceLinePacket decode(RegistryFriendlyByteBuf buf) {
			boolean triggerVoiceLine = buf.readBoolean();
			int entityId = buf.readInt();
			if (!triggerVoiceLine) {
				return PlayVoiceLinePacket.notTriggered(entityId);
			}
			return new PlayVoiceLinePacket(readOptionalSound(buf), readOptionalSound(buf),
					VoiceLineSpeaker.STREAM_CODEC.decode(buf), buf.readEnum(SoundSource.class),
					entityId, buf.readFloat(), buf.readFloat(), buf.readBoolean());
		}

		private static void writeOptionalSound(RegistryFriendlyByteBuf buf, @Nullable Holder<SoundEvent> sound) {
			buf.writeBoolean(sound != null);
			if (sound != null) {
				SoundEvent.STREAM_CODEC.encode(buf, sound);
			}
		}

		@Nullable
		private static Holder<SoundEvent> readOptionalSound(RegistryFriendlyByteBuf buf) {
			return buf.readBoolean() ? SoundEvent.STREAM_CODEC.decode(buf) : null;
		}

		@Override
		public void handle(PlayVoiceLinePacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				if (!payload.isTriggered()) {
					ClientVoiceLineManager.voiceLineNotTriggered(entity);
				}
				else {
					ClientVoiceLineManager.playVoiceLine(entity, payload);
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

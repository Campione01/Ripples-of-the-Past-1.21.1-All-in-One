package rotp.core.mechanics.voiceline;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.mechanics.clothes.CharacterFromClothes;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who says a voice line, as the voice packs filter it: the JoJo character the speaker's clothes make them, the story
 * part of those clothes and the speaker's Stand. Without a character no pack line applies.
 */
public record VoiceLineSpeaker(Optional<ResourceLocation> character, Optional<ResourceLocation> storyPart,
		Optional<ResourceLocation> standType) {
	public static final VoiceLineSpeaker NONE = new VoiceLineSpeaker(Optional.empty(), Optional.empty(), Optional.empty());

	public static VoiceLineSpeaker of(@Nullable LivingEntity entity) {
		CharacterFromClothes worn = CharacterFromClothes.of(entity);
		Optional<ResourceLocation> character = worn.character().flatMap(VoiceLineSpeaker::id);
		if (entity == null || character.isEmpty()) {
			return NONE;
		}
		Optional<ResourceLocation> standType = StandPower.getOptional(entity)
				.filter(StandPower::hasPower)
				.map(power -> power.getPowerType().getId());
		return new VoiceLineSpeaker(character, worn.storyPart().flatMap(VoiceLineSpeaker::id), standType);
	}

	public boolean hasCharacter() {
		return character.isPresent();
	}

	private static Optional<ResourceLocation> id(Holder<?> holder) {
		return holder.unwrapKey().map(ResourceKey::location);
	}

	public static final StreamCodec<FriendlyByteBuf, VoiceLineSpeaker> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), VoiceLineSpeaker::character,
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), VoiceLineSpeaker::storyPart,
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), VoiceLineSpeaker::standType,
			VoiceLineSpeaker::new);
}

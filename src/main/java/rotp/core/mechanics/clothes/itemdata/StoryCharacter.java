package rotp.core.mechanics.clothes.itemdata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import rotp.core.core.JojoRegistries;
import rotp.core.util.functions.CodecUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class StoryCharacter {
	protected final List<ResourceLocation> stands;
	private Map<ResourceLocation, Holder<ClothesSet>> clothesInit = new TreeMap<>();
	protected List<Holder<ClothesSet>> clothesOrdered;

	protected Component nameFull;
	protected Component nameShortened;
	
	public StoryCharacter(Optional<List<ResourceLocation>> stands) {
		this(stands.orElseGet(Collections::emptyList));
	}
	
	private StoryCharacter(List<ResourceLocation> stands) {
		this.stands = List.copyOf(stands);
	}
	
	/**
	 * The same character file from several packs: the Stand ids of all of them, in pack order, each once.
	 * A file without Stand ids adds nothing.
	 */
	public StoryCharacter merge(StoryCharacter other) {
		if (other.stands.isEmpty()) return this;
		if (this.stands.isEmpty()) return other;
		
		Set<ResourceLocation> allStandIds = new LinkedHashSet<>(this.stands);
		allStandIds.addAll(other.stands);
		return new StoryCharacter(List.copyOf(allStandIds));
	}
	
	public List<ResourceLocation> getStandIds() {
		return stands;
	}
	
	private Optional<List<ResourceLocation>> getStandIdsOptional() {
		return !stands.isEmpty() ? Optional.of(stands) : Optional.empty();
	}
	
	public void initName(ResourceKey<StoryCharacter> key) {
		ResourceLocation id = key.location();
		String tlKey = Util.makeDescriptionId("character", id);
		this.nameFull = Component.translatable(tlKey);
		this.nameShortened = Component.translatable(tlKey + ".short");
	}
	
	protected void addClothesSet(ResourceLocation key, Holder<ClothesSet> clothesSet) {
		clothesInit.put(key, clothesSet);
		clothesOrdered = null;
	}
	

	public Component getName(boolean shortened) {
		return shortened ? nameShortened : nameFull;
	}
	
	public List<Holder<ClothesSet>> getClothesSets() {
		if (clothesOrdered == null) {
			clothesOrdered = clothesInit.values().stream().toList();
		}
		return clothesOrdered;
	}
	
	
	/** {@code "stand"} is one Stand id or a list of them, and may be left out. */
	public static final Codec<StoryCharacter> DIRECT_CODEC = RecordCodecBuilder.create(
			builder -> builder.group(
					CodecUtil.listOrSingleCodec(ResourceLocation.CODEC).optionalFieldOf("stand").forGetter(StoryCharacter::getStandIdsOptional))
			.apply(builder, StoryCharacter::new));
	
	public static final Codec<Holder<StoryCharacter>> REG_CODEC = RegistryFixedCodec.create(JojoRegistries.STORY_CHARACTERS_REG_KEY);
	
	public static final StreamCodec<RegistryFriendlyByteBuf, Holder<StoryCharacter>> STREAM_CODEC = 
			ByteBufCodecs.holderRegistry(JojoRegistries.STORY_CHARACTERS_REG_KEY);
	
}

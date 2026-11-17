package rotp.core.mechanics.clothes;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.init.ModItemDataComponents;
import rotp.core.mechanics.clothes.itemdata.ClothesDataComponent;
import rotp.core.mechanics.clothes.itemdata.ClothesSet;
import rotp.core.mechanics.clothes.itemdata.ClothesSlotType;
import rotp.core.mechanics.clothes.itemdata.StoryCharacter;
import rotp.core.subsystems.StoryPart;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The JoJo character a wearer counts as: the one every worn clothes piece belongs to, and the story part every piece
 * shares. Either is empty when nothing is worn or the pieces disagree. Works on both logical sides, as the clothes are
 * synced to every client tracking the wearer.
 */
public record CharacterFromClothes(Optional<Holder<StoryCharacter>> character, Optional<Holder<StoryPart>> storyPart) {
	public static final CharacterFromClothes NONE = new CharacterFromClothes(Optional.empty(), Optional.empty());

	/** {@link #NONE} for an entity that cannot wear clothes. */
	public static CharacterFromClothes of(@Nullable LivingEntity entity) {
		EntityClothesInventory clothes = EntityClothesInventory.getExisting(entity);
		return clothes != null ? fromClothes(clothes) : NONE;
	}

	public static CharacterFromClothes fromClothes(EntityClothesInventory clothes) {
		Optional<Holder<StoryCharacter>> character = null;
		Optional<Holder<StoryPart>> storyPart = null;

		for (ClothesSlotType slot : ClothesSlotType.values()) {
			ItemStack clothesItem = clothes.getClothingPiece(slot);
			if (clothesItem.isEmpty()) continue;
			ClothesDataComponent clothesPiece = clothesItem.get(ModItemDataComponents.CLOTHES_PIECE);
			if (clothesPiece == null) continue;

			Holder<StoryCharacter> pieceCharacter = clothesPiece.getJojoCharacter();
			Optional<Holder<StoryPart>> pieceStoryPart = Optional.ofNullable(clothesPiece.getClothesSet().value()).flatMap(ClothesSet::getStoryPart);

			if (character == null) {
				character = Optional.of(pieceCharacter);
			}
			else if (character.isPresent() && !character.get().is(pieceCharacter)) {
				character = Optional.empty();
			}

			if (storyPart == null) {
				storyPart = pieceStoryPart;
			}
			else if (storyPart.isPresent() && (pieceStoryPart.isEmpty() || !storyPart.get().is(pieceStoryPart.get()))) {
				storyPart = Optional.empty();
			}
		}

		if (character == null) return NONE;
		return new CharacterFromClothes(character, storyPart);
	}
}

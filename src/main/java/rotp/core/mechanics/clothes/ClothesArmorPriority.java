package rotp.core.mechanics.clothes;

import javax.annotation.Nullable;

import rotp.core.config.client.ClientModSettings;
import rotp.core.mechanics.clothes.itemdata.ClothesSlotType;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Which of the two a humanoid shows when it wears a piece of armor and a piece of clothes in the same slot. */
public enum ClothesArmorPriority {
	CLOTHES,
	ARMOR;

	/** The local player's setting, or the default before the client settings are loaded. */
	public static ClothesArmorPriority current() {
		return ClientModSettings.getInstance() != null ? ClientModSettings.getSettingsReadOnly().clothesArmorPriority : CLOTHES;
	}

	/** Whether the entity's armor piece in that slot is drawn. */
	public boolean showsArmor(LivingEntity entity, EquipmentSlot slot) {
		if (this != CLOTHES) return true;
		ClothesSlotType clothesSlot = clothesSlot(slot);
		EntityClothesInventory clothes = EntityClothesInventory.getExisting(entity);
		return clothesSlot == null || clothes == null || clothes.getClothingPiece(clothesSlot).isEmpty();
	}

	/** Whether the entity's clothes piece in that slot is drawn. */
	public boolean showsClothes(LivingEntity entity, ClothesSlotType slot) {
		return this != ARMOR || entity.getItemBySlot(armorSlot(slot)).isEmpty();
	}

	public static EquipmentSlot armorSlot(ClothesSlotType slot) {
		return switch (slot) {
			case HEAD -> EquipmentSlot.HEAD;
			case CHEST -> EquipmentSlot.CHEST;
			case LEGS -> EquipmentSlot.LEGS;
			case FEET -> EquipmentSlot.FEET;
		};
	}

	@Nullable
	public static ClothesSlotType clothesSlot(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ClothesSlotType.HEAD;
			case CHEST -> ClothesSlotType.CHEST;
			case LEGS -> ClothesSlotType.LEGS;
			case FEET -> ClothesSlotType.FEET;
			default -> null;
		};
	}
}

package rotp.core.api.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mojang.datafixers.util.Pair;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.SummonedStand;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * A live entity's state taken and put back on the same live entity, with what the core keeps besides its saved data or
 * would lose in a plain load: for a rewind or a pull-back (Gold Experience Requiem's Return to Zero, King Crimson's
 * Epitaph). The entity stays in its level with its UUID. What the entity's saved data does not hold (a player's carried
 * stack, crafting grid, item cooldowns, an open menu, its vehicle) is the caller's. Server side.
 */
public final class EntityStateRestore {

	/**
	 * @param tag the entity's saved data with its type id and without its passengers
	 * @param frozen whether stopped time held it (its saved AI off may be the freeze's)
	 * @param noPhysics the entity's noPhysics, which its saved data does not hold
	 */
	public record Snapshot(CompoundTag tag, boolean frozen, boolean noPhysics) {
		public Snapshot {
			tag = tag.copy();
		}
	}

	private EntityStateRestore() {}

	public static Snapshot capture(Entity entity) {
		CompoundTag tag = entity.saveWithoutId(new CompoundTag());
		ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		tag.putString("id", type.toString());
		tag.remove("Passengers");
		return new Snapshot(tag, TimeStopState.shouldFreezeOnServer(entity), entity.noPhysics);
	}

	/**
	 * Loads the snapshot onto the live entity: the effects it gained since are taken off (a load only adds effects), its
	 * summoned Stand stays its user's (a load puts a new Stand power that would not know it; a Stand of another type is
	 * unsummoned), a mob that stopped time holds now, or held then, keeps the AI off it has now, and stopped time holds
	 * the entity where it now stands (TimeStopState#repinOrRelease). Then its trackers, and a player himself, are sent its
	 * place, its effects (the removed ones too) and its equipment, and a player his inventory and selected slot.
	 */
	public static void restore(Entity entity, Snapshot snapshot) {
		if (!(entity.level() instanceof ServerLevel level)) {
			throw new IllegalStateException("An entity state may only be restored on the server");
		}
		Set<Holder<MobEffect>> oldEffects = entity instanceof LivingEntity living
				? Set.copyOf(living.getActiveEffectsMap().keySet()) : Set.of();
		Boolean keptNoAi = entity instanceof Mob mob && (snapshot.frozen() || TimeStopState.shouldFreezeOnServer(mob))
				? mob.isNoAi() : null;
		CompoundTag tag = snapshot.tag().copy();
		tag.remove("id");
		tag.remove("Passengers");
		tag.putUUID("UUID", entity.getUUID());
		if (entity instanceof LivingEntity living) {
			for (Holder<MobEffect> effect : oldEffects) {
				living.removeEffect(effect);
			}
		}
		loadKeepingSummonedStand(entity, tag);
		entity.noPhysics = snapshot.noPhysics();
		if (keptNoAi != null && entity instanceof Mob mob) {
			mob.setNoAi(keptNoAi);
		}
		TimeStopState.repinOrReleaseOnServer(entity);
		sync(level, entity, oldEffects);
	}

	private static void loadKeepingSummonedStand(Entity entity, CompoundTag tag) {
		LivingEntity user = entity instanceof LivingEntity living ? living : null;
		StandPower previous = user != null ? StandPower.get(user) : null;
		SummonedStand summoned = previous != null ? previous.getSummonedStand() : null;
		StandType type = previous != null ? previous.getPowerType() : null;
		try {
			entity.load(tag);
		}
		finally {
			StandPower restored = user != null ? StandPower.get(user) : null;
			if (summoned != null && restored != previous) {
				if (restored != null && restored.getPowerType() == type) {
					restored.setSummonedStand(summoned);
				}
				else if (type != null) {
					type.forceUnsummon(user, previous);
				}
			}
		}
	}

	private static void sync(ServerLevel level, Entity entity, Set<Holder<MobEffect>> oldEffects) {
		for (Holder<MobEffect> effect : oldEffects) {
			level.getChunkSource().broadcastAndSend(entity, new ClientboundRemoveMobEffectPacket(entity.getId(), effect));
		}
		level.getChunkSource().broadcastAndSend(entity, new ClientboundTeleportEntityPacket(entity));
		if (entity instanceof LivingEntity living) {
			for (var effect : living.getActiveEffectsMap().values()) {
				level.getChunkSource().broadcastAndSend(entity, new ClientboundUpdateMobEffectPacket(entity.getId(), effect, true));
			}
			List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
			for (EquipmentSlot slot : EnumSet.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET,
					EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
				equipment.add(Pair.of(slot, living.getItemBySlot(slot).copy()));
			}
			level.getChunkSource().broadcastAndSend(entity, new ClientboundSetEquipmentPacket(entity.getId(), equipment));
		}
		if (entity instanceof ServerPlayer player) {
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
			player.inventoryMenu.broadcastFullState();
			player.containerMenu.broadcastFullState();
			player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
			player.resetSentInfo();
		}
	}
}

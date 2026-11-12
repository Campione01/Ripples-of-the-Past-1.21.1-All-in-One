package rotp.core.config.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.entityattachment.SynchronizablePlayerData;
import rotp.core.init.ModDataAttachmentTypes;

import rotp.core.network.c2s.ClBroadcastedModSettingsPacket;
import rotp.core.network.s2c.TrPlayerModSettingsPacket;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlayerClientBroadcastedSettings implements SynchronizablePlayerData, INBTSerializable<CompoundTag> {
	public static final float NO_COOLDOWN_TIME_STOP_STAMINA_COST_MULTIPLIER = 3.0F;
	public static final float NO_COOLDOWN_TIME_STOP_STAMINA_REGEN_MULTIPLIER = 0.2F;
	private static final Map<Integer, PlayerClientBroadcastedSettings> REMOTE_SETTINGS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> NORMAL_COOLDOWN_VIEW = ThreadLocal.withInitial(() -> Boolean.FALSE);

	@Nullable private final transient Player owner;
	public HumanoidArm standSide = HumanoidArm.RIGHT;
	public boolean vampireGlowingEyes = true;
	public boolean noStandAbilityCooldown = false;
	public boolean standAttackTargetLock = false;
	public boolean standAimAssist = true;
	public boolean standBreaksBlocks = true;
	public boolean standPicksUpItems = true;
	public boolean standMovesBeyondEffectiveRange = true;

	public PlayerClientBroadcastedSettings() {
		this.owner = null;
	}

	public PlayerClientBroadcastedSettings(Player owner) {
		this.owner = owner;
		addSynchronization(owner);
	}


	public void toBuf(FriendlyByteBuf buf) {
		buf.writeEnum(standSide);
		buf.writeBoolean(vampireGlowingEyes);
		buf.writeBoolean(noStandAbilityCooldown);
		buf.writeBoolean(standAttackTargetLock);
		buf.writeBoolean(standAimAssist);
		buf.writeBoolean(standBreaksBlocks);
		buf.writeBoolean(standPicksUpItems);
		buf.writeBoolean(standMovesBeyondEffectiveRange);
	}

	public void fromBuf(FriendlyByteBuf buf) {
		standSide = buf.readEnum(HumanoidArm.class);
		vampireGlowingEyes = buf.readBoolean();
		noStandAbilityCooldown = buf.readBoolean();
		standAttackTargetLock = buf.readBoolean();
		standAimAssist = buf.readBoolean();
		standBreaksBlocks = buf.readBoolean();
		standPicksUpItems = buf.readBoolean();
		standMovesBeyondEffectiveRange = buf.readBoolean();
	}

	public PlayerClientBroadcastedSettings copy() {
		PlayerClientBroadcastedSettings copy = new PlayerClientBroadcastedSettings();
		copy.copyFrom(this);
		return copy;
	}

	public void copyFrom(PlayerClientBroadcastedSettings other) {
		this.standSide = other.standSide;
		this.vampireGlowingEyes = other.vampireGlowingEyes;
		this.noStandAbilityCooldown = other.noStandAbilityCooldown;
		this.standAttackTargetLock = other.standAttackTargetLock;
		this.standAimAssist = other.standAimAssist;
		this.standBreaksBlocks = other.standBreaksBlocks;
		this.standPicksUpItems = other.standPicksUpItems;
		this.standMovesBeyondEffectiveRange = other.standMovesBeyondEffectiveRange;
	}


	public void broadcastToServer() {
		PacketDistributor.sendToServer(new ClBroadcastedModSettingsPacket(this.copy()));
	}

	public void syncToAll(Player player) {
		PacketDistributor.sendToPlayersTrackingEntity(player, new TrPlayerModSettingsPacket(player.getId(), this.copy()));
	}

	public void syncToTracking(Player player, ServerPlayer tracking) {
		PacketDistributor.sendToPlayer(tracking, new TrPlayerModSettingsPacket(player.getId(), this.copy()));
	}

	@Override
	public void syncToPlayer(ServerPlayer entityAsPlayer) {
		if (owner != null) {
			syncToTracking(owner, entityAsPlayer);
		}
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (owner != null) {
			syncToTracking(owner, trackingPlayer);
		}
	}

	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		PlayerClientBroadcastedSettings newSettings = getServerStored(newPlayer)
				.orElseGet(() -> newPlayer.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()));
		newSettings.copyFrom(this);
	}

	@Override
	public CompoundTag serializeNBT(Provider provider) {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("StandSide", standSide.getSerializedName());
		nbt.putBoolean("VampireGlowingEyes", vampireGlowingEyes);
		nbt.putBoolean("NoStandAbilityCooldown", noStandAbilityCooldown);
		nbt.putBoolean("StandAttackTargetLock", standAttackTargetLock);
		nbt.putBoolean("StandAimAssist", standAimAssist);
		nbt.putBoolean("StandBreaksBlocks", standBreaksBlocks);
		nbt.putBoolean("StandPicksUpItems", standPicksUpItems);
		nbt.putBoolean("StandMovesBeyondEffectiveRange", standMovesBeyondEffectiveRange);
		return nbt;
	}

	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		standSide = HumanoidArm.LEFT.getSerializedName().equals(nbt.getString("StandSide")) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		vampireGlowingEyes = nbt.getBoolean("VampireGlowingEyes");
		noStandAbilityCooldown = nbt.getBoolean("NoStandAbilityCooldown");
		standAttackTargetLock = nbt.getBoolean("StandAttackTargetLock");
		// on unless a save says otherwise: the setting came after saves that lack it
		standAimAssist = !nbt.contains("StandAimAssist") || nbt.getBoolean("StandAimAssist");
		standBreaksBlocks = !nbt.contains("StandBreaksBlocks") || nbt.getBoolean("StandBreaksBlocks");
		standPicksUpItems = !nbt.contains("StandPicksUpItems") || nbt.getBoolean("StandPicksUpItems");
		standMovesBeyondEffectiveRange = !nbt.contains("StandMovesBeyondEffectiveRange") || nbt.getBoolean("StandMovesBeyondEffectiveRange");
	}

	public static boolean isStandAttackTargetLockEnabled(StandPower standPower) {
		if (standPower == null || !standPower.hasPower()) {
			return false;
		}
		if (standPower.getUser() instanceof Player player) {
			return getPlayerSettings(player).map(settings -> settings.standAttackTargetLock).orElse(false);
		}
		return true;
	}

	/** The "aim assist" Stand setting of a player Stand user; a mob always has the assist. */
	public static boolean isStandAimAssistEnabled(@Nullable LivingEntity user) {
		if (user instanceof Player player) {
			return getPlayerSettings(player).map(settings -> settings.standAimAssist).orElse(true);
		}
		return true;
	}

	/** A player Stand user's toggle in the JoJo menu; a mob's Stand keeps every toggle on. */
	public static boolean isStandToggleOn(StandToggle toggle, @Nullable LivingEntity user) {
		if (user instanceof Player player) {
			return getPlayerSettings(player).map(toggle::isOn).orElse(true);
		}
		return true;
	}

	public static boolean isNoStandAbilityCooldownEnabled(StandPower standPower) {
		if (standPower == null || !standPower.hasPower() || NORMAL_COOLDOWN_VIEW.get()) {
			return false;
		}
		if (standPower.getUser() instanceof Player player) {
			return getPlayerSettings(player).map(settings -> settings.noStandAbilityCooldown).orElse(false);
		}
		return false;
	}

	/**
	 * Answers the query as the normal mode would, with every no-cooldown setting read as off: the cooldown an ability
	 * would set, which the no-cooldown mode's fatigue is derived from (Ability#getNormalCooldown).
	 */
	@ApiStatus.Internal
	public static int inNormalCooldownMode(IntSupplier query) {
		if (NORMAL_COOLDOWN_VIEW.get()) {
			return query.getAsInt();
		}
		NORMAL_COOLDOWN_VIEW.set(Boolean.TRUE);
		try {
			return query.getAsInt();
		}
		finally {
			NORMAL_COOLDOWN_VIEW.set(Boolean.FALSE);
		}
	}

	public static float getTimeStopStaminaCostMultiplier(StandPower standPower) {
		return isNoStandAbilityCooldownEnabled(standPower) ? NO_COOLDOWN_TIME_STOP_STAMINA_COST_MULTIPLIER : 1.0F;
	}

	public static float getTimeStopStaminaRegenMultiplier(StandPower standPower) {
		return isNoStandAbilityCooldownEnabled(standPower) ? NO_COOLDOWN_TIME_STOP_STAMINA_REGEN_MULTIPLIER : 1.0F;
	}

	public static Optional<PlayerClientBroadcastedSettings> getPlayerSettings(Player player) {
		if (player.level().isClientSide() && player.isLocalPlayer()) {
			return Optional.of(ClientModSettings.getSettingsReadOnly().broadcasted);
		}
		if (!player.level().isClientSide()) {
			return getServerStored(player);
		}
		return getRemoteSettings(player.getId());
	}

	public static Optional<PlayerClientBroadcastedSettings> getServerStored(Player player) {
		AttachmentType<PlayerClientBroadcastedSettings> type = ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get();
		return player.hasData(type) ? Optional.of(player.getData(type)) : Optional.empty();
	}

	public static Optional<PlayerClientBroadcastedSettings> getRemoteSettings(int entityId) {
		return Optional.ofNullable(REMOTE_SETTINGS.get(entityId));
	}

	public static void putRemoteSettings(int entityId, PlayerClientBroadcastedSettings settings) {
		REMOTE_SETTINGS.put(entityId, settings);
	}
}

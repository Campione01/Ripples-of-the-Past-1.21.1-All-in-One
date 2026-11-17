package rotp.addons.justice.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rotp.addons.justice.JusticeDisguise;

@Mixin(Villager.class)
abstract class JusticeVillagerPriceMixin {
	@Inject(method = "startTrading(Lnet/minecraft/world/entity/player/Player;)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/npc/Villager;setTradingPlayer(Lnet/minecraft/world/entity/player/Player;)V",
			shift = At.Shift.AFTER), require = 1)
	private void justice$priceCurrentSession(Player customer, CallbackInfo ci) {
		JusticeDisguise.beginTrade((Villager) (Object) this, customer);
	}

	@Inject(method = "setTradingPlayer(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), require = 1)
	private void justice$leaveOldSession(Player incoming, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (villager.getTradingPlayer() != incoming) JusticeDisguise.endTrade(villager);
	}

	@WrapMethod(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", require = 1)
	private void justice$saveWithoutSessionDiscount(CompoundTag tag, Operation<Void> original) {
		JusticeDisguise.saveWithoutTradeDiscount((Villager) (Object) this, () -> original.call(tag));
	}
}

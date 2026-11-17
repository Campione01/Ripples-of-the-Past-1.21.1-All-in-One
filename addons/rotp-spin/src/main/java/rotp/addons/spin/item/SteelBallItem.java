package rotp.addons.spin.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SteelBallEntity;
import rotp.addons.spin.power.SpinCharge;
import rotp.addons.spin.power.SpinData;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import net.minecraft.ChatFormatting;

public class SteelBallItem extends Item {
    private static final String DAMAGED_KEY = "Damaged";

    public SteelBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server && !throwFromHand(server, player, hand, 0)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Nullable
    public static InteractionHand handWithBall(Player player, boolean plainOnly) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (plainOnly ? stack.is(SpinAddon.STEEL_BALL.get()) : stack.getItem() instanceof SteelBallItem) {
                return hand;
            }
        }
        return null;
    }

    public static boolean throwFromHand(ServerLevel level, Player player, InteractionHand hand, double charge) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof SteelBallItem) || held.isEmpty()) {
            return false;
        }
        if (!throwBall(level, player, held.copyWithCount(1), false, charge)) {
            return false;
        }
        held.consume(1, player);
        return true;
    }

    /** Spawns a copy; the caller owns the single inventory or holster debit. */
    public static boolean throwBall(ServerLevel level, Player player, ItemStack stack, boolean fromHolster, double charge) {
        if (!player.isAlive() || player.isSpectator() || player.level() != level
                || stack.isEmpty() || !(stack.getItem() instanceof SteelBallItem)
                || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        float cost = SpinConfig.BALL_SPIN_COST.get().floatValue();
        boolean hasSpin = SpinData.of(player).isPresent();
        boolean spinning = hasSpin && (player.hasInfiniteMaterials() || SpinData.hasEnergy(player, cost));
        SteelBallEntity ball = new SteelBallEntity(level, player, stack.copyWithCount(1), spinning);
        ball.setFromHolster(fromHolster);
        if (player.hasInfiniteMaterials()) {
            ball.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        if (spinning && StandManualInput.isShiftHeld(player) && SpinConfig.ROPE_ENABLED.get()) {
            ball.makeRope();
        }
        float speed = (spinning ? SpinConfig.BALL_SPIN_VELOCITY.get() : SpinConfig.BALL_PLAIN_VELOCITY.get()).floatValue();
        if (spinning && charge > 0) {
            speed *= (float) SpinCharge.multiplier(charge, SpinConfig.CHARGE_MAX_VELOCITY_MULTIPLIER.get());
            ball.setBaseDamage(ball.getBaseDamage() * SpinCharge.multiplier(charge, SpinConfig.CHARGE_MAX_DAMAGE_MULTIPLIER.get()));
        }
        ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, speed, SpinConfig.BALL_INACCURACY.get().floatValue());
        if (!level.addFreshEntity(ball)) {
            return false;
        }
        if (spinning) {
            SpinData.consumeEnergy(player, cost);
            ball.onSpinThrow(player);
        }
        else if (hasSpin) {
            player.displayClientMessage(Component.translatable("rotp_spin.message.not_enough_spin",
                    (int) SpinData.energy(player), (int) cost).withStyle(ChatFormatting.RED), true);
        }
        level.playSound(null, player, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1, 1.2F);
        player.getCooldowns().addCooldown(stack.getItem(), SpinConfig.BALL_COOLDOWN_TICKS.get());
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        if (isChipped(stack)) {
            tooltip.add(Component.translatable("item.rotp_spin.steel_ball.damaged").withStyle(ChatFormatting.RED));
            Item repairMaterial = SteelBallRepair.repairMaterial();
            if (repairMaterial != null) {
                tooltip.add(Component.translatable("item.rotp_spin.steel_ball.repair_hint",
                        repairMaterial.getDescription()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        else {
            tooltip.add(Component.translatable("item.rotp_spin.steel_ball.desc").withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean isChipped(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(DAMAGED_KEY);
    }

    public static void setChipped(ItemStack stack, boolean chipped) {
        if (chipped || isChipped(stack)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                if (chipped) {
                    tag.putBoolean(DAMAGED_KEY, true);
                }
                else {
                    tag.remove(DAMAGED_KEY);
                }
            });
        }
    }
}

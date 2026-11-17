package rotp.addons.spin.equipment;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.ability.SpinBallThrowAbility;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SteelBallEntity;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.power.SpinData;
import rotp.addons.spin.power.SpinSail;

public final class SpinEquipment {
    private static final TagKey<Item> SAILS = TagKey.create(Registries.ITEM, SpinAddon.id("spin_sails"));
    private static final String SAILING = "rotp_spin_sailing";

    private SpinEquipment() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SpinEquipment::registerPayloads);
        NeoForge.EVENT_BUS.addListener(SpinEquipment::tickServerSail);
        SteelBallEntity.setReturnStorage(SpinEquipment::storeReturningBall);
        SpinBallThrowAbility.setHolsterThrow(new SpinBallThrowAbility.HolsterThrow() {
            @Override
            public boolean hasBall(Player player) {
                return !findHolster(player, stack -> GyrosHolsterItem.getBallCount(stack) > 0).isEmpty();
            }

            @Override
            public boolean throwBall(ServerPlayer player) {
                return throwFromHolster(player);
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(HolsterThrowPayload.TYPE, HolsterThrowPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) throwFromHolster(player);
        });
    }

    public record HolsterThrowPayload() implements CustomPacketPayload {
        static final HolsterThrowPayload INSTANCE = new HolsterThrowPayload();
        static final Type<HolsterThrowPayload> TYPE = new Type<>(SpinAddon.id("holster_throw"));
        static final StreamCodec<RegistryFriendlyByteBuf, HolsterThrowPayload> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<HolsterThrowPayload> type() {
            return TYPE;
        }
    }

    public static boolean throwFromHolster(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.getCooldowns().isOnCooldown(SpinAddon.STEEL_BALL.get())) {
            return false;
        }
        ItemStack holster = findHolster(player, stack -> GyrosHolsterItem.getBallCount(stack) > 0);
        if (holster.isEmpty()) {
            player.displayClientMessage(Component.translatable("rotp_spin.message.holster_empty").withStyle(ChatFormatting.RED), true);
            return false;
        }
        List<ItemStack> balls = GyrosHolsterItem.contents(holster);
        ItemStack ball = balls.getLast();
        if (!SteelBallItem.throwBall(player.serverLevel(), player, ball, true, 0.0)) return false;
        if (!player.hasInfiniteMaterials()) {
            balls.removeLast();
            GyrosHolsterItem.setContents(holster, balls);
            player.getInventory().setChanged();
        }
        return true;
    }

    public static boolean storeReturningBall(Player player, ItemStack ball) {
        ItemStack holster = findHolster(player, GyrosHolsterItem::hasSpace);
        boolean stored = !holster.isEmpty() && GyrosHolsterItem.insertBall(holster, ball);
        if (stored) player.getInventory().setChanged();
        return stored;
    }

    static ItemStack findHolster(Player player, Predicate<ItemStack> filter) {
        Predicate<ItemStack> accepts = stack -> stack.getItem() instanceof GyrosHolsterItem && filter.test(stack);
        ItemStack worn = findCurio(player, accepts);
        if (!worn.isEmpty()) return worn;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (accepts.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findCurio(Player player, Predicate<ItemStack> filter) {
        return ModList.get().isLoaded("curios") && SpinConfig.COMPAT_CURIOS_ENABLED.get()
                ? SpinCurios.find(player, filter) : ItemStack.EMPTY;
    }

    static boolean canSail(Player player) {
        return SpinConfig.SAIL_ENABLED.get() && !player.onGround() && !player.isFallFlying()
                && !player.getAbilities().flying && !player.isInWater() && !player.isInLava()
                && !player.isPassenger() && !player.isSpectator() && SteelBallItem.handWithBall(player, false) != null
                && SpinData.of(player).isPresent()
                && (player.getItemBySlot(EquipmentSlot.CHEST).is(SAILS)
                        || !findCurio(player, stack -> stack.is(SAILS)).isEmpty());
    }

    static boolean shouldSail(Player player, boolean sailing) {
        return canSail(player) && SpinSail.shouldStart(sailing, player.fallDistance, SpinConfig.SAIL_MIN_FALL_DISTANCE.get())
                && (player.hasInfiniteMaterials() || SpinSail.hasEnergy(sailing, SpinData.energy(player),
                        SpinConfig.SAIL_COST_PER_TICK.get(), SpinConfig.SAIL_START_ENERGY.get()));
    }

    private static void tickServerSail(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean wasSailing = player.getPersistentData().getBoolean(SAILING);
        boolean sailing = shouldSail(player, wasSailing)
                && SpinData.consumeEnergy(player, SpinConfig.SAIL_COST_PER_TICK.get().floatValue());
        if (sailing) player.fallDistance = 0.0F;
        if (sailing != wasSailing) player.getPersistentData().putBoolean(SAILING, sailing);
    }
}

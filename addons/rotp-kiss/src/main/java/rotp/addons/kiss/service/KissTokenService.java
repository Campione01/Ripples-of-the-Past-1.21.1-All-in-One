package rotp.addons.kiss.service;

import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;

public final class KissTokenService {
    public static final String ENTITY = "KissEntity";
    public static final String BLOCK_X = "KissBlockX";
    public static final String BLOCK_Y = "KissBlockY";
    public static final String BLOCK_Z = "KissBlockZ";
    public static final String BLOCK_FACE = "KissBlockFace";
    // 1.16 StickerEntity.mobHead named the head with a Chinese literal; zh_cn keeps that text
    public static final String MOB_HEAD_NAME = "item.kiss.mob_head";

    private KissTokenService() {}

    // 1.0.2a StickerEntity.grantEntity/grantBlock and KissFinisherAttack: no second item for a target the user
    // already carries one for
    public static void grantEntityToken(
            ServerPlayer owner, LivingEntity target) {
        if (carriesToken(owner, token -> target.getUUID().equals(token.entityUuid()))) {
            return;
        }
        ItemStack token = headFor(target);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                token,
                tag -> tag.putUUID(ENTITY, target.getUUID()));
        give(owner, token);
    }

    public static void grantBlockToken(
            ServerPlayer owner, BlockPos position, Direction face) {
        if (carriesToken(owner, token -> position.equals(token.blockPos()))) {
            return;
        }
        ItemStack token = new ItemStack(
                owner.level().getBlockState(position).getBlock().asItem());
        if (token.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, token, tag -> {
            tag.putInt(BLOCK_X, position.getX());
            tag.putInt(BLOCK_Y, position.getY());
            tag.putInt(BLOCK_Z, position.getZ());
            tag.putInt(BLOCK_FACE, face.get3DDataValue());
        });
        give(owner, token);
    }

    public static Token heldToken(LivingEntity owner) {
        ItemStack offhand = owner.getOffhandItem();
        Token token = parse(offhand);
        return token != null ? token : parse(owner.getMainHandItem());
    }

    public static boolean carriesToken(Player owner, Predicate<Token> matches) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            Token token = parse(owner.getInventory().getItem(slot));
            if (token != null && matches.test(token)) {
                return true;
            }
        }
        return false;
    }

    public static void removeBlockTokens(ServerPlayer owner, BlockPos position) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            Token token = parse(stack);
            if (token != null && position.equals(token.blockPos())) {
                stack.shrink(1);
            }
        }
    }

    public static void clearTokens(ServerPlayer owner) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            if (parse(stack) != null) {
                stack.setCount(0);
            }
        }
    }

    private static Token parse(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        if (tag.hasUUID(ENTITY)) {
            return new Token(stack, tag.getUUID(ENTITY), null, Direction.UP);
        }
        if (tag.contains(BLOCK_X)
                && tag.contains(BLOCK_Y)
                && tag.contains(BLOCK_Z)) {
            return new Token(
                    stack,
                    null,
                    new BlockPos(
                            tag.getInt(BLOCK_X),
                            tag.getInt(BLOCK_Y),
                            tag.getInt(BLOCK_Z)),
                    Direction.from3DDataValue(tag.getInt(BLOCK_FACE)));
        }
        return null;
    }

    private static ItemStack headFor(LivingEntity target) {
        ItemStack stack = new ItemStack(
                target instanceof WitherSkeleton
                        ? Items.WITHER_SKELETON_SKULL
                        : target instanceof Skeleton
                                ? Items.SKELETON_SKULL
                                : target instanceof Creeper
                                        ? Items.CREEPER_HEAD
                                        : target instanceof Zombie
                                                ? Items.ZOMBIE_HEAD
                                                : Items.PLAYER_HEAD);
        if (!(target instanceof Player)) {
            stack.set(
                    DataComponents.CUSTOM_NAME,
                    Component.translatable(MOB_HEAD_NAME, target.getName().getString()));
        } else {
            stack.set(
                    DataComponents.PROFILE,
                    new ResolvableProfile(((Player) target).getGameProfile()));
        }
        return stack;
    }

    private static void give(ServerPlayer owner, ItemStack token) {
        if (!owner.addItem(token)) {
            owner.drop(token, false);
        }
    }

    public record Token(
            ItemStack stack,
            UUID entityUuid,
            BlockPos blockPos,
            Direction face) {
        public boolean isEntity() {
            return entityUuid != null;
        }

        // 1.0.2a StickerEntity.consumeStickerItem empties the sticker item's whole slot
        public void consume() {
            stack.setCount(0);
        }
    }
}

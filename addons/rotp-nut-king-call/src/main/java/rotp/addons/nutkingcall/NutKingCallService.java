package rotp.addons.nutkingcall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.util.functions.JojoModUtil;
import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import rotp.addons.nutkingcall.entity.NutKingCallAttachedNailEntity;
import rotp.addons.nutkingcall.state.NailState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The server-side effects of 1.16's Nut King Call actions. */
public final class NutKingCallService {
    private static final Random RANDOM = new Random();
    public static final float ASSEMBLE_FAIL_CHANCE = 0.1F;
    public static final float DISASSEMBLE_LOST_CHANCE = 0.15F;
    public static final double DISMANTLE_RADIUS = 8.0D;
    public static final double UNPIN_SEARCH_RANGE = 32.0D;
    private static final String QUICK_ASSEMBLY_KEY = "action.nutkingcall.nut_king_call_quick_assembly.";

    private NutKingCallService() {}

    /**
     * The crafting recipes in id order. 1.16 walked its hash-ordered recipe map and 1.21's manager has another order;
     * a fixed order makes "the first recipe that makes this item" the same on every start.
     */
    public static List<RecipeHolder<?>> craftingRecipesById(Level level) {
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof CraftingRecipe crafting && !crafting.isSpecial()) {
                recipes.add(holder);
            }
        }
        recipes.sort(Comparator.comparing(holder -> holder.id().toString()));
        return recipes;
    }

    /** The core's block-breaking rule (game rule, protection events, the user's right to interact). */
    public static boolean canBreakBlock(Level level, BlockPos pos, LivingEntity breaker) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || JojoModUtil.canEntityDestroy(serverLevel, pos, state, breaker);
    }

    // ---- Quick Assembly / Disassembly (1.16 NutKingCallQuickAssemblyAction) ----

    /** A random craftable recipe, crafted from the inventory with a 10% failure and the result's tool worn 10-30%. */
    public static void assemble(Player player, Level level) {
        List<CraftingRecipe> craftable = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof CraftingRecipe crafting && !crafting.isSpecial()
                    && canCraft(player, crafting, level)) {
                craftable.add(crafting);
            }
        }
        if (craftable.isEmpty()) {
            notifyPlayer(player, "no_material");
            return;
        }
        CraftingRecipe chosen = craftable.get(RANDOM.nextInt(craftable.size()));
        if (RANDOM.nextFloat() < ASSEMBLE_FAIL_CHANCE) {
            notifyPlayer(player, "assemble_fail");
            return;
        }
        consumeIngredients(player, chosen);
        ItemStack result = chosen.getResultItem(level.registryAccess());
        if (!result.isEmpty()) {
            ItemStack give = result.copy();
            if (give.isDamageableItem()) {
                int maxDurability = give.getMaxDamage();
                float damageRatio = 0.1F + RANDOM.nextFloat() * 0.2F;
                int damage = (int) (maxDurability * damageRatio);
                give.setDamageValue(Math.min(damage, maxDurability - 1));
            }
            if (!player.getInventory().add(give)) {
                player.drop(give, false);
            }
            notifyPlayer(player, "assemble_success");
        }
    }

    /** A random single item with a reversible recipe is taken apart; each ingredient may be lost, more so when worn. */
    public static void disassemble(Player player, Level level) {
        Inventory inventory = player.getInventory();
        List<Integer> candidateSlots = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && canDisassemble(stack, level)) {
                candidateSlots.add(i);
            }
        }
        if (candidateSlots.isEmpty()) {
            notifyPlayer(player, "no_disassemble");
            return;
        }
        int slot = candidateSlots.get(RANDOM.nextInt(candidateSlots.size()));
        ItemStack chosenStack = inventory.getItem(slot);
        for (RecipeHolder<?> holder : craftingRecipesById(level)) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) {
                continue;
            }
            ItemStack result = crafting.getResultItem(level.registryAccess());
            if (result.isEmpty() || !ItemStack.isSameItemSameComponents(result, chosenStack)) {
                continue;
            }
            List<Ingredient> ingredients = crafting.getIngredients();
            if (!isReversibleRecipe(ingredients)) {
                continue;
            }
            float durabilityFactor = 1.0F;
            if (chosenStack.isDamageableItem()) {
                int max = chosenStack.getMaxDamage();
                int damage = chosenStack.getDamageValue();
                durabilityFactor = max > 0 ? (float) (max - damage) / (float) max : 1.0F;
            }
            chosenStack.shrink(1);
            if (chosenStack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            int lost = 0;
            for (Ingredient ingredient : ingredients) {
                if (ingredient.hasNoItems()) {
                    continue;
                }
                ItemStack[] matching = ingredient.getItems();
                if (matching.length == 0) {
                    continue;
                }
                float lostChance = DISASSEMBLE_LOST_CHANCE + (1.0F - durabilityFactor) * 0.5F;
                if (RANDOM.nextFloat() < lostChance) {
                    lost++;
                }
                else {
                    ItemStack give = matching[0].copy();
                    give.setCount(1);
                    if (!inventory.add(give)) {
                        player.drop(give, false);
                    }
                }
            }
            notifyPlayer(player, lost > 0 ? "disassemble_lost" : "disassemble_success");
            return;
        }
    }

    private static void notifyPlayer(Player player, String messageKey) {
        player.displayClientMessage(Component.translatable(QUICK_ASSEMBLY_KEY + messageKey), true);
    }

    private static boolean canDisassemble(ItemStack stack, Level level) {
        if (stack.isEmpty() || stack.getCount() > 1) {
            return false;
        }
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof CraftingRecipe crafting && !crafting.isSpecial()) {
                ItemStack result = crafting.getResultItem(level.registryAccess());
                if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, stack)
                        && isReversibleRecipe(crafting.getIngredients())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isReversibleRecipe(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.hasNoItems()) {
                return true;
            }
        }
        return false;
    }

    // 1.16: every ingredient finds a stack with a unit left, and the recipe never makes more than it takes
    private static boolean canCraft(Player player, CraftingRecipe recipe, Level level) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        int totalIngredients = 0;
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.hasNoItems()) {
                totalIngredients++;
            }
        }
        if (result.getCount() > totalIngredients) {
            return false;
        }
        Inventory inventory = player.getInventory();
        Map<Integer, Integer> available = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                available.put(i, stack.getCount());
            }
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient.hasNoItems()) {
                continue;
            }
            boolean found = false;
            for (Map.Entry<Integer, Integer> entry : available.entrySet()) {
                if (entry.getValue() > 0) {
                    ItemStack stack = inventory.getItem(entry.getKey());
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        entry.setValue(entry.getValue() - 1);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static void consumeIngredients(Player player, CraftingRecipe recipe) {
        Inventory inventory = player.getInventory();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.hasNoItems()) {
                continue;
            }
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    break;
                }
            }
        }
    }

    // ---- Remove Nails (1.16 NutKingCallDismantleAction) ----

    public static void dismantle(Level level, LivingEntity user) {
        Vec3 center = user.position();
        AABB area = new AABB(center, center).inflate(DISMANTLE_RADIUS);
        removeAttachedNails(level, user, area);
        removePinnedBlocks(level, user, area);
        removeNailBlocks(level, user, area);
    }

    private static void removeAttachedNails(Level level, LivingEntity user, AABB area) {
        for (Entity entity : level.getEntities(user, area)) {
            if (entity instanceof NutKingCallAttachedNailEntity nail && !nail.isPinned()) {
                if (entity.getVehicle() instanceof LivingEntity target) {
                    NailState state = NailState.getExisting(target);
                    if (state != null) {
                        state.removeNail();
                        state.removeNut();
                    }
                }
                entity.discard();
            }
        }
    }

    // 1.16 left the pinned entity's pin state as it was: it runs out by its own timer or owner distance
    private static void removePinnedBlocks(Level level, LivingEntity user, AABB area) {
        for (NutKingCallAttachedNailEntity nail : level.getEntitiesOfClass(NutKingCallAttachedNailEntity.class, area)) {
            if (nail.isPinned()) {
                BlockPos pinnedBlockPos = nail.getPinnedBlockPos();
                if (pinnedBlockPos != null) {
                    if (!canBreakBlock(level, pinnedBlockPos, user)) {
                        continue;
                    }
                    level.destroyBlock(pinnedBlockPos, true);
                }
                nail.discard();
            }
        }
    }

    private static void removeNailBlocks(Level level, LivingEntity user, AABB area) {
        BlockPos min = BlockPos.containing(area.minX, area.minY, area.minZ);
        BlockPos max = BlockPos.containing(area.maxX, area.maxY, area.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).getBlock() instanceof NutKingCallNailBlock) {
                NutKingCallNailBlock.dismantleNail(level, pos.immutable(), user);
            }
        }
    }

    // ---- Unpin Target (1.16 NutKingCallUnpinTargetAction) ----

    /** The nearest pinned living entity within 32 blocks whose pin is the user's or nobody's. */
    @Nullable
    public static LivingEntity findNearestPinnedTarget(LivingEntity user) {
        UUID ownerUUID = user.getUUID();
        AABB area = user.getBoundingBox().inflate(UNPIN_SEARCH_RANGE);
        List<LivingEntity> candidates = user.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != user && entity.isAlive() && entity != user.getVehicle());
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            NailState state = NailState.getExisting(candidate);
            if (state != null && state.isPinned()) {
                UUID pinOwner = state.getPinOwnerUUID();
                if (pinOwner == null || pinOwner.equals(ownerUUID)) {
                    double dist = candidate.distanceToSqr(user);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = candidate;
                    }
                }
            }
        }
        return nearest;
    }

    public static void unpinTarget(Level level, LivingEntity user) {
        LivingEntity target = findNearestPinnedTarget(user);
        if (target == null) {
            return;
        }
        NailState state = NailState.getExisting(target);
        if (state != null && state.isPinned()) {
            NutKingCallAttachedNailEntity.unpin(level, target, true);
            return;
        }
        boolean removedAny = false;
        for (NutKingCallAttachedNailEntity nail : level.getEntitiesOfClass(
                NutKingCallAttachedNailEntity.class, target.getBoundingBox().inflate(2.0D))) {
            if (nail.isPinned() && nail.getVehicle() == target) {
                BlockPos pinnedPos = nail.getPinnedBlockPos();
                if (pinnedPos != null) {
                    level.destroyBlock(pinnedPos, true);
                }
                nail.discard();
                removedAny = true;
            }
        }
        if (removedAny && state != null) {
            state.clearPinned();
        }
    }
}

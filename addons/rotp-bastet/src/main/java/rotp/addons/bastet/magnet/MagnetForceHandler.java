package rotp.addons.bastet.magnet;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandDamageContext;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 1.16 MagnetForceHandler: what a magnetized creature does to iron around it, and what it does to the creature. */
public final class MagnetForceHandler {
    public static final double BASE_RADIUS = 5.0D;
    public static final double MAX_RADIUS = 15.0D;
    public static final String ITEM_OWNER_TAG = "BastetMagnetOwner";
    public static final String ITEM_HURT_TAG = "BastetHurtOnce";
    private static final String IRON_BLOCK_HURT_PREFIX = "BastetIronBlockHurt_";
    private static final ResourceLocation BASE_ATTACK_DAMAGE_ID =
            ResourceLocation.withDefaultNamespace("base_attack_damage");
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Set<Item> IRON_ARMOR = Set.of(
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
    private static final Set<Item> SHARP_ITEMS = Set.of(
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.SHEARS);
    // raw iron, deepslate iron ore and the block of raw iron came after 1.16 (1.17) but are iron things all the same
    private static final Set<Item> DIRECT_IRON_ITEMS = Set.of(
            Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK, Items.IRON_ORE, Items.IRON_DOOR, Items.IRON_BARS,
            Items.RAW_IRON, Items.DEEPSLATE_IRON_ORE, Items.RAW_IRON_BLOCK,
            Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_HORSE_ARMOR,
            Items.MINECART, Items.BUCKET, Items.CAULDRON, Items.ANVIL, Items.CHIPPED_ANVIL, Items.DAMAGED_ANVIL,
            Items.HOPPER, Items.IRON_TRAPDOOR, Items.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Items.LIGHT_WEIGHTED_PRESSURE_PLATE, Items.SMITHING_TABLE, Items.STONECUTTER, Items.GRINDSTONE,
            Items.CROSSBOW, Items.SHIELD, Items.FLINT_AND_STEEL, Items.SHEARS, Items.RAIL, Items.DETECTOR_RAIL,
            Items.ACTIVATOR_RAIL, Items.POWERED_RAIL, Items.TRIPWIRE_HOOK, Items.CHAIN,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.LANTERN, Items.SOUL_LANTERN, Items.LODESTONE);
    // the 1.16 cauldron held its water in itself; 1.17 split the filled ones into blocks of their own
    private static final Set<Block> IRON_BLOCKS = Set.of(
            Blocks.IRON_BLOCK, Blocks.IRON_ORE, Blocks.IRON_BARS, Blocks.IRON_DOOR, Blocks.IRON_TRAPDOOR,
            Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK,
            Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL, Blocks.HOPPER,
            Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LAVA_CAULDRON, Blocks.POWDER_SNOW_CAULDRON,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL, Blocks.POWERED_RAIL,
            Blocks.STONECUTTER, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.CHAIN,
            Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.LODESTONE);

    // isIronItem depends only on the item and the recipes, so its answers hold until the recipes reload
    private static final Map<Item, Boolean> IRON_ITEM_CACHE = new IdentityHashMap<>();
    @Nullable
    private static RecipeManager cachedRecipes;

    private MagnetForceHandler() {}

    /** 5 blocks as the creature is magnetized, growing to 15 as the magnetization runs out. */
    public static double getRadius(LivingEntity entity) {
        return BASE_RADIUS + (MAX_RADIUS - BASE_RADIUS) * MagnetizedData.getProgress(entity);
    }

    /** 1.16: a player with a Stand; no other creature counts. */
    public static boolean isStandUser(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        StandPower power = StandPower.get(player);
        return power != null && power.hasPower();
    }

    public static boolean isMagnetDisabled(LivingEntity entity) {
        return isStandUser(entity);
    }

    public static void applyMagnetForce(LivingEntity entity) {
        if (isMagnetDisabled(entity)) {
            return;
        }
        Level level = entity.level();
        Vec3 pos = entity.position();
        double radius = getRadius(entity);
        int attachedCount = pullIronItems(entity, level, pos, radius);
        pullTowardIronBlocks(entity, level, pos, radius);
        pullOtherMagnetizedEntities(entity, level, pos, radius);
        pullIronArmorPlayers(entity, level, pos, radius);
        applySlowdown(entity, attachedCount);
        applyRotationResistance(entity, attachedCount);
        applyMagnetSlowdown(entity, level, pos, radius);
        tryEscape(entity, attachedCount);
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.85D, 1.0D, 0.85D));
    }

    private static AABB around(Vec3 pos, double radius) {
        return new AABB(pos, pos).inflate(radius);
    }

    private static void pullIronArmorPlayers(LivingEntity entity, Level level, Vec3 pos, double radius) {
        for (Player player : level.getEntitiesOfClass(Player.class, around(pos, radius))) {
            if (player == entity || !hasIronArmor(player)) {
                continue;
            }
            Vec3 dir = player.position().subtract(pos);
            double dist = dir.length();
            if (dist < 0.001D || dist > radius) {
                continue;
            }
            if (!entity.getBoundingBox().inflate(0.05D).intersects(player.getBoundingBox())) {
                double scaled = 0.3D * (1.0D - dist / radius);
                entity.setDeltaMovement(entity.getDeltaMovement().add(dir.normalize().scale(scaled)));
                entity.hurtMarked = true;
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false, false));
            }
        }
    }

    private static boolean hasIronArmor(Player player) {
        return isIronArmorItem(player.getItemBySlot(EquipmentSlot.HEAD))
                || isIronArmorItem(player.getItemBySlot(EquipmentSlot.CHEST))
                || isIronArmorItem(player.getItemBySlot(EquipmentSlot.LEGS))
                || isIronArmorItem(player.getItemBySlot(EquipmentSlot.FEET));
    }

    /** Iron and chainmail armor. */
    public static boolean isIronArmorItem(ItemStack stack) {
        return !stack.isEmpty() && IRON_ARMOR.contains(stack.getItem());
    }

    private static void applyMagnetSlowdown(LivingEntity entity, Level level, Vec3 pos, double radius) {
        List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, around(pos, radius),
                other -> other != entity && MagnetizedData.isMagnetized(other) && !isStandUser(other));
        if (!others.isEmpty()) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 3, false, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 1, false, false, false));
        }
    }

    // 1.16: a mob held by iron jerks away from the nearest magnet once a second, more often the more iron holds it
    private static void tryEscape(LivingEntity entity, int attachedCount) {
        if (attachedCount <= 0 || !(entity instanceof Mob mob)) {
            return;
        }
        float ratio = Math.min(1.0F, attachedCount / 30.0F);
        if (mob.tickCount % 20 != 0 || RANDOM.nextFloat() > 0.3F + ratio * 0.5F) {
            return;
        }
        Vec3 away = findNearestMagnetDirection(mob);
        if (away != null) {
            double force = 0.2D + ratio * 0.3D;
            mob.setDeltaMovement(mob.getDeltaMovement().add(away.scale(force)));
            mob.hurtMarked = true;
            mob.getNavigation().stop();
        }
    }

    @Nullable
    private static Vec3 findNearestMagnetDirection(LivingEntity entity) {
        Level level = entity.level();
        Vec3 pos = entity.position();
        double radius = getRadius(entity);
        List<LivingEntity> others = level.getEntitiesOfClass(LivingEntity.class, around(pos, radius),
                other -> other != entity && MagnetizedData.isMagnetized(other) && !isStandUser(other));
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity other : others) {
            double dist = other.distanceToSqr(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        if (nearest != null) {
            Vec3 dir = pos.subtract(nearest.position());
            if (dir.length() > 0.001D) {
                return dir.normalize();
            }
        }
        BlockPos nearestBlock = findNearestIronBlock(level, pos, (int) radius, 2);
        if (nearestBlock != null) {
            Vec3 dir = pos.subtract(Vec3.atCenterOf(nearestBlock));
            if (dir.length() > 0.001D) {
                return dir.normalize();
            }
        }
        return null;
    }

    // 1.16: the cube of the given radius around pos, visited at the given step, nearest block centre first found wins
    @Nullable
    private static BlockPos findNearestIronBlock(Level level, Vec3 pos, int r, int step) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (int x = -r; x <= r; x += step) {
            for (int y = -r; y <= r; y += step) {
                for (int z = -r; z <= r; z += step) {
                    mutable.set(pos.x + x, pos.y + y, pos.z + z);
                    if (isIronBlock(level.getBlockState(mutable))) {
                        double dist = pos.distanceToSqr(
                                mutable.getX() + 0.5D, mutable.getY() + 0.5D, mutable.getZ() + 0.5D);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = mutable.immutable();
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /*
     * 1.16: every iron item within the radius that no other creature holds is drawn to the creature's middle at 0.2 a
     * tick, kept from falling, despawning and being picked up; once it touches the creature it stops there, and a
     * weapon, armor piece or sharp tool hurts the creature once. Returns how many items it holds or draws.
     */
    private static int pullIronItems(LivingEntity entity, Level level, Vec3 pos, double radius) {
        int attached = 0;
        UUID entityId = entity.getUUID();
        AABB hitbox = entity.getBoundingBox().inflate(0.3D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around(pos, radius))) {
            ItemStack stack = item.getItem();
            if (!isIronItem(stack, level)) {
                continue;
            }
            CompoundTag nbt = item.getPersistentData();
            UUID owner;
            if (nbt.hasUUID(ITEM_OWNER_TAG)) {
                owner = nbt.getUUID(ITEM_OWNER_TAG);
            }
            else {
                owner = entityId;
                nbt.putUUID(ITEM_OWNER_TAG, entityId);
            }
            if (!owner.equals(entityId)) {
                continue;
            }
            item.setNoGravity(true);
            item.lifespan = Integer.MAX_VALUE;
            item.setPickUpDelay(Integer.MAX_VALUE);
            Vec3 target = pos.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
            Vec3 dir = target.subtract(item.position());
            if (hitbox.intersects(item.getBoundingBox())) {
                item.setDeltaMovement(Vec3.ZERO);
                item.hurtMarked = true;
                attached += stack.getCount();
                if (!nbt.getBoolean(ITEM_HURT_TAG)) {
                    float damage = getItemDamage(stack, entity);
                    if (damage > 0.0F) {
                        try (var running = StandDamageContext.enter(MagnetizedData.bastetPowerNear(entity))) {
                            entity.hurt(entity.damageSources().generic(), damage);
                        }
                        applySharpEnchantEffects(stack, entity);
                        nbt.putBoolean(ITEM_HURT_TAG, true);
                    }
                }
            }
            else {
                item.setDeltaMovement(item.getDeltaMovement().add(dir.normalize().scale(0.2D)));
                item.hurtMarked = true;
                attached += stack.getCount();
            }
        }
        return attached;
    }

    /*
     * 1.16: a sword's or tool's attack damage (SwordItem/ToolItem.getAttackDamage, the item's base attack damage
     * modifier in 1.21: iron sword 5, axe 8, pickaxe 3, shovel 3.5, hoe 0), 1 for iron or chainmail armor, 2 for shears,
     * plus the sharp enchantments; nothing for anything else.
     */
    private static float getItemDamage(ItemStack stack, LivingEntity target) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        float base = 0.0F;
        Item item = stack.getItem();
        if (item instanceof SwordItem || item instanceof DiggerItem) {
            base = baseAttackDamage(item);
        }
        else if (isIronArmorItem(stack)) {
            base = 1.0F;
        }
        else if (isSharpItem(stack)) {
            base = 2.0F;
        }
        if (base <= 0.0F) {
            return 0.0F;
        }
        return base + getSharpEnchantDamage(stack, target);
    }

    private static float baseAttackDamage(Item item) {
        ItemAttributeModifiers modifiers =
                item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        float damage = 0.0F;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()
                    && BASE_ATTACK_DAMAGE_ID.equals(entry.modifier().id())) {
                damage += (float) entry.modifier().amount();
            }
        }
        return damage;
    }

    // 1.16: Sharpness 1 a level; Smite 2.5 a level on the undead, Bane of Arthropods 2.5 a level on arthropods
    private static float getSharpEnchantDamage(ItemStack stack, LivingEntity target) {
        Level level = target.level();
        float damage = getEnchantLevel(level, stack, Enchantments.SHARPNESS) * 1.0F;
        int smite = getEnchantLevel(level, stack, Enchantments.SMITE);
        if (smite > 0 && target.getType().is(EntityTypeTags.UNDEAD)) {
            damage += smite * 2.5F;
        }
        int bane = getEnchantLevel(level, stack, Enchantments.BANE_OF_ARTHROPODS);
        if (bane > 0 && target.getType().is(EntityTypeTags.ARTHROPOD)) {
            damage += bane * 2.5F;
        }
        return damage;
    }

    // 1.16: Fire Aspect sets the creature on fire for 4 s a level; Knockback pushes it on along its motion
    private static void applySharpEnchantEffects(ItemStack stack, LivingEntity target) {
        Level level = target.level();
        int fireAspect = getEnchantLevel(level, stack, Enchantments.FIRE_ASPECT);
        if (fireAspect > 0) {
            target.igniteForSeconds(fireAspect * 4);
        }
        int knockback = getEnchantLevel(level, stack, Enchantments.KNOCKBACK);
        if (knockback > 0) {
            Vec3 knockDir = target.position().subtract(target.xo, target.yo, target.zo);
            if (knockDir.lengthSqr() < 0.001D) {
                knockDir = new Vec3(0.0D, 0.0D, 1.0D);
            }
            knockDir = knockDir.normalize().scale(knockback * 0.5D);
            target.push(knockDir.x, 0.1D, knockDir.z);
            target.hurtMarked = true;
        }
    }

    public static int getEnchantLevel(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) {
            return 0;
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
                .map(stack::getEnchantmentLevel)
                .orElse(0);
    }

    public static Holder<Enchantment> enchantment(Level level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    // 1.16: the more iron holds the creature, the slower it gets; from 100 items on it stops moving sideways
    private static void applySlowdown(LivingEntity entity, int attachedCount) {
        if (attachedCount <= 0) {
            return;
        }
        int level = Math.min(6, (int) (attachedCount * 0.5F / 10.0F) + 1);
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, level - 1, false, false, false));
        if (level >= 6) {
            entity.setDeltaMovement(0.0D, entity.getDeltaMovement().y, 0.0D);
            entity.hurtMarked = true;
        }
    }

    // 1.16: the iron it holds makes the creature turn slower, down to a tenth of its turn at 100 items
    private static void applyRotationResistance(LivingEntity entity, int attachedCount) {
        if (attachedCount <= 0) {
            return;
        }
        float ratio = Math.min(1.0F, attachedCount / 100.0F);
        float resistance = 1.0F - ratio * 0.9F;
        float yawDiff = entity.getYRot() - entity.yRotO;
        float pitchDiff = entity.getXRot() - entity.xRotO;
        float newYaw = entity.yRotO + yawDiff * resistance;
        float newPitch = entity.xRotO + pitchDiff * resistance;
        entity.setYRot(newYaw);
        entity.setXRot(newPitch);
        entity.yRotO = newYaw;
        entity.xRotO = newPitch;
        entity.yHeadRot = newYaw;
        entity.yHeadRotO = newYaw;
        entity.yBodyRot = newYaw;
        entity.yBodyRotO = newYaw;
    }

    // 1.16: the nearest iron block within the radius draws the creature at 0.2 a tick and hurts it once for 5 as it gets there
    private static void pullTowardIronBlocks(LivingEntity entity, Level level, Vec3 pos, double radius) {
        BlockPos nearest = findNearestIronBlock(level, pos, (int) radius, 1);
        if (nearest == null) {
            return;
        }
        double nearestDist = pos.distanceToSqr(nearest.getX() + 0.5D, nearest.getY() + 0.5D, nearest.getZ() + 0.5D);
        if (nearestDist <= 1.0D) {
            CompoundTag nbt = entity.getPersistentData();
            String key = IRON_BLOCK_HURT_PREFIX + nearest.getX() + "_" + nearest.getY() + "_" + nearest.getZ();
            if (!nbt.getBoolean(key)) {
                try (var running = StandDamageContext.enter(MagnetizedData.bastetPowerNear(entity))) {
                    entity.hurt(entity.damageSources().generic(), 5.0F);
                }
                nbt.putBoolean(key, true);
            }
        }
        else {
            Vec3 dir = new Vec3(
                    nearest.getX() + 0.5D - pos.x,
                    nearest.getY() + 0.5D - pos.y,
                    nearest.getZ() + 0.5D - pos.z)
                    .normalize()
                    .scale(0.2D);
            entity.setDeltaMovement(entity.getDeltaMovement().add(dir));
            entity.hurtMarked = true;
        }
    }

    // 1.16: two magnetized creatures draw each other, harder the closer they are, and stick once they touch
    private static void pullOtherMagnetizedEntities(LivingEntity entity, Level level, Vec3 pos, double radius) {
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, around(pos, radius))) {
            if (other == entity || !MagnetizedData.isMagnetized(other) || isStandUser(other)) {
                continue;
            }
            Vec3 dir = other.position().subtract(pos);
            double dist = dir.length();
            if (dist < 0.001D || dist > radius) {
                continue;
            }
            if (entity.getBoundingBox().inflate(0.05D).intersects(other.getBoundingBox())) {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.3D, 1.0D, 0.3D));
                other.setDeltaMovement(other.getDeltaMovement().multiply(0.3D, 1.0D, 0.3D));
            }
            else {
                Vec3 unit = dir.normalize();
                double scaled = 0.25D * (1.0D - dist / radius);
                entity.setDeltaMovement(entity.getDeltaMovement().add(unit.scale(scaled)));
                entity.hurtMarked = true;
                other.setDeltaMovement(other.getDeltaMovement().add(unit.scale(-scaled)));
                other.hurtMarked = true;
            }
        }
    }

    /** 1.16: one of the listed iron things, or an item a crafting recipe makes from one (three recipes deep). */
    public static boolean isIronItem(ItemStack stack, @Nullable Level level) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isDirectIronItem(stack)) {
            return true;
        }
        if (level == null) {
            return false;
        }
        RecipeManager recipes = level.getRecipeManager();
        if (recipes != cachedRecipes) {
            IRON_ITEM_CACHE.clear();
            cachedRecipes = recipes;
        }
        return IRON_ITEM_CACHE.computeIfAbsent(stack.getItem(), item -> recipeContainsIron(stack, level, 0));
    }

    public static boolean isSharpItem(ItemStack stack) {
        return !stack.isEmpty() && SHARP_ITEMS.contains(stack.getItem());
    }

    private static boolean isDirectIronItem(ItemStack stack) {
        return DIRECT_IRON_ITEMS.contains(stack.getItem());
    }

    private static boolean recipeContainsIron(ItemStack stack, Level level, int depth) {
        if (depth > 3) {
            return false;
        }
        try {
            for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
                if (!(holder.value() instanceof CraftingRecipe crafting) || crafting.isSpecial()) {
                    continue;
                }
                ItemStack result = crafting.getResultItem(level.registryAccess());
                if (result.isEmpty() || !ItemStack.isSameItem(result, stack)) {
                    continue;
                }
                for (Ingredient ingredient : crafting.getIngredients()) {
                    if (ingredient.hasNoItems()) {
                        continue;
                    }
                    for (ItemStack match : ingredient.getItems()) {
                        if (isDirectIronItem(match) || recipeContainsIron(match, level, depth + 1)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean isIronBlock(BlockState state) {
        return IRON_BLOCKS.contains(state.getBlock());
    }
}

package rotp.addons.gratefuldead.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.powers.hamon.HamonUtil;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.item.IceCubeItem;
import rotp.addons.gratefuldead.state.AgingRemovalData;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.ItemAgingState;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class AgingService {
    public static final double AURA_RADIUS = 100.0D;
    // 0.1.13 TheGratefulDeadAgingAuraEffect.EXPAND_BLOCKS_PER_TICK
    public static final double AURA_EXPAND_PER_TICK = 0.05D;
    public static final int AURA_DIAMETER_CHUNKS = 13;
    public static final int AURA_CHUNK_COUNT =
            AURA_DIAMETER_CHUNKS * AURA_DIAMETER_CHUNKS;
    public static final int MAX_AURA_ENTITY_CANDIDATES = 32;
    public static final int MAX_AURA_PLAYER_CANDIDATES = 32;
    public static final int MAX_AURA_BLOCK_COLUMNS = 24;
    public static final int MAX_PLAYER_ITEM_SLOTS_PER_TICK = 4;
    public static final float TIME_STOP_DISABLE_PROGRESS = 0.8F;
    // 0.1.13 getSkillStaminaCostMultiplier and getHamonEfficiency: both
    // reach their end at 80% aging
    public static final float SKILL_STAMINA_FULL_PROGRESS = 0.8F;
    public static final float HAMON_ZERO_PROGRESS = 0.8F;
    public static final float TOUCH_BLOCK_DELTA = 1.0F / 60.0F;
    // 0.1.13 contact 0.0125 and aura speed / 4800 a tick on its 0..2 scale
    public static final float TOUCH_LIVING_DELTA = 1.0F / 160.0F;
    public static final float SELF_LIVING_DELTA = 1.0F / 160.0F;
    public static final float TOUCH_ITEM_DELTA = 1.0F / 25.0F;
    public static final float AURA_LIVING_DELTA =
            (float) AURA_CHUNK_COUNT / 9600.0F;
    public static final float AURA_ITEM_DELTA =
            (float) AURA_CHUNK_COUNT / 750.0F;
    public static final float AURA_BLOCK_DELTA =
            (float) (AURA_CHUNK_COUNT * 256)
                    / (MAX_AURA_BLOCK_COLUMNS * 2400.0F);
    private static final int[] SURFACE_OFFSETS = {
            0, -1, -2, -3, -4, -6, -8, -12, -16
    };

    private AgingService() {}

    public static long generation(
            MinecraftServer server, UUID owner) {
        return AgingRemovalData.get(server)
                .generation(owner);
    }

    public static void requestFade(
            MinecraftServer server, UUID owner) {
        AgingRemovalData.get(server).advance(owner);
    }

    /** 0.1.13: only where the aging rate bottoms out (frozen biomes) is it cold. */
    public static boolean isCold(
            Level level, BlockPos position) {
        return AgingRate.isCold(level, position);
    }

    /**
     * 0.1.13 getTotalAgingSpeedMultiplier at a place: the signed aging
     * speed (1 on the plains), 0 where aging recedes.
     */
    public static float biomeAgingMultiplier(
            ServerLevel level, BlockPos position) {
        return Math.max(0.0F, AgingRate.speed(level, position));
    }

    /** The aging speed of a living target, its own heat and ice included. */
    public static float livingAgingMultiplier(
            LivingEntity target) {
        return Math.max(0.0F, AgingRate.speed(target));
    }

    public static boolean isAgeableBlock(
            BlockState state) {
        return !state.isAir()
                && !state.is(Blocks.WITHER_ROSE)
                && !state.is(Blocks.SOUL_SAND)
                && HamonUtil.isBlockLiving(state);
    }

    public static boolean isAgeableItem(
            ItemStack stack) {
        return !stack.isEmpty()
                && !stack.is(Items.DIRT)
                && !stack.is(Items.SOUL_SAND)
                && !stack.is(Items.WITHER_ROSE)
                && (HamonUtil.isItemLivingMatter(stack)
                        || isNaturalFood(stack));
    }

    public static boolean hasGratefulDead(
            LivingEntity owner) {
        if (owner == null) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == GratefulDeadAddon
                                .THE_GRATEFUL_DEAD.get();
    }

    public static boolean isNaturalFood(
            ItemStack stack) {
        return stack.is(Items.BEEF)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.COD)
                || stack.is(Items.MUTTON)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.RABBIT)
                || stack.is(Items.SALMON)
                || stack.is(Items.PUFFERFISH)
                || stack.is(Items.TROPICAL_FISH)
                || stack.is(Items.APPLE)
                || stack.is(Items.CARROT)
                || stack.is(Items.POTATO)
                || stack.is(Items.MELON_SLICE)
                || stack.is(Items.SWEET_BERRIES)
                || stack.is(Items.GLOW_BERRIES);
    }

    public static boolean isSeedItem(
            ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.MELON_SEEDS)
                || stack.is(Items.PUMPKIN_SEEDS);
    }

    public static LivingEntity canonicalLivingTarget(
            LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    public static void ageLiving(
            LivingEntity target,
            UUID owner,
            long ownerGeneration,
            float amount) {
        ageLiving(target, owner, ownerGeneration, amount, false);
    }

    public static void ageLiving(
            LivingEntity target,
            UUID owner,
            long ownerGeneration,
            float amount,
            boolean contact) {
        LivingEntity canonical =
                canonicalLivingTarget(target);
        if (!isAgeableLiving(canonical)) {
            return;
        }
        LivingAgingState.get(canonical).age(
                canonical,
                owner,
                ownerGeneration,
                amount,
                contact);
    }

    /**
     * 0.1.13 StandActionStaminaAgingMixin / getSkillStaminaCostMultiplier: an
     * aged user's skills cost up to three times their stamina (x1 + 2 x
     * easeOutQuad from 0 to 80%); punches, barrages, heavy punches and the
     * guard keep their cost, as does a self-aged user. A stamina check without
     * its ability (null) keeps the base cost, so melee is never refused.
     */
    public static float skillStaminaCostFactor(
            Ability ability, LivingEntity user) {
        if (ability == null
                || isMeleeStaminaAbility(ability)
                || user == null
                || !user.hasData(GratefulDeadAddon.LIVING_AGING)) {
            return 1.0F;
        }
        LivingAgingState state = LivingAgingState.get(user);
        if (state.selfInflicted(user)) {
            return 1.0F;
        }
        float t = LivingAgingState.rangeT(
                state.progress(), 0.0F, SKILL_STAMINA_FULL_PROGRESS);
        return t <= 0.0F
                ? 1.0F
                : 1.0F + (float) LivingAgingState.easeOutQuad(t) * 2.0F;
    }

    // 0.1.13 isMeleeStaminaAction: StandEntityMeleeBarrage, LightAttack,
    // HeavyAttack and Block
    private static boolean isMeleeStaminaAbility(Ability ability) {
        return ability instanceof StandEntityPunchAbility
                || ability instanceof StandEntityBarrageAbility
                || ability instanceof StandEntityHeavyPunchAbility
                || ability instanceof StandEntityHeavyPunchChargedAbility
                || ability instanceof StandEntityBlockAbility;
    }

    /**
     * 0.1.13 HamonDataAgingMixin / getHamonEfficiency: the bloodstream
     * efficiency falls to 0 at 80% aging (1 - easeInCirc), self-aging
     * included; at 0 the user neither breathes nor uses Hamon.
     */
    public static float hamonEfficiency(LivingEntity user) {
        if (user == null
                || !user.hasData(GratefulDeadAddon.LIVING_AGING)) {
            return 1.0F;
        }
        return (float) (1.0D - LivingAgingState.easeInCirc(
                LivingAgingState.rangeT(
                        LivingAgingState.get(user).progress(),
                        0.0F,
                        HAMON_ZERO_PROGRESS)));
    }

    /** 0.1.13 cannotUseStand, for the ability refusal. */
    public static boolean cannotUseStand(LivingEntity user) {
        return user != null
                && user.hasData(GratefulDeadAddon.LIVING_AGING)
                && LivingAgingState.get(user).cannotUseStand(user);
    }

    public static boolean isAgeableLiving(
            LivingEntity target) {
        return target != null
                && target.isAlive()
                && !(target instanceof StandEntity)
                && HamonUtil.isLiving(target);
    }

    /**
     * 1.16 TimeStopInstantAgingMixin: an aged user's time-stop
     * blink reach, 0 (no blink) from 85% aging.
     */
    public static int agedTimeStopBlinkTicks(
            LivingEntity user, int ticks) {
        if (user == null
                || !user.hasData(
                        GratefulDeadAddon.LIVING_AGING)) {
            return ticks;
        }
        LivingAgingState state = LivingAgingState.get(user);
        if (state.selfInflicted(user)) {
            return ticks;
        }
        float progress = state.progress();
        return progress > 0.0F
                ? agedActiveTimeStopTicks(ticks, progress)
                : ticks;
    }

    public static int agedActiveTimeStopTicks(
            int currentTicksLeft, float progress) {
        if (currentTicksLeft <= 0 || progress >=
                TIME_STOP_DISABLE_PROGRESS) {
            return 0;
        }
        if (progress <= 0.0F) {
            return currentTicksLeft;
        }
        float normalized = Mth.clamp(
                progress / TIME_STOP_DISABLE_PROGRESS,
                0.0F,
                1.0F);
        double multiplier = Math.pow(
                1.0D - normalized, 2.4D);
        int capped = Math.max(
                1,
                (int) Math.floor(
                        currentTicksLeft
                                * Math.max(0.05D, multiplier)));
        int extraDrain = Math.max(
                1,
                (int) Math.ceil(
                        1.0D
                                + normalized * normalized
                                        * 10.0D));
        return Math.max(
                0,
                Math.min(
                        capped,
                        currentTicksLeft - extraDrain));
    }

    public static ItemStack ageItem(
            ItemStack stack,
            UUID owner,
            long ownerGeneration,
            float amount) {
        if (!isAgeableItem(stack) || amount <= 0.0F) {
            return stack;
        }
        ItemAgingState current =
                stack.getOrDefault(
                        GratefulDeadAddon.ITEM_AGING,
                        ItemAgingState.EMPTY);
        ItemAgingState next = current.age(
                owner, ownerGeneration, amount);
        if (next.progress() >= 1.0F) {
            return terminalItem(stack);
        }
        stack.set(GratefulDeadAddon.ITEM_AGING, next);
        return stack;
    }

    public static ItemStack tickItemState(
            MinecraftServer server, ItemStack stack) {
        ItemAgingState state =
                stack.get(GratefulDeadAddon.ITEM_AGING);
        if (state == null) {
            return stack;
        }
        boolean removal = state.owner()
                .map(owner ->
                        AgingRemovalData.get(server)
                                        .generation(owner)
                                        > state.ownerGeneration()
                                || !hasGratefulDead(
                                        server.getPlayerList()
                                                .getPlayer(owner)))
                .orElse(true);
        if (removal) {
            stack.remove(GratefulDeadAddon.ITEM_AGING);
            return stack;
        }
        if (!state.fading()) {
            return stack;
        }
        ItemAgingState next = state.startFading()
                .fade(
                        1.0F
                                / LivingAgingState
                                        .DEFAULT_FADE_TICKS);
        if (next.progress() <= 0.0F) {
            stack.remove(
                    GratefulDeadAddon.ITEM_AGING);
        }
        else {
            stack.set(
                    GratefulDeadAddon.ITEM_AGING, next);
        }
        return stack;
    }

    public static ItemStack terminalItem(
            ItemStack source) {
        int count = source.getCount();
        if (source.is(Items.GRASS_BLOCK)
                || source.is(Items.MYCELIUM)) {
            return new ItemStack(
                    Items.DIRT,
                    Math.max(1, count));
        }
        if (source.is(ItemTags.FLOWERS)) {
            return new ItemStack(
                    Items.WITHER_ROSE,
                    Math.max(1, count));
        }
        if (source.is(ItemTags.SAPLINGS)) {
            return new ItemStack(
                    Items.DEAD_BUSH,
                    Math.max(1, count));
        }
        return ItemStack.EMPTY;
    }

    /** 0.1.13 getAuraCenter: the summoned Stand, else its user. */
    public static LivingEntity auraCenter(LivingEntity owner) {
        StandEntity stand = StandUtil.getSummonedStand(owner);
        return stand != null && stand.isAlive() ? stand : owner;
    }

    /** 0.1.13 getExpandedRadius: grows from 0 with the aura's ticks up to its range. */
    public static double auraRadius(int auraTicks) {
        return Math.min(
                AURA_RADIUS,
                Math.max(0.0D, auraTicks * AURA_EXPAND_PER_TICK));
    }

    /** The chunk cells around the centre's chunk that a radius reaches, at most the full schedule's. */
    public static int auraChunkReach(double radius) {
        return Mth.clamp(
                Mth.ceil(radius / 16.0D),
                1,
                AURA_DIAMETER_CHUNKS / 2);
    }

    public static void tickAura(LivingEntity owner, int auraTicks) {
        if (!(owner.level()
                instanceof ServerLevel level)) {
            return;
        }
        if (isCold(level, owner.blockPosition())) {
            return;
        }
        double radius = auraRadius(auraTicks);
        if (radius <= 0.0D) {
            return;
        }
        LivingEntity center = auraCenter(owner);
        UUID ownerId = owner.getUUID();
        long ownerGeneration =
                generation(level.getServer(), ownerId);
        tickAuraPlayerInventories(
                level, owner, center, radius, ownerId, ownerGeneration);
        // A smaller aura cycles through fewer cells, visiting each more often
        // for a share of the full cycle's per-visit amounts: the same rate.
        int diameter = 2 * auraChunkReach(radius) + 1;
        int cells = diameter * diameter;
        float cycleShare = (float) cells / AURA_CHUNK_COUNT;
        int cell = Math.floorMod(
                (int) level.getGameTime(),
                cells);
        int offsetX =
                cell % diameter
                        - diameter / 2;
        int offsetZ =
                cell / diameter
                        - diameter / 2;
        int chunkX = center.chunkPosition().x + offsetX;
        int chunkZ = center.chunkPosition().z + offsetZ;
        if (!level.hasChunk(chunkX, chunkZ)) {
            return;
        }

        double minX = chunkX * 16.0D;
        double minZ = chunkZ * 16.0D;
        AABB column = new AABB(
                minX,
                level.getMinBuildHeight(),
                minZ,
                minX + 16.0D,
                level.getMaxBuildHeight(),
                minZ + 16.0D);
        List<Entity> candidates = level.getEntities(
                owner,
                column,
                entity ->
                        entity instanceof LivingEntity
                                || entity
                                        instanceof ItemEntity);
        double radiusSqr = radius * radius;
        int candidateCount = candidates.size();
        int examined = Math.min(
                candidateCount,
                MAX_AURA_ENTITY_CANDIDATES);
        int candidateStart = candidateCount > 0
                ? Math.floorMod(
                        (int) level.getGameTime(),
                        candidateCount)
                : 0;
        Set<UUID> agedLivingTargets =
                new HashSet<>();
        for (int offset = 0;
                offset < examined;
                offset++) {
            Entity candidate = candidates.get(
                    (candidateStart + offset)
                            % candidateCount);
            if (candidate.distanceToSqr(center) > radiusSqr) {
                continue;
            }
            if (candidate instanceof LivingEntity living) {
                LivingEntity canonical =
                        canonicalLivingTarget(living);
                if (canonical == owner
                        || !isAgeableLiving(canonical)
                        || !agedLivingTargets.add(
                                canonical.getUUID())) {
                    continue;
                }
                float speedMultiplier =
                        livingAgingMultiplier(canonical);
                if (speedMultiplier <= 0.0F) {
                    continue;
                }
                ageLiving(
                        canonical,
                        ownerId,
                        ownerGeneration,
                        AURA_LIVING_DELTA
                                * cycleShare
                                * speedMultiplier);
            }
            else if (candidate instanceof ItemEntity item) {
                float speedMultiplier =
                        biomeAgingMultiplier(
                                level,
                                item.blockPosition());
                if (speedMultiplier <= 0.0F) {
                    continue;
                }
                ItemStack aged = ageItem(
                        item.getItem(),
                        ownerId,
                        ownerGeneration,
                        AURA_ITEM_DELTA
                                * cycleShare
                                * speedMultiplier);
                if (aged.isEmpty()) {
                    item.discard();
                }
                else {
                    item.setItem(aged);
                }
            }
        }

        int seed = Mth.floor(level.getGameTime())
                ^ chunkX * 73428767
                ^ chunkZ * 912931;
        BlockAgingData blocks = BlockAgingData.get(level);
        for (int index = 0;
                index < MAX_AURA_BLOCK_COLUMNS;
                index++) {
            seed = seed * 1664525 + 1013904223;
            int x = chunkX * 16 + ((seed >>> 8) & 15);
            seed = seed * 1664525 + 1013904223;
            int z = chunkZ * 16 + ((seed >>> 12) & 15);
            int top = level.getHeight(
                    Heightmap.Types
                            .WORLD_SURFACE,
                    x,
                    z) - 1;
            for (int offset : SURFACE_OFFSETS) {
                BlockPos position =
                        new BlockPos(x, top + offset, z);
                if (position.getY()
                                < level.getMinBuildHeight()
                        || position.getY()
                                >= level.getMaxBuildHeight()) {
                    continue;
                }
                double dx =
                        x + 0.5D - center.getX();
                double dy =
                        position.getY() + 0.5D
                                - center.getY();
                double dz =
                        z + 0.5D - center.getZ();
                if (dx * dx + dy * dy + dz * dz
                        > radiusSqr) {
                    continue;
                }
                if (isAgeableBlock(
                                level.getBlockState(position))
                        && !isCold(level, position)) {
                    blocks.age(
                            level,
                            position,
                            ownerId,
                            ownerGeneration,
                            AURA_BLOCK_DELTA
                                    * cycleShare
                                    * biomeAgingMultiplier(
                                            level, position));
                    break;
                }
            }
        }
    }

    private static void tickAuraPlayerInventories(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity center,
            double radius,
            UUID ownerId,
            long ownerGeneration) {
        List<ServerPlayer> players = level.players();
        int playerCount = players.size();
        if (playerCount == 0) {
            return;
        }
        int start = Math.floorMod(
                (int) level.getGameTime()
                        + ownerId.hashCode(),
                playerCount);
        int examined = Math.min(
                playerCount, MAX_AURA_PLAYER_CANDIDATES);
        double radiusSqr = radius * radius;
        for (int offset = 0; offset < examined; offset++) {
            ServerPlayer player = players.get(
                    (start + offset) % playerCount);
            if (player == owner
                    || !player.isAlive()
                    || player.distanceToSqr(center) > radiusSqr
                    || IceCubeItem.protectsInventory(player)
                    || livingAgingMultiplier(player) <= 0.0F) {
                continue;
            }
            agePlayerInventory(
                    player, ownerId, ownerGeneration);
        }
    }

    private static void agePlayerInventory(
            ServerPlayer player,
            UUID owner,
            long ownerGeneration) {
        int inventorySize = player.getInventory()
                .getContainerSize();
        int virtualSize = inventorySize + 1;
        float delta = (float) virtualSize
                / (MAX_PLAYER_ITEM_SLOTS_PER_TICK
                        * 750.0F)
                * livingAgingMultiplier(player);
        int start = Math.floorMod(
                player.tickCount
                        * MAX_PLAYER_ITEM_SLOTS_PER_TICK,
                virtualSize);
        boolean changed = false;
        for (int offset = 0;
                offset < MAX_PLAYER_ITEM_SLOTS_PER_TICK;
                offset++) {
            int slot = (start + offset) % virtualSize;
            ItemStack stack = slot < inventorySize
                    ? player.getInventory().getItem(slot)
                    : player.containerMenu.getCarried();
            if (!isAgeableItem(stack)) {
                continue;
            }
            ItemStack aged = ageItem(
                    stack, owner, ownerGeneration, delta);
            if (slot < inventorySize) {
                player.getInventory().setItem(slot, aged);
            }
            else {
                player.containerMenu.setCarried(aged);
            }
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    public static void tickPlayerInventory(
            Player player) {
        if (!(player.level()
                instanceof ServerLevel level)) {
            return;
        }
        int inventorySize = player.getInventory()
                .getContainerSize();
        int virtualSize = inventorySize + 1;
        int start = Math.floorMod(
                player.tickCount
                        * MAX_PLAYER_ITEM_SLOTS_PER_TICK,
                virtualSize);
        boolean changed = false;
        for (int offset = 0;
                offset < MAX_PLAYER_ITEM_SLOTS_PER_TICK;
                offset++) {
            int slot = (start + offset) % virtualSize;
            ItemStack stack = slot < inventorySize
                    ? player.getInventory().getItem(slot)
                    : player.containerMenu.getCarried();
            if (!stack.has(GratefulDeadAddon.ITEM_AGING)) {
                continue;
            }
            ItemStack next = tickItemState(
                    level.getServer(), stack);
            if (slot < inventorySize) {
                player.getInventory().setItem(
                        slot, next);
            }
            else {
                player.containerMenu.setCarried(next);
            }
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}

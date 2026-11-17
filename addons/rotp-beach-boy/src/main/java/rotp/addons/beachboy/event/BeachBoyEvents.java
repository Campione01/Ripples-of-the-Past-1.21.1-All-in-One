package rotp.addons.beachboy.event;

import java.util.concurrent.ThreadLocalRandom;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.item.BeachBoyRodItem;
import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyLine;
import rotp.addons.beachboy.util.BeachBoyPathSwim;
import rotp.addons.beachboy.util.BeachBoyResolveUtil;
import rotp.addons.beachboy.util.BeachBoySettings;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** 1.16 BeachBoyEvents, with the Resolve bonus and the sound sense range that 1.16 put in mixins. */
@EventBusSubscriber(modid = BeachBoyAddon.MOD_ID)
public final class BeachBoyEvents {
    private static final ResourceLocation HOOKED_SPEED_ID = BeachBoyAddon.id("hooked_slow");
    private static final double HOOKED_SPEED_PENALTY = -0.4;
    private static final double SOUND_SENSE_RANGE_SQ = BeachBoyStandUtil.SOUND_HEAR_RANGE * BeachBoyStandUtil.SOUND_HEAR_RANGE;

    private BeachBoyEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (BeachBoyStandUtil.isRodItem(event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item
                && BeachBoyStandUtil.isRodItem(item.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity item = event.getItemEntity();
        if (BeachBoyStandUtil.isRodItem(item.getItem()) && !BeachBoyStandUtil.isBeachBoyUser(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
            return;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHookEmbeddedIn(item);
        if (hook != null) {
            if (hook.isTrapArmed()) {
                hook.triggerTrap(event.getPlayer());
            }
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        BeachBoyPathSwim.keep(living);
        tickHookedSlow(living);
        if (!living.level().isClientSide()) {
            BeachBoyStandUtil.suppressPuppetAi(living);
            BeachBoyCombatUtil.tryTriggerWalk(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (BeachBoyStandUtil.isBeachBoyUser(player)) {
            ItemStack held = BeachBoyStandUtil.getHeldRod(player);
            if (!held.isEmpty()) {
                BeachBoyRodItem.syncStandScaledDamage(player, held);
            }
        }
        if (!BeachBoyStandUtil.isBeachBoySummoned(player)) {
            BeachBoyStandUtil.removeAllRods(player);
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(player);
            if (hook != null) {
                hook.discard();
            }
        }
    }

    // 1.16 synced the settings Stand effect to its user on login and respawn
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        BeachBoySettings.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BeachBoySettings.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        BeachBoySettings.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (BeachBoyStandUtil.findHookEmbeddedIn(event.getEntity()) != null) {
            event.setNewSpeed(event.getNewSpeed() * 0.4F);
        }
    }

    private static void tickHookedSlow(LivingEntity living) {
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean hooked = BeachBoyStandUtil.findHookEmbeddedIn(living) != null;
        boolean has = speed.getModifier(HOOKED_SPEED_ID) != null;
        if (hooked && !has) {
            speed.addTransientModifier(new AttributeModifier(HOOKED_SPEED_ID, HOOKED_SPEED_PENALTY,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        else if (!hooked && has) {
            speed.removeModifier(HOOKED_SPEED_ID);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        BeachBoyHookEntity ownHook = BeachBoyStandUtil.findHook(dead);
        if (ownHook != null) {
            ownHook.discard();
        }
        BeachBoyHookEntity embedded = BeachBoyStandUtil.findHookEmbeddedIn(dead);
        if (embedded != null) {
            embedded.markPendingDeathLoot(dead);
        }
        BeachBoyPathSwim.forget(dead.getUUID());
    }

    // 1.16: what a hooked creature drops goes onto the hook and comes back with the reel
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHookedDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHookEmbeddedIn(dead);
        if (hook == null) {
            for (BeachBoyHookEntity candidate : dead.level().getEntitiesOfClass(BeachBoyHookEntity.class,
                    dead.getBoundingBox().inflate(8.0), Entity::isAlive)) {
                if (candidate.isPendingDeathLoot(dead)) {
                    hook = candidate;
                    break;
                }
            }
        }
        if (hook != null) {
            for (ItemEntity drop : event.getDrops()) {
                if (drop != null && !drop.getItem().isEmpty()) {
                    hook.addDeathLoot(drop.getItem().copy());
                    drop.discard();
                }
            }
            event.getDrops().clear();
            hook.clearPendingDeathLoot();
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (target instanceof BeachBoyHookEntity hook) {
            BeachBoyLine.tryConductMelee(hook, event.getEntity());
            event.setCanceled(true);
        }
        else if (!event.getEntity().level().isClientSide()) {
            BeachBoyCombatUtil.tryTriggerEntityTrap(target, event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || BeachBoyLine.isConducting() || projectile instanceof BeachBoyHookEntity) {
            return;
        }
        HitResult result = event.getRayTraceResult();
        AABB box = projectile.getBoundingBox().inflate(256.0);
        for (BeachBoyLine line : BeachBoyLine.allIn(projectile.level(), box)) {
            LivingEntity hooked = line.getHookedLiving();
            if (hooked == null || !line.isThornsEnabled()) {
                continue;
            }
            if (result instanceof EntityHitResult entityHit && entityHit.getEntity() == hooked) {
                if (line.getHook().hasConductedProjectile(projectile.getId())) {
                    event.setCanceled(true);
                    return;
                }
            }
            else if ((line.crossesImpact(projectile, result) || line.crossesProjectilePath(projectile))
                    && line.tryConductProjectile(projectile)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** 1.16 onLivingAttack: an attack that crosses a thorned line lands on the creature on its hook instead. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (BeachBoyLine.isConducting() || victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        AABB box = victim.getBoundingBox().inflate(264.0);
        if (attacker != null) {
            box = box.minmax(attacker.getBoundingBox().inflate(264.0));
        }
        for (BeachBoyLine line : BeachBoyLine.allIn(victim.level(), box.inflate(256.0))) {
            LivingEntity hooked = line.getHookedLiving();
            if (hooked == null || hooked == victim || hooked == attacker) {
                continue;
            }
            boolean hitLine = false;
            if (attacker instanceof LivingEntity living) {
                double reach = BeachBoyLine.getMeleeReach(living);
                Vec3 start = living.getEyePosition(1.0F);
                Vec3 end = victim.getBoundingBox().getCenter();
                Vec3 lookEnd = start.add(living.getLookAngle().scale(reach));
                double extra = living instanceof StandEntity ? 0.35 : 0.0;
                double minDist = line.isFromOwnerSide(living) ? 1.75 : 0.0;
                boolean victimInReach = start.distanceTo(end) <= reach + 1.0;
                hitLine = line.intersectsRay(start, lookEnd, minDist, extra)
                        || victimInReach && line.intersectsRay(start, end, minDist, extra);
                if (!line.isFromOwnerSide(living)) {
                    hitLine = hitLine || living.distanceTo(line.getHook()) <= reach + 1.0 && line.intersectsEntity(living);
                }
            }
            else if (direct != null && direct != line.getHook()) {
                hitLine = line.intersectsEntity(direct);
            }
            if (hitLine && line.tryConductAttack(source, event.getAmount(), direct instanceof Projectile ? direct : null)) {
                event.setCanceled(true);
                return;
            }
        }
        BeachBoyCombatUtil.tryTriggerEntityTrap(victim, attacker);
    }

    // 1.16 ResolveCounterSexPistolsMixin at resolveOnHurtEvent: counted from the hit as it lands, as the core counts
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onResolveHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide() || target.is(source.getEntity()) || !target.isAlive()) {
            return;
        }
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            BeachBoyResolveUtil.addBeachBoyResolveBonus(source, target, container.getNewDamage());
            return reduction;
        });
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            BeachBoyCombatUtil.tryTriggerBlockTrap(player, event.getPos());
        }
        if (tryHitLine(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().level().isClientSide()) {
            BeachBoyCombatUtil.tryTriggerBlockTrap(event.getEntity(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getEntity().level().isClientSide()) {
            BeachBoyCombatUtil.tryTriggerEntityTrap(event.getTarget(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getEntity().level().isClientSide()) {
            BeachBoyCombatUtil.tryTriggerEntityTrap(event.getTarget(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && !player.level().isClientSide()) {
            BeachBoyCombatUtil.tryTriggerBlockTrap(player, event.getPos());
        }
    }

    // 1.16 PlayerListBeachBoySoundMixin: a sound sense user hears every sound within 128 blocks. It took only the
    // position packets (SPlaySoundEffectPacket, SPlaySoundPacket): a sound bound to an entity stays in its own range.
    // A level's sounds can come off the server thread (a mob placed in a generating chunk), so the seed is not the
    // world's random and the walk over the players goes to the server thread, with what it needs read here.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel() instanceof ServerLevel level && event.getSound() != null) {
            Vec3 pos = event.getPosition();
            Holder<SoundEvent> sound = event.getSound();
            SoundSource source = event.getSource();
            float volume = event.getNewVolume();
            float pitch = event.getNewPitch();
            long seed = ThreadLocalRandom.current().nextLong();
            onServerThread(level, () -> {
                double rangeSq = sq(sound.value().getRange(volume));
                for (ServerPlayer player : level.players()) {
                    double distSq = player.distanceToSqr(pos);
                    if (distSq >= rangeSq && distSq < SOUND_SENSE_RANGE_SQ && hasSoundSense(player)) {
                        player.connection.send(new ClientboundSoundPacket(sound, source, pos.x, pos.y, pos.z,
                                volume, pitch, seed));
                    }
                }
            });
        }
    }

    private static void onServerThread(ServerLevel level, Runnable work) {
        MinecraftServer server = level.getServer();
        if (server.isSameThread()) {
            work.run();
        }
        else {
            server.execute(work);
        }
    }

    private static double sq(double value) {
        return value * value;
    }

    private static boolean hasSoundSense(ServerPlayer player) {
        return BeachBoyStandUtil.isSoundSenseEnabled(StandPower.get(player));
    }

    /** 1.16 tryHitLine: a swing that crosses a line strikes the creature on its hook. */
    public static boolean tryHitLine(Player player) {
        if (player == null || player.level() == null || BeachBoyLine.isConducting()
                || BeachBoyLine.isHoldingHamonAction(player)) {
            return false;
        }
        double reach = BeachBoyLine.getMeleeReach(player);
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        AABB box = new AABB(start, end).inflate(2.0);
        for (BeachBoyLine line : BeachBoyLine.allIn(player.level(), box)) {
            boolean hit = line.intersectsRay(start, end, 1.75);
            if (!line.isOwnedBy(player)) {
                hit = hit || player.distanceTo(line.getHook()) <= reach + 1.0 && line.intersectsEntity(player);
            }
            if (hit && line.tryConductMelee(player)) {
                return true;
            }
        }
        return false;
    }
}

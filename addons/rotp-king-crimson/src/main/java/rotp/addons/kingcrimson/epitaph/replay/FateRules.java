package rotp.addons.kingcrimson.epitaph.replay;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;

/**
 * The future that was foreseen cannot be changed. A creature on its track can be hurt from outside, by anything that
 * is no op of the record, but it cannot be killed before its track ends: what would take its last health is held
 * back as a debt and paid as one hit when the track ends. After each foreseen hit its health is what its phantom had
 * then, less what outsiders really took. Players are not held so: a foreseen hit on a player is the real hit, with
 * whatever it does.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class FateRules {
    private FateRules() {}

    // The last event before health is taken, where the final amount is known.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!EpitaphSessions.any) {
            return;
        }
        EpitaphSandbox.countPass();
        LivingEntity victim = event.getEntity();
        Binding binding = EpitaphSessions.bindingOf(victim);
        if (binding == null || victim instanceof Player || EpitaphSandbox.replaying()) {
            return;
        }
        float damage = event.getNewDamage();
        float room = Math.max(0.0F, victim.getHealth() + victim.getAbsorptionAmount() - 1.0F);
        if (damage > room) {
            binding.debt += damage - room;
            binding.lastOutside = event.getSource();
            event.setNewDamage(room);
            damage = room;
        }
        binding.outsideTaken += Math.max(0.0F, damage - victim.getAbsorptionAmount());
    }

    /** After a foreseen hit that did not kill its phantom: the body has the health its phantom had, less the outsiders' share. */
    public static void settle(Binding binding, float recordedHealth) {
        if (binding.body instanceof LivingEntity living && !(living instanceof Player) && recordedHealth > 0.0F
                && living.isAlive()) {
            living.setHealth(Math.max(1.0F, recordedHealth - binding.outsideTaken));
        }
    }

    /** The track is over: what outsiders were owed is dealt now, as one hit of the last of them, and may kill. */
    public static void payDebt(Binding binding) {
        if (binding.debt > 0.0F && binding.lastOutside != null && binding.body instanceof LivingEntity living
                && living.isAlive()) {
            float owed = binding.debt;
            binding.debt = 0.0F;
            living.invulnerableTime = 0;
            living.hurt(binding.lastOutside, owed);
        }
    }

    /**
     * Harm that comes of the state of the one it harms: fire, drowning, hunger, an effect, the block he stands in.
     * Nobody did it, and it is no blast, no fall and no lightning. A walked player takes such harm by his own tick, at the
     * pace of his track (EPITAPH-ARCH, ruling R5), so it is no op for him.
     */
    public static boolean ofOwnState(DamageSource source) {
        return source.getEntity() == null && source.getDirectEntity() == null && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_LIGHTNING);
    }
}

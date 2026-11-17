package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.state.KingCrimsonReflexErase;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState.EraseKind;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;

/**
 * Owner 2026-11-07, the passive that took the place of Epitaph's dodge: "玩家受傷大於最大生命值10%時刪除一次時間進行規避...同時致死傷害
 * （或瀕死時受到的傷害）會無視當前冷卻零消耗自動釋放一次持續四秒的時間刪除，這個冷卻獨立計算，十分鐘一次". Every hit here is real damage on a
 * real server player; what is read is read in the callback that deals it.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonReflexEraseGameTests {
    private static final String KEY = "kingcrimson_timeerase";

    private KingCrimsonReflexEraseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_reflex_hit")
    public static void aHitAboveATenthOfHisHealthIsEscapedByATwoSecondEraseForStaminaAndACooldown(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reflex-hit");
        ServerPlayer player = scene.player;
        StandPower power = scene.power;
        float full = power.getMaxStamina();
        boolean on = KingCrimsonConfig.REFLEX_ERASE.get();
        scene.fixture.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(on));
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            scene.rule(scene.hit(1.5F) && player.getHealth() == 18.5F && scene.idle(),
                    "a hit of 1.5 on 20 health, under a tenth, did not simply land: health " + player.getHealth());
            scene.restore();
            boolean landed = scene.hit(6.0F);
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(player);
            scene.rule(!landed && player.getHealth() == 20.0F && KingCrimsonEraseFixture.erasing(player)
                            && state.eraseKind() == EraseKind.REFLEX && state.eraseLengthTicks() == 40,
                    "a hit of 6 on 20 health was not escaped by a reflex erase of 40 ticks: landed " + landed + ", health "
                            + player.getHealth() + ", " + scene.erase());
            scene.rule(Math.abs(power.getStamina() - (full - 300.0F)) < 1.0F, "the reflex cost " + (full - power.getStamina())
                    + " stamina, not 300");
            scene.rule(power.getAbilityCooldown(KingCrimsonReflexErase.REFLEX) == 600 && power.getAbilityCooldown(KEY) == 0
                            && power.getAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE) == 0,
                    "after a reflex its own cooldown is " + power.getAbilityCooldown(KingCrimsonReflexErase.REFLEX)
                            + " ticks (not 600), the key's " + power.getAbilityCooldown(KEY) + " and the last chance's "
                            + power.getAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE) + " (not 0)");
            // The key's erase ends when its upkeep cannot be paid; the passive's has none.
            power.setStamina(0.0F);
        }));
        helper.runAfterDelay(24, () -> scene.fixture.guarded(() ->
                scene.rule(KingCrimsonEraseFixture.erasing(player), "with no stamina left the reflex's erase ended early")));
        helper.runAfterDelay(52, () -> scene.fixture.guarded(() -> {
            scene.rule(scene.idle(), "a reflex erase of 2 s still runs 50 ticks on");
            scene.rule(power.getAbilityCooldown(KEY) == 0, "the end of a reflex erase started the key's cooldown: "
                    + power.getAbilityCooldown(KEY));
            scene.rule(power.getSummonedStandEntity() != null, "the Stand is not out after a reflex erase");
            scene.restore();
            scene.rule(scene.hit(6.0F) && player.getHealth() == 14.0F && scene.idle(),
                    "a second heavy hit inside the reflex's cooldown did not land: health " + player.getHealth());

            scene.restore();
            power.setAbilityCooldown(KingCrimsonReflexErase.REFLEX, 0);
            power.setStamina(100.0F);
            scene.rule(scene.hit(6.0F) && player.getHealth() == 14.0F && scene.idle(),
                    "with 100 stamina, short of the 300, a heavy hit did not land: health " + player.getHealth());

            // What his health bar would really lose counts: armour and absorption come off first.
            for (ItemStack piece : List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS))) {
                player.setItemSlot(player.getEquipmentSlotForItem(piece), piece);
            }
        }));
        helper.runAfterDelay(58, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(player.getArmorValue() == 20, "Setup: his armour is " + player.getArmorValue());
            scene.restore();
            scene.rule(scene.hit(3.0F) && player.getHealth() > 18.0F && player.getHealth() < 20.0F && scene.idle(),
                    "a hit of 3 on full diamond armour, about 1 after it, did not simply land: health " + player.getHealth()
                            + ", " + scene.erase());
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }));
        helper.runAfterDelay(64, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(player.getArmorValue() == 0, "Setup: his armour is still " + player.getArmorValue());
            scene.restore();
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
            helper.assertTrue(player.getAbsorptionAmount() == 4.0F, "Setup: absorption is " + player.getAbsorptionAmount());
            scene.rule(scene.hit(5.0F) && player.getHealth() == 19.0F && scene.idle(),
                    "a hit of 5 on 4 absorption, 1 off the health bar, did not simply land: health " + player.getHealth()
                            + ", " + scene.erase());
            player.removeEffect(MobEffects.ABSORPTION);

            scene.restore();
            KingCrimsonConfig.REFLEX_ERASE.set(false);
            scene.rule(scene.hit(6.0F) && scene.idle(), "with reflexErase off a heavy hit was escaped");
            KingCrimsonConfig.REFLEX_ERASE.set(true);

            // Nothing saves from the void.
            scene.restore();
            player.invulnerableTime = 0;
            scene.rule(player.hurt(player.damageSources().fellOutOfWorld(), 6.0F) && scene.idle(),
                    "the void's 6 was escaped by a reflex");

            // The Stand comes out with him.
            scene.restore();
            ((EntityStandType) power.getPowerType()).forceUnsummon(player, power);
            helper.assertTrue(power.getSummonedStandEntity() == null, "Setup: the Stand is still out");
            scene.rule(!scene.hit(6.0F) && KingCrimsonEraseFixture.erasing(player) && power.getSummonedStandEntity() != null,
                    "with the Stand not out a heavy hit was not escaped with the Stand summoned: " + scene.erase()
                            + ", Stand " + power.getSummonedStandEntity());
        }));
        helper.runAfterDelay(116, () -> scene.fixture.guarded(() -> {
            scene.rule(scene.idle(), "the second reflex erase still runs 50 ticks on");
            // An erase returned to zero (Gold Experience Requiem) never begins: the reflex is spent and the hit lands.
            Consumer<TimeEraseStartEvent> toZero = event -> {
                if (event.getEraser() == player) {
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TimeEraseStartEvent.class, toZero);
            scene.restore();
            power.setAbilityCooldown(KingCrimsonReflexErase.REFLEX, 0);
            boolean landed = scene.hit(6.0F);
            NeoForge.EVENT_BUS.unregister(toZero);
            scene.rule(landed && player.getHealth() == 14.0F && scene.idle()
                            && Math.abs(power.getStamina() - (full - 300.0F)) < 1.0F
                            && power.getAbilityCooldown(KingCrimsonReflexErase.REFLEX) == 600
                            && power.getAbilityCooldown(KEY) == 0,
                    "a reflex returned to zero: the hit landed " + landed + " (health " + player.getHealth() + "), it cost "
                            + (full - power.getStamina()) + " stamina, its cooldown is "
                            + power.getAbilityCooldown(KingCrimsonReflexErase.REFLEX) + " and the key's "
                            + power.getAbilityCooldown(KEY));

            // Not before Time Erase itself is unlocked.
            ServerPlayer novice = scene.fixture.player("kc-reflex-novice", 6.5D, 2.0D, 1.5D);
            scene.fixture.kingCrimsonUnskilled(novice, 2);
            novice.invulnerableTime = 0;
            scene.rule(novice.hurt(novice.damageSources().cactus(), 6.0F) && novice.getHealth() == 14.0F
                            && KingCrimsonEraseFixture.state(novice).isIdle(),
                    "at Resolve 2, before Time Erase is his, a user's heavy hit was escaped: health " + novice.getHealth());
            scene.finish("The reflex erase");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 320, batch = "kc_reflex_last")
    public static void aKillingHitOrAnyHitWhenNearlyDeadIsEscapedForNothingOnceInTenMinutes(GameTestHelper helper) {
        Scene scene = new Scene(helper, "kc-reflex-last");
        ServerPlayer player = scene.player;
        StandPower power = scene.power;
        helper.runAfterDelay(2, () -> scene.fixture.guarded(() -> {
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            // Neither the reflex's cooldown nor an empty stamina bar stands in its way.
            power.setAbilityCooldown(KingCrimsonReflexErase.REFLEX, 600);
            power.setStamina(0.0F);
            boolean landed = scene.hit(1000.0F);
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(player);
            scene.rule(!landed && player.getHealth() == 20.0F && KingCrimsonEraseFixture.erasing(player)
                            && state.eraseKind() == EraseKind.LAST_CHANCE && state.eraseLengthTicks() == 80,
                    "a killing hit was not escaped by a last-chance erase of 80 ticks: landed " + landed + ", health "
                            + player.getHealth() + ", " + scene.erase());
            scene.rule(power.getStamina() < 1.0F, "the last chance changed his stamina to " + power.getStamina());
            scene.rule(power.getAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE) == 12000
                            && power.getAbilityCooldown(KingCrimsonReflexErase.REFLEX) == 600
                            && power.getAbilityCooldown(KEY) == 0,
                    "after a last chance its own cooldown is " + power.getAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE)
                            + " ticks (not 12000), the reflex's " + power.getAbilityCooldown(KingCrimsonReflexErase.REFLEX)
                            + " (not the 600 it had) and the key's " + power.getAbilityCooldown(KEY) + " (not 0)");
            scene.rule(player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "a hit the last chance escaped used up his totem");
            // Kept with the Stand: saved with it, and so through death and relog.
            CompoundTag saved = power.serializeNBT(helper.getLevel().registryAccess()).getCompound("AbilityCooldowns");
            scene.rule(saved.getIntArray(KingCrimsonReflexErase.LAST_CHANCE).length == 2
                            && saved.getIntArray(KingCrimsonReflexErase.LAST_CHANCE)[0] == 12000,
                    "the last chance's cooldown is not in the Stand's saved data: " + saved);
        }));
        helper.runAfterDelay(100, () -> scene.fixture.guarded(() -> {
            scene.rule(scene.idle() && power.getAbilityCooldown(KEY) == 0, "100 ticks after a last chance of 4 s: "
                    + scene.erase() + ", the key's cooldown " + power.getAbilityCooldown(KEY));
            // Nearly dead, any hit at all.
            power.setAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE, 0);
            power.setAbilityCooldown(KingCrimsonReflexErase.REFLEX, 0);
            power.setStamina(0.0F);
            player.setHealth(2.0F);
            boolean landed = scene.hit(0.5F);
            scene.rule(!landed && player.getHealth() == 2.0F
                            && KingCrimsonEraseFixture.state(player).eraseKind() == EraseKind.LAST_CHANCE
                            && KingCrimsonEraseFixture.erasing(player),
                    "at 2 of 20 health a hit of 0.5 was not escaped by the last chance: landed " + landed + ", health "
                            + player.getHealth() + ", " + scene.erase());
        }));
        helper.runAfterDelay(196, () -> scene.fixture.guarded(() -> {
            scene.rule(scene.idle(), "the second last chance still runs");
            // Once in ten minutes: now the same small hit lands.
            player.setHealth(2.0F);
            scene.rule(scene.hit(0.5F) && player.getHealth() == 1.5F && scene.idle(),
                    "inside the last chance's cooldown a small hit on a nearly dead user did not land: health "
                            + player.getHealth());
            // A killing hit counts the absorption he has: 5 on 3 health and 4 absorption neither kills nor is heavy.
            power.setAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE, 0);
            player.setHealth(3.0F);
            power.setStamina(power.getMaxStamina());
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
            scene.rule(scene.hit(5.0F) && player.getHealth() == 2.0F && scene.idle(),
                    "a hit of 5 on 3 health and 4 absorption did not simply land: health " + player.getHealth() + ", "
                            + scene.erase());
            player.removeEffect(MobEffects.ABSORPTION);
            // With the last chance spent, a killing hit is still a heavy hit for the reflex.
            power.setAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE, 12000);
            player.setHealth(20.0F);
            boolean landed = scene.hit(1000.0F);
            scene.rule(!landed && KingCrimsonEraseFixture.state(player).eraseKind() == EraseKind.REFLEX
                            && KingCrimsonEraseFixture.erasing(player),
                    "with the last chance on its cooldown a killing hit was not escaped by the reflex: landed " + landed
                            + ", " + scene.erase());
            scene.finish("The last-chance erase");
        }));
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerPlayer player;
        private final StandPower power;
        private final List<String> broken = new ArrayList<>();

        private Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(12, 6);
            player = fixture.player(name, 2.5D, 2.0D, 1.5D);
            power = fixture.kingCrimson(player, 4);
        }

        /** A hit that armour and absorption take from, and that no difficulty scales. */
        private boolean hit(float amount) {
            player.invulnerableTime = 0;
            DamageSource cactus = player.damageSources().cactus();
            return player.hurt(cactus, amount);
        }

        private void restore() {
            player.setHealth(player.getMaxHealth());
            power.setStamina(power.getMaxStamina());
        }

        private boolean idle() {
            return KingCrimsonEraseFixture.state(player).isIdle();
        }

        private String erase() {
            KingCrimsonTemporalState state = KingCrimsonEraseFixture.state(player);
            return state.isIdle() ? "no erase" : "a " + state.eraseKind() + " erase of " + state.eraseLengthTicks() + " ticks";
        }

        private void rule(boolean holds, String broke) {
            if (!holds) {
                broken.add(broke);
            }
        }

        private void finish(String title) {
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(), title + ": " + String.join("; ", broken)));
            fixture.succeed();
        }
    }
}

package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAbility;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.controls.InputUseVanillaMapping;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * What a player's key or slot reaches of the Ultimate Lifeform's Hamon, against 1.16: the breath on the quick access key
 * (NonStandPowerType gave HAMON_BREATH to DefaultKey.mmb), the replaceAction of UltimateLifeformHeavyPunch,
 * UltimateLifeformBarrage and UltimateLifeformPlantInfusion, and their shift variations on the same slot. Presses go as
 * ClAbilityInputPacket.handlePress does: the key's base ability, the server's resolution of it, its check, the press.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateHamonReachGameTests {
    private static final short KEY = (short) 32_710;
    private static final String HEAVY_PUNCH = UltimateLifeformSpec.HEAVY_PUNCH.id;
    private static final String OVERDRIVE = UltimateLifeformSpec.HAMON_OVERDRIVE.id;
    private static final String BEAT = UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT.id;
    private static final String BARRAGE = UltimateLifeformSpec.ULTIMATE_BARRAGE.id;
    private static final String HAMON_BARRAGE = UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE.id;
    private static final String BLADE_BARRAGE = UltimateLifeformSpec.BLADE_BARRAGE.id;
    private static final String KICK = UltimateLifeformSpec.ULTIMATE_HAMON_KICK.id;
    private static final String PLANT = UltimateLifeformSpec.HAMON_PLANT_INFUSION.id;
    private static final String ORGANISM = UltimateLifeformSpec.HAMON_ORGANISM_INFUSION.id;
    private static final String MAGNETISM = UltimateLifeformSpec.HAMON_LIFE_MAGNETISM.id;
    private static final String SHOCK = UltimateLifeformSpec.HAMON_SHOCK.id;
    private static final String HYPNOTIZE = UltimateLifeformSpec.HYPNOTIZE.id;
    private static final String BREATH = UltimateLifeformSpec.HAMON_BREATH.id;

    private UltimateHamonReachGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hamonKeysAndSlotsAreTheOnes116Gave(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachControls", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c01");
        ControlSchemeTemplate template = holder.power.getPowerType().makeDefaultControlSchemeTemplate();
        helper.assertTrue(template.groups.size() == 1 && template.defaultGroup.hotbars.size() == 1,
                "the moves share one group and one skill bar");
        ControlSchemeTemplate.GroupTemplate group = template.defaultGroup;
        Pair<InputMethod, InputBindTemplate> breathBind = group.separateBinds.get(BREATH);
        helper.assertTrue(breathBind != null && breathBind.getFirst() == InputMethod.HOLD
                        && breathBind.getSecond() instanceof InputUseVanillaMapping mapping
                        && "jojo_ripples.key.hamon_breath".equals(mapping.keyMappingName),
                "1.16 gave the breath the quick access key: it must hold on the core's Hamon Breath key, got "
                        + breathBind);
        assertBind(helper, group, HEAVY_PUNCH, InputMethod.CLICK, InputKey.LMB, null);
        assertBind(helper, group, BEAT, InputMethod.CLICK, InputKey.LMB, InputKey.Modifier.SHIFT);
        assertBind(helper, group, BARRAGE, InputMethod.HOLD, InputKey.RMB, null);
        assertBind(helper, group, KICK, InputMethod.CLICK, InputKey.RMB, InputKey.Modifier.SHIFT);
        for (var bind : group.separateBinds.entrySet()) {
            helper.assertTrue(!(bind.getValue().getSecond() instanceof InputKey key)
                            || key.modifier != InputKey.Modifier.CONTROL,
                    "1.16 had one variation key; " + bind.getKey() + " is on a declared Ctrl");
        }
        List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots = group.hotbars.get(0).slots;
        for (String[] pair : new String[][] {
                { BEAT, HEAVY_PUNCH }, { KICK, BARRAGE }, { HYPNOTIZE, SHOCK }, { MAGNETISM, PLANT } }) {
            int found = 0;
            for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : slots) {
                boolean holdsVariation = slot.values().stream().anyMatch(by -> by.containsValue(pair[0]));
                if (holdsVariation) {
                    found++;
                    Map<InputMethod, String> variation = slot.get(InputKey.Modifier.SHIFT);
                    Map<InputMethod, String> base = slot.get(null);
                    helper.assertTrue(variation != null && variation.containsValue(pair[0])
                                    && base != null && base.containsValue(pair[1]),
                            pair[0] + " must sit under the variation key on the slot of " + pair[1] + ", got " + slot);
                }
            }
            helper.assertTrue(found == 1, pair[0] + " must be on one slot, is on " + found);
        }
        for (String noSlot : new String[] { OVERDRIVE, HAMON_BARRAGE, BLADE_BARRAGE, ORGANISM }) {
            helper.assertTrue(slots.stream().noneMatch(
                            slot -> slot.values().stream().anyMatch(by -> by.containsValue(noSlot))),
                    "1.16 reached " + noSlot + " through replaceAction alone, it has a slot here");
        }
        for (String ownSlot : new String[] {
                BREATH, UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE.id,
                UltimateLifeformSpec.ULTIMATE_HAMON_SENDO_OVERDRIVE.id,
                UltimateLifeformSpec.ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE.id,
                SHOCK, PLANT, HEAVY_PUNCH, BARRAGE }) {
            helper.assertTrue(slots.stream().anyMatch(slot -> slot.get(null) != null
                            && slot.get(null).containsValue(ownSlot)),
                    "1.16 had " + ownSlot + " on a slot of its own; the skill bar lost it");
        }
        holder.close();
        helper.succeed();
    }

    // 1.16: 11 attacks and 12 abilities, plus the mode's own that clAddMissingActions added.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theBarHoldsWhat116GaveForTheModeAlone(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachBar", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c0f");
        List<Map<InputKey.Modifier, Map<InputMethod, String>>> slots = holder.power.getPowerType()
                .makeDefaultControlSchemeTemplate().defaultGroup.hotbars.get(0).slots;
        for (UltimateLifeformSpec[] pair : new UltimateLifeformSpec[][] {
                { UltimateLifeformSpec.ULTIMATE_AGILITY, UltimateLifeformSpec.EVASION },
                { UltimateLifeformSpec.BLADE_DASH_ATTACK, UltimateLifeformSpec.BLADE_SLASH },
                { UltimateLifeformSpec.LIGHT_FLASH_DECOY, UltimateLifeformSpec.LIGHT_FLASH },
                { UltimateLifeformSpec.DIVINE_SANDSTORM, UltimateLifeformSpec.SMALL_SANDSTORM },
                { UltimateLifeformSpec.SCALE_SPLIT_SHOT, UltimateLifeformSpec.SCALE_SHOT },
                { UltimateLifeformSpec.PHANTOM_SUMMON, UltimateLifeformSpec.BUNNY_SUMMON },
                { UltimateLifeformSpec.ARMOR_FIRE_PROTECTION, UltimateLifeformSpec.ARMOR_PROTECTION },
                { UltimateLifeformSpec.ENDERMAN_TELEPORT, UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH },
                { UltimateLifeformSpec.ABSOLUTE_IMMORTALITY, UltimateLifeformSpec.UNLIMITED_POWER } }) {
            helper.assertTrue(slots.stream().anyMatch(slot -> slot.get(null) != null
                            && slot.get(null).containsValue(pair[1].id)
                            && slot.get(InputKey.Modifier.SHIFT) != null
                            && slot.get(InputKey.Modifier.SHIFT).containsValue(pair[0].id)),
                    pair[0].id + " must be the variation of " + pair[1].id + "'s slot, as 1.16's shiftVariationOf");
            helper.assertTrue(slots.stream().noneMatch(slot -> slot.get(null) != null
                            && slot.get(null).containsValue(pair[0].id)),
                    pair[0].id + " had no slot of its own in 1.16");
        }
        helper.assertTrue(slots.size() == 30, "23 slots of 1.16's arrays and the 7 of its modes, got " + slots.size());
        Map<PillarmanMode, Integer> barPerMode = Map.of(
                PillarmanMode.NONE, 23, PillarmanMode.LIGHT, 25, PillarmanMode.WIND, 25, PillarmanMode.HEAT, 26);
        for (var expected : barPerMode.entrySet()) {
            holder.data.setPreviousMode(holder.player, expected.getKey());
            int drawn = 0;
            for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : slots) {
                if (slot.get(null).values().stream()
                        .anyMatch(id -> holder.power.getMoveset().getAbility(id).isAbilityAvailable(holder.power))) {
                    drawn++;
                }
            }
            helper.assertTrue(drawn == expected.getValue(),
                    expected.getKey() + " must have " + expected.getValue() + " slots on the bar, has " + drawn);
            for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
                helper.assertTrue(holder.power.getMoveset().getAbility(spec.id).isAbilityAvailable(holder.power)
                                == (spec.requiredMode == null || spec.requiredMode == expected.getKey()),
                        spec.id + " in " + expected.getKey() + " must show for its own mode only");
            }
        }
        holder.close();
        helper.succeed();
    }

    private static void assertBind(GameTestHelper helper, ControlSchemeTemplate.GroupTemplate group, String ability,
            InputMethod method, InputKey button, InputKey.Modifier modifier) {
        Pair<InputMethod, InputBindTemplate> bind = group.separateBinds.get(ability);
        helper.assertTrue(bind != null && bind.getFirst() == method && bind.getSecond() instanceof InputKey key
                        && key.device == button.device && key.keyCode == button.keyCode && key.modifier == modifier,
                ability + " must be " + method + " on " + button.keyCode + " with modifier " + modifier + ", got "
                        + (bind != null ? bind.getFirst() + " " + bind.getSecond() : "no bind"));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void theBreathKeyChargesAndTheChargeTurnsTheKeysAsIn116(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachBreath", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c02");
        holder.expect(helper, HEAVY_PUNCH, HEAVY_PUNCH, "no charge: the left click is the heavy punch");
        holder.expect(helper, BEAT, HEAVY_PUNCH,
                "no charge: the heavy punch had no shift variation, so the variation key is still the heavy punch");
        holder.expect(helper, BARRAGE, BARRAGE, "no charge: the right click is the Ultimate Barrage");
        helper.runAfterDelay(1, () -> helper.assertTrue(holder.press(BREATH, InputMethod.HOLD),
                "the breath key was refused: " + holder.messages));
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(holder.data.getHeldAbility() == UltimateLifeformSpec.HAMON_BREATH,
                    "the breath key did not start the breath: " + holder.running());
            int charge = holder.data.getHamonCharge();
            helper.assertTrue(charge >= 800 && charge % 100 == 0,
                    "1.16 breathed in 100 a tick; after 10 ticks the charge is " + charge);
            holder.release();
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(holder.data.getHeldAbility() == null, "the breath outlived its key");
            holder.expect(helper, HEAVY_PUNCH, OVERDRIVE, "charged: the left click is the Hamon Overdrive");
            holder.expect(helper, BEAT, BEAT, "charged: the variation key on the left click is the Hamon Beat");
            holder.expect(helper, BARRAGE, HAMON_BARRAGE, "charged: the right click is the Hamon Barrage");
            holder.close();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void aChargedLeftClickIsTheHamonOverdriveAndAnEmptyOneTheHeavyPunch(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachClick", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c03");
        Cow cow = cow(helper);
        holder.data.addHamonCharge(1_000);
        holder.aim(cow);
        float[] health = new float[1];
        helper.runAfterDelay(1, () -> {
            health[0] = cow.getHealth();
            helper.assertTrue(holder.click(HEAVY_PUNCH), "the charged left click was refused: " + holder.messages);
        });
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD) && cow.getHealth() < health[0],
                    "1.16 replaced the charged heavy punch with the Hamon Overdrive: a Hamon hit and burn at once;"
                            + " health " + health[0] + " to " + cow.getHealth());
            helper.assertTrue(holder.data.getHamonCharge() == 1_000,
                    "1.16's Hamon Overdrive asked for the charge and spent none: " + holder.data.getHamonCharge());
        });
        helper.runAfterDelay(6, () -> {
            holder.data.consumeHamonCharge(holder.data.getHamonCharge());
            cow.removeEffect(UltimateLifeformAddon.HAMON_SPREAD);
            helper.assertTrue(holder.click(HEAVY_PUNCH), "the empty left click was refused: " + holder.messages);
        });
        helper.runAfterDelay(8, () -> helper.assertTrue(
                holder.specRunning() == UltimateLifeformSpec.HEAVY_PUNCH,
                "without a charge the left click is the heavy punch, running " + holder.running()));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                    "the heavy punch of an empty left click burned with Hamon");
            cow.discard();
            holder.close();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aChargedRightHoldIsTheHamonBarrageUntilItsKeyEnds(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachHold", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c04");
        Cow cow = cow(helper);
        holder.data.addHamonCharge(100);
        holder.aim(cow);
        helper.runAfterDelay(1, () -> helper.assertTrue(holder.press(BARRAGE, InputMethod.HOLD),
                "the charged right hold was refused: " + holder.messages));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(holder.specRunning() == UltimateLifeformSpec.ULTIMATE_HAMON_BARRAGE,
                    "1.16's held Hamon Barrage went on at no charge: running " + holder.running());
            helper.assertTrue(holder.data.getHamonCharge() == 0,
                    "the Hamon Barrage drains 20 a tick: " + holder.data.getHamonCharge());
            holder.expect(helper, BARRAGE, BARRAGE, "the key's next press at no charge is the Ultimate Barrage");
            holder.release();
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(holder.press(BARRAGE, InputMethod.HOLD),
                    "the empty right hold was refused: " + holder.messages);
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(holder.specRunning() == UltimateLifeformSpec.ULTIMATE_BARRAGE,
                    "without a charge the right hold is the Ultimate Barrage: running " + holder.running());
            holder.release();
            holder.data.setPreviousMode(holder.player, PillarmanMode.LIGHT);
            holder.data.addHamonCharge(1_000);
            holder.expect(helper, BARRAGE, BLADE_BARRAGE,
                    "1.16 asked for the Light mode first: charged or not it is the Blade Barrage");
            cow.discard();
            holder.close();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void theVariationKeyOnTheRightButtonIsTheKickAtItsCharge(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachKick", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c05");
        Cow cow = cow(helper);
        holder.aim(cow);
        helper.runAfterDelay(1, () -> {
            holder.expect(helper, KICK, KICK, "the Ultimate Barrage's variation is the kick at any charge");
            helper.assertTrue(!holder.resolved(KICK).conditionCheck.isPositive(),
                    "1.16's kick asks for 1000 charge; it passed at none");
            holder.data.addHamonCharge(1_000);
            helper.assertTrue(holder.click(KICK), "the kick at 1000 charge was refused: " + holder.messages);
        });
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(holder.specRunning() == UltimateLifeformSpec.ULTIMATE_HAMON_KICK,
                    "the variation key on the right button did not start the kick: " + holder.running());
            helper.assertTrue(holder.data.getHamonCharge() == 500,
                    "1.16's kick spends half its 1000: " + holder.data.getHamonCharge());
            cow.discard();
            holder.close();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void theVariationKeyOnTheLeftButtonIsTheBeatAtItsCharge(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachBeat", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c07");
        Cow cow = cow(helper);
        holder.aim(cow);
        helper.runAfterDelay(1, () -> {
            holder.data.addHamonCharge(1_000);
            holder.expect(helper, BEAT, BEAT, "charged: the Beat");
            helper.assertTrue(holder.click(BEAT), "the Beat at 1000 charge was refused: " + holder.messages);
        });
        helper.runAfterDelay(3, () -> helper.assertTrue(
                holder.specRunning() == UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT,
                "the variation key on the left button did not start the Beat: " + holder.running()));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(cow.hasEffect(UltimateLifeformAddon.HAMON_SPREAD),
                    "1.16's Beat burned its target at its fifth tick");
            cow.discard();
            holder.close();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void thePlantSlotIsTheOrganismInfusionAsIn116(GameTestHelper helper) {
        Holder holder = holder(helper, "UltimateReachInfusion", "d4f3b5c7-3e4a-4f6b-9c7d-8e9f0a1b2c06");
        Cow cow = cow(helper);
        holder.data.addHamonCharge(1_000);
        holder.aim(cow);
        helper.runAfterDelay(1, () -> {
            holder.expect(helper, PLANT, ORGANISM, "1.16's Plant Infusion replaced itself with the Organism Infusion");
            helper.assertTrue(holder.click(PLANT), "the slot refused a living mob: " + holder.messages);
        });
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(UltimateHamonEntityCharge.get(cow).hasHamonCharge(),
                    "the Plant Infusion slot on a living mob infuses it in 1.16; the cow holds no charge");
            UltimateHamonEntityCharge.get(cow).setHamonCharge(null);
            cow.discard();
            holder.close();
            helper.succeed();
        });
    }

    private static Cow cow(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        if (cow == null) {
            throw new IllegalStateException("Cow fixture failed");
        }
        cow.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 4))));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000.0D);
        cow.setHealth(1_000.0F);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static Holder holder(GameTestHelper helper, String name, String uuid) {
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(Component message, boolean overlay) {
                if (overlay) {
                    messages.add(message);
                }
            }
        };
        UltimateLifeformFixtures.claim(helper, player);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setOnGround(true);
        player.setGameMode(GameType.SURVIVAL);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException("Hamon reach test player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException("Hamon reach test power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        return new Holder(player, power, data, messages);
    }

    private record Holder(ServerPlayer player, PlayerPower power, UltimateLifeformData data,
            List<Component> messages) {
        // a fresh resolution: the power caches its moves within a tick
        AbilityConditionCheck resolved(String base) {
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            AbilityConditionCheck resolved = available.getContextVariationContainer(base);
            if (resolved == null) {
                throw new IllegalStateException(base + " is not available to the user");
            }
            return resolved;
        }

        void expect(GameTestHelper helper, String base, String ability, String why) {
            String got = resolved(base).ability.name();
            helper.assertTrue(ability.equals(got), why + ": the key of " + base + " resolved to " + got);
        }

        boolean press(String base, InputMethod method) {
            AbilityConditionCheck resolved = resolved(base);
            if (!AbilityInput.withConditionCheck(resolved, player, method)) {
                return false;
            }
            BufferingState buffering = BufferingState.clickCanBuffer();
            Object held = AbilityInput.keyPress(KEY, resolved.ability, player, null, method, 0.0F, buffering,
                    power.getAbility(base).getAbilityId());
            return !buffering.isBuffered() && (held != null || buffering.isActionSuccess);
        }

        boolean click(String base) {
            try {
                return press(base, InputMethod.CLICK);
            }
            finally {
                release();
            }
        }

        void release() {
            AbilityInput.keyRelease(KEY, player);
        }

        void aim(Entity target) {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(target));
        }

        EntityActionInstance running() {
            return LivingComponentAction.getCurEntityAction((LivingEntity) player);
        }

        UltimateLifeformSpec specRunning() {
            EntityActionInstance action = running();
            return action != null && !action.isOver() && action.ability instanceof UltimateLifeformAbility ultimate
                    ? ultimate.spec()
                    : null;
        }

        void close() {
            release();
            power.setPowerType(null);
            player.discard();
        }
    }
}

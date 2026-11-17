package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.human.PhantomAbilityAllowList;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDriver;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * The audit before delivery (EPITAPH-ARCH, ruling R13). For every Stand type the server has, a creature that bears it is
 * copied into a room with a foe in reach, and its copy presses every ability of its moveset that the table of phantoms
 * allows, the later blows of a combo too, each in a prediction of its own: held, let go, and lived on from. What must
 * hold for a type is in the broken rules of its line; what was pressed and what was not, and why, is in its line too.
 * <p>
 * Callable from the test of any module, with a player and places of its own: nothing here asks for the fixture of this
 * add-on. All of it happens within the call, in one tick of the server.
 */
public final class KingCrimsonEpitaphAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How long the copy holds a key, and how long the room lives on after it let go: what an ability sets off comes later. */
    private static final int HELD_TICKS = 10;
    private static final int AFTER_TICKS = 30;

    /**
     * One Stand type. Audited when its copy was made with its Stand out in the room and took at least one press: a type of
     * which none was taken, or that has no Stand entity, is one nobody audited.
     */
    public record Line(String type, boolean audited, List<String> taken, List<String> notTaken, List<String> broken) {
        @Override
        public String toString() {
            return type + (audited ? "" : " NOT AUDITED") + " taken " + taken + (notTaken.isEmpty() ? "" : ", not taken " + notTaken)
                    + (broken.isEmpty() ? "" : ", BROKEN " + broken);
        }
    }

    public record Report(List<Line> lines, int presses) {
        /** Every rule that broke, with the type it broke for: empty when the audit passed. */
        public List<String> broken() {
            List<String> all = new ArrayList<>();
            for (Line line : lines) {
                for (String rule : line.broken()) {
                    all.add(line.type() + ": " + rule);
                }
            }
            return all;
        }

        public List<String> audited() {
            return lines.stream().filter(Line::audited).map(Line::type).toList();
        }

        public List<String> notAudited() {
            return lines.stream().filter(line -> !line.audited()).map(Line::type).toList();
        }
    }

    private KingCrimsonEpitaphAudit() {}

    /**
     * @param user the player whose predictions these are: any server player, since no tick of the server passes meanwhile
     * @param bearerAt where the bearer of each Stand stands, on a floor
     * @param foeAt where its foe stands: two blocks from the bearer, in reach of a Stand beside its user
     * @param from one corner of the box whose blocks and entities must be as before
     * @param to the other corner
     */
    public static Report run(ServerPlayer user, Vec3 bearerAt, Vec3 foeAt, BlockPos from, BlockPos to) {
        ServerLevel level = user.serverLevel();
        List<Line> lines = new ArrayList<>();
        int presses = 0;
        for (StandType type : JojoRegistries.DEFAULT_STANDS_REG) {
            String name = String.valueOf(JojoRegistries.DEFAULT_STANDS_REG.getKey(type));
            List<String> taken = new ArrayList<>();
            List<String> notTaken = new ArrayList<>();
            List<String> broken = new ArrayList<>();
            Zombie bearer = stander(level, bearerAt);
            Zombie foe = stander(level, foeAt);
            boolean audited;
            try {
                audited = one(user, type, bearer, foe, from, to, taken, notTaken, broken);
            }
            finally {
                bearer.discard();
                foe.discard();
            }
            if (EpitaphSessions.any) {
                broken.add("a prediction is left running");
            }
            Line line = new Line(name, audited, List.copyOf(taken), List.copyOf(notTaken), List.copyOf(broken));
            presses += taken.size();
            lines.add(line);
            LOGGER.info("Epitaph[audit] {}", line);
        }
        return new Report(List.copyOf(lines), presses);
    }

    // A creature that stands where it is put, is not pushed about and does not burn in the light of day.
    private static Zombie stander(ServerLevel level, Vec3 at) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("The audit could not make a zombie");
        }
        zombie.moveTo(at.x, at.y, at.z, 90.0F, 0.0F);
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        zombie.setHealth(200.0F);
        if (!level.addFreshEntity(zombie)) {
            throw new IllegalStateException("The audit could not add a zombie to the level");
        }
        return zombie;
    }

    private static boolean one(ServerPlayer user, StandType type, Zombie bearer, Zombie foe, BlockPos from, BlockPos to,
            List<String> taken, List<String> notTaken, List<String> broken) {
        ServerLevel level = user.serverLevel();
        StandPower power = PowerClass.STAND.attachGet(bearer);
        boolean given = StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED;
        boolean out = false;
        if (given && type instanceof EntityStandType) {
            power.skipProgression();
            power.setResolveLevel(4);
            out = type.summon(bearer, power) && power.getSummonedStandEntity() != null;
        }
        if (!out) {
            // Nothing to copy into a room. If its moveset has a name of the table all the same, nobody audited that.
            List<String> named = new ArrayList<>();
            if (given) {
                for (Ability ability : power.getMoveset().abilities.values()) {
                    if (PhantomAbilityAllowList.allows(ability, power)) {
                        named.add(ability.name());
                    }
                }
            }
            notTaken.add((given ? "it has no Stand entity that could be summoned" : "it could not be given to a creature")
                    + (named.isEmpty() ? "" : ", and its moveset has names of the table: " + named));
            return false;
        }
        power.getSummonedStandEntity().summonLockTicks = 0;
        power.setStamina(power.getMaxStamina());
        KingCrimsonEpitaphWorldPicture before = KingCrimsonEpitaphWorldPicture.of(level, from, to);
        int leaks = EpitaphSandbox.leaks();
        // Found with the question the table itself is asked, so that the audit cannot fall behind the table.
        List<String> allowed = new ArrayList<>();
        for (Ability ability : power.getMoveset().abilities.values()) {
            if (PhantomAbilityAllowList.allows(ability, power)) {
                allowed.add(ability.name());
            } else if (PhantomAbilityAllowList.excludes(ability, power)) {
                notTaken.add(ability.name() + " (excluded for this Stand)");
            }
        }
        // In an order that is the same on every server.
        allowed.sort(Comparator.naturalOrder());
        if (allowed.isEmpty()) {
            notTaken.add("its moveset has no name of the table");
        }
        // A prediction for each: its copy presses it with nothing else in progress, holds the key, lets it go and lives on.
        for (String ability : allowed) {
            power.setStamina(power.getMaxStamina());
            EpitaphSession session = EpitaphSessions.predict(user, List.of(bearer, foe));
            if (session == null) {
                broken.add("no prediction began for its bearer");
                break;
            }
            try {
                press(session, bearer, foe, ability, taken, notTaken, broken);
            }
            finally {
                session.end(EpitaphEnd.CANCELLED);
            }
        }
        List<String> changed = before.differences(KingCrimsonEpitaphWorldPicture.of(level, from, to));
        if (!changed.isEmpty()) {
            broken.add("the world is not as it was: " + changed.size() + " differences, the first: " + changed.get(0));
        }
        if (EpitaphSandbox.leaks() != leaks) {
            broken.add("its copy reached past the room: " + EpitaphSandbox.firstLeak());
        }
        TimeStopState stops = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        if (stops.getInstance(bearer.getId()).isPresent() || TimeStopState.shouldFreezeOnServer(foe)) {
            broken.add("time is stopped in the world");
        }
        type.forceUnsummon(bearer, power);
        return !taken.isEmpty();
    }

    // In the room: the copy presses one ability at the foe, holds its key, lets it go, and the room lives on a while.
    private static void press(EpitaphSession session, Zombie bearer, Zombie foe, String name, List<String> taken,
            List<String> notTaken, List<String> broken) {
        LivingEntity copy = session.room().phantom(bearer.getId()) instanceof LivingEntity living ? living : null;
        StandPower copied = copy != null ? StandPower.get(copy) : null;
        StandEntity twin = copied != null ? copied.getSummonedStandEntity() : null;
        Entity target = session.room().phantom(foe.getId());
        if (twin == null || target == null) {
            note(broken, "the copy of its bearer has no Stand out in the room");
            return;
        }
        Ability pressable = copied.getAbility(name);
        if (pressable == null) {
            notTaken.add(name + " (the power of the copy does not have it)");
            return;
        }
        HeldInputEntry held;
        ConditionCheck check;
        EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
        try {
            EpitaphSandbox.setActor(copy);
            ActionTarget aim = new ActionTarget(target);
            LivingComponentAction.getComponent(copy).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(twin).entityAim.setTarget(aim.copy());
            check = pressable.checkConditions(copied);
            held = AbilityInput.keyPressMob(pressable, copy, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
        }
        finally {
            EpitaphSandbox.exit();
        }
        if (held != null) {
            taken.add(name);
        } else {
            notTaken.add(name + " (" + (check.isPositive() ? "the core did not take the press" : "its conditions refuse it"
                    + (check.getWarning() != null ? ": " + check.getWarning().getString() : "")) + ")");
        }
        for (int tick = 0; tick < HELD_TICKS + AFTER_TICKS && session.room().tick() < session.record().foresight() - 1; tick++) {
            if (tick == HELD_TICKS && held != null) {
                EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
                try {
                    EpitaphSandbox.setActor(copy);
                    AbilityInput.keyRelease(held.keyId, copy);
                }
                finally {
                    EpitaphSandbox.exit();
                }
            }
            SandboxDriver.step(session.room());
        }
        int threw = session.room().counts().phantomFaults;
        if (threw > 0) {
            note(broken, "with " + name + " pressed, the tick of a phantom threw " + threw + " times");
        }
    }

    private static void note(List<String> broken, String rule) {
        if (!broken.contains(rule)) {
            broken.add(rule);
        }
    }
}

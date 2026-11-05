package rotp.addons.rhcp;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Beta2 RHCPEntity.moveStandManually while canTravel, with its smartMove, diodeCaseR / diodeCaseL and
 * getCardinalPoint, and the travel direction RHCPGettingIntoTheMechanism.standPerform gives on entry. The codes are
 * Beta2's: 1 south, 2 west, 3 north, 4 east, 0 none. A button, lever, tripwire hook, wall torch or diode facing that
 * way has the same code (getDirectionAction), a floor or ceiling button or lever 5 or 6, and 7 is a block of a kind
 * the caller did not ask for.
 */
public final class RhcpConductorTravel {
    public static final int NONE = 0;
    public static final int SOUTH = 1;
    public static final int WEST = 2;
    public static final int NORTH = 3;
    public static final int EAST = 4;
    // Beta2 RHCPEntity.standCount after a turn: the next turn waits until it has counted down to -1.
    public static final int TURN_TICKS = 5;

    private RhcpConductorTravel() {}

    /** One tick: the conductor RHCP hops into (null: it stays), its travel direction and its turn wait after it. */
    public record Travel(@Nullable BlockPos next, int direction, int turnWait) {}

    /**
     * The keys of one tick (strafe left +, forward +), after the manual movement locks. A strafe key turns the
     * direction a quarter when the last turn's wait is over; forward or back follows the conductors along it; jump
     * or sneak alone goes straight up or down.
     */
    public static Travel travel(Level level, BlockPos pos, int direction, int turnWait,
            float strafe, float forward, boolean jumping, boolean sneaking) {
        if (!jumping && !sneaking && forward == 0.0F && strafe == 0.0F) {
            return new Travel(null, direction, turnWait);
        }
        if (strafe != 0.0F && turnWait == -1) {
            direction = turned(direction, strafe);
            turnWait = TURN_TICKS;
        }
        if (!RhcpService.isConductor(level.getBlockState(pos))) {
            return new Travel(null, direction, turnWait);
        }
        if (forward != 0.0F) {
            return forward(level, pos, direction, turnWait, forward, jumping);
        }
        if (jumping) {
            return new Travel(up(level, pos), direction, turnWait);
        }
        if (sneaking) {
            return new Travel(down(level, pos), direction, turnWait);
        }
        return new Travel(null, direction, turnWait);
    }

    // Beta2 RHCPEntity.tick: standCount runs down by one a tick to -1.
    public static int waited(int turnWait) {
        return turnWait >= 0 ? turnWait - 1 : turnWait;
    }

    // Right (strafe < 0) turns clockwise, left counterclockwise; no direction, no turn.
    public static int turned(int direction, float strafe) {
        return switch (direction) {
            case SOUTH -> strafe < 0.0F ? WEST : EAST;
            case WEST -> strafe < 0.0F ? NORTH : SOUTH;
            case NORTH -> strafe < 0.0F ? EAST : WEST;
            case EAST -> strafe < 0.0F ? SOUTH : NORTH;
            default -> direction;
        };
    }

    /**
     * RHCPGettingIntoTheMechanism.standPerform, by the head yaw of RHCP under manual control, else of its user. The
     * first of south, north, west, east that a wire connects to (any side for other blocks) and that the look points
     * to by Beta2's yaw bounds turns RHCP the other way, back against the look. Beta2 read the raw yaw, so the result
     * hung on how far the player had turned; here it is a bearing in [0, 360). With no side, the direction stays.
     */
    public static int entryDirection(BlockState state, float yHeadRot, int current) {
        float yaw = Mth.positiveModulo(yHeadRot, 360.0F);
        boolean towardSouth = yaw < 90.0F || yaw - 360.0F > -90.0F && yaw - 360.0F < 90.0F;
        boolean towardWest = yaw - 90.0F < 90.0F;
        boolean towardNorth = yaw - 180.0F < 90.0F;
        boolean towardEast = yaw - 270.0F < 90.0F;
        boolean south = towardSouth;
        boolean west = towardWest;
        boolean north = towardNorth;
        boolean east = towardEast;
        // For other blocks Beta2 or-ed each side's conductor test with the bare yaw test, so only the yaw counts.
        if (state.getBlock() instanceof RedStoneWireBlock) {
            south = towardSouth && state.getValue(RedStoneWireBlock.SOUTH).isConnected();
            west = towardWest && state.getValue(RedStoneWireBlock.WEST).isConnected();
            north = towardNorth && state.getValue(RedStoneWireBlock.NORTH).isConnected();
            east = towardEast && state.getValue(RedStoneWireBlock.EAST).isConnected();
        }
        if (south) {
            return NORTH;
        }
        if (north) {
            return SOUTH;
        }
        if (west) {
            return EAST;
        }
        if (east) {
            return WEST;
        }
        return current;
    }

    @Nullable
    public static Direction direction(int code) {
        return switch (code) {
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            default -> null;
        };
    }

    public static int code(Direction direction) {
        return switch (direction) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case NORTH -> NORTH;
            case EAST -> EAST;
            default -> NONE;
        };
    }

    private static Travel forward(Level level, BlockPos pos, int d, int turnWait, float forward, boolean jumping) {
        BlockState state = level.getBlockState(pos);
        int cardinalPoint = cardinalPoint(state, false, false, false);
        boolean south;
        boolean west;
        boolean north;
        boolean east;
        if (state.getBlock() instanceof RedStoneWireBlock) {
            cardinalPoint = cardinalPoint(state, true, false, false);
            south = wireSide(level, pos, state, SOUTH);
            west = wireSide(level, pos, state, WEST);
            north = wireSide(level, pos, state, NORTH);
            east = wireSide(level, pos, state, EAST);
        }
        else {
            south = blockSide(level, pos, SOUTH, d, forward, cardinalPoint);
            west = blockSide(level, pos, WEST, d, forward, cardinalPoint);
            north = blockSide(level, pos, NORTH, d, forward, cardinalPoint);
            east = blockSide(level, pos, EAST, d, forward, cardinalPoint);
        }
        // A side counts only when the way straight on (or straight back) does not.
        south = (d == WEST ? (forward > 0.0F ? !west : !east) : d != EAST || (forward > 0.0F ? !east : !west)) && south;
        west = (d == SOUTH ? (forward > 0.0F ? !south : !north) : d != NORTH || (forward > 0.0F ? !north : !south)) && west;
        north = (d == WEST ? (forward > 0.0F ? !west : !east) : d != EAST || (forward > 0.0F ? !east : !west)) && north;
        east = (d == SOUTH ? (forward > 0.0F ? !south : !north) : d != NORTH || (forward > 0.0F ? !north : !south)) && east;
        BlockPos next = null;
        boolean front;
        boolean back;
        boolean right;
        boolean left;
        int rightCode;
        int leftCode;
        switch (d) {
            case SOUTH -> {
                front = south; back = north; right = west; left = east; rightCode = WEST; leftCode = EAST;
            }
            case WEST -> {
                front = west; back = east; right = north; left = south; rightCode = NORTH; leftCode = SOUTH;
            }
            case NORTH -> {
                front = north; back = south; right = east; left = west; rightCode = EAST; leftCode = WEST;
            }
            case EAST -> {
                front = east; back = west; right = south; left = north; rightCode = SOUTH; leftCode = NORTH;
            }
            default -> {
                return new Travel(null, d, turnWait);
            }
        }
        int direction = d;
        if (front || back) {
            next = smartMove(level, pos, d, forward, jumping, front, back, right, left, cardinalPoint, true);
            if ((next == null || !isConductor(level, next)) && (right || left)) {
                next = smartMove(level, pos, d, forward, jumping, front, back, right, left, cardinalPoint, false);
                direction = right && left ? d
                        : forward > 0.0F ? (right ? rightCode : leftCode) : (right ? leftCode : rightCode);
            }
        }
        if (next != null && !level.isEmptyBlock(next) && isConductor(level, next)) {
            return new Travel(next.immutable(), direction, turnWait);
        }
        return new Travel(null, direction, turnWait);
    }

    // A wire's side: its own connection, a conductor a step up past a block or a step down, one on either diagonal,
    // or past the next block a wall button, lever, hook or diode facing that way, or a diode taking its input from it.
    private static boolean wireSide(Level level, BlockPos pos, BlockState state, int side) {
        Direction way = direction(side);
        BlockPos sidePos = pos.relative(way);
        BlockPos rightPos = sidePos.relative(way.getClockWise());
        BlockPos leftPos = sidePos.relative(way.getCounterClockWise());
        return state.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(way)).isConnected()
                || !level.isEmptyBlock(sidePos) && isConductor(level, sidePos.above())
                || isConductor(level, sidePos.below())
                || isConductor(level, rightPos)
                || isConductor(level, leftPos)
                || pastTheNext(level, sidePos, side);
    }

    // Another conductor's side: a conductor there, the block's own facing through its wall, a step up or down, a
    // diagonal one the diode rules let through, or the same reach past the next block as a wire's.
    private static boolean blockSide(Level level, BlockPos pos, int side, int d, float forward, int cardinalPoint) {
        Direction way = direction(side);
        BlockPos sidePos = pos.relative(way);
        BlockPos rightPos = sidePos.relative(way.getClockWise());
        BlockPos leftPos = sidePos.relative(way.getCounterClockWise());
        return isConductor(level, sidePos)
                || cardinalPoint == opposite(side)
                || !level.isEmptyBlock(sidePos) && isConductor(level, sidePos.above())
                || isConductor(level, sidePos.below())
                || isConductor(level, rightPos) && diodeCaseR(level, d, forward, cardinalPoint, rightPos)
                || isConductor(level, leftPos) && diodeCaseL(level, d, forward, cardinalPoint, leftPos)
                || pastTheNext(level, sidePos, side);
    }

    private static boolean pastTheNext(Level level, BlockPos sidePos, int side) {
        BlockPos predi = sidePos.relative(direction(side));
        if (!isConductor(level, predi)) {
            return false;
        }
        BlockState prediState = level.getBlockState(predi);
        return cardinalPoint(prediState, true, false, false) == side
                || cardinalPoint(prediState, true, false, true) == opposite(side) && !level.isEmptyBlock(sidePos);
    }

    private static int opposite(int code) {
        return code > 2 ? code - 2 : code + 2;
    }

    private static boolean diodeCaseR(Level level, int direction, float forward, int buttonOrLever, BlockPos r) {
        int diodeR = diodeValue(level, r);
        return diodeR != 0 && diodeR < 5
                ? direction == 1 && buttonOrLever == 2 && (forward > 0.0F ? diodeR == 1 : diodeR == 3)
                        || direction == 2 && buttonOrLever == 3 && (forward > 0.0F ? diodeR == 2 : diodeR == 4)
                        || direction == 3 && buttonOrLever == 4 && (forward > 0.0F ? diodeR == 3 : diodeR == 1)
                        || direction == 4 && buttonOrLever == 1 && (forward > 0.0F ? diodeR == 4 : diodeR == 2)
                : true;
    }

    private static boolean diodeCaseL(Level level, int direction, float forward, int buttonOrLever, BlockPos l) {
        int diodeL = diodeValue(level, l);
        return diodeL != 0 && diodeL < 5
                ? direction == 1 && buttonOrLever == 4 && (forward > 0.0F ? diodeL == 1 : diodeL == 3)
                        || direction == 2 && buttonOrLever == 1 && (forward > 0.0F ? diodeL == 2 : diodeL == 4)
                        || direction == 3 && buttonOrLever == 2 && (forward > 0.0F ? diodeL == 3 : diodeL == 1)
                        || direction == 4 && buttonOrLever == 3 && (forward > 0.0F ? diodeL == 4 : diodeL == 2)
                : true;
    }

    // Beta2 RHCPEntity.smartMove, line for line: with thereIsAWay the way straight on (or back), else the side one.
    @Nullable
    private static BlockPos smartMove(Level level, BlockPos currentPos, int d, float forward, boolean jumping,
            boolean front, boolean back, boolean right, boolean left, int buttonOrLever, boolean thereIsAWay) {
        BlockPos topPos = currentPos.above();
        boolean direction = d == 1 || d == 4;
        boolean directionX = d == 2 || d == 4;
        boolean canGoUp = level.isEmptyBlock(topPos) || !fullBlock(level, topPos);
        BlockPos nextPos;
        if (thereIsAWay) {
            nextPos = forward > 0.0F && front
                    ? currentPos.offset(directionX ? (direction ? 1 : -1) : 0, 0, directionX ? 0 : (direction ? 1 : -1))
                    : forward < 0.0F && back
                            ? currentPos.offset(directionX ? (direction ? -1 : 1) : 0, 0,
                                    directionX ? 0 : (direction ? -1 : 1))
                            : null;
            boolean canGoDown = nextPos != null && (level.isEmptyBlock(nextPos) || !fullBlock(level, nextPos));
            if (nextPos != null && !isConductor(level, nextPos)) {
                BlockPos nextPosVariant = forward > 0.0F
                        ? currentPos.offset(directionX ? (direction ? 2 : -2) : 0, 0,
                                directionX ? 0 : (direction ? 2 : -2))
                        : currentPos.offset(directionX ? (direction ? -2 : 2) : 0, 0,
                                directionX ? 0 : (direction ? -2 : 2));
                BlockState variantState = level.getBlockState(nextPosVariant);
                BlockState currentState = level.getBlockState(currentPos);
                int cardinalPoint = cardinalPoint(variantState, false, buttonOrLever != 0, false);
                int torch = currentState.getBlock() instanceof RedstoneWallTorchBlock
                        ? cardinalPoint(currentState, true, true, false)
                        : variantState.getBlock() instanceof RedstoneWallTorchBlock
                                ? cardinalPoint(variantState, false, true, false) : 0;
                int diode = variantState.getBlock() instanceof DiodeBlock ? diodeValue(level, nextPosVariant) : 0;
                boolean torchRot = cardinalPoint == 1 && buttonOrLever == 3
                        || cardinalPoint == 2 && buttonOrLever == 4
                        || cardinalPoint == 3 && buttonOrLever == 1
                        || cardinalPoint == 4 && buttonOrLever == 2
                        || torch == 1 && cardinalPoint == 3
                        || torch == 2 && cardinalPoint == 4
                        || torch == 3 && cardinalPoint == 1
                        || torch == 4 && cardinalPoint == 2
                        || torch == 0;
                boolean diodeRot = d == buttonOrLever && diode == buttonOrLever
                        || d == 1 && buttonOrLever == 3 && diode == 3
                        || d == 2 && buttonOrLever == 4 && diode == 4
                        || d == 3 && buttonOrLever == 1 && diode == 1
                        || d == 4 && buttonOrLever == 2 && diode == 2
                        || d == 1 && buttonOrLever == 1 && diode == 3
                        || d == 2 && buttonOrLever == 2 && diode == 4
                        || d == 3 && buttonOrLever == 3 && diode == 1
                        || d == 4 && buttonOrLever == 4 && diode == 2;
                boolean canCross = diode != 0 && diode < 5 && cardinalPoint != 0 && torch < 1
                        ? diodeRot
                        : (forward > 0.0F
                                ? torchRot && (d == cardinalPoint
                                        || d == 1 && buttonOrLever == 3
                                        || d == 2 && buttonOrLever == 4
                                        || d == 3 && buttonOrLever == 1
                                        || d == 4 && buttonOrLever == 2)
                                : torchRot && (d == 1 && cardinalPoint == 3
                                        || d == 2 && cardinalPoint == 4
                                        || d == 3 && cardinalPoint == 1
                                        || d == 4 && cardinalPoint == 2
                                        || d == buttonOrLever))
                                && buttonOrLever < 5 && cardinalPoint < 5;
                BlockPos rightPredi = nextPos.offset(directionX ? 0 : (direction ? -1 : 1), 0,
                        directionX ? (direction ? 1 : -1) : 0);
                int cardinalPointR = cardinalPoint(level.getBlockState(rightPredi), false, buttonOrLever != 0, false);
                int diodeR = diodeValue(level, rightPredi);
                BlockPos leftPredi = nextPos.offset(directionX ? 0 : (direction ? 1 : -1), 0,
                        directionX ? (direction ? -1 : 1) : 0);
                int cardinalPointL = cardinalPoint(level.getBlockState(leftPredi), false, buttonOrLever != 0, false);
                int diodeL = diodeValue(level, leftPredi);
                BlockPos nextPosAlt;
                if (buttonOrLever != 0) {
                    nextPosAlt = nextPos.offset(
                            directionX ? 0 : (buttonOrLever == 2 ? 1 : buttonOrLever == 4 ? -1 : 0), 0,
                            directionX ? (buttonOrLever == 1 ? -1 : buttonOrLever == 3 ? 1 : 0) : 0);
                }
                else if ((cardinalPointR != 1 || cardinalPointL != 3) && (cardinalPointR != 3 || cardinalPointL != 1)
                        && (cardinalPointR != 2 || cardinalPointL != 4) && (cardinalPointR != 4 || cardinalPointL != 2)) {
                    boolean rightClosed = (d != 1 || cardinalPointR != 2
                                    || (forward > 0.0F ? torch != 3 : torch != 1) && torch != 0)
                            && (d != 2 || cardinalPointR != 3
                                    || (forward > 0.0F ? torch != 4 : torch != 2) && torch != 0)
                            && (d != 3 || cardinalPointR != 4
                                    || (forward > 0.0F ? torch != 1 : torch != 3) && torch != 0)
                            && (d != 4 || cardinalPointR != 1
                                    || (forward > 0.0F ? torch != 2 : torch != 4) && torch != 0)
                            && (forward > 0.0F
                                    ? d != cardinalPointR
                                    : (d != 1 || cardinalPointR != 3) && (d != 2 || cardinalPointR != 4)
                                            && (d != 3 || cardinalPointR != 1) && (d != 4 || cardinalPointR != 2));
                    nextPosAlt = !isConductor(level, rightPredi) || rightClosed
                            ? (isConductor(level, leftPredi) ? leftPredi : nextPos)
                            : rightPredi;
                }
                else {
                    nextPosAlt = nextPos;
                }
                boolean checkAlt = forward > 0.0F
                        ? d == 1 && (cardinalPointL == 1 || cardinalPointR == 1) && (cardinalPointL != 1 || cardinalPointR != 1)
                                || d == 2 && (cardinalPointL == 2 || cardinalPointR == 2) && (cardinalPointL != 2 || cardinalPointR != 2)
                                || d == 3 && (cardinalPointL == 3 || cardinalPointR == 3) && (cardinalPointL != 3 || cardinalPointR != 3)
                                || d == 4 && (cardinalPointL == 4 || cardinalPointR == 4) && (cardinalPointL != 4 || cardinalPointR != 4)
                        : d == 1 && (cardinalPointL == 3 || cardinalPointR == 3) && (cardinalPointL != 3 || cardinalPointR != 3)
                                || d == 2 && (cardinalPointL == 4 || cardinalPointR == 4) && (cardinalPointL != 4 || cardinalPointR != 4)
                                || d == 3 && (cardinalPointL == 1 || cardinalPointR == 1) && (cardinalPointL != 1 || cardinalPointR != 1)
                                || d == 4 && (cardinalPointL == 2 || cardinalPointR == 2) && (cardinalPointL != 2 || cardinalPointR != 2);
                boolean checkBoL = forward > 0.0F
                        ? d == 1 && (buttonOrLever == 2 && (diodeR == 1 || diodeL == 3 || cardinalPointL == 1 || cardinalPointL == 0)
                                        || buttonOrLever == 4 && (diodeR == 3 || diodeL == 1 || cardinalPointR == 1 || cardinalPointR == 0))
                                || d == 2 && (buttonOrLever == 1 && (diodeR == 4 || diodeL == 2 || cardinalPointR == 2 || cardinalPointR == 0)
                                        || buttonOrLever == 3 && (diodeR == 2 || diodeL == 4 || cardinalPointL == 2 || cardinalPointL == 0))
                                || d == 3 && (buttonOrLever == 2 && (diodeR == 1 || diodeL == 3 || cardinalPointR == 3 || cardinalPointR == 0)
                                        || buttonOrLever == 4 && (diodeR == 3 || diodeL == 1 || cardinalPointL == 3 || cardinalPointL == 0))
                                || d == 4 && (buttonOrLever == 1 && (diodeR == 4 || diodeL == 2 || cardinalPointL == 4 || cardinalPointL == 0)
                                        || buttonOrLever == 3 && (diodeR == 2 || diodeL == 4 || cardinalPointR == 4 || cardinalPointR == 0))
                        : d == 1 && (buttonOrLever == 2 && (diodeR == 3 || diodeL == 1 || cardinalPointL == 3 || cardinalPointL == 0)
                                        || buttonOrLever == 4 && (diodeR == 1 || diodeL == 3 || cardinalPointR == 3 || cardinalPointR == 0))
                                || d == 2 && (buttonOrLever == 1 && (diodeR == 2 || diodeL == 4 || cardinalPointR == 4 || cardinalPointR == 0)
                                        || buttonOrLever == 3 && (diodeR == 4 || diodeL == 2 || cardinalPointL == 4 || cardinalPointL == 0))
                                || d == 3 && (buttonOrLever == 2 && (diodeR == 3 || diodeL == 1 || cardinalPointR == 1 || cardinalPointR == 0)
                                        || buttonOrLever == 4 && (diodeR == 1 || diodeL == 3 || cardinalPointL == 1 || cardinalPointL == 0))
                                || d == 4 && (buttonOrLever == 1 && (diodeR == 2 || diodeL == 4 || cardinalPointL == 2 || cardinalPointL == 0)
                                        || buttonOrLever == 3 && (diodeR == 4 || diodeL == 2 || cardinalPointR == 2 || cardinalPointR == 0));
                boolean altRoute = buttonOrLever != 0
                        ? checkBoL && ((d == 1 || d == 3) && (buttonOrLever == 2 || buttonOrLever == 4)
                                || (d == 2 || d == 4) && (buttonOrLever == 1 || buttonOrLever == 3))
                        : ((forward > 0.0F
                                        ? cardinalPointR == 1 && torch == 2 || cardinalPointL == 1 && torch == 4
                                                || cardinalPointL == 2 && torch == 1 || cardinalPointR == 2 && torch == 3
                                                || cardinalPointL == 3 && torch == 2 || cardinalPointR == 3 && torch == 4
                                                || cardinalPointR == 4 && torch == 1 || cardinalPointL == 4 && torch == 3
                                        : cardinalPointL == 1 && torch == 2 || cardinalPointR == 1 && torch == 4
                                                || cardinalPointR == 2 && torch == 1 || cardinalPointL == 2 && torch == 3
                                                || cardinalPointR == 3 && torch == 2 || cardinalPointL == 3 && torch == 4
                                                || cardinalPointL == 4 && torch == 1 || cardinalPointR == 4 && torch == 3)
                                || torch == 0)
                                && (checkAlt
                                        || d == 1 && cardinalPointR == 2 || d == 2 && cardinalPointR == 3
                                        || d == 3 && cardinalPointR == 4 || d == 4 && cardinalPointR == 1
                                        || d == 1 && cardinalPointL == 4 || d == 4 && cardinalPointL == 3
                                        || d == 3 && cardinalPointL == 2 || d == 2 && cardinalPointL == 1);
                BlockPos nextPosAgain = stepUpOrDown(level, nextPos, canGoUp, canGoDown, jumping);
                BlockPos sidePos = right && left
                        ? null
                        : right
                                ? currentPos.offset(directionX ? 0 : (direction ? -1 : 1), 0,
                                        directionX ? (direction ? 1 : -1) : 0)
                                : currentPos.offset(directionX ? 0 : (direction ? 1 : -1), 0,
                                        directionX ? (direction ? -1 : 1) : 0);
                nextPos = isConductor(level, nextPosAgain) && isConductor(level, nextPosVariant) && canCross
                        ? (jumping ? nextPosAgain : nextPosVariant)
                        : isConductor(level, nextPosAgain)
                                ? nextPosAgain
                                : isConductor(level, nextPosVariant) && canCross
                                        ? nextPosVariant
                                        : altRoute ? nextPosAlt : sidePos;
            }
        }
        else {
            nextPos = right && left
                    ? null
                    : right
                            ? currentPos.offset(directionX ? 0 : (direction ? -1 : 1), 0,
                                    directionX ? (direction ? 1 : -1) : 0)
                            : currentPos.offset(directionX ? 0 : (direction ? 1 : -1), 0,
                                    directionX ? (direction ? -1 : 1) : 0);
            boolean canGoDown = nextPos != null && (level.isEmptyBlock(nextPos) || !fullBlock(level, nextPos));
            if (nextPos != null && !isConductor(level, nextPos)) {
                BlockPos nextPosVariant = right
                        ? currentPos.offset(directionX ? 0 : (direction ? -2 : 2), 0,
                                directionX ? (direction ? 2 : -2) : 0)
                        : currentPos.offset(directionX ? 0 : (direction ? 2 : -2), 0,
                                directionX ? (direction ? -2 : 2) : 0);
                int cardinalPoint = cardinalPoint(level.getBlockState(nextPosVariant), true, false, false);
                boolean canCross = right
                        ? d == 1 && cardinalPoint == 2 || d == 2 && cardinalPoint == 3
                                || d == 3 && cardinalPoint == 4 || d == 4 && cardinalPoint == 1
                        : d == 1 && cardinalPoint == 4 || d == 4 && cardinalPoint == 3
                                || d == 3 && cardinalPoint == 2 || d == 2 && cardinalPoint == 1;
                BlockPos nextPosAgain = stepUpOrDown(level, nextPos, canGoUp, canGoDown, jumping);
                nextPos = isConductor(level, nextPosAgain) && isConductor(level, nextPosVariant) && canCross
                        ? (jumping ? nextPosAgain : nextPosVariant)
                        : isConductor(level, nextPosAgain)
                                ? nextPosAgain
                                : isConductor(level, nextPosVariant) && canCross ? nextPosVariant : nextPos;
            }
        }
        return nextPos;
    }

    // Past a block that is not a conductor: a step down when there is room and a conductor below it (unless jumping
    // with room above), else a step up when there is room above; neither, the same block.
    private static BlockPos stepUpOrDown(Level level, BlockPos nextPos, boolean canGoUp, boolean canGoDown,
            boolean jumping) {
        int y = !canGoDown && !canGoUp
                ? 0
                : (!canGoUp || canGoDown) && (!jumping || !canGoUp)
                        && (isConductor(level, nextPos.below()) || !canGoUp) ? -1 : 1;
        return nextPos.offset(0, y, 0);
    }

    // Jump alone: the conductor above, or the one past a full block above when a ceiling button or lever, a torch,
    // or a floor button, lever or pressure plate on top of that block joins them.
    @Nullable
    private static BlockPos up(Level level, BlockPos pos) {
        BlockPos nextPos = pos.above();
        if (isConductor(level, nextPos)) {
            return nextPos.immutable();
        }
        BlockState state = level.getBlockState(pos);
        BlockPos topPos = nextPos.above();
        BlockState topState = level.getBlockState(topPos);
        if (!level.isEmptyBlock(nextPos)
                && (attached(state, AttachFace.CEILING) || state.getBlock() instanceof RedstoneTorchBlock
                        || attached(topState, AttachFace.FLOOR) || topState.getBlock() instanceof BasePressurePlateBlock)
                && fullBlock(level, nextPos)
                && !level.isEmptyBlock(topPos)
                && RhcpService.isConductor(topState)
                && !(topState.getBlock() instanceof DiodeBlock)) {
            return topPos.immutable();
        }
        return null;
    }

    // Sneak alone: the conductor below, or the one past a full block below joined the same way.
    @Nullable
    private static BlockPos down(Level level, BlockPos pos) {
        BlockPos nextPos = pos.below();
        if (isConductor(level, nextPos)) {
            return nextPos.immutable();
        }
        BlockState state = level.getBlockState(pos);
        BlockPos downPos = nextPos.below();
        BlockState downState = level.getBlockState(downPos);
        if (!level.isEmptyBlock(nextPos)
                && (attached(state, AttachFace.FLOOR) || state.getBlock() instanceof BasePressurePlateBlock
                        || attached(downState, AttachFace.CEILING)
                        || downState.getBlock() instanceof RedstoneTorchBlock
                                && !(state.getBlock() instanceof RedstoneWallTorchBlock))
                && fullBlock(level, nextPos)
                && !level.isEmptyBlock(downPos)
                && RhcpService.isConductor(downState)
                && !(downState.getBlock() instanceof DiodeBlock)) {
            return downPos.immutable();
        }
        return null;
    }

    private static boolean attached(BlockState state, AttachFace face) {
        Block block = state.getBlock();
        return (block instanceof ButtonBlock || block instanceof LeverBlock)
                && state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == face;
    }

    // Beta2 RHCPEntity.getCardinalPoint with RHCPGettingIntoTheMechanism.getDirectionAction.
    static int cardinalPoint(BlockState state, boolean onlyWall, boolean onlyTorch, boolean onlyDiode) {
        Block block = state.getBlock();
        if (block instanceof ButtonBlock || block instanceof LeverBlock) {
            if (onlyTorch || onlyDiode) {
                return 7;
            }
            return switch (state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
                case FLOOR -> onlyWall ? 0 : 5;
                case WALL -> code(state.getValue(HorizontalDirectionalBlock.FACING));
                case CEILING -> onlyWall ? 0 : 6;
            };
        }
        if (block instanceof TripWireHookBlock) {
            return onlyTorch || onlyDiode ? 7 : code(state.getValue(TripWireHookBlock.FACING));
        }
        if (block instanceof DiodeBlock) {
            return onlyTorch ? 7 : code(state.getValue(HorizontalDirectionalBlock.FACING));
        }
        if (block instanceof RedstoneWallTorchBlock && (onlyTorch || onlyDiode)) {
            return onlyDiode ? 7 : code(state.getValue(RedstoneWallTorchBlock.FACING));
        }
        return 0;
    }

    private static int diodeValue(Level level, BlockPos pos) {
        return cardinalPoint(level.getBlockState(pos), false, false, true);
    }

    private static boolean isConductor(Level level, @Nullable BlockPos pos) {
        return pos != null && RhcpService.isConductor(level.getBlockState(pos));
    }

    private static boolean fullBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).getShape(level, pos) == Shapes.block();
    }
}

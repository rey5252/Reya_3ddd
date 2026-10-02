package com.reya.boundlessrouters.router;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;

/**
 * A module's direction, relative to the router's front: left and right as seen by someone facing the router's
 * front (the player who placed it), up and down always up and down. NONE is no direction.
 */
public enum RelativeDirection {
    NONE, FRONT, BACK, UP, DOWN, LEFT, RIGHT;

    private static final RelativeDirection[] ALL = values();

    @Nullable
    public Direction toAbsolute(Direction facing) {
        return switch (this) {
            case NONE -> null;
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
        };
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RelativeDirection byId(int id) {
        return id >= 0 && id < ALL.length ? ALL[id] : NONE;
    }
}

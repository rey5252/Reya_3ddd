package com.reya.boundlessrouters.module;

import java.util.Locale;

import com.reya.boundlessrouters.router.RelativeDirection;

/**
 * The kinds of module: what each does when its router runs, whether it works in a direction, what it can be bound
 * to (a place, many places, a player) and which settings its screen shows.
 */
public enum ModuleKind {
    /** Sends items from the buffer into an inventory: the first one along its direction, or the one it is bound to. */
    SENDER(true, Binding.PLACE, Panel.TARGET, 0x5FD45F),
    /** Pulls items into the buffer from the inventory in its direction, or the one it is bound to. */
    PULLER(true, Binding.PLACE, Panel.TARGET, 0x4FA8FF),
    /** Sends items from the buffer to many bound inventories, in turn, at random, nearest or farthest first. */
    DISTRIBUTOR(false, Binding.PLACES, Panel.DISTRIBUTOR, 0x3FE0D0),
    /** Drops items from the buffer into the world in its direction. */
    DROPPER(true, Binding.NONE, Panel.INFO, 0xC9C9C9),
    /** Throws items from the buffer, as fast and as high as it is set. */
    FLINGER(true, Binding.NONE, Panel.FLINGER, 0xFF9A3C),
    /** Places blocks from the buffer in its direction. */
    PLACER(true, Binding.NONE, Panel.INFO, 0x9BE15D),
    /** Breaks the block in its direction into the buffer, with silk touch or fortune as it is set. */
    BREAKER(true, Binding.NONE, Panel.BREAKER, 0xFF5A4F),
    /** Picks dropped items up into the buffer round the router, or round a spot in its direction. */
    VACUUM(true, Binding.NONE, Panel.VACUUM, 0xC07CFF),
    /** Destroys items in the buffer. */
    VOID(false, Binding.NONE, Panel.INFO, 0x8A5CD0),
    /** Moves items between the buffer and its bound player's inventory, while the router reaches them. */
    PLAYER(false, Binding.PLAYER, Panel.PLAYER, 0xFFD24A),
    /** Gives out a redstone signal in its direction while the buffer holds what it looks for. */
    DETECTOR(true, Binding.NONE, Panel.DETECTOR, 0xFF3B3B),
    /** Builds a line of blocks from the buffer in its direction while the router has a signal, and takes it back without. */
    EXTRUDER(true, Binding.NONE, Panel.EXTRUDER, 0xD29A5C),
    /**
     * Does what a player does with a click, holding the buffer's item: right-clicks the block in its direction, the air
     * or a creature there; left-clicks a block (a hit, or digging it as long as a player would), or hits a creature.
     */
    ACTIVATOR(true, Binding.NONE, Panel.ACTIVATOR, 0xFF8AC8);

    /** What a module is bound to with a sneaking right-click. */
    public enum Binding {
        NONE, PLACE, PLACES, PLAYER;

        public boolean places() {
            return this == PLACE || this == PLACES;
        }
    }

    /** Which settings of its own a module's screen shows under the filter. */
    public enum Panel {
        INFO, TARGET, DISTRIBUTOR, FLINGER, BREAKER, VACUUM, PLAYER, DETECTOR, EXTRUDER, ACTIVATOR
    }

    private static final ModuleKind[] ALL = values();

    private final boolean directional;
    private final Binding binding;
    private final Panel panel;
    private final int colour;

    ModuleKind(boolean directional, Binding binding, Panel panel, int colour) {
        this.directional = directional;
        this.binding = binding;
        this.panel = panel;
        this.colour = colour;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean directional() {
        return directional;
    }

    public Binding binding() {
        return binding;
    }

    public Panel panel() {
        return panel;
    }

    /** Its colour, 0xRRGGBB: its icon's and its screen's accents. */
    public int colour() {
        return colour;
    }

    /** The direction a new module of this kind starts with: the router's front, or none (a vacuum round the router). */
    public RelativeDirection defaultDirection() {
        return directional && this != VACUUM ? RelativeDirection.FRONT : RelativeDirection.NONE;
    }

    public static ModuleKind byId(int id) {
        return id >= 0 && id < ALL.length ? ALL[id] : SENDER;
    }
}

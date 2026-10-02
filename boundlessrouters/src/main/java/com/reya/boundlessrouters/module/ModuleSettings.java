package com.reya.boundlessrouters.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.router.RelativeDirection;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * A module's settings, kept on the module item itself (its "Module" tag): its direction, its filter, whether it
 * stops the modules after it, when redstone lets it work, and its own settings. Reading a module that was never
 * set up gives the defaults: a new module moves everything (its filter is an empty blacklist).
 */
public final class ModuleSettings {
    public static final String TAG = "Module";
    public static final int FILTER_SIZE = 9;
    /** A flinger's speed is kept in tenths of a block a tick. */
    public static final int MAX_SPEED = 100;
    public static final int MAX_FORTUNE = 10;

    /** When redstone lets a single module work, besides its router's own mode. */
    public enum ModuleRedstone {
        ALWAYS, HIGH, LOW;

        public boolean allows(boolean powered) {
            return this == ALWAYS || (this == HIGH) == powered;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The order a distributor tries its targets in. */
    public enum Strategy {
        ROUND_ROBIN, RANDOM, NEAREST, FARTHEST;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The part of a player's inventory a player module works with. */
    public enum Section {
        MAIN, ARMOR, OFFHAND, ENDER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which way a player module moves items: out of the player into the buffer, or the other way. */
    public enum Operation {
        EXTRACT, INSERT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What an activator does with its click. */
    public enum Action {
        /** A right click on the block in front. */
        USE_BLOCK,
        /** A right click in the air (throwing, drinking, scooping water with a bucket). */
        USE_AIR,
        /** A right click on a creature in front (shearing, milking, feeding). */
        USE_ENTITY,
        /** A left click on the block in front: one hit. */
        HIT_BLOCK,
        /** The left button held on the block in front: digging it as long as a player would, then breaking it. */
        DIG_BLOCK,
        /** A left click on a creature in front: hitting it with the item. */
        ATTACK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a module's screen can change (sent to the server as the setting's ordinal and a number). */
    public enum Setting {
        DIRECTION, BLACKLIST, MATCH_DAMAGE, MATCH_NBT, MATCH_TAGS, MATCH_MOD, TERMINATE, REDSTONE,
        SPEED, PITCH, YAW, RADIUS, POWER, STRONG, SECTION, OPERATION, STRATEGY, SILK, FORTUNE, ACTION, SNEAK,
        CLEAR_TARGETS, REMOVE_TARGET, CLEAR_PLAYER;

        private static final Setting[] ALL = values();

        @Nullable
        public static Setting byId(int id) {
            return id >= 0 && id < ALL.length ? ALL[id] : null;
        }
    }

    private final ItemStack stack;
    private final ModuleKind kind;

    public ModuleSettings(ItemStack stack) {
        this.stack = stack;
        this.kind = stack.getItem() instanceof ModuleItem module ? module.kind() : ModuleKind.SENDER;
    }

    public ModuleKind kind() {
        return kind;
    }

    /** The settings to read (an empty tag if it has none yet; changing that changes nothing). */
    private CompoundTag read() {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null ? tag : new CompoundTag();
    }

    private CompoundTag write() {
        return stack.getOrCreateTagElement(TAG);
    }

    // ------------------------------------------------------------------ shared by every module

    public RelativeDirection direction() {
        CompoundTag tag = read();
        return tag.contains("Dir", Tag.TAG_BYTE) ? RelativeDirection.byId(tag.getByte("Dir")) : kind.defaultDirection();
    }

    /** Whether the filter lets through everything but what it lists (true for a new module) or only what it lists. */
    public boolean blacklist() {
        CompoundTag tag = read();
        return !tag.contains("Black", Tag.TAG_BYTE) || tag.getBoolean("Black");
    }

    public boolean matchDamage() {
        return read().getBoolean("Dmg");
    }

    public boolean matchNbt() {
        return read().getBoolean("Nbt");
    }

    public boolean matchTags() {
        return read().getBoolean("Tags");
    }

    public boolean matchMod() {
        return read().getBoolean("Mod");
    }

    /** Whether the modules after it are skipped once it has done something. */
    public boolean terminates() {
        return read().getBoolean("Term");
    }

    public ModuleRedstone redstone() {
        int id = read().getByte("Rs");
        return id >= 0 && id < ModuleRedstone.values().length ? ModuleRedstone.values()[id] : ModuleRedstone.ALWAYS;
    }

    public NonNullList<ItemStack> filterItems() {
        NonNullList<ItemStack> items = NonNullList.withSize(FILTER_SIZE, ItemStack.EMPTY);
        ListTag list = read().getList("Filter", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < FILTER_SIZE) items.set(slot, ItemStack.of(entry));
        }
        return items;
    }

    public void setFilterItems(List<ItemStack> items) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.size() && i < FILTER_SIZE; i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty()) continue;
            CompoundTag entry = item.copyWithCount(1).save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        write().put("Filter", list);
    }

    public Filter filter() {
        return new Filter(filterItems(), blacklist(), matchDamage(), matchNbt(), matchTags(), matchMod());
    }

    // ------------------------------------------------------------------ bound places and players

    public List<Target> targets() {
        List<Target> targets = new ArrayList<>();
        ListTag list = read().getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Target target = Target.load(list.getCompound(i));
            if (target != null) targets.add(target);
        }
        return targets;
    }

    public void setTargets(List<Target> targets) {
        ListTag list = new ListTag();
        for (Target target : targets) list.add(target.save());
        write().put("Targets", list);
    }

    /**
     * Binds a place: a module of one place is bound to it (unbound if it was already); a distributor gains it, or
     * loses it if it had it. Returns whether the place is now among the targets.
     */
    public boolean toggleTarget(Target target) {
        List<Target> targets = targets();
        boolean had = targets.removeIf(t -> t.samePlace(target));
        if (kind.binding() == ModuleKind.Binding.PLACE) {
            targets.clear();
            if (!had) targets.add(target);
        } else if (!had) {
            targets.add(target);
        }
        setTargets(targets);
        return !had;
    }

    @Nullable
    public UUID playerId() {
        CompoundTag tag = read();
        return tag.hasUUID("Player") ? tag.getUUID("Player") : null;
    }

    public String playerName() {
        return read().getString("PlayerName");
    }

    public void setPlayer(@Nullable UUID id, String name) {
        CompoundTag tag = write();
        if (id == null) {
            tag.remove("Player");
            tag.remove("PlayerName");
        } else {
            tag.putUUID("Player", id);
            tag.putString("PlayerName", name);
        }
    }

    // ------------------------------------------------------------------ each kind's own

    /** A flinger's speed in blocks a tick. */
    public float speed() {
        CompoundTag tag = read();
        return (tag.contains("Speed", Tag.TAG_INT) ? tag.getInt("Speed") : 10) / 10.0F;
    }

    public int pitch() {
        return read().getInt("Pitch");
    }

    public int yaw() {
        return read().getInt("Yaw");
    }

    public int radius() {
        CompoundTag tag = read();
        return Mth.clamp(tag.contains("Radius", Tag.TAG_INT) ? tag.getInt("Radius") : 6, 1, RouterConfig.MAX_RADIUS.get());
    }

    public int power() {
        CompoundTag tag = read();
        return tag.contains("Power", Tag.TAG_INT) ? Mth.clamp(tag.getInt("Power"), 1, 15) : 15;
    }

    public boolean strong() {
        return read().getBoolean("Strong");
    }

    public Section section() {
        int id = read().getByte("Section");
        return id >= 0 && id < Section.values().length ? Section.values()[id] : Section.MAIN;
    }

    public Operation operation() {
        int id = read().getByte("Op");
        return id >= 0 && id < Operation.values().length ? Operation.values()[id] : Operation.EXTRACT;
    }

    public Strategy strategy() {
        int id = read().getByte("Strategy");
        return id >= 0 && id < Strategy.values().length ? Strategy.values()[id] : Strategy.ROUND_ROBIN;
    }

    /** The target a round-robin distributor tries first next time. */
    public int next() {
        return read().getInt("Next");
    }

    public void setNext(int next) {
        write().putInt("Next", next);
    }

    public Action action() {
        int i = read().getByte("Act");
        return Action.values()[Mth.clamp(i, 0, Action.values().length - 1)];
    }

    public boolean sneak() {
        return read().getBoolean("Sneak");
    }

    public boolean silk() {
        return read().getBoolean("Silk");
    }

    public int fortune() {
        return Mth.clamp(read().getInt("Fortune"), 0, MAX_FORTUNE);
    }

    /** How many blocks an extruder has put out. */
    public int extended() {
        return Math.max(0, read().getInt("Extended"));
    }

    public void setExtended(int extended) {
        write().putInt("Extended", Math.max(0, extended));
    }

    // ------------------------------------------------------------------ the screen's changes

    /** A setting's value as the screen shows it. */
    public int get(Setting setting) {
        return switch (setting) {
            case DIRECTION -> direction().ordinal();
            case BLACKLIST -> blacklist() ? 1 : 0;
            case MATCH_DAMAGE -> matchDamage() ? 1 : 0;
            case MATCH_NBT -> matchNbt() ? 1 : 0;
            case MATCH_TAGS -> matchTags() ? 1 : 0;
            case MATCH_MOD -> matchMod() ? 1 : 0;
            case TERMINATE -> terminates() ? 1 : 0;
            case REDSTONE -> redstone().ordinal();
            case SPEED -> Math.round(speed() * 10.0F);
            case PITCH -> pitch();
            case YAW -> yaw();
            case RADIUS -> radius();
            case POWER -> power();
            case STRONG -> strong() ? 1 : 0;
            case SECTION -> section().ordinal();
            case OPERATION -> operation().ordinal();
            case STRATEGY -> strategy().ordinal();
            case SILK -> silk() ? 1 : 0;
            case FORTUNE -> fortune();
            case ACTION -> action().ordinal();
            case SNEAK -> sneak() ? 1 : 0;
            case CLEAR_TARGETS, REMOVE_TARGET, CLEAR_PLAYER -> 0;
        };
    }

    /** Sets a setting from the screen, keeping it within its bounds. */
    public void apply(Setting setting, int value) {
        CompoundTag tag = write();
        switch (setting) {
            case DIRECTION -> tag.putByte("Dir", (byte) RelativeDirection.byId(value).ordinal());
            case BLACKLIST -> tag.putBoolean("Black", value != 0);
            case MATCH_DAMAGE -> tag.putBoolean("Dmg", value != 0);
            case MATCH_NBT -> tag.putBoolean("Nbt", value != 0);
            case MATCH_TAGS -> tag.putBoolean("Tags", value != 0);
            case MATCH_MOD -> tag.putBoolean("Mod", value != 0);
            case TERMINATE -> tag.putBoolean("Term", value != 0);
            case REDSTONE -> tag.putByte("Rs", (byte) Mth.clamp(value, 0, ModuleRedstone.values().length - 1));
            case SPEED -> tag.putInt("Speed", Mth.clamp(value, 0, MAX_SPEED));
            case PITCH -> tag.putInt("Pitch", Mth.clamp(value, -90, 90));
            case YAW -> tag.putInt("Yaw", Mth.clamp(value, -90, 90));
            case RADIUS -> tag.putInt("Radius", Mth.clamp(value, 1, RouterConfig.MAX_RADIUS.get()));
            case POWER -> tag.putInt("Power", Mth.clamp(value, 1, 15));
            case STRONG -> tag.putBoolean("Strong", value != 0);
            case SECTION -> tag.putByte("Section", (byte) Mth.clamp(value, 0, Section.values().length - 1));
            case OPERATION -> tag.putByte("Op", (byte) Mth.clamp(value, 0, Operation.values().length - 1));
            case STRATEGY -> tag.putByte("Strategy", (byte) Mth.clamp(value, 0, Strategy.values().length - 1));
            case SILK -> tag.putBoolean("Silk", value != 0);
            case FORTUNE -> tag.putInt("Fortune", Mth.clamp(value, 0, MAX_FORTUNE));
            case ACTION -> tag.putByte("Act", (byte) Mth.clamp(value, 0, Action.values().length - 1));
            case SNEAK -> tag.putBoolean("Sneak", value != 0);
            case CLEAR_TARGETS -> {
                tag.remove("Targets");
                tag.remove("Next");
            }
            case REMOVE_TARGET -> {
                List<Target> targets = targets();
                if (value >= 0 && value < targets.size()) {
                    targets.remove(value);
                    setTargets(targets);
                }
            }
            case CLEAR_PLAYER -> setPlayer(null, "");
        }
    }
}

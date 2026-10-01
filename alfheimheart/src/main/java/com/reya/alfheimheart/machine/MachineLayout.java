package com.reya.alfheimheart.machine;

import com.reya.alfheimheart.AlfheimHeart;
import net.minecraft.resources.ResourceLocation;

/**
 * Where everything is on a machine's GUI, in the menu's coordinates: the panel's size, the machine's slots (their
 * items' corners: inputs, outputs, special slots), the player's inventory, the redstone and close buttons, the
 * pool lamp, the status gem, the heart (the panel grows out of it, crafts fly out of it), where crafts land, the
 * mana gauge (its tooltip's box), JEI's click areas, the twinkling lights, the frame's groove mana runs round.
 * <p>
 * Every machine has its own ({@link MachineLayouts}, written by tools/machines/layouts.py from the numbers its
 * panels are drawn on); how many slots of each kind a machine has is its layout's too.
 */
public final class MachineLayout {
    /** The panel texture's margin round the panel (ornaments reach over its edges). */
    public static final int MARGIN = 12;

    public final String key;
    /** The first-generation look, the living-wood panel every machine shared: its arrows and its mana bar. */
    public final boolean classic;
    public final int width, height, machineHeight;
    public final int[][] inputs, outputs, special;
    public final int invX, invY, hotbarY, invPanelX1, invPanelX2;
    public final int closeX, closeY, redstoneX, redstoneY, poolX, poolY, gemX, gemY, heartX, heartY, landX, landY;
    /** The mana gauge's box: x1, y1, x2, y2. */
    public final int[] gauge;
    /** JEI's click areas: x, y, w, h each. */
    public final int[][] clickAreas;
    public final int[][] lights;
    /** The groove mana runs round while the machine works: x1, y1, x2, y2. */
    public final int[] vein;

    public MachineLayout(String key, boolean classic, int width, int height, int machineHeight, int[][] inputs, int[][] outputs,
                         int[][] special, int[] inventory, int[] close, int[] redstone, int[] pool, int[] gem, int[] heart, int[] land,
                         int[] gauge, int[][] clickAreas, int[][] lights, int[] vein) {
        this.key = key;
        this.classic = classic;
        this.width = width;
        this.height = height;
        this.machineHeight = machineHeight;
        this.inputs = inputs;
        this.outputs = outputs;
        this.special = special;
        this.invX = inventory[0];
        this.invY = inventory[1];
        this.hotbarY = inventory[2];
        this.invPanelX1 = inventory[3];
        this.invPanelX2 = inventory[4];
        this.closeX = close[0];
        this.closeY = close[1];
        this.redstoneX = redstone[0];
        this.redstoneY = redstone[1];
        this.poolX = pool[0];
        this.poolY = pool[1];
        this.gemX = gem[0];
        this.gemY = gem[1];
        this.heartX = heart[0];
        this.heartY = heart[1];
        this.landX = land[0];
        this.landY = land[1];
        this.gauge = gauge;
        this.clickAreas = clickAreas;
        this.lights = lights;
        this.vein = vein;
    }

    public int inputCount() {
        return inputs.length;
    }

    public int outputCount() {
        return outputs.length;
    }

    public int specialCount() {
        return special.length;
    }

    /** The machine's slots: its inputs first, then its outputs, then its special slots. */
    public int outputStart() {
        return inputs.length;
    }

    public int specialStart() {
        return inputs.length + outputs.length;
    }

    public int slots() {
        return inputs.length + outputs.length + special.length;
    }

    /** The panel texture (the panel with the margin round it). */
    public ResourceLocation panel() {
        return gui(key);
    }

    /** The widget sheet: the machine's own, or the one the first-generation machines share. */
    public ResourceLocation widgets() {
        return gui(classic ? "machine_widgets" : key + "_widgets");
    }

    /** Where a shine sweeps over the panel (the panel's size; white where it shines), or null. */
    public ResourceLocation gloss() {
        return classic ? null : gui(key + "_gloss");
    }

    private static ResourceLocation gui(String name) {
        return new ResourceLocation(AlfheimHeart.MODID, "textures/gui/" + name + ".png");
    }
}

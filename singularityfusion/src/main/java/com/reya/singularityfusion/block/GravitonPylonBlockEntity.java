package com.reya.singularityfusion.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/**
 * A graviton pylon: an injector holding one ingredient for the fusion core it serves (the core finds its pylons and
 * links them). Its renderer raises a great pylon from it, aimed at the singularity over the core, the ingredient
 * floating at its tip. Placed, it unfolds: its shaft slides out piece by piece, its rings open out, its crystal
 * lights; then it swivels to its singularity.
 */
public class GravitonPylonBlockEntity extends BlockEntity {
    /** The event placing a pylon sends the clients round it: it unfolds. */
    public static final int EVENT_DEPLOY = 1;
    /** When its sounds come as it unfolds (0 to 1 through it): the foot, the shaft's pieces and the neck, the rings, the crystal. */
    private static final float[] CUES = {0.0F, FusionGeometry.DEPLOY_PIECES[1] + 0.1F, FusionGeometry.DEPLOY_PIECES[2] + 0.1F,
            FusionGeometry.DEPLOY_PIECES[3] + 0.1F, FusionGeometry.DEPLOY_PIECES[4] + 0.1F, FusionGeometry.DEPLOY_RINGS + 0.08F,
            FusionGeometry.DEPLOY_RINGS + FusionGeometry.DEPLOY_RING_STEP + 0.08F, FusionGeometry.DEPLOY_RINGS + 2.0F * FusionGeometry.DEPLOY_RING_STEP + 0.08F,
            FusionGeometry.DEPLOY_RINGS + 3.0F * FusionGeometry.DEPLOY_RING_STEP + 0.08F,
            FusionGeometry.DEPLOY_RINGS + 4.0F * FusionGeometry.DEPLOY_RING_STEP + 0.08F, FusionGeometry.DEPLOY_CRYSTAL + 0.04F};

    private ItemStack item = ItemStack.EMPTY;
    @Nullable
    private BlockPos core;
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new Handler());

    // the client's own animation state: how lit it looks (catching up with its core's charge), how far its rings have
    // turned; when it was placed (it unfolds) and when it came into the client's world; where it points and how big it
    // is drawn (swivelling and growing smoothly to what its core makes them)
    public float shownCharge, spin;
    public long lastFrameNanos;
    public long deployedAt = Long.MIN_VALUE, loadedAt = Long.MIN_VALUE;
    @Nullable
    public Vec3 shownAim;
    public float shownScale = 1.0F;
    private int cuesPlayed;

    public GravitonPylonBlockEntity(BlockPos pos, BlockState state) {
        super(SingularityFusion.GRAVITON_PYLON_BE.get(), pos, state);
    }

    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack.copyWithCount(Math.min(1, stack.getCount()));
        changed();
    }

    /** The core this pylon serves, or null. */
    @Nullable
    public BlockPos core() {
        return core;
    }

    public void link(@Nullable BlockPos core) {
        if (java.util.Objects.equals(this.core, core)) return;
        this.core = core == null ? null : core.immutable();
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ unfolding (the client's)

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) loadedAt = level.getGameTime();
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_DEPLOY) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            deployedAt = level.getGameTime();
            cuesPlayed = 0;
            shownAim = null;
        }
        return true;
    }

    /** How far it has unfolded (0 to 1): all the way, unless it was placed a moment ago. */
    public float deploy(float partialTick) {
        if (level == null) return 1.0F;
        long now = level.getGameTime();
        if (deployedAt != Long.MIN_VALUE) return Mth.clamp((now - deployedAt + partialTick) / FusionGeometry.DEPLOY_TICKS, 0.0F, 1.0F);
        // just come into the world with no word of its placing yet: wait a moment before showing it whole (it may be about to unfold)
        return loadedAt != Long.MIN_VALUE && now - loadedAt < 3L ? 0.0F : 1.0F;
    }

    /** Whether it is waiting to hear if it unfolds (and so shows nothing yet). */
    public boolean waiting() {
        return deployedAt == Long.MIN_VALUE && deploy(0.0F) <= 0.0F;
    }

    /** The client's ticks: the sounds and sparks of unfolding, each as its part comes out. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, GravitonPylonBlockEntity pylon) {
        if (pylon.deployedAt == Long.MIN_VALUE) return;
        float t = (level.getGameTime() - pylon.deployedAt) / FusionGeometry.DEPLOY_TICKS;
        if (t > 1.2F) return;
        for (int i = 0; i < CUES.length; i++) {
            if ((pylon.cuesPlayed & 1 << i) != 0 || t < CUES[i]) continue;
            pylon.cuesPlayed |= 1 << i;
            pylon.cue(level, pos, i);
        }
    }

    private void cue(Level level, BlockPos pos, int cue) {
        RandomSource random = level.random;
        Vec3 base = FusionGeometry.base(pos), aim = shownAim != null ? shownAim : new Vec3(0.0D, 1.0D, 0.0D);
        if (cue == 0) {
            // the foot: a thud, a hum, void puffing out round the plinth
            level.playLocalSound(pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7F, 0.55F, false);
            level.playLocalSound(pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.35F, 1.7F, false);
            for (int i = 0; i < 18; i++) {
                double a = 2.0D * Math.PI * i / 18.0D;
                level.addParticle(ParticleTypes.REVERSE_PORTAL, base.x + 0.45D * Math.cos(a), base.y + 0.06D, base.z + 0.45D * Math.sin(a),
                        0.07D * Math.cos(a), 0.02D, 0.07D * Math.sin(a));
            }
        } else if (cue <= 4) {
            // a piece of the shaft slides out: a click, sparks at its joint
            level.playLocalSound(pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.3F, 0.8F + 0.14F * cue, false);
            Vec3 joint = base.add(aim.scale(FusionGeometry.PIECES[cue - 1] * shownScale));
            for (int i = 0; i < 7; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, joint.x + (random.nextDouble() - 0.5D) * 0.5D, joint.y, joint.z + (random.nextDouble() - 0.5D) * 0.5D,
                        (random.nextDouble() - 0.5D) * 0.2D, random.nextDouble() * 0.1D, (random.nextDouble() - 0.5D) * 0.2D);
            }
        } else if (cue <= 9) {
            // a ring opens out: a chime, higher for each
            level.playLocalSound(pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 0.85F + 0.15F * (cue - 5), false);
        } else {
            // the crystal lights: light bursting from it
            level.playLocalSound(pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.45F, 1.7F, false);
            level.playLocalSound(pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.9F, false);
            Vec3 tip = base.add(aim.scale(FusionGeometry.CRYSTAL * shownScale));
            for (int i = 0; i < 20; i++) {
                Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                double length = d.length();
                if (length < 1.0E-4D) continue;
                d = d.scale(0.09D / length);
                level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, d.x, d.y, d.z);
            }
        }
    }

    // ------------------------------------------------------------------ saving and the client's copy

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeShared(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readShared(tag);
    }

    private void writeShared(CompoundTag tag) {
        if (!item.isEmpty()) tag.put("Item", item.save(new CompoundTag()));
        if (core != null) tag.putLong("Core", core.asLong());
    }

    private void readShared(CompoundTag tag) {
        item = tag.contains("Item") ? ItemStack.of(tag.getCompound("Item")) : ItemStack.EMPTY;
        core = tag.contains("Core") ? BlockPos.of(tag.getLong("Core")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeShared(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readShared(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        readShared(tag == null ? new CompoundTag() : tag);
    }

    /** The pylon rises from the block toward the singularity, a few blocks off. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(6.0D);
    }

    // ------------------------------------------------------------------ hoppers and pipes

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    /** One slot of one item: a hopper fills an empty pylon, and empties a full one. */
    private class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            return item;
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !item.isEmpty()) return stack;
            if (!simulate) setItem(stack.copyWithCount(1));
            ItemStack left = stack.copy();
            left.shrink(1);
            return left;
        }

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || item.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = item.copy();
            if (!simulate) setItem(ItemStack.EMPTY);
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return true;
        }
    }
}

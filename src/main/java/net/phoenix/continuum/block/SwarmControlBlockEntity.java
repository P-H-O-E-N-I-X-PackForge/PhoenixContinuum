package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.common.SwarmHolograms;
import net.phoenix.continuum.config.ContinuumConfigs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The plain Swarm Control: it stays online while it is fed Forge Energy, and shows its hologram while online. */
public class SwarmControlBlockEntity extends BlockEntity implements SwarmHolograms.Source {

    private static final int SECONDS_OF_BUFFER = 30;

    private long stored;
    private boolean online;
    private UUID owner;
    private final IEnergyStorage input = new Input();
    private final LazyOptional<IEnergyStorage> inputCap = LazyOptional.of(() -> input);

    public SwarmControlBlockEntity(BlockPos pos, BlockState state) {
        super(ContinuumRegistry.SWARM_CONTROL_ENTITY.get(), pos, state);
    }

    private final class Input implements IEnergyStorage {

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int accepted = (int) Math.max(0L, Math.min(Math.min(amount, drain() * 4L), capacity() - stored));
            if (accepted > 0 && !simulate) {
                stored += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, stored);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private static int drain() {
        return Math.max(1, ContinuumConfigs.INSTANCE.swarmControlFePerTick);
    }

    private static long capacity() {
        return (long) drain() * 20L * SECONDS_OF_BUFFER;
    }

    public void setOwner(@Nullable UUID player) {
        if (player == null) return;
        owner = player;
        setChanged();
    }

    @Override
    public boolean active() {
        return online;
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    public long stored() {
        return stored;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SwarmControlBlockEntity self) {
        if (level.isClientSide) return;
        SwarmHolograms.register(level, pos, self);
        boolean now = self.owner != null && self.stored >= drain();
        if (now) self.stored -= drain();
        if (now != self.online) {
            self.online = now;
            self.setChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) SwarmHolograms.register(level, worldPosition, this);
    }

    @Override
    public void setRemoved() {
        if (level != null) SwarmHolograms.unregister(level, worldPosition);
        inputCap.invalidate();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) SwarmHolograms.unregister(level, worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return inputCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Stored", stored);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = tag.getLong("Stored");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}

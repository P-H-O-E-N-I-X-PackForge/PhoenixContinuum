package net.phoenix.continuum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.machine.LaunchComplex;
import net.phoenix.continuum.machine.LaunchComplexes;
import net.phoenix.continuum.machine.PadTiers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class LaunchPadBlockEntity extends BlockEntity implements LaunchComplex {

    private static final int BUFFER_LAUNCHES = 8;

    private final PadEnergy energy;
    private final LazyOptional<IEnergyStorage> energyCap;
    private UUID owner;
    private UUID registeredTeam;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(ContinuumRegistry.LAUNCH_PAD_ENTITY.get(), pos, state);
        var cfg = ContinuumConfigs.INSTANCE;
        int capacity = (int) Math.min(Integer.MAX_VALUE, (long) cfg.basicLaunchEnergyFE * BUFFER_LAUNCHES);
        this.energy = new PadEnergy(Math.max(1, capacity), Math.max(1, cfg.basicPadMaxFePerTick));
        this.energyCap = LazyOptional.of(() -> energy);
    }

    private final class PadEnergy extends EnergyStorage {

        PadEnergy(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int received = super.receiveEnergy(amount, simulate);
            if (received > 0 && !simulate) setChanged();
            return received;
        }

        void drain(int amount) {
            energy = Math.max(0, energy - amount);
            setChanged();
        }

        void set(int stored) {
            energy = Math.max(0, Math.min(capacity, stored));
        }
    }

    public void setOwner(@Nullable UUID player) {
        if (player == null || level == null || level.isClientSide) return;
        owner = player;
        setChanged();
        register();
    }

    public void claim(UUID player) {
        if (owner == null) setOwner(player);
    }

    private void register() {
        if (!isActive() || owner == null || level == null) return;
        registeredTeam = LaunchComplexes.formed(this, level, worldPosition, owner, registeredTeam);
    }

    private void unregister() {
        registeredTeam = LaunchComplexes.unregister(this, registeredTeam);
    }

    private boolean isActive() {
        return PadTiers.basic();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    public void setRemoved() {
        unregister();
        energyCap.invalidate();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregister();
        super.onChunkUnloaded();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY && isActive()) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.set(tag.getInt("Energy"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    private static TagKey<Block> foundation(int tier) {
        return TagKey.create(Registries.BLOCK,
                new ResourceLocation(PhoenixContinuum.MOD_ID, "pad_foundation/tier_" + tier));
    }

    @Override
    public boolean isFormed() {
        return isActive();
    }

    @Override
    public boolean hasBuses() {
        return false;
    }

    @Override
    public int padTier() {
        if (level == null) return 0;
        BlockState below = level.getBlockState(worldPosition.below());
        for (int tier = PadTiers.BASIC_MAX_TIER; tier >= 1; tier--) {
            if (below.is(foundation(tier))) return tier;
        }
        return 0;
    }

    @Override
    public long energyCapacity() {
        return energy.getMaxEnergyStored();
    }

    @Override
    public long storedEnergy() {
        return energy.getEnergyStored();
    }

    @Override
    public boolean drainEnergy(long amount) {
        if (amount <= 0) return true;
        if (energy.getEnergyStored() < amount) return false;
        energy.drain((int) amount);
        return true;
    }

    @Override
    public long chargeEnergy(long amount) {
        if (amount <= 0) return 0;
        int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        int taken = (int) Math.min(amount, room);
        if (taken > 0) energy.set(energy.getEnergyStored() + taken);
        if (taken > 0) setChanged();
        return taken;
    }

    @Override
    public int count(Predicate<ItemStack> matches) {
        return 0;
    }

    @Override
    public ItemStack peek(Predicate<ItemStack> matches) {
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> take(Predicate<ItemStack> matches, int amount) {
        return List.of();
    }

    @Override
    public ItemStack insertOutput(ItemStack stack) {
        return stack;
    }

    @Override
    public ItemStack insertInput(ItemStack stack) {
        return stack;
    }
}

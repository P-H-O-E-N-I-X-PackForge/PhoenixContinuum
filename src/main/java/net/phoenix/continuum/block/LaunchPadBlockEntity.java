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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.common.ContinuumMissions;
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

    /** The first half is what launches spend (input), the second what comes home (output). */
    public static final int INPUT_SLOTS = 9;
    public static final int SLOTS = 18;

    private final PadEnergy energy;
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler outside = new Outside();
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> outside);
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

    /** What hoppers and pipes see: stock goes in the input half, only the output half comes out. */
    private final class Outside implements IItemHandler {

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUT_SLOTS || !isItemValid(slot, stack)) return stack;
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < INPUT_SLOTS ? ItemStack.EMPTY : items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < INPUT_SLOTS && ContinuumMissions.isStockItem(stack);
        }
    }

    /** Put by hand: whatever of the stack the input could take is taken out of it. */
    public void stock(ItemStack stack) {
        if (!ContinuumMissions.isStockItem(stack)) return;
        ItemStack left = insertInput(stack.copy());
        stack.shrink(stack.getCount() - left.getCount());
    }

    /** The contents, for when the block is broken. */
    public List<ItemStack> contents() {
        List<ItemStack> all = new java.util.ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!items.getStackInSlot(i).isEmpty()) all.add(items.getStackInSlot(i).copy());
        }
        return all;
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
        itemCap.invalidate();
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
        if (cap == ForgeCapabilities.ITEM_HANDLER && isActive()) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Items", items.serializeNBT());
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.set(tag.getInt("Energy"));
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
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

    /** No buses: a launch by hand spends the player's stock. A schedule spends what the pad's buffer holds. */
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
        int total = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty() && matches.test(stack)) total += stack.getCount();
        }
        return total;
    }

    @Override
    public ItemStack peek(Predicate<ItemStack> matches) {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty() && matches.test(stack)) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> take(Predicate<ItemStack> matches, int amount) {
        List<ItemStack> taken = new java.util.ArrayList<>();
        for (int i = 0; i < INPUT_SLOTS && amount > 0; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty() || !matches.test(stack)) continue;
            ItemStack out = items.extractItem(i, amount, false);
            amount -= out.getCount();
            taken.add(out);
        }
        return taken;
    }

    private ItemStack insert(ItemStack stack, int from, int to) {
        for (int i = from; i < to && !stack.isEmpty(); i++) stack = items.insertItem(i, stack, false);
        return stack;
    }

    @Override
    public ItemStack insertOutput(ItemStack stack) {
        return insert(stack, INPUT_SLOTS, SLOTS);
    }

    @Override
    public ItemStack insertInput(ItemStack stack) {
        return ContinuumMissions.isStockItem(stack) ? insert(stack, 0, INPUT_SLOTS) : stack;
    }
}

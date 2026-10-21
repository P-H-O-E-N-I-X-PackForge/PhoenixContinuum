package net.phoenix.continuum.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** The launch complex controller for GregTech 7.x. {@code LaunchPadMachine8} is the GregTech 8 twin of this class. */
public class LaunchPadMachine extends MultiblockControllerMachine implements LaunchComplex, IInteractedMachine {

    private UUID registeredTeam;

    public LaunchPadMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureInvalid() {
        registeredTeam = LaunchComplexes.unregister(this, registeredTeam);
        super.onStructureInvalid();
    }

    @Override
    public void onUnload() {
        registeredTeam = LaunchComplexes.unregister(this, registeredTeam);
        super.onUnload();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        registeredTeam = LaunchComplexes.formed(this, getLevel(), getPos(), getOwnerUUID(), registeredTeam);
    }

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        if (!isFormed() || player.isShiftKeyDown()) return InteractionResult.PASS;
        return LaunchComplexes.use(level, pos, player);
    }

    private List<NotifiableItemStackHandler> inputInventories() {
        List<NotifiableItemStackHandler> inventories = new ArrayList<>();
        for (var part : getParts()) {
            if (!(part instanceof ItemBusPartMachine bus)) continue;
            NotifiableItemStackHandler inventory = bus.getInventory();
            if (inventory.handlerIO == IO.IN || inventory.handlerIO == IO.BOTH) inventories.add(inventory);
        }
        return inventories;
    }

    @Override
    public int count(Predicate<ItemStack> matches) {
        int total = 0;
        for (var inventory : inputInventories()) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && matches.test(stack)) total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public ItemStack peek(Predicate<ItemStack> matches) {
        for (var inventory : inputInventories()) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && matches.test(stack)) return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> take(Predicate<ItemStack> matches, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        int remaining = amount;
        for (var inventory : inputInventories()) {
            for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || !matches.test(stack)) continue;

                ItemStack extracted = inventory.extractItemInternal(slot, Math.min(remaining, stack.getCount()), false);
                if (extracted.isEmpty()) continue;
                remaining -= extracted.getCount();
                taken.add(extracted);
            }
        }
        return taken;
    }

    private List<NotifiableItemStackHandler> inventories(boolean output) {
        List<NotifiableItemStackHandler> inventories = new ArrayList<>();
        for (var part : getParts()) {
            if (!(part instanceof ItemBusPartMachine bus)) continue;
            NotifiableItemStackHandler inventory = bus.getInventory();
            IO io = inventory.handlerIO;
            if (io == IO.BOTH || io == (output ? IO.OUT : IO.IN)) inventories.add(inventory);
        }
        return inventories;
    }

    private static ItemStack insertInto(ItemStack stack, List<NotifiableItemStackHandler> inventories) {
        ItemStack rest = stack.copy();
        for (var inventory : inventories) {
            for (int slot = 0; slot < inventory.getSlots() && !rest.isEmpty(); slot++) {
                rest = inventory.insertItemInternal(slot, rest, false);
            }
            if (rest.isEmpty()) break;
        }
        return rest;
    }

    @Override
    public ItemStack insertOutput(ItemStack stack) {
        return insertInto(stack, inventories(true));
    }

    @Override
    public ItemStack insertInput(ItemStack stack) {
        return insertInto(stack, inventories(false));
    }

    @Override
    public int padTier() {
        int tier = 0;
        for (var part : getParts()) {
            if (part instanceof EnergyHatchPartMachine hatch) tier = Math.max(tier, hatch.getTier());
        }
        return tier;
    }

    @Override
    public long energyCapacity() {
        long total = 0;
        for (var part : getParts()) {
            if (part instanceof EnergyHatchPartMachine hatch) total += hatch.energyContainer.getEnergyCapacity();
        }
        return total;
    }

    @Override
    public long storedEnergy() {
        long total = 0;
        for (var part : getParts()) {
            if (part instanceof EnergyHatchPartMachine hatch) total += hatch.energyContainer.getEnergyStored();
        }
        return total;
    }

    @Override
    public boolean drainEnergy(long amount) {
        if (amount <= 0) return true;
        if (storedEnergy() < amount) return false;

        long remaining = amount;
        for (var part : getParts()) {
            if (remaining <= 0) break;
            if (!(part instanceof EnergyHatchPartMachine hatch)) continue;

            long take = Math.min(remaining, hatch.energyContainer.getEnergyStored());
            if (take <= 0) continue;
            hatch.energyContainer.changeEnergy(-take);
            remaining -= take;
        }
        return true;
    }
}

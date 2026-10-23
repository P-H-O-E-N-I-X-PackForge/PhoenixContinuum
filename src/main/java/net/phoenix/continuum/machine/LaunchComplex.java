package net.phoenix.continuum.machine;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public interface LaunchComplex {

    boolean isFormed();

    default boolean hasBuses() {
        return true;
    }

    int count(Predicate<ItemStack> matches);

    ItemStack peek(Predicate<ItemStack> matches);

    List<ItemStack> take(Predicate<ItemStack> matches, int amount);

    ItemStack insertOutput(ItemStack stack);

    ItemStack insertInput(ItemStack stack);

    int padTier();

    long energyCapacity();

    long storedEnergy();

    boolean drainEnergy(long amount);

    long chargeEnergy(long amount);

    static List<LaunchComplex> complexesOf(UUID team) {
        return LaunchComplexes.of(team);
    }
}

package net.phoenix.continuum.machine;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A launch complex as the rest of Continuum sees it. The machine itself is a different class per GregTech version (the
 * controller API changed between 7.x and 8.x); everything that launches rockets works through this.
 */
public interface LaunchComplex {

    boolean isFormed();

    /** False for the plain Launch Pad: rockets, probes and kits come from the launching player, not from item buses. */
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

    /** The formed launch complexes whose power outposts draw their upkeep from. */
    static List<LaunchComplex> complexesOf(UUID team) {
        return LaunchComplexes.of(team);
    }
}

package net.phoenix.continuum.integration.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.machine.ContinuumMachines;
import net.phoenix.continuum.machine.LaunchComplex;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Everything that touches GregTech classes while Continuum is set up; only loaded when GregTech is installed. */
public final class ContinuumGt {

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(PhoenixContinuum.MOD_ID);

    private ContinuumGt() {}

    public static void init(IEventBus modEventBus) {
        REGISTRATE.registerEventListeners(modEventBus);
        ContinuumMachines.init();
    }

    public static @Nullable LaunchComplex complexAt(Level level, BlockPos pos) {
        return MetaMachine.getMachine(level, pos) instanceof LaunchComplex complex ? complex : null;
    }

    public static List<ItemStack> machineStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(ContinuumMachines.LAUNCH_COMPLEX.asStack());
        stacks.add(ContinuumMachines.SWARM_RECEIVER.asStack());
        return stacks;
    }

    public static long voltage(int tier) {
        var v = com.gregtechceu.gtceu.api.GTValues.V;
        return v[Math.max(0, Math.min(tier, v.length - 1))];
    }

    public static String tierName(int tier) {
        var vn = com.gregtechceu.gtceu.api.GTValues.VN;
        return vn[Math.max(0, Math.min(tier, vn.length - 1))];
    }
}

package net.phoenix.continuum.integration.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenix.continuum.ContinuumRegistry;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.machine.ContinuumMachines;
import net.phoenix.continuum.machine.LaunchComplex;

import com.tterrag.registrate.util.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;

public final class ContinuumGt {

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(PhoenixContinuum.MOD_ID);

    public static final RegistryEntry<CreativeModeTab> CREATIVE_TAB = REGISTRATE
            .defaultCreativeTab(PhoenixContinuum.MOD_ID,
                    builder -> builder
                            .title(Component.translatable("itemGroup." + PhoenixContinuum.MOD_ID + ".creative_tab"))
                            .icon(() -> ContinuumRegistry.ROCKET.asStack())
                            .displayItems((parameters, output) -> {
                                for (ItemStack stack : ContinuumRegistry.allStacks()) output.accept(stack);
                            })
                            .build())
            .register();

    private ContinuumGt() {}

    public static void init(IEventBus modEventBus) {
        GTCEuCompat.init(modEventBus);
        modEventBus.addGenericListener(MachineDefinition.class,
                (GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) -> ContinuumMachines.init());
    }

    public static @Nullable LaunchComplex complexAt(Level level, BlockPos pos) {
        return MetaMachine.getMachine(level, pos) instanceof LaunchComplex complex ? complex : null;
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

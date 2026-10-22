package net.phoenix.continuum;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.phoenix.continuum.block.LaunchPadBlock;
import net.phoenix.continuum.block.ObservatoryBlock;
import net.phoenix.continuum.common.RocketStats;
import net.phoenix.continuum.item.ContinuumProbeItem;
import net.phoenix.continuum.item.ContinuumRefitKitItem;
import net.phoenix.continuum.item.ContinuumRepairKitItem;
import net.phoenix.continuum.item.ContinuumRocketItem;
import net.phoenix.continuum.item.ContinuumUpgradeItem;

import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;

import static net.phoenix.continuum.PhoenixContinuum.REGISTRATE;

public final class ContinuumRegistry {

    private ContinuumRegistry() {}

    public static final ItemEntry<ContinuumRocketItem> ROCKET = REGISTRATE
            .item("continuum_rocket", ContinuumRocketItem::new)
            .lang("Continuum Rocket")
            .register();

    public static final ItemEntry<ContinuumProbeItem> PROBE = REGISTRATE
            .item("continuum_probe", ContinuumProbeItem::new)
            .lang("Extraction Probe")
            .register();

    public static final ItemEntry<ContinuumUpgradeItem> MODULE_OVERDRIVE = REGISTRATE
            .item("continuum_module_overdrive",
                    p -> new ContinuumUpgradeItem(p, RocketStats.OVERDRIVE, 5, "Each level: -10% trip time"))
            .lang("Overdrive Module")
            .register();

    public static final ItemEntry<ContinuumUpgradeItem> MODULE_HULL = REGISTRATE
            .item("continuum_module_hull",
                    p -> new ContinuumUpgradeItem(p, RocketStats.REINFORCED_HULL, 3, "Each level: -25% wear per trip"))
            .lang("Reinforced Hull Plating")
            .register();

    public static final ItemEntry<ContinuumUpgradeItem> MODULE_INTERLOCK = REGISTRATE
            .item("continuum_module_interlock",
                    p -> new ContinuumUpgradeItem(p, RocketStats.HULL_INTERLOCK, 1,
                            "Refuses to launch when the rocket is too worn"))
            .lang("Hull Interlock Module")
            .register();

    public static final ItemEntry<ContinuumUpgradeItem> MODULE_STELLAR_SHIELD = REGISTRATE
            .item("continuum_module_stellar_shield",
                    p -> new ContinuumUpgradeItem(p, RocketStats.STELLAR_SHIELD, 1,
                            "Lets the rocket extract from, and build outposts on, a star"))
            .lang("Stellar Shielding Module")
            .register();

    public static final ItemEntry<ContinuumUpgradeItem> MODULE_SINGULARITY_SHIELD = REGISTRATE
            .item("continuum_module_singularity_shield",
                    p -> new ContinuumUpgradeItem(p, RocketStats.SINGULARITY_SHIELD, 1,
                            "Lets the rocket extract from, and build outposts on, a black hole or quasar"))
            .lang("Singularity Shielding Module")
            .register();

    public static final ItemEntry<ContinuumRefitKitItem> REFIT_MK2 = REGISTRATE
            .item("continuum_hull_refit_mk2", p -> new ContinuumRefitKitItem(p, 2))
            .lang("Hull Refit Kit Mk II")
            .register();

    public static final ItemEntry<ContinuumRefitKitItem> REFIT_MK3 = REGISTRATE
            .item("continuum_hull_refit_mk3", p -> new ContinuumRefitKitItem(p, 3))
            .lang("Hull Refit Kit Mk III")
            .register();

    public static final ItemEntry<ContinuumRefitKitItem> REFIT_MK4 = REGISTRATE
            .item("continuum_hull_refit_mk4", p -> new ContinuumRefitKitItem(p, 4))
            .lang("Hull Refit Kit Mk IV")
            .register();

    public static final ItemEntry<ContinuumRepairKitItem> REPAIR_KIT = REGISTRATE
            .item("continuum_repair_kit", ContinuumRepairKitItem::new)
            .lang("Rocket Repair Kit")
            .register();

    public static final BlockEntry<LaunchPadBlock> LAUNCH_PAD = REGISTRATE
            .block("continuum_launch_pad", LaunchPadBlock::new)
            .initialProperties(() -> Blocks.POLISHED_BLACKSTONE)
            .properties(p -> p.strength(4.0f, 12.0f))
            .lang("Test Launch Pad (no power or tier)")
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(), prov.models().cubeBottomTop(ctx.getName(),
                    prov.mcLoc("block/chiseled_polished_blackstone"), prov.mcLoc("block/polished_blackstone"),
                    prov.mcLoc("block/polished_blackstone"))))
            .item(BlockItem::new)
            .build()
            .register();

    public static final BlockEntry<ObservatoryBlock> OBSERVATORY = REGISTRATE
            .block("continuum_observatory", ObservatoryBlock::new)
            .initialProperties(() -> Blocks.POLISHED_DEEPSLATE)
            .properties(p -> p.strength(3.0f, 9.0f).noOcclusion())
            .lang("Observatory")
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(), prov.models().cubeBottomTop(ctx.getName(),
                    prov.mcLoc("block/polished_deepslate"), prov.mcLoc("block/polished_deepslate"),
                    prov.mcLoc("block/lapis_block"))))
            .item(BlockItem::new)
            .build()
            .register();

    public static void init() {}
}

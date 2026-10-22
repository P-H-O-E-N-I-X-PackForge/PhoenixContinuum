package net.phoenix.continuum;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.phoenix.continuum.block.LaunchPadBlock;
import net.phoenix.continuum.block.LaunchPadBlockEntity;
import net.phoenix.continuum.block.ObservatoryBlock;
import net.phoenix.continuum.block.SwarmReceiverBlock;
import net.phoenix.continuum.block.SwarmReceiverBlockEntity;
import net.phoenix.continuum.common.RocketStats;
import net.phoenix.continuum.integration.gtceu.GregTech;
import net.phoenix.continuum.item.ContinuumProbeItem;
import net.phoenix.continuum.item.ContinuumRefitKitItem;
import net.phoenix.continuum.item.ContinuumRepairKitItem;
import net.phoenix.continuum.item.ContinuumRocketItem;
import net.phoenix.continuum.item.ContinuumUpgradeItem;

import java.util.function.Supplier;

import static net.phoenix.continuum.PhoenixContinuum.MOD_ID;

/**
 * Continuum items and blocks. These use plain deferred registers, so Continuum starts without GregTech (and without
 * Registrate); the GregTech launch complex is registered on its own when GregTech is there.
 */
public final class ContinuumRegistry {

    private ContinuumRegistry() {}

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            MOD_ID);

    /** A registered item that can be used wherever an item, or a supplier of one, is wanted. */
    public static final class ItemRef<T extends Item> implements Supplier<T>, ItemLike {

        private final RegistryObject<T> object;

        ItemRef(RegistryObject<T> object) {
            this.object = object;
        }

        @Override
        public T get() {
            return object.get();
        }

        @Override
        public Item asItem() {
            return object.get();
        }

        public ItemStack asStack() {
            return new ItemStack(object.get());
        }

        public ItemStack asStack(int count) {
            return new ItemStack(object.get(), count);
        }
    }

    public static final class BlockRef<T extends Block> implements Supplier<T>, ItemLike {

        private final RegistryObject<T> object;

        BlockRef(RegistryObject<T> object) {
            this.object = object;
        }

        @Override
        public T get() {
            return object.get();
        }

        @Override
        public Item asItem() {
            return object.get().asItem();
        }

        public ItemStack asStack() {
            return new ItemStack(object.get());
        }
    }

    private static <T extends Item> ItemRef<T> item(String name, Supplier<T> factory) {
        return new ItemRef<>(ITEMS.register(name, factory));
    }

    private static <T extends Block> BlockRef<T> block(String name, Supplier<T> factory) {
        BlockRef<T> ref = new BlockRef<>(BLOCKS.register(name, factory));
        ITEMS.register(name, () -> new BlockItem(ref.get(), new Item.Properties()));
        return ref;
    }

    public static final ItemRef<ContinuumRocketItem> ROCKET = item("continuum_rocket",
            () -> new ContinuumRocketItem(new Item.Properties()));

    public static final ItemRef<ContinuumProbeItem> PROBE = item("continuum_probe",
            () -> new ContinuumProbeItem(new Item.Properties()));

    public static final ItemRef<ContinuumUpgradeItem> MODULE_OVERDRIVE = item("continuum_module_overdrive",
            () -> new ContinuumUpgradeItem(new Item.Properties(), RocketStats.OVERDRIVE, 5,
                    "Each level: -10% trip time"));

    public static final ItemRef<ContinuumUpgradeItem> MODULE_HULL = item("continuum_module_hull",
            () -> new ContinuumUpgradeItem(new Item.Properties(), RocketStats.REINFORCED_HULL, 3,
                    "Each level: -25% wear per trip"));

    public static final ItemRef<ContinuumUpgradeItem> MODULE_INTERLOCK = item("continuum_module_interlock",
            () -> new ContinuumUpgradeItem(new Item.Properties(), RocketStats.HULL_INTERLOCK, 1,
                    "Refuses to launch when the rocket is too worn"));

    public static final ItemRef<ContinuumUpgradeItem> MODULE_STELLAR_SHIELD = item(
            "continuum_module_stellar_shield",
            () -> new ContinuumUpgradeItem(new Item.Properties(), RocketStats.STELLAR_SHIELD, 1,
                    "Lets the rocket extract from, and build outposts on, a star"));

    public static final ItemRef<ContinuumUpgradeItem> MODULE_SINGULARITY_SHIELD = item(
            "continuum_module_singularity_shield",
            () -> new ContinuumUpgradeItem(new Item.Properties(), RocketStats.SINGULARITY_SHIELD, 1,
                    "Lets the rocket extract from, and build outposts on, a black hole or quasar"));

    public static final ItemRef<ContinuumRefitKitItem> REFIT_MK2 = item("continuum_hull_refit_mk2",
            () -> new ContinuumRefitKitItem(new Item.Properties(), 2));

    public static final ItemRef<ContinuumRefitKitItem> REFIT_MK3 = item("continuum_hull_refit_mk3",
            () -> new ContinuumRefitKitItem(new Item.Properties(), 3));

    public static final ItemRef<ContinuumRefitKitItem> REFIT_MK4 = item("continuum_hull_refit_mk4",
            () -> new ContinuumRefitKitItem(new Item.Properties(), 4));

    public static final ItemRef<ContinuumRepairKitItem> REPAIR_KIT = item("continuum_repair_kit",
            () -> new ContinuumRepairKitItem(new Item.Properties()));

    public static final BlockRef<LaunchPadBlock> LAUNCH_PAD = block("continuum_launch_pad",
            () -> new LaunchPadBlock(BlockBehaviour.Properties.copy(Blocks.POLISHED_BLACKSTONE).strength(4.0f, 12.0f)));

    public static final BlockRef<ObservatoryBlock> OBSERVATORY = block("continuum_observatory",
            () -> new ObservatoryBlock(
                    BlockBehaviour.Properties.copy(Blocks.POLISHED_DEEPSLATE).strength(3.0f, 9.0f).noOcclusion()));

    public static final BlockRef<SwarmReceiverBlock> SWARM_RECEIVER = block("continuum_swarm_receiver",
            () -> new SwarmReceiverBlock(BlockBehaviour.Properties.copy(Blocks.POLISHED_BLACKSTONE).strength(4.0f, 12.0f)));

    public static final RegistryObject<BlockEntityType<SwarmReceiverBlockEntity>> SWARM_RECEIVER_ENTITY = BLOCK_ENTITIES
            .register("continuum_swarm_receiver", () -> BlockEntityType.Builder
                    .of(SwarmReceiverBlockEntity::new, SWARM_RECEIVER.get()).build(null));

    public static final RegistryObject<BlockEntityType<LaunchPadBlockEntity>> LAUNCH_PAD_ENTITY = BLOCK_ENTITIES
            .register("continuum_launch_pad",
                    () -> BlockEntityType.Builder.of(LaunchPadBlockEntity::new, LAUNCH_PAD.get()).build(null));

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = TABS.register("creative_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MOD_ID + ".creative_tab"))
                    .icon(() -> ROCKET.asStack())
                    .displayItems((parameters, output) -> {
                        for (RegistryObject<Item> entry : ITEMS.getEntries()) output.accept(entry.get());
                        if (GregTech.loaded()) {
                            ItemStack complex = net.phoenix.continuum.integration.gtceu.ContinuumGt
                                    .launchComplexStack();
                            if (!complex.isEmpty()) output.accept(complex);
                        }
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
    }
}

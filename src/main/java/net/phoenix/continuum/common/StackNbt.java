package net.phoenix.continuum.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.Nullable;

public final class StackNbt {

    private static final net.minecraft.core.HolderLookup.Provider PROVIDER = net.minecraft.core.RegistryAccess
            .fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);

    private StackNbt() {}

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CompoundTag copy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (stack.isEmpty()) return copy;
        CustomData fresh = CustomData.of(copy);
        stack.set(DataComponents.CUSTOM_DATA, fresh);
        return fresh.getUnsafe();
    }

    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void removeTagKey(ItemStack stack, String key) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(key));
    }

    public static CompoundTag saveStack(ItemStack stack) {
        if (stack.isEmpty()) return new CompoundTag();
        return (CompoundTag) stack.save(PROVIDER);
    }

    public static void saveInto(ItemStack stack, CompoundTag into) {
        if (!stack.isEmpty()) stack.save(PROVIDER, into);
    }

    public static ItemStack loadStack(CompoundTag tag) {
        return ItemStack.parseOptional(PROVIDER, tag);
    }
}

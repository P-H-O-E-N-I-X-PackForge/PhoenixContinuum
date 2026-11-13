package net.phoenix.continuum.item;

import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.common.RocketStats;

import java.util.List;

public class ContinuumRocketItem extends Item {

    public ContinuumRocketItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,
                                                                      net.minecraft.world.entity.player.Player player,
                                                                      net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    net.phoenix.continuum.client.ContinuumClientHooks.openRefit();
                }
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        int tier = RocketStats.tier(stack);
        Component name = super.getName(stack);
        return tier > 1 ? name.copy().append(" " + RocketStats.tierName(tier)) : name;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return RocketStats.wear(stack) > 0.0f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * (1.0f - RocketStats.wear(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0f, 1.0f - RocketStats.wear(stack)) / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        Level level = context.level();
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.hull", RocketStats.tierName(RocketStats.tier(stack)))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.sneak_use_to_refit").withStyle(ChatFormatting.DARK_GRAY));
        float wear = RocketStats.wear(stack);
        ChatFormatting wearColor = wear < 0.35f ? ChatFormatting.GREEN : wear < 0.7f ? ChatFormatting.YELLOW :
                ChatFormatting.RED;
        tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.wear").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Math.round(wear * 100.0f) + "%").withStyle(wearColor)));

        int overdrive = RocketStats.level(stack, RocketStats.OVERDRIVE);
        int hull = RocketStats.level(stack, RocketStats.REINFORCED_HULL);
        boolean interlock = RocketStats.level(stack, RocketStats.HULL_INTERLOCK) > 0;

        if (overdrive > 0) {
            tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.overdrive_trip_time", overdrive, overdrive * 10)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (hull > 0) {
            tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.reinforced_hull_wear", hull, Math.min(75, hull * 25))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (interlock) {
            tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.hull_interlock_refuses_to_launch")
                    .withStyle(ChatFormatting.AQUA));
            if (RocketStats.interlockRefuses(stack)) {
                tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.interlock_engaged_repair_needed").withStyle(ChatFormatting.RED));
            }
        }
        boolean stellar = RocketStats.level(stack, RocketStats.STELLAR_SHIELD) > 0;
        boolean singularity = RocketStats.level(stack, RocketStats.SINGULARITY_SHIELD) > 0;
        if (stellar) {
            tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.stellar_shielding_can_work_at").withStyle(ChatFormatting.AQUA));
        }
        if (singularity) {
            tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.singularity_shielding_can_work_at")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (overdrive == 0 && hull == 0 && !interlock && !stellar && !singularity) {
            tooltip.add(ContinuumLang.tr("phoenix_continuum.continuum_rocket_item.no_upgrades").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

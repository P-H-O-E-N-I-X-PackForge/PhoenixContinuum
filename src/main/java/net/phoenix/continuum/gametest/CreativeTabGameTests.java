package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;

/** The creative screen builds every tab contents; a duplicate item in a tab makes that throw (and crashes the client). */
@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreativeTabGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void everyCreativeTabBuilds(GameTestHelper helper) {
        var level = helper.getLevel();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        helper.succeed();
    }
}

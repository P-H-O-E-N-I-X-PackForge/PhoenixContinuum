package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class DiscoverAllGameTests {

    @GameTest(template = "gametest_empty", timeoutTicks = 100)
    public static void discoverAllIsAnAcceptedCommand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(4);
        var parse = server.getCommands().getDispatcher().parse("continuumadmin discover all surveyed", source);
        helper.assertTrue(parse.getReader().canRead() == false, "the command should parse completely");
        helper.assertTrue(parse.getExceptions().isEmpty(), "the command should not have parse errors");
        helper.succeed();
    }
}

package net.phoenix.continuum.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.SystemGenerator;

@GameTestHolder(PhoenixContinuum.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneratedGalaxyGameTests {

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), command);
    }

    private static long generated() {
        return ContinuumData.systems().stream()
                .filter(system -> system.id().getNamespace().equals(SystemGenerator.NAMESPACE)).count();
    }

    @GameTest(template = "gametest_empty", timeoutTicks = 200)
    public static void generateClearAndUndoWorkPerWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        run(server, "continuumadmin generate clear");
        helper.assertTrue(generated() == 0, "a clean start should have no generated systems, has " + generated());

        run(server, "continuumadmin generate 5 42");
        helper.assertTrue(generated() == 5, "generate 5 should give 5 systems, has " + generated());
        helper.assertTrue(ContinuumData.allBodies().stream()
                .anyMatch(body -> body.id().getNamespace().equals(SystemGenerator.NAMESPACE)),
                "the generated systems should have worlds");

        run(server, "continuumadmin generate undo");
        helper.assertTrue(generated() == 0, "undo should put back the empty galaxy, has " + generated());

        run(server, "continuumadmin generate 3 7");
        run(server, "continuumadmin generate clear");
        helper.assertTrue(generated() == 0, "clear should remove them, has " + generated());
        run(server, "continuumadmin generate undo");
        helper.assertTrue(generated() == 3, "undoing the clear should bring back 3, has " + generated());

        run(server, "continuumadmin generate clear");
        helper.succeed();
    }
}

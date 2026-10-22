package net.phoenix.continuum.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackSelectionConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.phoenix.continuum.PhoenixContinuum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ContinuumPackFolder {

    public static final String ID = "continuum_pack_folder";
    private static final int DATA_PACK_FORMAT = 48;

    private ContinuumPackFolder() {}

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("phoenix_continuum").resolve("pack");
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;

        Path root = folder();
        try {
            Files.createDirectories(root.resolve("data"));
            Path meta = root.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, "{\"pack\":{\"pack_format\":" + DATA_PACK_FORMAT +
                        ",\"description\":\"Continuum pack folder\"}}");
            }
            Path readme = root.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Drop Continuum content here like a data pack:

                          data/<namespace>/continuum/bodies/<name>.json
                          data/<namespace>/continuum/systems/<name>.json

                        A body or system can start from another one with "inherits" and only write what differs.
                        Run /continuumadmin reload to load changes, and /continuumadmin validate to see problems.
                        """);
            }
        } catch (IOException e) {
            PhoenixContinuum.LOGGER.warn("Could not prepare the Continuum pack folder {}: {}", root, e.getMessage());
            return;
        }

        event.addRepositorySource(consumer -> {
            PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Continuum pack folder"),
                    PackSource.BUILT_IN, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(root),
                    PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
        });
    }
}

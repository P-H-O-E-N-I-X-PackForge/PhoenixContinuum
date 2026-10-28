package net.phoenix.continuum.pdim;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.PdimWorldgen;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Turns the registered {@link PdimWorldgen} specs into dimension files, in a small data pack Continuum owns and
 * rewrites on every start. The game reads dimensions when it loads data packs, so this has to happen while the list of
 * packs is being built: specs added later wait for a restart.
 */
public final class PdimWorldgenPack {

    public static final String ID = "continuum_pdim_worldgen";
    private static final int DATA_PACK_FORMAT = 48;

    /** Called by scripts: lets startup scripts register their specs. Set by the KubeJS integration when it is loaded. */
    public static volatile Consumer<Void> scriptHook = null;

    private PdimWorldgenPack() {}

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("phoenix_continuum").resolve("pdim_worldgen_pack");
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;

        PdimWorldgenFiles.load(FMLPaths.CONFIGDIR.get().resolve("phoenix_continuum").resolve("pdim_worldgen"));
        NeoForge.EVENT_BUS.post(new PdimWorldgen.Collect());
        Consumer<Void> hook = scriptHook;
        if (hook != null) hook.accept(null);

        Path root = folder();
        try {
            deleteTree(root.resolve("data"));
            Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + DATA_PACK_FORMAT +
                    ",\"description\":\"Continuum personal dimension worldgen (generated, do not edit)\"}}");
            Path dimensions = root.resolve("data").resolve(PhoenixContinuum.MOD_ID).resolve("dimension")
                    .resolve("pdim");
            Files.createDirectories(dimensions);
            var gson = new GsonBuilder().setPrettyPrinting().create();
            int written = 0;
            for (PdimWorldgen.Spec spec : PdimWorldgen.all()) {
                Files.writeString(dimensions.resolve(spec.body().getPath() + ".json"),
                        gson.toJson(spec.toDimensionJson()));
                written++;
            }
            if (written > 0) PhoenixContinuum.LOGGER.info("Wrote worldgen for {} personal dimension(s)", written);
        } catch (IOException e) {
            PhoenixContinuum.LOGGER.warn("Could not write the personal dimension worldgen pack {}: {}", root,
                    e.getMessage());
            return;
        }

        event.addRepositorySource(consumer -> {
            PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Continuum personal dimension worldgen"),
                    PackSource.BUILT_IN, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(root),
                    PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
        });
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            for (Path p : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }
}

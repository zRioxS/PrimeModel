package kr.rioxs.primemodel;
import kr.rioxs.primemodel.api.manager.Managers.Manager;

import kr.rioxs.primemodel.api.PrimeModelPlatform;
import kr.rioxs.primemodel.manager.ReloadPipeline;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.function.BiConsumer;

public interface PrimeModelPlatformImpl extends PrimeModelPlatform {

    void saveResource(@NotNull String resourcePath);

    void loadAssets(@NotNull ReloadPipeline pipeline, @NotNull String prefix, @NotNull BiConsumer<String, InputStream> consumer);
}

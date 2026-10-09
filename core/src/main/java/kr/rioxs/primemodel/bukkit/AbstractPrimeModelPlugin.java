package kr.rioxs.primemodel.bukkit;

import kr.rioxs.primemodel.PrimeModelPlatformImpl;
import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.PrimeModelLogger;
import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit;
import kr.rioxs.primemodel.api.bukkit.platform.BukkitPlatform.BukkitAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public abstract class AbstractPrimeModelPlugin extends JavaPlugin implements PrimeModelPlatformImpl, PrimeModelBukkit {

    protected boolean skipInitialReload;
    protected final AtomicBoolean onReload = new AtomicBoolean();
    protected final AtomicBoolean firstLoad = new AtomicBoolean();
    protected final BukkitAdapter adapter = new BukkitAdapter();
    protected final PrimeModelLogger logger = new PrimeModelLogger() {

        private volatile ComponentLogger internalLogger;

        private @NotNull ComponentLogger logger() {
            ComponentLogger logger;
            if ((logger = internalLogger) != null) return logger;
            synchronized (this) {
                if ((logger = internalLogger) != null) return logger;
                return internalLogger = ComponentLogger.logger(getLogger().getName());
            }
        }

        @Override
        public void info(@NotNull Component... message) {
            var log = logger();
            synchronized (this) {
                for (Component s : message) {
                    log.info(s);
                }
            }
        }

        @Override
        public void warn(@NotNull Component... message) {
            var log = logger();
            synchronized (this) {
                for (Component s : message) {
                    log.warn(s);
                }
            }
        }
    };
    private @Nullable Attributes attributes;

    public void onLoad() {
        new PrimeModelLibrary().load(this);
        PrimeModel.register(this);
    }

    public void skipInitialReload() {
        this.skipInitialReload = true;
    }

    @Override
    public void saveResource(@NotNull String resourcePath) {
        saveResource(resourcePath, false);
    }

    @Override
    @NotNull
    public BukkitAdapter adapter() {
        return adapter;
    }

    public @NotNull Attributes attributes() {
        if (attributes != null) return attributes;
        synchronized (this) {
            if (attributes != null) return attributes;
            try (
                var stream = Objects.requireNonNull(getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF"))
            ) {
                return attributes = new Manifest(stream).getMainAttributes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
package kr.rioxs.primemodel.api.pack;

import kr.rioxs.primemodel.api.PrimeModel;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Merged pack class containing PackNamespace, PackBuilder, PackAssets, PackResult.
 */
public final class Pack {

    private Pack() {
        throw new RuntimeException();
    }

    // ============================================================
    // PackNamespace
    // ============================================================
    public static final class PackNamespace {

        private final PackBuilder items, models, textures;

        PackNamespace(@NotNull PackAssets assets, @NotNull String namespace) {
            var subPath = assets.path.resolve("assets", namespace);
            items = new PackBuilder(assets, subPath.resolve("items"));
            models = new PackBuilder(assets, subPath.resolve("models"));
            textures = new PackBuilder(assets, subPath.resolve("textures", "item"));
        }

        public @NotNull PackBuilder items() {
            return items;
        }

        public @NotNull PackBuilder models() {
            return models;
        }

        public @NotNull PackBuilder textures() {
            return textures;
        }
    }

    // ============================================================
    // PackBuilder
    // ============================================================
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static final class PackBuilder {
        private final PackAssets assets;
        private final PackPath path;
        private final PackObfuscator obfuscator = PackObfuscator.order();

        public @NotNull PackBuilder resolve(@NotNull String... paths) {
            return new PackBuilder(assets, path.resolve(paths));
        }

        public void add(@NotNull String path, long estimatedSize, @NotNull Supplier<byte[]> supplier) {
            add(new String[] { path }, estimatedSize, supplier);
        }

        public void add(@NotNull String[] paths, long size, @NotNull Supplier<byte[]> supplier) {
            var resolve = path.resolve(paths);
            assets.resourceMap.putIfAbsent(resolve, PackResource.of(assets.overlay, resolve, size, supplier));
        }

        public @NotNull PackObfuscator obfuscator() {
            return obfuscator;
        }

        public void add(@NotNull String path, @NotNull Supplier<byte[]> supplier) {
            add(path, -1, supplier);
        }

        public void add(@NotNull String[] paths, @NotNull Supplier<byte[]> supplier) {
            add(paths, -1, supplier);
        }
    }

    // ============================================================
    // PackAssets
    // ============================================================
    public static final class PackAssets {
        final PackPath path;
        final PackOverlay overlay;
        final Map<PackPath, PackResource> resourceMap = new ConcurrentHashMap<>();

        private final PackNamespace primemodel, minecraft;
        private final PackObfuscator obfuscator = PackObfuscator.order();

        PackAssets(@NotNull PackOverlay overlay, @NotNull PackObfuscator obfuscator) {
            this.overlay = overlay;
            this.path = overlay.path(PrimeModel.config().namespace(), obfuscator);
            primemodel = new PackNamespace(this, PrimeModel.config().namespace());
            minecraft = new PackNamespace(this, "minecraft");
        }

        public @NotNull PackNamespace primemodel() {
            return primemodel;
        }

        public @NotNull PackNamespace minecraft() {
            return minecraft;
        }

        public @NotNull String obfuscate(@NotNull String namespace) {
            return obfuscator.obfuscate(namespace);
        }

        int size() {
            return resourceMap.size();
        }

        boolean dirty() {
            return size() > 0;
        }

        public void add(@NotNull String path, long size, @NotNull Supplier<byte[]> supplier) {
            add(new String[] { path }, size, supplier);
        }

        public void add(@NotNull String[] paths, long size, @NotNull Supplier<byte[]> supplier) {
            var resolve = path.resolve(paths);
            resourceMap.putIfAbsent(resolve, PackResource.of(overlay, resolve, size, supplier));
        }

        public void add(@NotNull String path, @NotNull Supplier<byte[]> supplier) {
            add(path, -1, supplier);
        }

        public void add(@NotNull String[] paths, @NotNull Supplier<byte[]> supplier) {
            add(paths, -1, supplier);
        }
    }

    // ============================================================
    // PackResult
    // ============================================================
    @RequiredArgsConstructor
    public static final class PackResult {
        private final PackMeta meta;
        private final File directory;
        private final SortedMap<PackOverlay, SortedSet<PackByte>> overlays = new TreeMap<>();
        private final SortedSet<PackByte> assets = new TreeSet<>();
        private final SortedSet<PackByte> assetsView = Collections.unmodifiableSortedSet(assets);

        private final long creationTime = System.currentTimeMillis();
        private boolean frozen = false;
        private boolean changed = false;
        private UUID uuid;

        @ApiStatus.Internal
        public void set(@Nullable PackOverlay overlay, @NotNull PackByte packByte) {
            if (frozen) throw new IllegalStateException("result is frozen.");
            if (overlay == null) {
                synchronized (assets) {
                    assets.add(packByte);
                }
                return;
            }
            synchronized (overlays) {
                overlays.computeIfAbsent(overlay, _ -> new TreeSet<>()).add(packByte);
            }
        }

        public void freeze() {
            freeze(false);
        }

        public boolean changed() {
            return changed;
        }

        public void freeze(boolean changed) {
            if (frozen) throw new IllegalStateException("result is frozen.");
            frozen = true;
            this.changed = changed;
        }

        @NotNull
        public PackMeta meta() {
            return meta;
        }

        public @Nullable File directory() {
            return directory;
        }

        public @NotNull UUID hash() {
            if (uuid != null) return uuid;
            synchronized (this) {
                if (uuid != null) return uuid;
                try {
                    var sha = MessageDigest.getInstance("SHA-256");
                    stream().map(PackByte::bytes).forEach(sha::update);
                    return uuid = UUID.nameUUIDFromBytes(sha.digest());
                } catch (Exception e) {
                    return uuid = UUID.randomUUID();
                }
            }
        }

        public int size() {
            return assets.size() + overlays.values().stream().mapToInt(Set::size).sum();
        }

        public long time() {
            return System.currentTimeMillis() - creationTime;
        }

        @NotNull
        @Unmodifiable
        public SortedSet<PackByte> overlays(@NotNull PackOverlay overlay) {
            var get = overlays.get(overlay);
            return get != null ? Collections.unmodifiableSortedSet(get) : Collections.emptySortedSet();
        }

        public @NotNull Stream<PackByte> stream() {
            return Stream.concat(
                overlays.values().stream().flatMap(Collection::stream),
                assets.stream()
            );
        }

        @NotNull
        @Unmodifiable
        public SortedSet<PackByte> assets() {
            return assetsView;
        }
    }
}
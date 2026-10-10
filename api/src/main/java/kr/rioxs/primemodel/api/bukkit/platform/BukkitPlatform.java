package kr.rioxs.primemodel.api.bukkit.platform;

import kr.rioxs.primemodel.api.bukkit.PrimeModelBukkit;
import kr.rioxs.primemodel.api.platform.PlatformAdapter;
import kr.rioxs.primemodel.api.platform.PlatformEntity;
import kr.rioxs.primemodel.api.platform.PlatformItemStack;
import kr.rioxs.primemodel.api.platform.PlatformLivingEntity;
import kr.rioxs.primemodel.api.platform.PlatformLocation;
import kr.rioxs.primemodel.api.platform.PlatformOfflinePlayer;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.scheduler.Schedulers.ModelTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import kr.rioxs.primemodel.api.platform.PlatformRegionHolder;

/**
 * Merged Bukkit platform classes.
 */
public final class BukkitPlatform {

    private BukkitPlatform() {
        throw new RuntimeException();
    }

    public static class BukkitEntity implements PlatformEntity {

        private final Entity delegate;

        public BukkitEntity(@NotNull Entity source) {
            this.delegate = source;
        }

        public Entity getSource() {
            return delegate;
        }

        @Override
        public @NotNull UUID uuid() {
            return delegate.getUniqueId();
        }

        @Override
        public @NotNull PlatformLocation location() {
            return BukkitAdapter.adapt(delegate.getLocation());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BukkitEntity that)) return false;
            return delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public String toString() {
            return "BukkitEntity(source=" + delegate + ")";
        }
    }

    public static class BukkitLivingEntity extends BukkitEntity implements PlatformLivingEntity {

        public BukkitLivingEntity(@NotNull LivingEntity source) {
            super(source);
        }

        @Override
        public LivingEntity getSource() {
            return (LivingEntity) super.getSource();
        }

        @Override
        public @NotNull PlatformLocation eyeLocation() {
            return BukkitAdapter.adapt(getSource().getEyeLocation());
        }
    }

    public static final class BukkitPlayer extends BukkitLivingEntity implements PlatformPlayer {

        public BukkitPlayer(@NotNull Player source) {
            super(source);
        }

        @Override
        public @NotNull Player getSource() {
            return (Player) super.getSource();
        }

        @Override
        public @NotNull String name() {
            return getSource().getName();
        }
    }

    public record BukkitItemStack(@NotNull ItemStack source) implements PlatformItemStack {
        @Override
        public boolean isAir() {
            return source.getType().isAir() || source.getAmount() <= 0;
        }

        @Override
        public @NotNull PlatformItemStack enchant(boolean enchant) {
            var meta = source.getItemMeta();
            if (meta == null) return this;
            meta.setEnchantmentGlintOverride(enchant);
            source.setItemMeta(meta);
            return this;
        }

        @Override
        public @NotNull PlatformItemStack itemModel(@Nullable PlatformRegionHolder.Namespace namespace) {
            var meta = source.getItemMeta();
            if (meta == null) return this;
            meta.setItemModel(namespace == null ? null : new NamespacedKey(namespace.namespace(), namespace.path()));
            source.setItemMeta(meta);
            return this;
        }

        @Override
        public @NotNull PlatformItemStack clone() {
            return BukkitAdapter.adapt(source.clone());
        }
    }

    public record BukkitLocation(@NotNull Location source) implements PlatformLocation {

        @Override
        public @NotNull PlatformLocation.World world() {
            return BukkitAdapter.adapt(source.getWorld());
        }

        @Override
        public double x() { return source.getX(); }

        @Override
        public double y() { return source.getY(); }

        @Override
        public double z() { return source.getZ(); }

        @Override
        public float pitch() { return source.getPitch(); }

        @Override
        public float yaw() { return source.getYaw(); }

        @Override
        public @NotNull PlatformLocation add(double x, double y, double z) {
            return BukkitAdapter.adapt(source.clone().add(x, y, z));
        }

        @Override
        public @Nullable ModelTask task(@NotNull Runnable runnable) {
            return PrimeModelBukkit.platform().scheduler().task(source, runnable);
        }

        @Override
        public @Nullable ModelTask taskLater(long delay, @NotNull Runnable runnable) {
            return PrimeModelBukkit.platform().scheduler().taskLater(source, delay, runnable);
        }
    }

    public record BukkitOfflinePlayer(@NotNull OfflinePlayer source) implements PlatformOfflinePlayer {
        @Override
        public @NotNull UUID uuid() {
            return source.getUniqueId();
        }

        @Override
        public @Nullable String name() {
            return source.getName();
        }
    }

    public record BukkitWorld(@NotNull World source) implements PlatformLocation.World {
    }

    public static final class BukkitAdapter implements PlatformAdapter {

        public static @NotNull PlatformEntity adapt(@NotNull Entity entity) {
            return new BukkitEntity(entity);
        }

        public static @NotNull PlatformLivingEntity adapt(@NotNull LivingEntity livingEntity) {
            return new BukkitLivingEntity(livingEntity);
        }

        public static @NotNull PlatformOfflinePlayer adapt(@NotNull OfflinePlayer player) {
            return new BukkitOfflinePlayer(player);
        }

        public static @NotNull PlatformPlayer adapt(@NotNull Player player) {
            return new BukkitPlayer(player);
        }

        public static @NotNull PlatformItemStack adapt(@NotNull ItemStack itemStack) {
            return new BukkitItemStack(itemStack);
        }

        public static @NotNull PlatformLocation adapt(@NotNull Location location) {
            return new BukkitLocation(location);
        }

        public static @NotNull PlatformLocation.World adapt(@NotNull World world) {
            return new BukkitWorld(world);
        }

        @Override
        public @Nullable PlatformPlayer player(@NotNull UUID uuid) {
            var bukkit = Bukkit.getPlayer(uuid);
            return bukkit != null ? adapt(bukkit) : null;
        }

        @Override
        public @NotNull PlatformOfflinePlayer offlinePlayer(@NotNull UUID uuid) {
            return adapt(Bukkit.getOfflinePlayer(uuid));
        }

        @Override
        public int serverViewDistance() {
            return Bukkit.getViewDistance();
        }

        @Override
        public boolean isTickThread() {
            return Bukkit.isPrimaryThread();
        }

        @Override
        public boolean isRegionSafe() {
            return !PrimeModelBukkit.IS_FOLIA || isTickThread();
        }

        @Override
        public @NotNull PlatformItemStack air() {
            return adapt(new ItemStack(Material.AIR));
        }

        @Override
        public @NotNull PlatformLocation zero() {
            return adapt(new Location(null, 0, 0, 0));
        }
    }
}

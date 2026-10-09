package kr.rioxs.primemodel.api.nms;

import java.util.Objects;
import java.util.Comparator;
import java.util.Objects;
import org.semver4j.Semver;
import java.util.Objects;
import java.util.Comparator;
import java.util.Objects;
import lombok.Getter;
import java.util.Objects;
import java.util.Comparator;
import java.util.Objects;
import org.semver4j.Semver;
import java.util.Objects;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import java.util.Comparator;
import java.util.Objects;
import org.semver4j.Semver;
import java.util.Objects;
import java.util.Comparator;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Enumerates supported Minecraft server versions and their associated metadata.
 * <p>
 * This enum maps internal version identifiers to Minecraft versions and resource pack formats.
 * </p>
 *
 * @since 1.15.2
 */
@RequiredArgsConstructor
@Getter
public enum NMSVersion {
    /**
     * Minecraft 1.21.4
     * @since 1.15.2
     */
    V1_21_R3(46),
    /**
     * Minecraft 1.21.5
     * @since 1.15.2
     */
    V1_21_R4(55),
    /**
     * Minecraft 1.21.6 - 1.21.8
     * @since 1.15.2
     */
    V1_21_R5(64),
    /**
     * Minecraft 1.21.9 - 1.21.10
     * @since 1.15.2
     */
    V1_21_R6(69),
    /**
     * Minecraft 1.21.11
     * @since 1.15.2
     */
    V1_21_R7(75),
    /**
     * Minecraft 26.1.x
     * @since 3.0.0
     */
    V26_R1(84),
    /**
     * Minecraft 26.2.x
     * @since 3.2.0
     */
    V26_R2(88),
    /**
     * Minecraft 26.3.x
     * @since 3.5.0
     */
    V26_R3(97)
    ;
    /**
     * The resource pack format version (pack.mcmeta).
     */
    private final int metaVersion;

    /**
     * Returns the oldest supported version.
     *
     * @return the first version enum
     * @since 1.15.2
     */
    public static @NotNull NMSVersion first() {
        return values()[0];
    }

    /**
     * Returns the latest supported version.
     *
     * @return the last version enum
     * @since 1.15.2
     */
    public static @NotNull NMSVersion latest() {
        var entries = values();
        return entries[entries.length - 1];
    }

    // ===== MinecraftVersion =====

    /**
     * Minecraft version.
     * @param major title
     * @param minor main update
     * @param patch minor update
     */
    public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {
        /**
         * 26.3
         */
        public static final MinecraftVersion V26_3 = of(26, 3, 0);
        /**
         * 26.2
         */
        public static final MinecraftVersion V26_2 = of(26, 2, 0);
        /**
         * 26.1.2
         */
        public static final MinecraftVersion V26_1_2 = of(26, 1, 2);
        /**
         * 26.1.1
         */
        public static final MinecraftVersion V26_1_1 = of(26, 1, 1);
        /**
         * 26.1
         */
        public static final MinecraftVersion V26_1 = of(26, 1, 0);
        /**
         * 1.21.11
         */
        public static final MinecraftVersion V1_21_11 = of(1, 21, 11);
        /**
         * 1.21.10
         */
        public static final MinecraftVersion V1_21_10 = of(1, 21, 10);
        /**
         * 1.21.9
         */
        public static final MinecraftVersion V1_21_9 = of(1, 21, 9);
        /**
         * 1.21.8
         */
        public static final MinecraftVersion V1_21_8 = of(1, 21, 8);
        /**
         * 1.21.7
         */
        public static final MinecraftVersion V1_21_7 = of(1, 21, 7);
        /**
         * 1.21.6
         */
        public static final MinecraftVersion V1_21_6 = of(1, 21, 6);
        /**
         * 1.21.5
         */
        public static final MinecraftVersion V1_21_5 = of(1, 21, 5);
        /**
         * 1.21.4
         */
        public static final MinecraftVersion V1_21_4 = of(1, 21, 4);

        /**
         * Comparator
         */
        private static final Comparator<MinecraftVersion> COMPARATOR = Comparator.comparing(MinecraftVersion::major)
            .thenComparing(MinecraftVersion::minor)
            .thenComparing(MinecraftVersion::patch);

        /**
         * Parses version from string
         * @param version version like "1.21.11"
         * @return parsed version
         */
        public static @NotNull MinecraftVersion parse(@NotNull String version) {
            var split = Objects.requireNonNull(Semver.coerce(version));
            return of(split.getMajor(), split.getMinor(), split.getPatch());
        }

        /**
         * Creates version
         * @param major major
         * @param minor minor
         * @param patch patch
         * @return Minecraft version
         */
        public static @NotNull MinecraftVersion of(int major, int minor, int patch) {
            return new MinecraftVersion(major, minor, patch);
        }

        @Override
        public int compareTo(@NotNull MinecraftVersion o) {
            return COMPARATOR.compare(this, o);
        }

        @NotNull
        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
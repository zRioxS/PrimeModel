package kr.rioxs.primemodel.api.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import kr.rioxs.primemodel.api.data.raw.RawData.ModelData;
import kr.rioxs.primemodel.api.data.raw.RawData.ModelLoadResult;
import kr.rioxs.primemodel.api.data.raw.RawData.ModelResolution;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import kr.rioxs.primemodel.api.util.Utils.PackUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Merged data classes.
 */
public final class DataClasses {

    private DataClasses() {
        throw new RuntimeException();
    }

    public record Float2(float x, float y) {
        public static final JsonDeserializer<Float2> PARSER = (json, _, _) -> {
            var array = json.getAsJsonArray();
            return new Float2(array.get(0).getAsFloat(), array.get(1).getAsFloat());
        };

        public @NotNull Vector2f toVector() {
            return new Vector2f(x, y);
        }
    }

    @ApiStatus.Internal
    public record Float3(float x, float y, float z) {

        public Float3(float value) {
            this(value, value, value);
        }

        public static final Float3 CENTER = new Float3(8, 8, 8);
        public static final Float3 ZERO = new Float3(0, 0, 0);
        public static final Float3 MESH_TRIANGLE_FROM = new Float3(-8, 0, 0);
        public static final Float3 MESH_TRIANGLE_TO = new Float3(0, 8, 0);

        public static final JsonDeserializer<Float3> PARSER = (json, _, _) -> {
            var array = json.getAsJsonArray();
            return new Float3(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
        };

        public @NotNull Float3 plus(@NotNull Float3 other) {
            return new Float3(x + other.x, y + other.y, z + other.z);
        }

        public @NotNull Float3 convertToMinecraftDegree() {
            var vec = MathUtil.toXYZEuler(toVector());
            return new Float3(vec.x, vec.y, vec.z);
        }

        public @NotNull Float3 rotate(@NotNull Quaternionf quaternionf) {
            var vec = toVector().rotate(quaternionf);
            return new Float3(vec.x, vec.y, vec.z);
        }

        public @NotNull Float3 minus(@NotNull Float3 other) {
            return new Float3(x - other.x, y - other.y, z - other.z);
        }

        public @NotNull Float3 toBlockScale() {
            return div(MathUtil.MODEL_TO_BLOCK_MULTIPLIER);
        }

        public @NotNull Float3 times(float value) {
            return new Float3(x * value, y * value, z * value);
        }

        public @NotNull Float3 div(float value) {
            return new Float3(x / value, y / value, z / value);
        }

        public @NotNull Float3 invertXZ() {
            return new Float3(-x, y, -z);
        }

        public @NotNull JsonArray toJson() {
            var array = new JsonArray(3);
            array.add(x); array.add(y); array.add(z);
            return array;
        }

        public @NotNull Quaternionf toQuaternionZYX() {
            return new Quaternionf().rotateZYX(
                z * MathUtil.DEGREES_TO_RADIANS,
                y * MathUtil.DEGREES_TO_RADIANS,
                x * MathUtil.DEGREES_TO_RADIANS
            );
        }

        public @NotNull Quaternionf toQuaternionXYZ() {
            return new Quaternionf().rotateXYZ(
                x * MathUtil.DEGREES_TO_RADIANS,
                y * MathUtil.DEGREES_TO_RADIANS,
                z * MathUtil.DEGREES_TO_RADIANS
            );
        }

        public @NotNull Vector3f toVector() {
            return new Vector3f(x, y, z);
        }

        @Override
        public int hashCode() {
            var hash = 31; var value = 1;
            value = value * hash + MathUtil.similarHashCode(x);
            value = value * hash + MathUtil.similarHashCode(y);
            value = value * hash + MathUtil.similarHashCode(z);
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof Float3(float x1, float y1, float z1))) return false;
            return MathUtil.isSimilar(x, x1) && MathUtil.isSimilar(y, y1) && MathUtil.isSimilar(z, z1);
        }

        @Override
        public @NotNull String toString() {
            return toJson().toString();
        }
    }

    @ApiStatus.Internal
    public record Float4(float dx, float dz, float tx, float tz) {

        public static final JsonDeserializer<Float4> PARSER = (json, _, _) -> {
            var array = json.getAsJsonArray();
            return new Float4(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat(), array.get(3).getAsFloat());
        };

        public static final Float4 MAX_UV = new Float4(0, 0, 16, 16);

        public @NotNull Float4 div(@NotNull ModelResolution resolution) {
            return div((float) resolution.width() / MathUtil.MODEL_TO_BLOCK_MULTIPLIER, (float) resolution.height() / MathUtil.MODEL_TO_BLOCK_MULTIPLIER);
        }

        public @NotNull Float4 div(float width, float height) {
            return new Float4(dx / width, dz / height, tx / width, tz / height);
        }

        public boolean isValid() {
            return dx >= 0 && dx <= 16 && dz >= 0 && dz <= 16 && tx >= 0 && tx <= 16 && tz >= 0 && tz <= 16;
        }

        public @NotNull JsonArray toJson() {
            var array = new JsonArray(4);
            array.add(dx); array.add(dz); array.add(tx); array.add(tz);
            return array;
        }

        @Override
        public int hashCode() {
            var hash = 31; var value = 1;
            value = value * hash + MathUtil.similarHashCode(dx);
            value = value * hash + MathUtil.similarHashCode(dz);
            value = value * hash + MathUtil.similarHashCode(tx);
            value = value * hash + MathUtil.similarHashCode(tz);
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof Float4(float dx1, float dz1, float tx1, float tz1))) return false;
            return MathUtil.isSimilar(dx, dx1) && MathUtil.isSimilar(dz, dz1) && MathUtil.isSimilar(tx, tx1) && MathUtil.isSimilar(tz, tz1);
        }

        @Override
        public @NotNull String toString() {
            return toJson().toString();
        }
    }

    public record ModelAsset(
        @NotNull String rawName,
        @NotNull String name,
        long sizeAssume,
        @NotNull StreamSupplier supplier
    ) implements Comparable<ModelAsset> {

        @ApiStatus.Internal
        public ModelAsset {
        }

        public static @NotNull ModelAsset of(@NotNull String name, byte[] bytes) {
            return of(name, bytes.length, () -> new ByteArrayInputStream(bytes));
        }

        public static @NotNull ModelAsset of(@NotNull String name, @NotNull StreamSupplier supplier) {
            return of(name, 0, supplier);
        }

        public static @NotNull ModelAsset of(@NotNull String name, long sizeAssume, @NotNull StreamSupplier supplier) {
            PackUtil.assertPackName(name);
            return new ModelAsset(name, name, sizeAssume, supplier);
        }

        public static @NotNull ModelAsset of(@NotNull File file) {
            return new ModelAsset(file.getPath(), nameWithoutExtension(file.getName()), file.length(), () -> new FileInputStream(file));
        }

        public static @NotNull ModelAsset of(@NotNull Path path) {
            try {
                return new ModelAsset(path.toString(), nameWithoutExtension(path.getFileName().toString()), Files.size(path), () -> Files.newInputStream(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static @NotNull String nameWithoutExtension(@NotNull String name) {
            var index = name.lastIndexOf('.');
            return PackUtil.toPackName(index > 0 ? name.substring(0, index) : name);
        }

        public @NotNull ModelLoadResult toResult() {
            try (
                var stream = supplier.get();
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)
            ) {
                var result = ModelData.GSON.fromJson(reader, ModelData.class);
                result.assertSupported();
                return result.loadBlueprint(name);
            } catch (IOException e) {
                throw new RuntimeException("Unable to load this asset: " + this, e);
            } catch (JsonParseException e) {
                throw new RuntimeException("Unable to parse this json asset: " + this, e);
            }
        }

        @Override
        public int compareTo(@NotNull ModelAsset o) {
            return name.compareTo(o.name);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ModelAsset that)) return false;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public @NotNull String toString() {
            return rawName;
        }

        public interface StreamSupplier {
            @NotNull InputStream get() throws IOException;
        }
    }
}
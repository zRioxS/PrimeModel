package kr.rioxs.primemodel.api.data.blueprint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.floats.FloatAVLTreeSet;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatSortedSet;
import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator.AnimationProgress;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator.TimedStorage;
import kr.rioxs.primemodel.api.animation.Animations.AnimationKeyframe;
import kr.rioxs.primemodel.api.animation.Animations.AnimationKeyframe.VectorPoint;
import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier;
import kr.rioxs.primemodel.api.bone.BoneName;
import kr.rioxs.primemodel.api.bone.BoneTags;
import kr.rioxs.primemodel.api.data.DataClasses.Float2;
import kr.rioxs.primemodel.api.data.DataClasses.Float3;
import kr.rioxs.primemodel.api.data.raw.RawData.ModelFace;
import kr.rioxs.primemodel.api.data.raw.RawData.ModelResolution;
import kr.rioxs.primemodel.api.pack.PackObfuscator;
import kr.rioxs.primemodel.api.script.Scripts.BlueprintScript;
import kr.rioxs.primemodel.api.util.Utils.InterpolationUtil;
import kr.rioxs.primemodel.api.util.Utils.JsonBuilders.JsonObjectBuilder;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import kr.rioxs.primemodel.api.util.Utils.PackUtil;
import kr.rioxs.primemodel.library.javamesh.MeshBuilder;
import kr.rioxs.primemodel.library.javamesh.MeshImage;
import kr.rioxs.primemodel.library.javamesh.MeshPoint;
import kr.rioxs.primemodel.library.javamesh.MeshShape;
import kr.rioxs.primemodel.library.javamesh.MeshTriangleName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static kr.rioxs.primemodel.api.util.Utils.CollectionUtil.*;

@ApiStatus.Internal
public final class Blueprints {

    private Blueprints() {
        throw new RuntimeException();
    }
    public static final class AnimationGenerator {

        private final AnimationTree[] trees;

        public static @NotNull Map<BoneName, BlueprintAnimator> generate(
            float length,
            @NotNull List<BlueprintElement> children,
            @NotNull Map<BoneName, BlueprintAnimator.AnimatorData> pointMap
        ) {
            var floatSet = mapFloat(pointMap.values()
                .stream()
                .flatMap(BlueprintAnimator.AnimatorData::allPoints), VectorPoint::time, () -> new FloatAVLTreeSet(MathUtil.FRAME_COMPARATOR));
            floatSet.add(0F);
            floatSet.add(length);
            var generator = new AnimationGenerator(g -> pointMap.get(g.name()), children);
            InterpolationUtil.insertLerpFrame(floatSet);
            generator.interpolateRotation(floatSet);
            generator.interpolateStep(floatSet);
            return associate(
                pointMap.values().stream().parallel().map(v -> new BlueprintAnimator(
                    v.name(),
                    InterpolationUtil.buildAnimation(v.position(), v.rotation(), v.scale(), v.rotationGlobal(), floatSet)
                )),
                BlueprintAnimator::name
            );
        }

        private AnimationGenerator(
            @NotNull Function<BlueprintElement.Group, BlueprintAnimator.AnimatorData> function,
            @NotNull List<BlueprintElement> children
        ) {
            trees = filterIsInstance(children, BlueprintElement.Group.class)
                .map(g -> new AnimationTree(null, g, function))
                .flatMap(AnimationTree::flatten)
                .toArray(AnimationTree[]::new);
        }

        public void interpolateRotation(@NotNull FloatSortedSet floats) {
            var list = new FloatArrayList(floats);
            var map = associate(
                Arrays.stream(trees).parallel().map(t -> {
                    var d = t.data;
                    if (d == null) return null;
                    var rot = d.rotation();
                    if (rot.size() < 2) return null;
                    var interpolator = InterpolationUtil.interpolatorFor(rot);
                    return new RotationVector(t, list.doubleStream().mapToObj(value -> interpolator.build((float) value).vector()).toList());
                }).filter(Objects::nonNull),
                v -> v.tree,
                v -> v.vectors
            );
            if (map.isEmpty()) return;
            IntStream.range(1, list.size()).parallel().forEach(i -> {
                var cache = new IdentityHashMap<AnimationTree, Vector3f>(trees.length);
                for (AnimationTree t : trees) {
                    Vector3f delta, parent;
                    var getVec = map.get(t);
                    delta = getVec != null ? getVec.get(i).sub(getVec.get(i - 1), new Vector3f()) : new Vector3f();
                    cache.put(t, t.parent != null && (parent = cache.get(t.parent)) != null ? delta.add(parent) : delta);
                }
                var length = (float) Math.ceil(cache.values().stream().mapToDouble(Vector3f::length).max().orElse(0.0) / 90.0);
                if (length < 2F) return;
                var previous = list.getFloat(i - 1);
                var next = list.getFloat(i);
                var interpolateTime = Math.max((next - previous) / length, MathUtil.MINECRAFT_TICK_SECONDS);
                synchronized (floats) {
                    for (float f = 1; f < length; f++) {
                        var addTime = MathUtil.fma(f, interpolateTime, previous);
                        if (next - addTime < MathUtil.MINECRAFT_TICK_SECONDS + MathUtil.FRAME_EPSILON) break;
                        floats.add(addTime);
                    }
                }
            });
        }

        private record RotationVector(@NotNull AnimationTree tree, @NotNull List<Vector3f> vectors) {}

        public void interpolateStep(@NotNull FloatSortedSet floats) {
            Arrays.stream(trees).map(tree -> tree.data).filter(Objects::nonNull).forEach(data -> {
                interpolateStep(floats, data.position());
                interpolateStep(floats, data.rotation());
                interpolateStep(floats, data.scale());
            });
        }

        private void interpolateStep(@NotNull FloatSortedSet floats, @NotNull List<VectorPoint> points) {
            if (points.size() < 2) return;
            for (int i = 1; i < points.size(); i++) {
                var before = points.get(i - 1);
                if (before.isContinuous()) continue;
                var time = points.get(i).time() - MathUtil.MINECRAFT_TICK_SECONDS;
                if (time < 0 || time - before.time() < 0) continue;
                floats.add(time);
            }
        }

        private static final class AnimationTree {
            private final AnimationTree parent;
            private final AnimationTree[] children;
            private final BlueprintAnimator.AnimatorData data;

            AnimationTree(@Nullable AnimationTree parent, @NotNull BlueprintElement.Group group,
                          @NotNull Function<BlueprintElement.Group, BlueprintAnimator.AnimatorData> function) {
                this.parent = parent;
                this.data = function.apply(group);
                children = filterIsInstance(group.children(), BlueprintElement.Group.class)
                    .map(g -> new AnimationTree(this, g, function))
                    .toArray(AnimationTree[]::new);
            }

            @NotNull Stream<AnimationTree> flatten() {
                return children.length == 0 ? Stream.of(this) : Stream.concat(Stream.of(this), Arrays.stream(children).flatMap(AnimationTree::flatten));
            }
        }
    }

    public record BlueprintAnimation(
        @NotNull String name,
        @NotNull AnimationIterator.Type loop,
        float length,
        boolean override,
        @NotNull @Unmodifiable Map<BoneName, BlueprintAnimator> animator,
        @Nullable BlueprintScript script,
        @NotNull TimedStorage<AnimationProgress> emptyAnimator
    ) {
        public @Nullable BlueprintScript script(@NotNull AnimationModifier modifier) {
            return modifier.override(override) || modifier.player() != null ? null : script;
        }

        public @NotNull AnimationIterator<AnimationProgress> emptyIterator(@NotNull AnimationIterator.Type type) {
            return type.create(emptyAnimator);
        }
    }

    public record BlueprintAnimator(@NotNull BoneName name, @NotNull AnimationKeyframe keyframe) {

        public record AnimatorData(
            @NotNull BoneName name,
            @NotNull List<VectorPoint> position,
            @NotNull List<VectorPoint> scale,
            @NotNull List<VectorPoint> rotation,
            boolean rotationGlobal
        ) {
            public @NotNull Stream<VectorPoint> allPoints() {
                return Stream.concat(Stream.concat(position.stream(), scale.stream()), rotation.stream());
            }
        }

        public @NotNull AnimationIterator<AnimationProgress> iterator(@NotNull AnimationIterator.Type type) {
            return type.create(keyframe);
        }
    }

    public sealed interface BlueprintElement {

        String MESH_TRIANGLE_SINGLE = "mesh_triangle_single";
        String MESH_TRIANGLE_DUPLEX = "mesh_triangle_duplex";
        String MESH_PIXEL = "mesh_pixel";

        sealed interface Bone extends BlueprintElement {
            @NotNull UUID uuid();
            @NotNull BoneName name();
            @NotNull Float3 origin();
        }

        default @NotNull Float3 rotation() {
            return Float3.ZERO;
        }

        default boolean visibility() {
            return false;
        }

        record Group(
            @NotNull UUID uuid,
            @NotNull BoneName name,
            @NotNull Float3 origin,
            @NotNull Float3 rotation,
            @NotNull List<BlueprintElement> children,
            boolean visibility
        ) implements Bone {

            @Override
            public @NotNull Float3 origin() {
                return origin.invertXZ();
            }

            public @NotNull String jsonName(@NotNull BlueprintLoadContext context) {
                return PackUtil.toPackName(context.name() + "_" + name.rawName());
            }

            @Nullable
            @Unmodifiable
            public List<BlueprintJson> buildModernJson(
                @NotNull PackObfuscator.Pair obfuscator,
                @NotNull BlueprintLoadContext context
            ) {
                var scale = scale();
                var list = mapIndexed(
                    group(filterIsInstance(children, Cube.class), Cube::identifierDegree),
                    (i, entry) -> buildJson(i + 1, scale, obfuscator, context, entry.getKey(), entry.getValue().stream())
                ).filter(Objects::nonNull).toList();
                return list.isEmpty() ? null : list;
            }

            public @Nullable JsonObject buildMeshItemModel(@NotNull BlueprintLoadContext context) {
                var scale = 1F / scale();
                var meshes = filterIsInstance(children, Mesh.class).toList();
                if (meshes.isEmpty()) return null;
                var builder = MeshBuilder.of(context.triangleName())
                    .matrixModifier(mat -> mat.scale(scale))
                    .image(context.imageByIndex());
                meshes.forEach(mesh -> builder.load(mesh.toShape(origin)));
                return builder.toJson();
            }

            private @Nullable BlueprintJson buildJson(
                int number, float scale,
                @NotNull PackObfuscator.Pair obfuscator,
                @NotNull BlueprintLoadContext context,
                @NotNull Float3 identifier,
                @NotNull Stream<Cube> cubes
            ) {
                var cubeElement = cubes.filter(Cube::hasTexture).toList();
                var selectedTextures = cubeElement.stream()
                    .flatMapToInt(tex -> tex.faces().textureIndex())
                    .distinct().sorted()
                    .mapToObj(i -> Map.entry(Integer.toString(i), context.texture(i).packNamespace(obfuscator.textures())))
                    .toList();
                if (selectedTextures.isEmpty()) return null;
                return new BlueprintJson(obfuscator.models().obfuscate(jsonName(context) + "_" + number), () -> JsonObjectBuilder.builder()
                    .jsonObject("textures", textures -> textures
                        .stringProperties(selectedTextures)
                        .property("particle", selectedTextures.getFirst().getValue()))
                    .jsonArray("elements", mapToJson(cubeElement, cube -> cube.buildJson(scale, context, this, identifier)))
                    .jsonObject("display", display -> display.jsonObject("fixed", fixed -> {
                        if (!identifier.equals(Float3.ZERO)) {
                            fixed.jsonArray("rotation", identifier.convertToMinecraftDegree().toJson());
                        }
                    }))
                    .build());
            }

            public float scale() {
                return (float) Math.max(filterIsInstance(children, Cube.class)
                    .mapToDouble(e -> e.max(origin) / 16F)
                    .max().orElse(1F), 1F);
            }

            public @Nullable ModelBoundingBox hitBox() {
                return filterIsInstance(children, Cube.class)
                    .map(element -> {
                        var from = element.from().minus(origin).toBlockScale();
                        var to = element.to().minus(origin).toBlockScale();
                        return ModelBoundingBox.of(from.x(), from.y(), from.z(), to.x(), to.y(), to.z()).invert();
                    })
                    .max(Comparator.comparingDouble(ModelBoundingBox::length))
                    .orElse(null);
            }
        }

        record Locator(@NotNull UUID uuid, @NotNull BoneName name, @NotNull Float3 origin) implements Bone {
            @Override public @NotNull Float3 origin() { return origin.invertXZ(); }
        }

        record Camera(@NotNull UUID uuid) implements BlueprintElement {}

        record NullObject(
            @NotNull UUID uuid,
            @NotNull BoneName name,
            @Nullable UUID ikTarget,
            @Nullable UUID ikSource,
            @NotNull Float3 origin
        ) implements Bone {
            @Override public @NotNull Float3 origin() { return origin.invertXZ(); }
        }

        record Cube(
            @NotNull String name,
            @NotNull Float3 from,
            @NotNull Float3 to,
            float inflate,
            @NotNull Float3 rotation,
            @NotNull Float3 origin,
            @Nullable ModelFace faces,
            @Nullable Integer lightEmission,
            boolean visibility
        ) implements BlueprintElement {

            private @NotNull Float3 identifierDegree() {
                return MathUtil.identifier(rotation());
            }

            private static @NotNull Float3 centralize(@NotNull Float3 target, @NotNull Float3 groupOrigin, float scale) {
                return target.minus(groupOrigin).div(scale);
            }

            private static @NotNull Float3 deltaPosition(@NotNull Float3 target, @NotNull Quaternionf quaternionf) {
                return target.rotate(quaternionf).minus(target);
            }

            private @NotNull JsonObject buildJson(float scale, @NotNull BlueprintLoadContext parent,
                                                  @NotNull BlueprintElement.Group group, @NotNull Float3 identifier) {
                var qua = identifier.toQuaternionZYX().invert();
                var centerOrigin = centralize(origin(), group.origin, scale);
                var groupDelta = deltaPosition(centerOrigin, qua);
                var inflate = new Float3(inflate() / scale);
                return JsonObjectBuilder.builder()
                    .property("light_emission", group.name.tagged(BoneTags.GLOW) ? Integer.valueOf(15) : lightEmission)
                    .jsonArray("from", centralize(from(), group.origin, scale).plus(groupDelta).plus(Float3.CENTER).minus(inflate).toJson())
                    .jsonArray("to", centralize(to(), group.origin, scale).plus(groupDelta).plus(Float3.CENTER).plus(inflate).toJson())
                    .jsonObject("faces", faces().toJson(parent))
                    .jsonObject("rotation", Optional.of(rotation().minus(identifier))
                        .filter(r -> !Float3.ZERO.equals(r))
                        .map(rot -> {
                            var rotation = getRotation(rot);
                            rotation.add("origin", centerOrigin.plus(groupDelta).plus(Float3.CENTER).toJson());
                            return rotation;
                        }).orElse(null))
                    .build();
            }

            public float max(@NotNull Float3 origin) {
                var f = from().minus(origin);
                var t = to().minus(origin);
                var max = 0F;
                max = Math.max(max, Math.abs(f.x()));
                max = Math.max(max, Math.abs(f.y()));
                max = Math.max(max, Math.abs(f.z()));
                max = Math.max(max, Math.abs(t.x()));
                max = Math.max(max, Math.abs(t.y()));
                max = Math.max(max, Math.abs(t.z()));
                return max;
            }

            @Override
            public @NotNull ModelFace faces() {
                return Objects.requireNonNull(faces);
            }

            public boolean hasTexture() {
                return faces != null && faces.hasTexture();
            }

            private @NotNull JsonObject getRotation(@NotNull Float3 rot) {
                var rotation = new JsonObject();
                if (Math.abs(rot.x()) > 0) {
                    rotation.addProperty("angle", rot.x());
                    rotation.addProperty("axis", "x");
                } else if (Math.abs(rot.y()) > 0) {
                    rotation.addProperty("angle", rot.y());
                    rotation.addProperty("axis", "y");
                } else if (Math.abs(rot.z()) > 0) {
                    rotation.addProperty("angle", rot.z());
                    rotation.addProperty("axis", "z");
                }
                return rotation;
            }
        }

        record Mesh(
            @NotNull Float3 origin,
            @NotNull Float3 rotation,
            @NotNull List<Face> faces,
            boolean visibility
        ) implements BlueprintElement {

            @NotNull
            @Unmodifiable
            public List<MeshShape> toShape(@NotNull Float3 parentOrigin) {
                var deltaOrigin = origin().minus(parentOrigin).toVector();
                var pointRotation = rotation().toQuaternionXYZ();
                return faces.stream()
                    .map(face -> new MeshShape(
                        face.points.stream()
                            .map(p -> new MeshPoint(
                                p.vertices.toVector().rotate(pointRotation).add(deltaOrigin).mul(-1F, 1F, -1F).div(MathUtil.MODEL_TO_BLOCK_MULTIPLIER),
                                p.uv.toVector().div(MathUtil.MODEL_TO_BLOCK_MULTIPLIER)
                            ))
                            .toList(),
                        Integer.toString(face.texture)
                    ))
                    .toList();
            }

            public record Face(@NotNull @Unmodifiable List<Point> points, int texture) {}

            public record Point(@NotNull Float3 vertices, @NotNull Float2 uv) {}
        }
    }

    public record BlueprintImage(@NotNull String name, byte[] image, @Nullable JsonObject mcmeta) {
        public long estimatedSize() { return image.length; }
        public @NotNull String pngName() { return name + ".png"; }
        public @NotNull String mcmetaName() { return pngName() + ".mcmeta"; }
    }

    public record BlueprintJson(@NotNull String name, @NotNull Supplier<JsonElement> element) {
        public @NotNull String jsonName() { return name + ".json"; }
        public @NotNull JsonElement buildJson() { return element.get(); }
    }

    @ApiStatus.Internal
    public static final class BlueprintLoadContext {

        private final String name;
        private final ModelResolution resolution;
        private final TextureRef[] textureRefs;
        private final boolean canBeRendered;

        private volatile Map<String, MeshImage> imageRefMap;

        private final MeshTriangleName triangleName = new MeshTriangleName(
            PrimeModel.config().namespace() + ":" + BlueprintElement.MESH_TRIANGLE_SINGLE,
            PrimeModel.config().namespace() + ":" + BlueprintElement.MESH_TRIANGLE_DUPLEX
        );

        BlueprintLoadContext(@NotNull String name, @NotNull ModelResolution resolution, @NotNull List<BlueprintTexture> textures) {
            this.name = name;
            this.resolution = resolution;
            this.textureRefs = new TextureRef[textures.size()];
            var i = 0;
            var canBeRendered = false;
            for (BlueprintTexture texture : textures) {
                canBeRendered |= texture.canBeRendered();
                this.textureRefs[i++] = new TextureRef(texture);
            }
            this.canBeRendered = canBeRendered;
        }

        public @NotNull String name() { return name; }
        public @NotNull MeshTriangleName triangleName() { return triangleName; }
        public @NotNull ModelResolution resolution() { return resolution; }
        public @NotNull BlueprintTexture texture(int index) { return Objects.requireNonNull(textureRefs[index]).texture(); }

        @NotNull
        @Unmodifiable
        Map<String, MeshImage> imageByIndex() {
            Map<String, MeshImage> map;
            if ((map = imageRefMap) != null) return map;
            synchronized (this) {
                if ((map = imageRefMap) != null) return map;
                return imageRefMap = Collections.unmodifiableMap(new AbstractMap<>() {
                    @Override public MeshImage get(Object key) {
                        var get = textureRefs[Integer.parseInt(key.toString())];
                        return get != null ? get.image() : null;
                    }
                    @Override
                    public @NotNull Set<Entry<String, MeshImage>> entrySet() {
                        return IntStream.range(0, textureRefs.length)
                            .mapToObj(i -> Map.entry(Integer.toString(i), textureRefs[i].image()))
                            .collect(Collectors.toUnmodifiableSet());
                    }
                });
            }
        }

        public boolean canBeRendered() { return canBeRendered; }

        @NotNull
        public Stream<BlueprintImage> buildImage(@NotNull PackObfuscator obfuscator) {
            if (!canBeRendered()) return Stream.empty();
            return Arrays.stream(textureRefs)
                .filter(TextureRef::canBeRendered)
                .map(ref -> new BlueprintImage(
                    ref.texture.packName(obfuscator), ref.texture.image(),
                    ref.texture.isAnimatedTexture() ? ref.texture.toMcmeta() : null));
        }

        private static final class TextureRef {
            private final BlueprintTexture texture;
            private final AtomicBoolean referenced = new AtomicBoolean();
            private volatile MeshImage image;

            private TextureRef(BlueprintTexture texture) { this.texture = texture; }
            public boolean canBeRendered() { return referenced.get() && texture.canBeRendered(); }
            public @NotNull BlueprintTexture texture() { referenced.set(true); return texture; }
            public @NotNull MeshImage image() {
                MeshImage img;
                if ((img = image) != null) return img;
                synchronized (this) {
                    if ((img = image) != null) return img;
                    try (var input = new ByteArrayInputStream(texture.image())) {
                        return image = MeshImage.from(ImageIO.read(input));
                    } catch (IOException e) { throw new RuntimeException(e); }
                }
            }
        }
    }

    public record BlueprintTexture(
        @NotNull String name, byte[] image, int width, int height,
        int uvWidth, int uvHeight, boolean canBeRendered, int frameTime, boolean frameInterpolate
    ) {
        public boolean isAnimatedTexture() {
            if (hasUVSize()) {
                var h = (float) height / uvHeight;
                var w = (float) width / uvWidth;
                return h > w;
            } else {
                return height > 0 && width > 0 && height / width > 1;
            }
        }

        public @NotNull JsonObject toMcmeta() {
            return JsonObjectBuilder.builder()
                .jsonObject("animation", animation -> {
                    animation.property("interpolate", frameInterpolate());
                    animation.property("frametime", frameTime());
                })
                .build();
        }

        public @NotNull String packName(@NotNull PackObfuscator obfuscator) { return obfuscator.obfuscate(name()); }
        public @NotNull String packNamespace(@NotNull PackObfuscator obfuscator) { return PrimeModel.config().namespace() + ":item/" + packName(obfuscator); }
        public boolean hasUVSize() { return uvWidth > 0 && uvHeight > 0; }
        public @NotNull ModelResolution resolution(@NotNull ModelResolution resolution) {
            if (!hasUVSize()) return resolution;
            return resolution.width() == width && resolution.height() == height ? resolution : new ModelResolution(uvWidth, uvHeight);
        }
    }

    public record ModelBlueprint(
        @NotNull String name,
        @NotNull ModelResolution resolution,
        @NotNull List<BlueprintTexture> textures,
        @NotNull List<BlueprintElement> elements,
        @NotNull Map<String, BlueprintAnimation> animations
    ) {
        public @NotNull BlueprintLoadContext context() {
            return new BlueprintLoadContext(name(), resolution(), textures());
        }
    }

    public record ModelBoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public static final ModelBoundingBox MIN = of(0.1, 0.1, 0.1);

        public static @NotNull ModelBoundingBox of(@NotNull Vector3d min, @NotNull Vector3d max) {
            return of(min.x, min.y, min.z, max.x, max.y, max.z);
        }

        public static @NotNull ModelBoundingBox of(double x, double y, double z) {
            return of(-x / 2, -y / 2, -z / 2, x / 2, y / 2, z / 2);
        }

        public static @NotNull ModelBoundingBox of(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return new ModelBoundingBox(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
        }

        public double x() { return maxX - minX; }
        public double y() { return maxY - minY; }
        public double centerY() { return (maxY + minY) / 2; }
        public double z() { return maxZ - minZ; }

        public @NotNull Vector3f centerPoint() {
            return new Vector3f((float) (minX + maxX), (float) (minY + maxY), (float) (minZ + maxZ)).div(2F);
        }

        public @NotNull ModelBoundingBox times(double scale) {
            return of(minX * scale, minY * scale, minZ * scale, maxX * scale, maxY * scale, maxZ * scale);
        }

        public @NotNull ModelBoundingBox center() {
            var center = centerPoint();
            return of(minX - center.x, minY - center.y, minZ - center.z, maxX - center.x, maxY - center.y, maxZ - center.z);
        }

        public @NotNull ModelBoundingBox invert() {
            return of(-minX, minY, -minZ, -maxX, maxY, -maxZ);
        }

        public @NotNull ModelBoundingBox rotate(@NotNull Quaterniond quaterniond) {
            var centerVec = centerPoint();
            return of(min().sub(centerVec).rotate(quaterniond).add(centerVec), max().sub(centerVec).rotate(quaterniond).add(centerVec));
        }

        public @NotNull Vector3d min() { return new Vector3d(minX, minY, minZ); }
        public @NotNull Vector3d max() { return new Vector3d(maxX, maxY, maxZ); }
        public double lengthZX() { return Math.sqrt(Math.pow(x(), 2) + Math.pow(z(), 2)); }
        public double length() { return Math.sqrt(Math.pow(x(), 2) + Math.pow(y(), 2) + Math.pow(z(), 2)); }
    }
}

package kr.rioxs.primemodel.api.data.raw;
import kr.rioxs.primemodel.api.manager.Managers.Manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator.AnimationProgress;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator.Timed;
import kr.rioxs.primemodel.api.animation.Animations.AnimationKeyframe.VectorPoint;
import kr.rioxs.primemodel.api.bone.BoneName;
import kr.rioxs.primemodel.api.data.DataClasses.Float2;
import kr.rioxs.primemodel.api.data.DataClasses.Float3;
import kr.rioxs.primemodel.api.data.DataClasses.Float4;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.AnimationGenerator;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintAnimation;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintAnimator;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintElement;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintLoadContext;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.BlueprintTexture;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.ModelBlueprint;
import kr.rioxs.primemodel.api.manager.Managers.ScriptManager;
import kr.rioxs.primemodel.api.script.Scripts.AnimationScript;
import kr.rioxs.primemodel.api.script.Scripts.BlueprintScript;
import kr.rioxs.primemodel.api.script.Scripts.TimeScript;
import kr.rioxs.primemodel.api.util.Utils.CollectionUtil;
import kr.rioxs.primemodel.api.util.Utils.Functions.Float2FloatConstantFunction;
import kr.rioxs.primemodel.api.util.Utils.Functions.Float2FloatFunction;
import kr.rioxs.primemodel.api.util.Utils.Functions.FloatFunction;
import kr.rioxs.primemodel.api.util.Utils.InterpolationUtil;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import kr.rioxs.primemodel.api.util.Utils.PackUtil;
import kr.rioxs.primemodel.api.util.Utils.VectorInterpolator;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.joml.Vector3f;
import org.semver4j.Semver;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static kr.rioxs.primemodel.api.util.Utils.CollectionUtil.*;

@ApiStatus.Internal
public final class RawData {

    private RawData() {
        throw new RuntimeException();
    }

    public enum KeyframeChannel {
        @SerializedName("position") POSITION,
        @SerializedName("rotation") ROTATION,
        @SerializedName("scale") SCALE,
        @SerializedName("timeline") TIMELINE,
        @SerializedName("sound") SOUND,
        @SerializedName("particle") PARTICLE,
        NOT_FOUND
    }

    public record ModelAnimation(
        @NotNull String name,
        @Nullable AnimationIterator.Type loop,
        boolean override,
        @NotNull String uuid,
        float length,
        @Nullable Map<String, ModelAnimator> animators
    ) {
        public @NotNull BlueprintAnimation toBlueprint(@NotNull ModelLoadContext context, @NotNull List<BlueprintElement> children) {
            var animators = AnimationGenerator.generate(length(), children, associate(
                animators().entrySet().stream()
                    .filter(e -> context.availableUUIDs.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(ModelAnimator::isAvailable)
                    .map(a -> buildAnimationData(context, a)),
                BlueprintAnimator.AnimatorData::name
            ));
            return new BlueprintAnimation(
                name(), loop(), length(), override(), animators,
                Optional.ofNullable(animators().get("effects"))
                    .filter(ModelAnimator::isNotEmpty)
                    .map(a -> toScript(a, context.placeholder))
                    .orElseGet(() -> BlueprintScript.fromEmpty(this)),
                animators.isEmpty() ? AnimationProgress.emptyStorage(length()) : animators.values().iterator().next().keyframe().toEmpty()
            );
        }

        private @NotNull BlueprintScript toScript(@NotNull ModelAnimator animator, @NotNull ModelPlaceholder placeholder) {
            var set = new ObjectAVLTreeSet<TimeScript>();
            set.add(TimeScript.EMPTY);
            set.add(TimeScript.EMPTY.time(length()));
            animator.stream()
                .filter(f -> f.point().hasScript())
                .map(d -> AnimationScript.of(Arrays.stream(placeholder.parseVariable(d.point().script()).split("\n"))
                    .map(PrimeModel.platform().manager(ScriptManager.class)::build)
                    .filter(Objects::nonNull)
                    .toList())
                    .time(d.time()))
                .forEach(set::add);
            var array = new TimeScript[set.size()];
            var before = 0F;
            var i = 0;
            for (TimeScript timeScript : set) {
                var t = timeScript.time();
                array[i++] = timeScript.time(InterpolationUtil.roundTime(t - before));
                before = t;
            }
            return new BlueprintScript(name(), loop(), length(), List.of(array));
        }

        @Override
        public @NotNull AnimationIterator.Type loop() {
            return loop != null ? loop : AnimationIterator.Type.PLAY_ONCE;
        }

        @Override
        public @NotNull Map<String, ModelAnimator> animators() {
            return animators != null ? animators : Collections.emptyMap();
        }

        @NotNull
        private BlueprintAnimator.AnimatorData buildAnimationData(@NotNull ModelLoadContext context, @NotNull ModelAnimator animator) {
            var position = new ArrayList<VectorPoint>();
            var rotation = new ArrayList<VectorPoint>();
            var scale = new ArrayList<VectorPoint>();
            var version = context.meta.formatVersion();
            animator.stream().filter(keyframe -> keyframe.time() <= length()).forEach(keyframe -> {
                switch (keyframe.channel()) {
                    case POSITION -> position.add(keyframe.point(context, version::convertAnimationPosition));
                    case ROTATION -> rotation.add(keyframe.point(context, version::convertAnimationRotation));
                    case SCALE -> scale.add(keyframe.point(context, version::convertAnimationScale));
                }
            });
            return new BlueprintAnimator.AnimatorData(animator.name(), position, scale, rotation, animator.rotationGlobal());
        }
    }

    public record ModelAnimator(
        @Nullable BoneName name,
        @Nullable List<ModelKeyframe> keyframes,
        @Nullable @SerializedName("rotation_global") Boolean _rotationGlobal
    ) {
        public boolean rotationGlobal() {
            return Boolean.TRUE.equals(_rotationGlobal);
        }

        public boolean isAvailable() {
            return name != null && isNotEmpty();
        }

        public boolean isNotEmpty() {
            return !keyframes().isEmpty();
        }

        @Override
        public @NotNull BoneName name() {
            return Objects.requireNonNull(name);
        }

        @Override
        public @NotNull List<ModelKeyframe> keyframes() {
            return keyframes != null ? keyframes : Collections.emptyList();
        }

        public @NotNull Stream<ModelKeyframe> stream() {
            return keyframes().stream().filter(ModelKeyframe::hasPoint).sorted();
        }
    }

    public record ModelData(
        @NotNull ModelMeta meta,
        @NotNull ModelResolution resolution,
        @NotNull List<ModelElement> elements,
        @NotNull List<ModelOutliner> outliner,
        @NotNull List<ModelTexture> textures,
        @Nullable List<ModelAnimation> animations,
        @Nullable List<ModelGroup> groups,
        @Nullable @SerializedName("animation_variable_placeholders") ModelPlaceholder placeholder
    ) {
        public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Float2.class, Float2.PARSER)
            .registerTypeAdapter(Float3.class, Float3.PARSER)
            .registerTypeAdapter(Float4.class, Float4.PARSER)
            .registerTypeAdapter(BoneName.class, BoneName.PARSER)
            .registerTypeAdapter(ModelMeta.class, ModelMeta.PARSER)
            .registerTypeAdapter(ModelOutliner.class, ModelOutliner.PARSER)
            .registerTypeAdapter(ModelPlaceholder.class, ModelPlaceholder.PARSER)
            .registerTypeAdapter(ModelElement.class, ModelElement.PARSER)
            .create();

        public @NotNull ModelLoadResult loadBlueprint(@NotNull String name) {
            return loadBlueprint(name, PrimeModel.config().enableStrictLoading());
        }

        public @NotNull ModelLoadResult loadBlueprint(@NotNull String name, boolean strict) {
            var context = new ModelLoadContext(
                name, placeholder(), meta(),
                associate(elements(), ModelElement::uuid),
                associate(groups(), ModelGroup::uuid),
                mapToSet(outliner().stream().flatMap(ModelOutliner::flatten), ModelOutliner::uuid),
                strict
            );
            var group = mapToList(outliner(), outliner -> outliner.toBlueprint(context));
            return new ModelLoadResult(
                new ModelBlueprint(
                    context.name, resolution(),
                    mapToList(textures(), texture -> texture.toBlueprint(context)),
                    group,
                    associate(animations().stream().parallel().map(raw -> raw.toBlueprint(context, group)), BlueprintAnimation::name)
                ),
                context.errors
            );
        }

        public void assertSupported() {
            elements().stream().filter(e -> !e.isSupported()).findFirst().ifPresent(e -> {
                throw new RuntimeException("This model file has unsupported element type: " + e.type());
            });
        }

        @Override
        public @NotNull ModelPlaceholder placeholder() {
            return placeholder != null ? placeholder : ModelPlaceholder.EMPTY;
        }

        @Override
        public @NotNull List<ModelAnimation> animations() {
            return animations != null ? animations : Collections.emptyList();
        }

        @Override
        public @NotNull List<ModelGroup> groups() {
            return groups != null ? groups : Collections.emptyList();
        }
    }

    public record ModelDatapoint(
        @Nullable JsonPrimitive x,
        @Nullable JsonPrimitive y,
        @Nullable JsonPrimitive z,
        @Nullable String script
    ) {
        public boolean hasScript() {
            return script != null;
        }

        @Override
        public @NotNull String script() {
            return Objects.requireNonNull(script);
        }

        public @NotNull FloatFunction<Vector3f> toFunction(@NotNull ModelLoadContext context) {
            var xb = build(x, context);
            var yb = build(y, context);
            var zb = build(z, context);
            if (xb instanceof Float2FloatConstantFunction(float xc)
                && yb instanceof Float2FloatConstantFunction(float yc)
                && zb instanceof Float2FloatConstantFunction(float zc)) {
                return FloatFunction.of(new Vector3f(xc, yc, zc));
            } else {
                return f -> new Vector3f(xb.applyAsFloat(f), yb.applyAsFloat(f), zb.applyAsFloat(f));
            }
        }

        private static @NotNull Float2FloatFunction build(@Nullable JsonPrimitive primitive, @NotNull ModelLoadContext context) {
            if (primitive == null) return Float2FloatFunction.ZERO;
            if (primitive.isNumber()) return Float2FloatFunction.of(primitive.getAsFloat());
            var string = primitive.getAsString().trim();
            if (string.isEmpty()) return Float2FloatFunction.ZERO;
            try {
                return Float2FloatFunction.of(Float.parseFloat(string));
            } catch (NumberFormatException _) {
                return context.trySupply(
                    () -> PrimeModel.platform().evaluator().compile(context.placeholder.parseVariable(string)),
                    error -> new ModelLoadContext.Fallback<>(Float2FloatFunction.ZERO, "Cannot parse this datapoint: " + primitive + ", reason: " + error.getMessage())
                );
            }
        }
    }

    public sealed interface ModelElement {
        String NULL_OBJECT = "null_object";
        String LOCATOR = "locator";
        String CAMERA = "camera";
        String CUBE = "cube";
        String MESH = "mesh";

        JsonDeserializer<ModelElement> PARSER = (json, _, context) -> {
            var t = json.getAsJsonObject().getAsJsonPrimitive("type");
            var select = t != null ? t.getAsString() : CUBE;
            return switch (select) {
                case NULL_OBJECT -> context.deserialize(json, NullObject.class);
                case LOCATOR -> context.deserialize(json, Locator.class);
                case CAMERA -> context.deserialize(json, Camera.class);
                case CUBE -> context.deserialize(json, Cube.class);
                case MESH -> context.deserialize(json, Mesh.class);
                default -> new Unsupported(select);
            };
        };

        @NotNull String uuid();
        @NotNull String type();
        @NotNull BlueprintElement toBlueprint();

        default boolean isSupported() {
            return true;
        }

        record Locator(@NotNull String name, @NotNull String uuid, @Nullable Float3 position) implements ModelElement {
            @Override public @NotNull String type() { return LOCATOR; }
            @Override public @NotNull Float3 position() { return position != null ? position : Float3.ZERO; }
            @Override public @NotNull BlueprintElement toBlueprint() {
                return new BlueprintElement.Locator(UUID.fromString(uuid), BoneName.of(name()), position());
            }
        }

        record Camera(@NotNull String uuid) implements ModelElement {
            @Override public @NotNull String type() { return CAMERA; }
            @Override public @NotNull BlueprintElement toBlueprint() {
                return new BlueprintElement.Camera(UUID.fromString(uuid));
            }
        }

        record NullObject(@NotNull String name, @NotNull String uuid,
                          @Nullable @SerializedName("ik_target") String ikTarget,
                          @Nullable @SerializedName("ik_source") String ikSource,
                          @Nullable Float3 position) implements ModelElement {
            @Override public @NotNull String type() { return NULL_OBJECT; }
            @Override public @NotNull Float3 position() { return position != null ? position : Float3.ZERO; }
            @Override public @NotNull BlueprintElement toBlueprint() {
                return new BlueprintElement.NullObject(UUID.fromString(uuid), BoneName.of(name()),
                    Optional.ofNullable(ikTarget()).filter(s -> !s.isEmpty()).map(UUID::fromString).orElse(null),
                    Optional.ofNullable(ikSource()).filter(s -> !s.isEmpty()).map(UUID::fromString).orElse(null),
                    position());
            }
        }

        record Unsupported(@NotNull String type) implements ModelElement {
            @Override public @NotNull String uuid() { throw new UnsupportedOperationException(type()); }
            @Override public boolean isSupported() { return false; }
            @Override public @NotNull BlueprintElement toBlueprint() { throw new UnsupportedOperationException(type()); }
        }

        record Cube(@NotNull String name, @NotNull String uuid,
                    @Nullable Float3 from, @Nullable Float3 to, float inflate,
                    @Nullable Float3 rotation, @NotNull Float3 origin, @Nullable ModelFace faces,
                    @SerializedName("light_emission") int lightEmission,
                    @SerializedName("visibility") @Nullable Boolean _visibility) implements ModelElement {
            @Override public @NotNull String type() { return CUBE; }
            @Override public @NotNull Float3 from() { return from != null ? from : Float3.ZERO; }
            @Override public @NotNull Float3 to() { return to != null ? to : Float3.ZERO; }
            public boolean visibility() { return !Boolean.FALSE.equals(_visibility); }
            @Override public @NotNull Float3 rotation() { return rotation != null ? rotation : Float3.ZERO; }
            @Override public int lightEmission() { return name().toLowerCase().contains("glow") ? 15 : lightEmission; }
            @Override public @NotNull BlueprintElement toBlueprint() {
                return new BlueprintElement.Cube(name(), from(), to(), inflate(), rotation(), origin(), faces(),
                    Optional.of(lightEmission()).filter(i -> i > 0).orElse(null), visibility());
            }
        }

        record Mesh(@NotNull String uuid, @Nullable Float3 origin, @Nullable Float3 rotation,
                    @NotNull Map<String, Float3> vertices, @NotNull Map<String, Face> faces,
                    @SerializedName("visibility") @Nullable Boolean _visibility) implements ModelElement {
            @Override public @NotNull Float3 origin() { return origin != null ? origin : Float3.ZERO; }
            @Override public @NotNull Float3 rotation() { return rotation != null ? rotation : Float3.ZERO; }
            @Override public @NotNull String type() { return MESH; }
            public boolean visibility() { return !Boolean.FALSE.equals(_visibility); }
            @Override public @NotNull BlueprintElement toBlueprint() {
                return new BlueprintElement.Mesh(origin(), rotation(),
                    faces.values().stream().map(face -> new BlueprintElement.Mesh.Face(
                        face.vertices.stream().map(n -> new BlueprintElement.Mesh.Point(
                            Objects.requireNonNull(vertices.get(n)), Objects.requireNonNull(face.uv.get(n))
                        )).toList(), face.texture)).toList(), visibility());
            }
            public record Face(@NotNull Map<String, Float2> uv, @NotNull Set<String> vertices, int texture) {}
        }
    }

    public record ModelFace(@NotNull ModelUV north, @NotNull ModelUV east, @NotNull ModelUV south,
                            @NotNull ModelUV west, @NotNull ModelUV up, @NotNull ModelUV down) {
        public @NotNull JsonObject toJson(@NotNull BlueprintLoadContext parent) {
            var object = new JsonObject();
            JsonObject add;
            if ((add = north.toJson(parent)) != null) object.add("north", add);
            if ((add = east.toJson(parent)) != null) object.add("east", add);
            if ((add = south.toJson(parent)) != null) object.add("south", add);
            if ((add = west.toJson(parent)) != null) object.add("west", add);
            if ((add = up.toJson(parent)) != null) object.add("up", add);
            if ((add = down.toJson(parent)) != null) object.add("down", add);
            return object;
        }

        public boolean hasTexture() {
            return north.hasTexture() || east.hasTexture() || south.hasTexture() || west.hasTexture() || up.hasTexture() || down.hasTexture();
        }

        public @NotNull IntStream textureIndex() {
            var builder = IntStream.builder();
            if (north.hasTexture()) builder.add(north.textureIndex());
            if (east.hasTexture()) builder.add(east.textureIndex());
            if (south.hasTexture()) builder.add(south.textureIndex());
            if (west.hasTexture()) builder.add(west.textureIndex());
            if (up.hasTexture()) builder.add(up.textureIndex());
            if (down.hasTexture()) builder.add(down.textureIndex());
            return builder.build();
        }
    }

    public record ModelGroup(@NotNull String name, @NotNull String uuid,
                             @Nullable Float3 origin, @Nullable Float3 rotation,
                             @SerializedName("light_emission") int lightEmission,
                             @Nullable @SerializedName("visibility") Boolean _visibility) {
        @Override public @NotNull Float3 origin() { return origin != null ? origin : Float3.ZERO; }
        @Override public @NotNull Float3 rotation() { return rotation != null ? rotation : Float3.ZERO; }
        public boolean visibility() { return !Boolean.FALSE.equals(_visibility); }
    }

    public record ModelKeyframe(@Nullable KeyframeChannel channel,
                                @SerializedName("data_points") @NotNull List<ModelDatapoint> dataPoints,
                                @SerializedName("bezier_left_time") @Nullable Float3 bezierLeftTime,
                                @SerializedName("bezier_left_value") @Nullable Float3 bezierLeftValue,
                                @SerializedName("bezier_right_time") @Nullable Float3 bezierRightTime,
                                @SerializedName("bezier_right_value") @Nullable Float3 bezierRightValue,
                                @Nullable VectorInterpolator interpolation,
                                float time) implements Timed {
        public boolean hasPoint() { return !dataPoints.isEmpty(); }
        public @NotNull ModelDatapoint point() { return dataPoints.getFirst(); }

        public @NotNull VectorPoint point(@NotNull ModelLoadContext context, @NotNull Function<Vector3f, Vector3f> function) {
            return new VectorPoint(
                point().toFunction(context).map(function).memoize(), time(),
                new VectorPoint.BezierConfig(
                    Optional.ofNullable(bezierLeftTime).map(Float3::toVector).orElse(null),
                    Optional.ofNullable(bezierLeftValue).map(Float3::toVector).map(function).orElse(null),
                    Optional.ofNullable(bezierRightTime).map(Float3::toVector).orElse(null),
                    Optional.ofNullable(bezierRightValue).map(Float3::toVector).map(function).orElse(null)),
                interpolation());
        }

        @Override public @NotNull VectorInterpolator interpolation() { return interpolation != null ? interpolation : VectorInterpolator.LINEAR; }
        @Override public @NotNull KeyframeChannel channel() { return channel != null ? channel : KeyframeChannel.NOT_FOUND; }
    }

    @RequiredArgsConstructor
    public static final class ModelLoadContext {
        final @NotNull String name;
        final @NotNull ModelPlaceholder placeholder;
        final @NotNull ModelMeta meta;
        final @NotNull Map<String, ModelElement> elements;
        final @NotNull Map<String, ModelGroup> groups;
        final @NotNull Set<String> availableUUIDs;
        private final boolean strict;
        private final List<String> _errors = new ArrayList<>();
        final List<String> errors = Collections.unmodifiableList(_errors);

        @NotNull <T> T trySupply(@NotNull Supplier<T> supplier, @NotNull Function<Exception, Fallback<T>> fallbackFunction) {
            if (strict) return supplier.get();
            try {
                return supplier.get();
            } catch (Exception e) {
                var fallback = fallbackFunction.apply(e);
                _errors.add(fallback.message);
                return fallback.value;
            }
        }

        record Fallback<T>(@NotNull T value, @NotNull String message) {}
    }

    public record ModelLoadResult(@NotNull ModelBlueprint blueprint, @NotNull @Unmodifiable List<String> errors) {}

    public record ModelMeta(@NotNull FormatVersion formatVersion) {
        public static final JsonDeserializer<ModelMeta> PARSER = (json, _, _) -> new ModelMeta(
            FormatVersion.find(Objects.requireNonNull(Semver.coerce(json.getAsJsonObject().getAsJsonPrimitive("format_version").getAsString())).getMajor())
        );

        @RequiredArgsConstructor
        public enum FormatVersion {
            BLOCKBENCH_5(5) {
                @Override public @NotNull Vector3f convertAnimationRotation(@NotNull Vector3f vector) {
                    vector.x = -vector.x; vector.z = -vector.z; return vector;
                }
                @Override public @NotNull Vector3f convertAnimationPosition(@NotNull Vector3f vector) {
                    vector.x = -vector.x; vector.z = -vector.z; return vector.div(MathUtil.MODEL_TO_BLOCK_MULTIPLIER);
                }
            },
            BLOCKBENCH_LEGACY(0) {
                @Override public @NotNull Vector3f convertAnimationRotation(@NotNull Vector3f vector) {
                    vector.y = -vector.y; vector.z = -vector.z; return vector;
                }
                @Override public @NotNull Vector3f convertAnimationPosition(@NotNull Vector3f vector) {
                    vector.z = -vector.z; return vector.div(MathUtil.MODEL_TO_BLOCK_MULTIPLIER);
                }
            };

            private final int major;

            public static @NotNull FormatVersion find(int major) {
                return Arrays.stream(values()).filter(v -> v.major <= major).findFirst().orElseThrow();
            }

            public abstract @NotNull Vector3f convertAnimationRotation(@NotNull Vector3f vector);
            public abstract @NotNull Vector3f convertAnimationPosition(@NotNull Vector3f vector);
            public @NotNull Vector3f convertAnimationScale(@NotNull Vector3f vector) {
                vector.sub(1F, 1F, 1F); return vector;
            }
        }
    }

    public sealed interface ModelOutliner {
        JsonDeserializer<ModelOutliner> PARSER = (json, _, context) -> {
            if (json.isJsonPrimitive()) return new Reference(json.getAsString());
            else if (json.isJsonObject()) {
                var children = json.getAsJsonObject().getAsJsonArray("children");
                return new Tree(
                    context.deserialize(json, ModelGroup.class),
                    children.asList().stream().map(child -> (ModelOutliner) context.deserialize(child, ModelOutliner.class)).toList()
                );
            } else throw new RuntimeException();
        };

        @NotNull BlueprintElement toBlueprint(@NotNull ModelLoadContext context);
        @NotNull Stream<ModelOutliner> flatten();
        @NotNull String uuid();

        record Reference(@NotNull String uuid) implements ModelOutliner {
            @Override public @NotNull BlueprintElement toBlueprint(@NotNull ModelLoadContext context) {
                return Objects.requireNonNull(context.elements.get(uuid())).toBlueprint();
            }
            @Override public @NotNull Stream<ModelOutliner> flatten() { return Stream.of(this); }
        }

        record Tree(@NotNull ModelGroup group, @NotNull @Unmodifiable List<ModelOutliner> children) implements ModelOutliner {
            @Override public @NotNull BlueprintElement toBlueprint(@NotNull ModelLoadContext context) {
                var child = mapToList(children, c -> c.toBlueprint(context));
                var filtered = filterIsInstance(child, BlueprintElement.Cube.class).toList();
                var selectedGroup = context.groups.getOrDefault(uuid(), group);
                return new BlueprintElement.Group(
                    UUID.fromString(selectedGroup.uuid()), BoneName.of(selectedGroup.name()),
                    selectedGroup.origin(), selectedGroup.rotation().invertXZ(), child,
                    filtered.isEmpty() ? selectedGroup.visibility() : filtered.stream().anyMatch(BlueprintElement.Cube::visibility));
            }
            @Override public @NotNull Stream<ModelOutliner> flatten() {
                return children.isEmpty() ? Stream.of(this) : Stream.concat(Stream.of(this), children.stream().flatMap(ModelOutliner::flatten));
            }
            @Override public @NotNull String uuid() { return group.uuid(); }
        }
    }

    public record ModelPlaceholder(@NotNull @Unmodifiable Map<String, String> variables) {
        public static final ModelPlaceholder EMPTY = new ModelPlaceholder(Collections.emptyMap());

        public static final JsonDeserializer<ModelPlaceholder> PARSER = (json, _, _) -> new ModelPlaceholder(associate(
            Arrays.stream(json.getAsString().split("\n")).map(line -> line.split("=", 2)).filter(array -> array.length == 2),
            array -> array[0].trim(), array -> array[1].trim()
        ));

        public @NotNull String parseVariable(@NotNull String expression) {
            for (var entry : variables.entrySet()) {
                expression = expression.replace(entry.getKey(), entry.getValue());
            }
            return expression;
        }
    }

    public record ModelResolution(int width, int height) {}

    public record ModelTexture(@NotNull String name, @NotNull String source, int width, int height,
                               @SerializedName("uv_width") int uvWidth, @SerializedName("uv_height") int uvHeight,
                               @SerializedName("frame_time") int frameTime,
                               @SerializedName("frame_interpolate") boolean frameInterpolate) {
        public @NotNull BlueprintTexture toBlueprint(@NotNull ModelLoadContext context) {
            var name = nameWithoutExtension();
            return new BlueprintTexture(
                PackUtil.toPackName(name.startsWith("global_") ? name : context.name + "_" + name),
                Base64.getDecoder().decode(source().substring(source().indexOf(',') + 1)),
                width(), height(), uvWidth(), uvHeight(), !name.startsWith("-"), frameTime(), frameInterpolate()
            );
        }

        public @NotNull String nameWithoutExtension() {
            var name = name();
            var nameIndex = name.lastIndexOf('.');
            return nameIndex >= 0 ? name.substring(0, nameIndex) : name;
        }
    }

    public record ModelUV(@NotNull Float4 uv, int rotation, @Nullable JsonElement texture) {
        public boolean hasTexture() {
            return texture != null && texture.isJsonPrimitive() && texture.getAsJsonPrimitive().isNumber();
        }
        public int textureIndex() { return Objects.requireNonNull(texture).getAsInt(); }
        public @Nullable JsonObject toJson(@NotNull BlueprintLoadContext context) {
            if (!hasTexture()) return null;
            var div = uv.div(context.texture(textureIndex()).resolution(context.resolution()));
            if (!div.isValid()) return null;
            var object = new JsonObject();
            object.add("uv", div.toJson());
            if (rotation != 0) object.addProperty("rotation", rotation);
            object.addProperty("tintindex", 0);
            object.addProperty("texture", "#" + texture);
            return object;
        }
    }
}
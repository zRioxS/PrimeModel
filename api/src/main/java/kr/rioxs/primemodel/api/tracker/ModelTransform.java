package kr.rioxs.primemodel.api.tracker;

import kr.rioxs.primemodel.api.tracker.TrackerUtils.ModelRotation;
import kr.rioxs.primemodel.api.entity.BaseEntity;
import kr.rioxs.primemodel.api.util.Utils.CollectionUtil;
import kr.rioxs.primemodel.api.util.Utils.FunctionUtil;
import kr.rioxs.primemodel.api.util.Utils.MathUtil;
import kr.rioxs.primemodel.api.util.Utils.LazyFloatProvider;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import com.google.gson.JsonArray;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import java.util.Collections;
import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Container for model transformation types: body rotation, rotation logic, and scaling.
 * <p>
 * This class groups three related transformation components that define how a model
 * behaves in terms of orientation and size.
 * </p>
 *
 * @since 1.15.2
 */
public final class ModelTransform {

    public static final class EntityBodyRotator {

        private static final float DEGREE_EPSILON = 1 / MathUtil.DEGREES_TO_PACKED_BYTE;

        private final EntityTrackerRegistry registry;
        private final BaseEntity entity;
        private final LazyFloatProvider provider;
        private final Supplier<Quaternionf> headSupplier;
        private final Supplier<ModelRotation> bodySupplier;
        private final AtomicBoolean rotationLock = new AtomicBoolean();
        private int tick;
        private final Quaternionf headRotation = new Quaternionf();
        private ModelRotation rotation;
        private volatile boolean headUneven;
        private volatile boolean bodyUneven;
        private volatile boolean playerMode;
        private volatile float minBody;
        private volatile float maxBody;
        private volatile float minHead;
        private volatile float maxHead;
        private volatile float stable;
        private volatile int rotationDuration;
        private volatile int rotationDelay;

        static @NotNull RotatorData defaultData() {
            return new RotatorData(
                false,
                false,
                false,
                -75,
                75,
                -75,
                75,
                15,
                10,
                10
            );
        }

        EntityBodyRotator(@NotNull EntityTrackerRegistry registry) {
            this.registry = registry;
            this.entity = registry.entity();
            this.rotation = new ModelRotation(
                entity.pitch(),
                entity.yaw()
            );
            this.provider = new LazyFloatProvider(entity.yaw(), () -> rotationDuration * MathUtil.MINECRAFT_TICK_MILLS);
            var vector = new Vector3f();
            var vectorSupplier = LazyFloatProvider.ofVector(() -> 3 * MathUtil.MINECRAFT_TICK_MILLS, () -> vector.set(
                clampHead(entity.pitch()),
                clampHead(wrapDegrees(bodyRotation().y() - entity.headYaw())),
                0
            ));
            headSupplier = FunctionUtil.throttleTick(Tracker.TRACKER_TICK_INTERVAL, () -> MathUtil.toQuaternion(vectorSupplier.get(), headRotation));
            bodySupplier = FunctionUtil.throttleTick(() -> new ModelRotation(
                entity.pitch(),
                bodyRotation0()
            ));
            reset();
        }

        private float clampHead(float value) {
            return Math.clamp(value, headUneven ? minHead : -maxHead, maxHead);
        }

        private float clampBody(float value, float compare) {
            return Math.clamp(value, compare + (bodyUneven ? minBody : -maxBody), compare + maxBody);
        }

        /**
         * Locks or unlocks the rotation updates.
         *
         * @param lock true to lock, false to unlock
         * @return true if the state changed
         * @since 1.15.2
         */
        public boolean lockRotation(boolean lock) {
            return rotationLock.compareAndSet(!lock, lock);
        }

        @NotNull ModelRotation bodyRotation() {
            return rotationLock.get() ? rotation : (rotation = bodySupplier.get());
        }

        private float bodyRotation0() {
            if (playerMode) return entity.bodyYaw();
            if (registry.hasControllingPassenger()) return entity.yaw();
            if (entity.onWalk()) {
                tick = rotationDelay;
                return stableBodyYaw();
            } else if (MathUtil.isSimilar(entity.headYaw(), rotation.y(), DEGREE_EPSILON)) {
                tick = 0;
                return entity.headYaw();
            } else if (++tick > rotationDelay) {
                var headYaw = entity.headYaw();
                var providedYaw = provider.updateAndGet(headYaw);
                return wrapDegrees(clampBody(providedYaw, headYaw));
            }
            provider.storedValue(rotation.y());
            return rotation.y();
        }

        private float stableBodyYaw() {
            var bodyYaw = rotation.y();
            var yaw = entity.yaw();
            var minStable = yaw - stable;
            var maxStable = yaw + stable;
            return wrapDegrees(Math.clamp(bodyYaw, Math.min(minStable, maxStable), Math.max(minStable, maxStable)));
        }

        private static float wrapDegrees(float value) {
            var f = value % 360.0F;
            if (f >= 180.0F) f -= 360.0F;
            if (f < -180.0F) f += 360.0F;
            return f;
        }

        @NotNull Quaternionf headRotation() {
            return rotationLock.get() ? headRotation : headSupplier.get();
        }

        /**
         * Configures the rotator using a consumer.
         *
         * @param consumer the configuration consumer
         * @since 1.15.2
         */
        public void setValue(@NotNull Consumer<RotatorData> consumer) {
            Objects.requireNonNull(consumer);
            var data = createData();
            consumer.accept(data);
            setValue(data);
        }

        synchronized void setValue(@NotNull RotatorData data) {
            data.set(this);
        }

        /**
         * Resets the rotator to default settings.
         *
         * @since 1.15.2
         */
        public void reset() {
            setValue(defaultData());
        }

        synchronized @NotNull RotatorData createData() {
            return new RotatorData(
                headUneven,
                bodyUneven,
                playerMode,
                minBody,
                maxBody,
                minHead,
                maxHead,
                stable,
                rotationDuration,
                rotationDelay
            );
        }

        /**
         * Configuration data for the entity body rotator.
         *
         * @since 1.15.2
         */
        @Setter
        @AllArgsConstructor
        public static final class RotatorData {

            @SerializedName("head_uneven")
            private boolean headUneven;
            @SerializedName("body_uneven")
            private boolean bodyUneven;
            @SerializedName("player_mode")
            private boolean playerMode;
            @SerializedName("min_body")
            private float minBody;
            @SerializedName("max_body")
            private float maxBody;
            @SerializedName("min_head")
            private float minHead;
            @SerializedName("max_head")
            private float maxHead;
            @SerializedName("stable")
            private float stable;
            @SerializedName("rotation_duration")
            private int rotationDuration;
            @SerializedName("rotation_delay")
            private int rotationDelay;

            private void set(@NotNull EntityBodyRotator rotator) {
                rotator.headUneven = headUneven;
                rotator.bodyUneven = bodyUneven;
                rotator.playerMode = playerMode;
                rotator.minBody = Math.min(minBody, maxBody);
                rotator.maxBody = Math.max(minBody, maxBody);
                rotator.minHead = Math.min(minHead, maxHead);
                rotator.maxHead = Math.max(minHead, maxHead);
                rotator.stable = Math.max(stable, 0);
                rotator.rotationDuration = Math.max(rotationDuration, 0);
                rotator.rotationDelay = Math.max(rotationDelay, 0);
            }
        }
    }

    public static sealed interface ModelRotator extends BiFunction<Tracker, ModelRotation, ModelRotation> {
        /**
         * The global deserializer instance for rotators.
         * @since 1.15.2
         */
        Deserializer DESERIALIZER = new Deserializer();
        /**
         * Default rotator (applies rotation as-is).
         * @since 1.15.2
         */
        @NotNull
        ModelRotator DEFAULT = Objects.requireNonNull(DESERIALIZER._default.apply());
        /**
         * Empty rotator (returns zero rotation).
         * @since 1.15.2
         */
        @NotNull
        ModelRotator EMPTY = Objects.requireNonNull(DESERIALIZER.empty.apply());
        /**
         * Pitch-only rotator.
         * @since 1.15.2
         */
        @NotNull
        ModelRotator PITCH = Objects.requireNonNull(DESERIALIZER.pitch.apply());
        /**
         * Yaw-only rotator.
         * @since 1.15.2
         */
        @NotNull
        ModelRotator YAW = Objects.requireNonNull(DESERIALIZER.yaw.apply());

        /**
         * Deserializes a rotator from a JSON object.
         *
         * @param object the JSON object
         * @return the deserialized rotator, or EMPTY if invalid
         * @since 1.15.2
         */
        static @NotNull ModelRotator deserialize(@NotNull JsonObject object) {
            var result = DESERIALIZER.deserialize(object);
            return result != null ? result : EMPTY;
        }

        /**
         * Creates a lazy rotator that smooths rotation over time.
         *
         * @param mills the smoothing duration in milliseconds
         * @return the lazy rotator
         * @since 1.15.2
         */
        static @NotNull ModelRotator lazy(long mills) {
            return Objects.requireNonNull(DESERIALIZER.lazy.apply(mills));
        }

        /**
         * Returns the name of this rotator type.
         *
         * @return the name
         * @since 1.15.2
         */
        @NotNull String name();

        /**
         * Returns the source rotator if this is a chained rotator.
         *
         * @return the source rotator, or null
         * @since 1.15.2
         */
        @Nullable ModelRotator source();

        /**
         * Returns the configuration data for this rotator.
         *
         * @return the data, or null
         * @since 1.15.2
         */
        @Nullable JsonElement data();

        /**
         * Returns the root rotator in the chain.
         *
         * @return the root rotator
         * @since 1.15.2
         */
        default @NotNull ModelRotator root() {
            var source = source();
            return source != null ? source.root() : this;
        }

        /**
         * Serializes this rotator to a JSON object.
         *
         * @return the JSON object
         * @since 1.15.2
         */
        default @NotNull JsonObject serialize() {
            var json = new JsonObject();
            json.addProperty("name", name());
            var d = data();
            if (d != null) json.add("data", d);
            var s = source();
            if (s != null) json.add("source", s.serialize());
            return json;
        }

        /**
         * Applies the rotator to a tracker with default rotation.
         *
         * @param tracker the tracker
         * @return the calculated rotation
         * @since 1.15.2
         */
        default @NotNull ModelRotation apply(@NotNull Tracker tracker) {
            return apply(tracker, ModelRotation.EMPTY);
        }

        /**
         * Applies the rotator to a tracker with a base rotation.
         *
         * @param tracker the tracker
         * @param rotation the base rotation
         * @return the calculated rotation
         * @since 1.15.2
         */
        @Override
        @NotNull
        ModelRotation apply(@NotNull Tracker tracker, @NotNull ModelRotation rotation);

        /**
         * Chains this rotator with another one.
         *
         * @param rotator the next rotator in the chain
         * @return the chained rotator
         * @since 1.15.2
         */
        default @NotNull ModelRotator then(@NotNull ModelRotator rotator) {
            return new SourcedRotator(this, rotator);
        }

        /**
         * Implementation of a chained rotator.
         *
         * @param source source rotator
         * @param delegate delegated rotator
         * @since 1.15.2
         */
        record SourcedRotator(@NotNull ModelRotator source, @NotNull ModelRotator delegate) implements ModelRotator {
            @Override
            public @NotNull String name() {
                return delegate.name();
            }

            @Override
            public @Nullable JsonElement data() {
                return delegate.data();
            }

            @Override
            public @NotNull ModelRotation apply(@NotNull Tracker tracker, @NotNull ModelRotation rotation) {
                return delegate.apply(tracker, source.apply(tracker, rotation));
            }
        }

        /**
         * Functional interface for calculating rotation.
         *
         * @since 1.15.2
         */
        interface Getter {
            /**
             * Default getter returning the input rotation.
             * @since 1.15.2
             */
            Getter DEFAULT = of(r -> r);
            /**
             * Calculates the rotation.
             *
             * @param tracker the tracker
             * @param modelRotation the base rotation
             * @return the calculated rotation
             * @since 1.15.2
             */
            @NotNull
            ModelRotation apply(@NotNull Tracker tracker, @NotNull ModelRotation modelRotation);

            /**
             * Creates a constant rotation getter.
             *
             * @param rotator the rotation
             * @return the getter
             * @since 1.15.2
             */
            static @NotNull Getter of(@NotNull ModelRotation rotator) {
                return (_, _) -> rotator;
            }
            /**
             * Creates a supplier-based rotation getter.
             *
             * @param rotator the supplier
             * @return the getter
             * @since 1.15.2
             */
            static @NotNull Getter of(@NotNull Supplier<ModelRotation> rotator) {
                return (_, _) -> rotator.get();
            }
            /**
             * Creates a function-based rotation getter.
             *
             * @param rotator the function
             * @return the getter
             * @since 1.15.2
             */
            static @NotNull Getter of(@NotNull Function<ModelRotation, ModelRotation> rotator) {
                return (_, r) -> rotator.apply(r);
            }
        }

        /**
         * Builder interface for creating Getters from JSON.
         *
         * @since 1.15.2
         */
        interface Builder {
            /**
             * Builds a getter from JSON data.
             *
             * @param element the JSON data
             * @return the getter, or null if invalid
             * @since 1.15.2
             */
            @Nullable Getter build(@NotNull JsonElement element);
        }

        /**
         * Helper interface for built-in deserializers.
         *
         * @since 1.15.2
         */
        interface BuiltInDeserializer extends Function<JsonElement, ModelRotator> {
            @Override
            @Nullable
            ModelRotator apply(@NotNull JsonElement element);

            /**
             * Deserializes a default instance.
             *
             * @return the rotator
             * @since 1.15.2
             */
            default @Nullable ModelRotator apply() {
                return apply(JsonNull.INSTANCE);
            }

            /**
             * Deserializes from a long value.
             *
             * @param value the value
             * @return the rotator
             * @since 1.15.2
             */
            default @Nullable ModelRotator apply(long value) {
                return apply(new JsonPrimitive(value));
            }
        }

        /**
         * Registry and factory for rotators.
         *
         * @since 1.15.2
         */
        final class Deserializer {
            private final Map<String, Builder> builderMap = CollectionUtil.newAddressingMap();

            private final BuiltInDeserializer _default = register("default", _ -> Getter.of(r -> r));
            private final BuiltInDeserializer empty = register("empty", _ -> Getter.of(ModelRotation.EMPTY));
            private final BuiltInDeserializer yaw = register("yaw", _ -> Getter.of(ModelRotation::yaw));
            private final BuiltInDeserializer pitch = register("pitch", _ -> Getter.of(ModelRotation::pitch));
            private final BuiltInDeserializer lazy = register("lazy", j -> {
                if (j.isJsonPrimitive()) {
                    var f = j.getAsLong();
                    var xLazy = new LazyFloatProvider(f);
                    var yLazy = new LazyFloatProvider(f);
                    return Getter.of(r -> new ModelRotation(xLazy.updateAndGet(r.x()), yLazy.updateAndGet(r.y())));
                } else return null;
            });

            private Deserializer() {
            }

            /**
             * Registers a new rotator type.
             *
             * @param name the rotator name
             * @param builder the builder
             * @return a built-in deserializer helper
             * @since 1.15.2
             */
            public @NotNull BuiltInDeserializer register(@NotNull String name, @NotNull Builder builder) {
                var get = builderMap.putIfAbsent(name, builder);
                var selected = get != null ? get : builder;
                return e -> {
                    var build = selected.build(e);
                    var source = e.isJsonObject() ? e.getAsJsonObject().get("source") : null;
                    return build != null ? pack(name, source != null && source.isJsonObject() ? deserialize(source.getAsJsonObject()) : null, e, build) : null;
                };
            }

            /**
             * Deserializes a rotator from a JSON object.
             *
             * @param object the JSON object
             * @return the rotator, or null if invalid
             * @since 1.15.2
             */
            public @Nullable ModelRotator deserialize(@NotNull JsonObject object) {
                var rawName = object.getAsJsonPrimitive("name");
                if (rawName == null) return null;
                var name = rawName.getAsString();
                var get = builderMap.get(name);
                if (get == null) return null;
                var data = object.get("data");
                var source = object.getAsJsonObject().get("source");
                var build = get.build(data == null ? JsonNull.INSTANCE : data);
                return build != null ? pack(
                        name,
                        source != null && source.isJsonObject() ? deserialize(source.getAsJsonObject()) : null,
                        data,
                        build
                ) : null;
            }

            private @NotNull Pack pack(@NotNull String name, @Nullable ModelRotator source, @Nullable JsonElement data, @NotNull Getter getter) {
                return new Pack(name, source, data, getter);
            }

            private record Pack(@NotNull String name, @Nullable ModelRotator source, @Nullable JsonElement data, @NotNull Getter delegate) implements ModelRotator {

                @Override
                public @NotNull ModelRotation apply(@NotNull Tracker tracker, @NotNull ModelRotation modelRotation) {
                    return delegate.apply(tracker, modelRotation);
                }
            }
        }
    }

    public static sealed interface ModelScaler {

        /**
         * The global deserializer instance for scalers.
         * @since 1.15.2
         */
        Deserializer DESERIALIZER = new Deserializer();

        /**
         * Returns the name of this scaler type.
         *
         * @return the name
         * @since 1.15.2
         */
        @NotNull String name();

        /**
         * Calculates the scale for a given tracker.
         *
         * @param tracker the tracker
         * @return the calculated scale factor
         * @since 1.15.2
         */
        float scale(@NotNull Tracker tracker);

        /**
         * Returns the configuration data for this scaler as a JSON element.
         *
         * @return the data, or null if none
         * @since 1.15.2
         */
        @Nullable JsonElement data();

        /**
         * Deserializes a scaler from a JSON object.
         *
         * @param element the JSON object
         * @return the deserialized scaler, or the default scaler if invalid
         * @since 1.15.2
         */
        static @NotNull ModelScaler deserialize(@NotNull JsonObject element) {
            var scaler = DESERIALIZER.buildScaler(element);
            return scaler != null ? scaler : defaultScaler();
        }

        /**
         * Returns the default scaler (constant 1.0).
         *
         * @return the default scaler
         * @since 1.15.2
         */
        static @NotNull ModelScaler defaultScaler() {
            return DESERIALIZER.defaultScaler();
        }

        /**
         * Returns a scaler that uses the entity's scale attribute.
         *
         * @return the entity scaler
         * @since 1.15.2
         */
        static @NotNull ModelScaler entity() {
            return DESERIALIZER.entity.deserialize();
        }

        /**
         * Returns a constant value scaler.
         *
         * @param value the scale value
         * @return the value scaler
         * @since 1.15.2
         */
        static @NotNull ModelScaler value(float value) {
            return DESERIALIZER.value.deserialize(value);
        }

        /**
         * Creates a composite scaler that multiplies the results of multiple scalers.
         *
         * @param scalers the scalers to combine
         * @return the composite scaler
         * @since 1.15.2
         */
        static @NotNull ModelScaler composite(@NotNull ModelScaler... scalers) {
            return new Composite(new Composite.CompositeGetter(Arrays.asList(scalers)));
        }

        /**
         * Multiplies this scaler by a constant value.
         *
         * @param value the multiplier
         * @return the new composite scaler
         * @since 1.15.2
         */
        default @NotNull ModelScaler multiply(float value) {
            return composite(value(value));
        }

        /**
         * Multiplies this scaler by another scaler.
         *
         * @param scaler the other scaler
         * @return the new composite scaler
         * @since 1.15.2
         */
        default @NotNull ModelScaler composite(@NotNull ModelScaler scaler) {
            var list = new ArrayList<ModelScaler>();
            if (this instanceof Composite composite) {
                list.addAll(composite.getter.list);
            } else list.add(this);
            if (scaler instanceof Composite composite) {
                list.addAll(composite.getter.list);
            } else list.add(scaler);
            return new Composite(new Composite.CompositeGetter(list));
        }

        /**
         * Serializes this scaler to a JSON object.
         *
         * @return the JSON object
         * @since 1.15.2
         */
        default @NotNull JsonObject serialize() {
            var json = new JsonObject();
            json.addProperty("name", name());
            var d = data();
            if (d != null) json.add("data", d);
            return json;
        }

        /**
         * Functional interface for calculating scale.
         *
         * @since 1.15.2
         */
        interface Getter {
            /**
             * Default getter returning 1.0.
             * @since 1.15.2
             */
            Getter DEFAULT = _ -> 1F;
            /**
             * Getter using entity scale.
             * @since 1.15.2
             */
            Getter ENTITY = t -> t instanceof EntityTracker entityTracker ? (float) entityTracker.registry().entity().scale() : 1F;

            /**
             * Calculates the scale.
             *
             * @param tracker the tracker
             * @return the scale
             * @since 1.15.2
             */
            float get(@NotNull Tracker tracker);

            /**
             * Creates a constant value getter.
             *
             * @param value the value
             * @return the getter
             * @since 1.15.2
             */
            static @NotNull Getter value(float value) {
                return _ -> value;
            }
        }

        /**
         * Builder interface for creating Getters from JSON.
         *
         * @since 1.15.2
         */
        interface Builder {
            /**
             * Builds a getter from JSON data.
             *
             * @param data the JSON data
             * @return the getter, or null if invalid
             * @since 1.15.2
             */
            @Nullable Getter build(@NotNull JsonElement data);
        }

        /**
         * Implementation of a composite scaler.
         *
         * @since 1.15.2
         */
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        final class Composite implements ModelScaler {

            private final CompositeGetter getter;

            private record CompositeGetter(@NotNull List<ModelScaler> list) implements Getter {
                @Override
                public float get(@NotNull Tracker tracker) {
                    var f = 1F;
                    for (ModelScaler modelScaler : list) {
                        f *= modelScaler.scale(tracker);
                    }
                    return f;
                }
            }

            @NotNull
            @Override
            public String name() {
                return "composite";
            }

            @Override
            public float scale(@NotNull Tracker tracker) {
                return getter.get(tracker);
            }

            private void add(@NotNull JsonArray array, @NotNull ModelScaler scaler) {
                if (scaler instanceof Composite composite) {
                    for (ModelScaler childScaler : composite.getter.list) {
                        add(array, childScaler);
                    }
                } else array.add(scaler.serialize());
            }

            @Override
            public JsonElement data() {
                var arr = new JsonArray();
                for (ModelScaler modelScaler : getter.list) {
                    add(arr, modelScaler);
                }
                return arr.isEmpty() ? null : arr;
            }
        }

        /**
         * Helper interface for built-in deserializers.
         *
         * @since 1.15.2
         */
        interface BuiltInDeserializer extends Function<JsonElement, ModelScaler> {
            /**
             * Deserializes from a float value.
             *
             * @param value the value
             * @return the scaler
             * @since 1.15.2
             */
            default @NotNull ModelScaler deserialize(float value) {
                return apply(new JsonPrimitive(value));
            }
            /**
             * Deserializes a default instance.
             *
             * @return the scaler
             * @since 1.15.2
             */
            default @NotNull ModelScaler deserialize() {
                return apply(JsonNull.INSTANCE);
            }
        }

        /**
         * Registry and factory for scalers.
         *
         * @since 1.15.2
         */
        final class Deserializer {

            private final Map<String, Builder> getterMap = CollectionUtil.newAddressingMap();

            private final BuiltInDeserializer def = addScaler("default", _ -> Getter.DEFAULT);
            private final BuiltInDeserializer entity = addScaler("entity", _ -> Getter.ENTITY);
            private final BuiltInDeserializer value = addScaler("value", d -> d.isJsonPrimitive() ? Getter.value(d.getAsFloat()) : Getter.DEFAULT);

            private Deserializer() {
                getterMap.put("composite", d -> {
                    if (d.isJsonArray()) {
                        return new Composite.CompositeGetter(d.getAsJsonArray()
                                .asList()
                                .stream()
                                .filter(JsonElement::isJsonObject)
                                .map(element -> buildScaler(element.getAsJsonObject()))
                                .filter(Objects::nonNull)
                                .toList());
                    } else return Getter.DEFAULT;
                });
            }

            private @NotNull ModelScaler defaultScaler() {
                return def.deserialize();
            }

            /**
             * Registers a new scaler type.
             *
             * @param name the scaler name
             * @param builder the builder
             * @return a built-in deserializer helper
             * @since 1.15.2
             */
            public @NotNull BuiltInDeserializer addScaler(@NotNull String name, @NotNull Builder builder) {
                var put = getterMap.putIfAbsent(name, builder);
                var target = put != null ? put : builder;
                return element -> pack(name, target, element);
            }

            /**
             * Builds a scaler from a JSON object.
             *
             * @param rawData the JSON object
             * @return the scaler, or null if invalid
             * @since 1.15.2
             */
            public @Nullable ModelScaler buildScaler(@NotNull JsonObject rawData) {
                var n = rawData.getAsJsonPrimitive("name");
                if (n == null) return null;
                var name = n.getAsString();
                var get = getterMap.get(name);
                if (get == null) return null;
                var d = rawData.get("data");
                return pack(name, get, d);
            }

            private @NotNull ModelScaler pack(@NotNull String name, @NotNull Builder builder, @Nullable JsonElement data) {
                var build = Optional.ofNullable(builder.build(data != null ? data : JsonNull.INSTANCE))
                        .orElse(Getter.DEFAULT);
                return build instanceof Composite.CompositeGetter compositeGetter ? new Composite(compositeGetter) : new Pack(name, build, data);
            }

            private record Pack(@NotNull String name, @NotNull Getter getter, @Nullable JsonElement data) implements ModelScaler {
                @Override
                public float scale(@NotNull Tracker tracker) {
                    return getter.get(tracker);
                }
            }
        }
    }
}
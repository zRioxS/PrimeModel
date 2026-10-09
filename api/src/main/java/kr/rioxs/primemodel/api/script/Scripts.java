package kr.rioxs.primemodel.api.script;

import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator.Timed;
import kr.rioxs.primemodel.api.animation.Animations.AnimationIterator.TimedStorage;
import kr.rioxs.primemodel.api.animation.Animations.AnimationModifier;
import kr.rioxs.primemodel.api.data.raw.RawData.ModelAnimation;
import kr.rioxs.primemodel.api.tracker.Tracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class Scripts {

    private Scripts() {
        throw new RuntimeException();
    }

    public interface AnimationScript extends Consumer<Tracker> {

        AnimationScript EMPTY = of(_ -> {});

        @Override
        void accept(@NotNull Tracker tracker);

        boolean isSync();

        default @NotNull TimeScript time(float time) {
            return new TimeScript(time, this);
        }

        static @NotNull AnimationScript of(@NotNull Consumer<Tracker> source) {
            return of(false, source);
        }

        static @NotNull AnimationScript of(boolean isSync, @NotNull Consumer<Tracker> source) {
            Objects.requireNonNull(source);
            return new AnimationScript() {
                @Override
                public boolean isSync() {
                    return isSync;
                }

                @Override
                public void accept(@NotNull Tracker tracker) {
                    source.accept(tracker);
                }
            };
        }

        static @NotNull AnimationScript of(@NotNull List<AnimationScript> scriptList) {
            return switch (scriptList.size()) {
                case 0 -> EMPTY;
                case 1 -> scriptList.getFirst();
                default -> {
                    var sync = false;
                    Consumer<Tracker> consumer = _ -> {};
                    for (AnimationScript entityScript : scriptList) {
                        sync |= entityScript.isSync();
                        consumer = consumer.andThen(entityScript);
                    }
                    yield of(sync, consumer);
                }
            };
        }
    }

    public record TimeScript(float time, @NotNull AnimationScript script) implements AnimationScript, Timed {

        public static final TimeScript EMPTY = AnimationScript.EMPTY.time(0);

        @Override
        public boolean isSync() {
            return script.isSync();
        }

        @Override
        public void accept(@NotNull Tracker tracker) {
            script.accept(tracker);
        }

        public @NotNull TimeScript time(float newTime) {
            if (time == newTime) return this;
            return new TimeScript(newTime, script);
        }
    }

    @ApiStatus.Internal
    public record BlueprintScript(@NotNull String name, @NotNull AnimationIterator.Type type, float length, @NotNull List<TimeScript> scripts) {

        public static @NotNull BlueprintScript fromEmpty(@NotNull ModelAnimation animation) {
            return new BlueprintScript(
                animation.name(),
                animation.loop(),
                animation.length(),
                List.of(TimeScript.EMPTY, AnimationScript.EMPTY.time(animation.length()))
            );
        }

        public @NotNull AnimationIterator<TimeScript> iterator(@NotNull AnimationModifier modifier) {
            return modifier.type(type).create(TimedStorage.listOf(scripts));
        }
    }

    @FunctionalInterface
    public interface ScriptBuilder {
        @NotNull AnimationScript build(@NotNull ScriptData data);

        record ScriptData(
            @Nullable String args,
            @NotNull ScriptMetaData metadata
        ) {}

        interface ScriptMetaData {

            @NotNull @Unmodifiable
            Map<String, String> toMap();

            default @Nullable Boolean asBoolean(@NotNull String key) {
                var get = toMap().get(key);
                if (get == null) return null;
                return switch (get) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> null;
                };
            }

            default @Nullable Number asNumber(@NotNull String key) {
                var get = toMap().get(key);
                if (get == null) return null;
                try {
                    return new BigDecimal(get);
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            default @Nullable String asString(@NotNull String key) {
                return toMap().get(key);
            }
        }
    }
}
package kr.rioxs.primemodel.api.event;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent;
import kr.rioxs.primemodel.api.PrimeModelPlatform;
import kr.rioxs.primemodel.api.data.DataClasses.ModelAsset;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.ModelBlueprint;
import kr.rioxs.primemodel.api.data.renderer.ModelRenderer;
import kr.rioxs.primemodel.api.pack.PackZipper;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.tracker.TrackerUtils.DummyTracker;
import kr.rioxs.primemodel.api.tracker.EntityTracker;
import kr.rioxs.primemodel.api.tracker.Tracker;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Merged record events.
 */
public final class RecordEvents {

    private RecordEvents() {
        throw new RuntimeException();
    }

    public record AnimationSignalEvent(
        @NotNull PlatformPlayer player,
        @NotNull String signal
    ) implements ModelEvent {
    }

    public record CloseTrackerEvent(
        @NotNull Tracker tracker,
        @NotNull Tracker.CloseReason reason
    ) implements ModelEvent {
    }

    public record CreateDummyTrackerEvent(
        @NotNull DummyTracker tracker
    ) implements ModelEvent {
    }

    public record CreateEntityTrackerEvent(
        @NotNull EntityTracker tracker
    ) implements ModelEvent {
    }

    public record ModelAssetsEvent(@NotNull ModelRenderer.Type type, @NotNull Set<ModelAsset> assets) implements ModelEvent {
        public void addAsset(@NotNull ModelAsset asset) {
            if (!assets.add(asset)) throw new IllegalArgumentException("Asset " + asset.name() + " already exists.");
        }
    }

    public record ModelDespawnAtPlayerEvent(
        @NotNull PlatformPlayer player,
        @NotNull Tracker tracker
    ) implements ModelEvent {
    }

    public record ModelImportedEvent(
        @NotNull ModelBlueprint blueprint,
        @NotNull ModelRenderer renderer
    ) implements ModelEvent {
    }

    public record PlayerPerAnimationEndEvent(
        @NotNull Tracker tracker,
        @NotNull PlatformPlayer player
    ) implements ModelEvent {
    }

    public record PlayerPerAnimationStartEvent(
        @NotNull Tracker tracker,
        @NotNull PlatformPlayer player
    ) implements ModelEvent {
    }

    public record PluginEndReloadEvent(
        @NotNull PrimeModelPlatform.ReloadResult result
    ) implements ModelEvent {
    }

    public record PluginStartReloadEvent(
        @NotNull PackZipper zipper
    ) implements ModelEvent {
    }
}
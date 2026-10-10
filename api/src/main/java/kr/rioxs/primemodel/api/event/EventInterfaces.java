package kr.rioxs.primemodel.api.event;

import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.PrimeModelPlatform;
import kr.rioxs.primemodel.api.bone.RenderedBone;
import kr.rioxs.primemodel.api.data.DataClasses.ModelAsset;
import kr.rioxs.primemodel.api.data.blueprint.Blueprints.ModelBlueprint;
import kr.rioxs.primemodel.api.data.renderer.ModelRenderer;
import kr.rioxs.primemodel.api.nms.HitBox;
import kr.rioxs.primemodel.api.nms.NMSTypes.ModelInteractionHand;
import kr.rioxs.primemodel.api.pack.PackZipper;
import kr.rioxs.primemodel.api.platform.PlatformEntity;
import kr.rioxs.primemodel.api.platform.PlatformLocation;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile;
import kr.rioxs.primemodel.api.tracker.EntityTracker;
import kr.rioxs.primemodel.api.tracker.Tracker;
import kr.rioxs.primemodel.api.tracker.TrackerUtils.DummyTracker;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Set;

/**
 * Merged event interfaces and implementations.
 */
public final class EventInterfaces {

    private EventInterfaces() {
        throw new RuntimeException();
    }

    // ===== Base Interfaces =====

    public interface ModelEvent {

        default boolean call() {
            return PrimeModel.eventBus().call(this).triggered();
        }
    }

    public interface CancellableEvent extends ModelEvent {

        boolean isCancelled();

        void setCancelled(boolean cancel);

        @Override
        default boolean call() {
            return ModelEvent.super.call() && !isCancelled();
        }
    }

    public interface ModelDamageSource {

        @Nullable PlatformEntity getCausingEntity();

        @Nullable PlatformEntity getDirectEntity();

        @Nullable PlatformLocation getDamageLocation();

        @Nullable PlatformLocation getSourceLocation();

        boolean isIndirect();

        float getFoodExhaustion();

        boolean scalesWithDifficulty();
    }

    public interface ModelEventApplication {
        boolean isEnabled();
    }

    public interface ModelEventListener {

        ModelEventListener NONE = () -> {};

        void unregister();
    }

    // ===== Record Events =====

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

    // ===== Class Events =====

    public static final class CreatePlayerSkinEvent implements ModelEvent {

        private ModelProfile modelProfile;

        @ApiStatus.Internal
        public CreatePlayerSkinEvent(@NotNull ModelProfile modelProfile) {
            this.modelProfile = modelProfile;
        }

        public ModelProfile modelProfile() {
            return modelProfile;
        }

        public void modelProfile(ModelProfile modelProfile) {
            this.modelProfile = modelProfile;
        }

        public ModelProfile getModelProfile() {
            return modelProfile;
        }

        public void setModelProfile(ModelProfile modelProfile) {
            this.modelProfile = modelProfile;
        }
    }

    public static final class DismountModelEvent implements CancellableEvent {

        private final EntityTracker tracker;
        private final RenderedBone bone;
        private final HitBox hitbox;
        private final PlatformEntity entity;
        private boolean cancelled;

        @ApiStatus.Internal
        public DismountModelEvent(@NotNull EntityTracker tracker, @NotNull RenderedBone bone, @NotNull HitBox hitbox, @NotNull PlatformEntity entity) {
            this.tracker = tracker;
            this.bone = bone;
            this.hitbox = hitbox;
            this.entity = entity;
        }

        public @NotNull EntityTracker tracker() { return tracker; }
        public @NotNull RenderedBone bone() { return bone; }
        public @NotNull HitBox hitbox() { return hitbox; }
        public PlatformEntity entity() { return entity; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public static final class ModelSpawnAtPlayerEvent implements CancellableEvent {

        private final Tracker tracker;
        private final PlatformPlayer player;
        private boolean cancelled;

        @ApiStatus.Internal
        public ModelSpawnAtPlayerEvent(@NotNull PlatformPlayer player, @NotNull Tracker tracker) {
            this.tracker = tracker;
            this.player = player;
        }

        public @NotNull Tracker tracker() { return tracker; }
        public @NotNull PlatformPlayer player() { return player; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public static final class MountModelEvent implements CancellableEvent {

        private final EntityTracker tracker;
        private final RenderedBone bone;
        private final HitBox hitBox;
        private final PlatformEntity entity;
        private boolean cancelled;

        @ApiStatus.Internal
        public MountModelEvent(@NotNull EntityTracker tracker, @NotNull RenderedBone bone, @NotNull HitBox hitBox, @NotNull PlatformEntity entity) {
            this.tracker = tracker;
            this.bone = bone;
            this.hitBox = hitBox;
            this.entity = entity;
        }

        public @NotNull EntityTracker tracker() { return tracker; }
        public @NotNull RenderedBone bone() { return bone; }
        public @NotNull HitBox hitbox() { return hitBox; }
        public PlatformEntity entity() { return entity; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public static final class PlayerHideTrackerEvent implements CancellableEvent {

        private final Tracker tracker;
        private final PlatformPlayer player;
        private boolean cancelled;

        @ApiStatus.Internal
        public PlayerHideTrackerEvent(@NotNull Tracker tracker, @NotNull PlatformPlayer player) {
            this.tracker = tracker;
            this.player = player;
        }

        public @NotNull Tracker tracker() { return tracker; }
        public @NotNull PlatformPlayer player() { return player; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public static final class PlayerShowTrackerEvent implements CancellableEvent {

        private final Tracker tracker;
        private final PlatformPlayer player;
        private boolean cancelled;

        @ApiStatus.Internal
        public PlayerShowTrackerEvent(@NotNull Tracker tracker, @NotNull PlatformPlayer player) {
            this.tracker = tracker;
            this.player = player;
        }

        public @NotNull Tracker tracker() { return tracker; }
        public @NotNull PlatformPlayer player() { return player; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    public static final class RemovePlayerSkinEvent implements CancellableEvent {

        private final ModelProfile modelProfile;
        private boolean cancelled;

        public RemovePlayerSkinEvent(@NotNull ModelProfile modelProfile) {
            this.modelProfile = modelProfile;
        }

        public ModelProfile modelProfile() { return modelProfile; }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    // ===== HitBox Events =====

    public interface HitBoxEvent extends ModelEvent {

        @NotNull HitBox getHitBox();
    }

    public record HitBoxCreateEvent(@NotNull HitBox hitBox) implements HitBoxEvent {

        @Override
        public @NotNull HitBox getHitBox() {
            return hitBox;
        }
    }

    @Getter
    @Setter
    public static final class HitBoxDamagedEvent implements CancellableEvent, HitBoxEvent {

        private final @NotNull HitBox hitBox;
        private final ModelDamageSource source;

        private float damage;
        private boolean cancelled;

        @ApiStatus.Internal
        public HitBoxDamagedEvent(@NotNull HitBox hitBox, @NotNull ModelDamageSource source, float damage) {
            this.hitBox = hitBox;
            this.source = source;
            this.damage = damage;
        }

        @Override
        public @NotNull HitBox getHitBox() { return hitBox; }
    }

    public record HitBoxDismountEvent(@NotNull HitBox hitBox, @NotNull PlatformEntity entity) implements HitBoxEvent {
        @Override
        public @NotNull HitBox getHitBox() {
            return hitBox;
        }
    }

    @Getter
    public static final class HitBoxInteractAtEvent implements CancellableEvent, HitBoxEvent {

        @Setter
        private boolean cancelled;
        private final PlatformPlayer who;
        private final @NotNull HitBox hitBox;
        private final @NotNull ModelInteractionHand hand;
        private final @NotNull Vector3f position;

        @ApiStatus.Internal
        public HitBoxInteractAtEvent(@NotNull PlatformPlayer who, @NotNull HitBox hitBox, @NotNull ModelInteractionHand hand, @NotNull Vector3f position) {
            this.who = who;
            this.hitBox = hitBox;
            this.hand = hand;
            this.position = position;
        }

        @Override
        public @NotNull HitBox getHitBox() { return hitBox; }
    }

    public record HitBoxMountEvent(@NotNull HitBox hitBox, @NotNull PlatformEntity entity) implements HitBoxEvent {
        @Override
        public @NotNull HitBox getHitBox() {
            return hitBox;
        }
    }

    public record HitBoxRemoveEvent(@NotNull HitBox hitBox) implements HitBoxEvent {

        @Override
        public @NotNull HitBox getHitBox() {
            return hitBox;
        }
    }
}
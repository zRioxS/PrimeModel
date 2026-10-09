package kr.rioxs.primemodel.api.event;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent;
import kr.rioxs.primemodel.api.event.EventInterfaces.CancellableEvent;
import kr.rioxs.primemodel.api.bone.RenderedBone;
import kr.rioxs.primemodel.api.nms.HitBox;
import kr.rioxs.primemodel.api.platform.PlatformEntity;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile;
import kr.rioxs.primemodel.api.tracker.EntityTracker;
import kr.rioxs.primemodel.api.tracker.Tracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Merged event class containing all CancellableEvent implementations.
 * Originally split across multiple files.
 */
public final class ClassEvents {

    private ClassEvents() {
        throw new RuntimeException();
    }

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
}
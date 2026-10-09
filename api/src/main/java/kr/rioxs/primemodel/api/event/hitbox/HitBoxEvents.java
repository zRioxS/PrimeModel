package kr.rioxs.primemodel.api.event.hitbox;

import kr.rioxs.primemodel.api.event.EventInterfaces.CancellableEvent;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelDamageSource;
import kr.rioxs.primemodel.api.event.EventInterfaces.ModelEvent;
import kr.rioxs.primemodel.api.nms.HitBox;
import kr.rioxs.primemodel.api.nms.NMSTypes.ModelInteractionHand;
import kr.rioxs.primemodel.api.platform.PlatformEntity;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public final class HitBoxEvents {

    private HitBoxEvents() {
        throw new RuntimeException();
    }

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
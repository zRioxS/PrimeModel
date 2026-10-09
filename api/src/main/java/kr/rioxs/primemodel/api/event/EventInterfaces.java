package kr.rioxs.primemodel.api.event;
import kr.rioxs.primemodel.api.PrimeModel;
import kr.rioxs.primemodel.api.platform.PlatformEntity;
import kr.rioxs.primemodel.api.platform.PlatformLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Merged event interfaces.
 */
public final class EventInterfaces {

    private EventInterfaces() {
        throw new RuntimeException();
    }

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
}
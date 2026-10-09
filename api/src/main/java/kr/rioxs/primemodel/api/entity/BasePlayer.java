package kr.rioxs.primemodel.api.entity;

import kr.rioxs.primemodel.api.nms.NMSTypes.Profiled;
import kr.rioxs.primemodel.api.platform.PlatformPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * An adapter of player
 */
public interface BasePlayer extends BaseEntity, Profiled {

    /**
     * Updates current inventory
     */
    void updateInventory();

    @Override
    @NotNull PlatformPlayer platform();
}

package kr.rioxs.primemodel.api.nms;

import kr.rioxs.primemodel.api.armor.PlayerArmor;
import kr.rioxs.primemodel.api.player.PlayerSkinParts;
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an entity that has a player profile, armor, and skin customization settings.
 * <p>
 * This interface is typically implemented by player-like entities or trackers that need to render player skins and equipment.
 * </p>
 *
 * @since 1.15.2
 */
public interface Profiled {

    /**
     * Returns the model profile (skin) of the entity.
     *
     * @return the model profile
     * @since 1.15.2
     */
    @NotNull ModelProfile profile();

    /**
     * Returns the armor equipment of the entity.
     *
     * @return the player armor
     * @since 1.15.2
     */
    @NotNull PlayerArmor armors();

    /**
     * Returns the skin customization parts (e.g., jacket, hat) of the entity.
     *
     * @return the skin parts
     * @since 1.15.2
     */
    @NotNull PlayerSkinParts skinParts();
}

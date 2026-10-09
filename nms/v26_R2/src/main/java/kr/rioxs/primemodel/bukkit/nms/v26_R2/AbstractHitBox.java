package kr.rioxs.primemodel.bukkit.nms.v26_R2;

import kr.rioxs.primemodel.api.nms.HitBox;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AbstractHitBox extends ArmorStand implements HitBox {

    AbstractHitBox(@NotNull Level level) {
        super(EntityTypes.ARMOR_STAND, level);
    }

    @Override //Only for provide compiler hint for Kotlin jvm
    public final boolean equals(@Nullable Object other) {
        return super.equals(other);
    }

    @Override //Only for provide compiler hint for Kotlin jvm
    public final int hashCode() {
        return super.hashCode();
    }
}
